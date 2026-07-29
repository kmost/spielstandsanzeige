package de.kmost.scoreboard.model;

import java.util.function.LongSupplier;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Spieluhr. {@code elapsedMillis} ist die gesamte verbrauchte Spielzeit, monoton über alle
 * Perioden (bei 2×30 min also 0 bis 60 min). Die Uhr wird von außen per {@link #tick()}
 * getrieben; die Zeit selbst kommt aus der Nanosekunden-Quelle, nicht aus der Tick-Frequenz.
 */
public class GameClock {

    public enum Phase { NOT_STARTED, RUNNING, PAUSED, HALF_TIME, FINISHED }

    private final GameConfig config;
    private final LongSupplier nanoSource;

    private long accumulatedMillis;
    private long startNanos;
    private Runnable onPeriodEnd;

    private final ReadOnlyLongWrapper elapsedMillis = new ReadOnlyLongWrapper(0);
    private final ReadOnlyIntegerWrapper period = new ReadOnlyIntegerWrapper(1);
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<Phase> phase = new ReadOnlyObjectWrapper<>(Phase.NOT_STARTED);

    public GameClock(GameConfig config) {
        this(config, System::nanoTime);
    }

    public GameClock(GameConfig config, LongSupplier nanoSource) {
        this.config = config;
        this.nanoSource = nanoSource;
    }

    /** Startet die Uhr bzw. setzt sie nach einer Pause fort. */
    public void start() {
        Phase p = phase.get();
        if (p != Phase.NOT_STARTED && p != Phase.PAUSED) {
            return;
        }
        startNanos = nanoSource.getAsLong();
        running.set(true);
        phase.set(Phase.RUNNING);
    }

    public void pause() {
        if (phase.get() != Phase.RUNNING) {
            return;
        }
        accumulatedMillis = currentElapsed();
        running.set(false);
        phase.set(Phase.PAUSED);
    }

    /** Startet die nächste Periode (2. Halbzeit); nur aus der Halbzeitpause heraus erlaubt. */
    public void startNextPeriod() {
        if (phase.get() != Phase.HALF_TIME) {
            return;
        }
        period.set(period.get() + 1);
        startNanos = nanoSource.getAsLong();
        running.set(true);
        phase.set(Phase.RUNNING);
    }

    /** Beendet das Spiel sofort (Spielabbruch): Die Uhr stoppt endgültig bei der aktuellen Zeit. */
    public void finish() {
        if (phase.get() == Phase.FINISHED) {
            return;
        }
        if (phase.get() == Phase.RUNNING) {
            accumulatedMillis = currentElapsed();
            elapsedMillis.set(accumulatedMillis);
        }
        running.set(false);
        phase.set(Phase.FINISHED);
    }

    /**
     * Stellt die Uhr manuell auf eine Zeit innerhalb der aktuellen Periode
     * (z. B. nach Fehlstart oder verpasstem Stopp); Werte außerhalb werden auf
     * die Periodengrenzen begrenzt. Läuft die Uhr, läuft sie ab der neuen Zeit
     * weiter. Aus der Halbzeitpause heraus wird die Periode wieder geöffnet
     * (Phase PAUSED); nach Spielende ist keine Korrektur mehr möglich.
     */
    public void setElapsed(long millis) {
        if (phase.get() == Phase.FINISHED) {
            return;
        }
        long periodStart = (long) (period.get() - 1) * config.periodMillis();
        long clamped = Math.clamp(millis, periodStart, currentPeriodEndMillis());
        accumulatedMillis = clamped;
        startNanos = nanoSource.getAsLong();
        elapsedMillis.set(clamped);
        if (phase.get() == Phase.HALF_TIME && clamped < currentPeriodEndMillis()) {
            phase.set(Phase.PAUSED);
        }
    }

    public void tick() {
        if (!running.get()) {
            return;
        }
        long elapsed = currentElapsed();
        long periodEnd = currentPeriodEndMillis();
        if (elapsed >= periodEnd) {
            accumulatedMillis = periodEnd;
            running.set(false);
            phase.set(period.get() >= config.mode().periodCount() ? Phase.FINISHED : Phase.HALF_TIME);
            elapsedMillis.set(periodEnd);
            if (onPeriodEnd != null) {
                onPeriodEnd.run();
            }
        } else {
            elapsedMillis.set(elapsed);
        }
    }

    private long currentElapsed() {
        return accumulatedMillis + (nanoSource.getAsLong() - startNanos) / 1_000_000;
    }

    public long currentPeriodEndMillis() {
        return (long) period.get() * config.periodMillis();
    }

    public void setOnPeriodEnd(Runnable onPeriodEnd) {
        this.onPeriodEnd = onPeriodEnd;
    }

    public ReadOnlyLongProperty elapsedMillisProperty() {
        return elapsedMillis.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty periodProperty() {
        return period.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Phase> phaseProperty() {
        return phase.getReadOnlyProperty();
    }
}
