package de.kmost.scoreboard.model;

import java.util.function.LongSupplier;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Gesamter Spielzustand: Konfiguration, Uhr, Tore, Zeitstrafen und Team-Timeouts.
 * Kampfgericht-Konsole und Publikumsanzeige beobachten dasselbe GameState-Objekt
 * über Properties und ObservableLists und bleiben so automatisch synchron.
 */
public class GameState {

    private final GameConfig config;
    private final GameClock clock;
    private final LongSupplier nanoSource;
    private final IntegerProperty homeScore = new SimpleIntegerProperty(0);
    private final IntegerProperty guestScore = new SimpleIntegerProperty(0);
    private final IntegerProperty homeTimeoutsUsed = new SimpleIntegerProperty(0);
    private final IntegerProperty guestTimeoutsUsed = new SimpleIntegerProperty(0);
    private final ObservableList<PenaltyTimer> homePenalties = FXCollections.observableArrayList();
    private final ObservableList<PenaltyTimer> guestPenalties = FXCollections.observableArrayList();
    private final ObjectProperty<TeamTimeout> activeTimeout = new SimpleObjectProperty<>();
    private final ObjectProperty<Shootout> shootout = new SimpleObjectProperty<>();
    private Runnable onTimeoutEnd;
    private Runnable onShootoutEnd;

    public GameState(GameConfig config) {
        this(config, System::nanoTime);
    }

    public GameState(GameConfig config, LongSupplier nanoSource) {
        this.config = config;
        this.nanoSource = nanoSource;
        this.clock = new GameClock(config, nanoSource);
    }

    public GameConfig config() {
        return config;
    }

    public GameClock clock() {
        return clock;
    }

    public IntegerProperty scoreProperty(TeamSide side) {
        return side == TeamSide.HOME ? homeScore : guestScore;
    }

    public IntegerProperty timeoutsUsedProperty(TeamSide side) {
        return side == TeamSide.HOME ? homeTimeoutsUsed : guestTimeoutsUsed;
    }

    public ObservableList<PenaltyTimer> penalties(TeamSide side) {
        return side == TeamSide.HOME ? homePenalties : guestPenalties;
    }

    public ObjectProperty<TeamTimeout> activeTimeoutProperty() {
        return activeTimeout;
    }

    public void addGoal(TeamSide side) {
        scoreProperty(side).set(scoreProperty(side).get() + 1);
    }

    public void removeGoal(TeamSide side) {
        IntegerProperty score = scoreProperty(side);
        score.set(Math.max(0, score.get() - 1));
    }

    public void addPenalty(TeamSide side) {
        addPenalty(side, null);
    }

    public void addPenalty(TeamSide side, String playerNumber) {
        String number = playerNumber == null || playerNumber.isBlank() ? null : playerNumber.strip();
        penalties(side).add(new PenaltyTimer(side, number,
                clock.elapsedMillisProperty().get(),
                config.profile().penaltyDuration().toMillis()));
    }

    public void removePenalty(PenaltyTimer timer) {
        homePenalties.remove(timer);
        guestPenalties.remove(timer);
    }

    /**
     * Startet ein Team-Timeout: hält die Spieluhr an und startet den Echtzeit-Countdown.
     * Die Anzahl wird nur gezählt, nicht begrenzt — das Kampfgericht entscheidet.
     */
    public void startTeamTimeout(TeamSide side) {
        if (activeTimeout.get() != null) {
            return;
        }
        GameClock.Phase phase = clock.phaseProperty().get();
        if (phase != GameClock.Phase.RUNNING && phase != GameClock.Phase.PAUSED) {
            return;
        }
        clock.pause();
        timeoutsUsedProperty(side).set(timeoutsUsedProperty(side).get() + 1);
        activeTimeout.set(new TeamTimeout(side, nanoSource.getAsLong(),
                config.profile().teamTimeoutDuration().toMillis()));
    }

    /** Beendet das laufende Team-Timeout vorzeitig (ohne Signal). */
    public void endTeamTimeout() {
        activeTimeout.set(null);
    }

    /** Das 7-m-Werfen; {@code null}, solange keines gestartet wurde. */
    public ObjectProperty<Shootout> shootoutProperty() {
        return shootout;
    }

    /**
     * Startet das 7-m-Werfen mit dem gewählten Startteam — nur einmal und nur,
     * wenn das Spiel sein Abschnittsende regulär erreicht hat (gleiche Bedingung
     * wie für eine Verlängerung; nach Spielabbruch nicht möglich).
     */
    public void startShootout(TeamSide startingTeam) {
        if (shootout.get() != null || !clock.canStartOvertime()) {
            return;
        }
        shootout.set(new Shootout(startingTeam));
    }

    /** Verbucht den nächsten 7-m-Wurf; ein Tor zählt auf den Spielstand. */
    public void recordShootoutAttempt(boolean goal) {
        Shootout current = shootout.get();
        if (current == null || current.winnerProperty().get() != null) {
            return;
        }
        TeamSide thrower = current.nextThrowerProperty().get();
        current.record(goal);
        if (goal) {
            addGoal(thrower);
        }
        if (current.winnerProperty().get() != null && onShootoutEnd != null) {
            onShootoutEnd.run();
        }
    }

    /** Nimmt den letzten 7-m-Wurf zurück; ein verbuchtes Tor wird wieder abgezogen. */
    public void undoShootoutAttempt() {
        Shootout current = shootout.get();
        if (current == null) {
            return;
        }
        Shootout.Attempt removed = current.undoLast();
        if (removed != null && removed.goal()) {
            removeGoal(removed.side());
        }
    }

    public void setOnShootoutEnd(Runnable onShootoutEnd) {
        this.onShootoutEnd = onShootoutEnd;
    }

    /** Bricht das Spiel sofort ab: Uhr stoppt endgültig, ein laufendes Timeout endet. */
    public void abortGame() {
        activeTimeout.set(null);
        clock.finish();
    }

    public void setOnTimeoutEnd(Runnable onTimeoutEnd) {
        this.onTimeoutEnd = onTimeoutEnd;
    }

    public void tick() {
        clock.tick();
        long elapsed = clock.elapsedMillisProperty().get();
        updatePenalties(homePenalties, elapsed);
        updatePenalties(guestPenalties, elapsed);
        updateTimeout();
    }

    private void updateTimeout() {
        TeamTimeout timeout = activeTimeout.get();
        if (timeout == null) {
            return;
        }
        // Startet das Kampfgericht die Uhr wieder, ist das Timeout beendet (ohne Signal)
        if (clock.runningProperty().get()) {
            activeTimeout.set(null);
            return;
        }
        timeout.update(nanoSource.getAsLong());
        if (timeout.isExpired()) {
            activeTimeout.set(null);
            if (onTimeoutEnd != null) {
                onTimeoutEnd.run();
            }
        }
    }

    private static void updatePenalties(ObservableList<PenaltyTimer> penalties, long elapsed) {
        for (PenaltyTimer timer : penalties) {
            timer.update(elapsed);
        }
        penalties.removeIf(PenaltyTimer::isExpired);
    }
}
