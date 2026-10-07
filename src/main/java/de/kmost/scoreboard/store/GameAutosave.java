package de.kmost.scoreboard.store;

import java.util.Objects;
import java.util.function.LongSupplier;

import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameSnapshot;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.Shootout;
import de.kmost.scoreboard.model.TeamSide;
import javafx.beans.InvalidationListener;

/**
 * Sichert ein Spiel fortlaufend im {@link GameSnapshotStore}: sofort bei Änderungen an
 * Spielstand, Strafen, Timeouts, Abschnitt und 7-m-Werfen, sonst höchstens einmal pro
 * Sekunde über {@link #tick()} (die laufende Uhr, Zeitkorrekturen, Strafen-Verlängerung).
 * Es wird nur geschrieben, wenn sich das Abbild geändert hat. Ist das Spiel vorbei
 * ({@link GameState#isOver()}), wird die Sicherung gelöscht. Ein noch nicht gestartetes
 * Spiel wird nicht gesichert. Alles läuft auf dem FX-Thread.
 */
public class GameAutosave {

    private static final long INTERVAL_NANOS = 1_000_000_000L;

    private final GameState state;
    private final GameSnapshotStore store;
    private final LongSupplier nanoSource;
    private final InvalidationListener onChange = obs -> sync();

    private long lastTickNanos;
    private boolean synced;
    private GameSnapshot last; // null = Sicherung gelöscht (Spiel vorbei)
    private boolean disposed;

    public GameAutosave(GameState state, GameSnapshotStore store) {
        this(state, store, System::nanoTime);
    }

    public GameAutosave(GameState state, GameSnapshotStore store, LongSupplier nanoSource) {
        this.state = state;
        this.store = store;
        this.nanoSource = nanoSource;
        this.lastTickNanos = nanoSource.getAsLong();
        for (TeamSide side : TeamSide.values()) {
            state.scoreProperty(side).addListener(onChange);
            state.timeoutsUsedProperty(side).addListener(onChange);
            state.penalties(side).addListener(onChange);
        }
        state.clock().phaseProperty().addListener(onChange);
        state.endedProperty().addListener(onChange);
        state.clock().periodProperty().addListener(onChange);
        state.shootoutProperty().addListener(obs -> {
            observe(state.shootoutProperty().get());
            sync();
        });
        observe(state.shootoutProperty().get());
    }

    /** Würfe und Sieger des 7-m-Werfens: Der Sieger ändert sich erst nach der Wurfliste. */
    private void observe(Shootout shootout) {
        if (shootout != null) {
            shootout.attempts().addListener(onChange);
            shootout.winnerProperty().addListener(onChange);
        }
    }

    /** Vom Takt der App aufgerufen; sichert höchstens einmal pro Sekunde. */
    public void tick() {
        long now = nanoSource.getAsLong();
        if (now - lastTickNanos >= INTERVAL_NANOS) {
            lastTickNanos = now;
            sync();
        }
    }

    /** Sichert sofort (z. B. beim Schließen des Fensters). */
    public void saveNow() {
        sync();
    }

    /** Beendet die Sicherung dieses Spiels, ohne die Datei anzufassen. */
    public void dispose() {
        disposed = true;
    }

    private void sync() {
        if (disposed || state.clock().phaseProperty().get() == GameClock.Phase.NOT_STARTED) {
            return;
        }
        GameSnapshot current = state.isOver() ? null : state.snapshot();
        if (synced && Objects.equals(current, last)) {
            return;
        }
        if (current == null) {
            store.delete();
        } else {
            store.save(current);
        }
        synced = true;
        last = current;
    }
}
