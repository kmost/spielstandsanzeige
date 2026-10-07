package de.kmost.scoreboard.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
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
    private final PenaltyBoard penalties = new PenaltyBoard();
    private final TimeoutTracker timeouts = new TimeoutTracker();
    private final ObjectProperty<Shootout> shootout = new SimpleObjectProperty<>();
    private final ReadOnlyBooleanWrapper ended = new ReadOnlyBooleanWrapper(false);
    private final List<Runnable> timeoutEndListeners = new ArrayList<>();
    private final List<Runnable> shootoutEndListeners = new ArrayList<>();
    // Regeln, wann etwas möglich ist: Die UI bindet nur daran und dupliziert sie nicht.
    // Die Bindings müssen die Properties der Uhr lesen (nicht deren Methoden), sonst werden
    // Änderungen nicht weitergereicht, sobald jemand die Property beobachtet.
    private final ReadOnlyBooleanWrapper canStartTeamTimeout = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canStartNextSegment = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canStartShootout = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canEndGame = new ReadOnlyBooleanWrapper(false);

    public GameState(GameConfig config) {
        this(config, System::nanoTime);
    }

    public GameState(GameConfig config, LongSupplier nanoSource) {
        this.config = config;
        this.nanoSource = nanoSource;
        this.clock = new GameClock(config, nanoSource);
        canStartTeamTimeout.bind(Bindings.createBooleanBinding(
                () -> timeouts.activeProperty().get() == null
                        && (clock.phaseProperty().get() == GameClock.Phase.RUNNING
                            || clock.phaseProperty().get() == GameClock.Phase.PAUSED),
                timeouts.activeProperty(), clock.phaseProperty()));
        canStartNextSegment.bind(Bindings.createBooleanBinding(
                () -> shootout.get() == null
                        && (clock.phaseProperty().get() == GameClock.Phase.HALF_TIME
                            || clock.canStartOvertimeProperty().get()),
                shootout, clock.phaseProperty(), clock.canStartOvertimeProperty()));
        canStartShootout.bind(Bindings.createBooleanBinding(
                () -> shootout.get() == null && clock.canStartOvertimeProperty().get(),
                shootout, clock.canStartOvertimeProperty()));
        canEndGame.bind(Bindings.createBooleanBinding(
                () -> !ended.get()
                        && shootout.get() == null
                        && clock.canStartOvertimeProperty().get()
                        && homeScore.get() == guestScore.get(),
                ended, shootout, clock.canStartOvertimeProperty(), homeScore, guestScore));
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
        return timeouts.usedProperty(side);
    }

    /** Die Strafen eines Teams in der Reihenfolge ihres Anlegens. */
    public ObservableList<PenaltyTimer> penalties(TeamSide side) {
        return penalties.list(side);
    }

    /**
     * Die Strafen eines Teams in Anzeigereihenfolge: älteste (kürzeste Restzeit) zuerst.
     * Ordnet sich bei Verlängerung und Zeitkorrektur neu; schreibgeschützt.
     */
    public ObservableList<PenaltyTimer> sortedPenalties(TeamSide side) {
        return penalties.sorted(side);
    }

    public ObjectProperty<TeamTimeout> activeTimeoutProperty() {
        return timeouts.activeProperty();
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
        penalties.add(side, playerNumber, clock.elapsedMillisProperty().get(),
                config.profile().penaltyDuration().toMillis());
    }

    /** Verlängert eine laufende Zeitstrafe auf die doppelte Dauer (2 → 4 Minuten). */
    public void extendPenalty(PenaltyTimer timer) {
        timer.extend();
    }

    public void removePenalty(PenaltyTimer timer) {
        penalties.remove(timer);
    }

    /**
     * Startet ein Team-Timeout: hält die Spieluhr an und startet den Echtzeit-Countdown.
     * Die Anzahl wird nur gezählt, nicht begrenzt — das Kampfgericht entscheidet.
     */
    public void startTeamTimeout(TeamSide side) {
        if (!canStartTeamTimeout.get()) {
            return;
        }
        clock.pause();
        timeouts.start(side, nanoSource.getAsLong(), config.profile().teamTimeoutDuration().toMillis());
    }

    /** Ein Team-Timeout ist möglich, wenn keines läuft und die Uhr läuft oder pausiert ist. */
    public ReadOnlyBooleanProperty canStartTeamTimeoutProperty() {
        return canStartTeamTimeout.getReadOnlyProperty();
    }

    /** Beendet das laufende Team-Timeout vorzeitig (ohne Signal). */
    public void endTeamTimeout() {
        timeouts.end();
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
        if (!canStartShootout.get()) {
            return;
        }
        shootout.set(new Shootout(startingTeam));
    }

    /** Das 7-m-Werfen ist möglich, solange keines läuft und das Spiel regulär zu Ende ist. */
    public ReadOnlyBooleanProperty canStartShootoutProperty() {
        return canStartShootout.getReadOnlyProperty();
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
        if (current.winnerProperty().get() != null) {
            for (Runnable listener : List.copyOf(shootoutEndListeners)) {
                listener.run();
            }
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

    /** Wird aufgerufen, wenn das 7-m-Werfen einen Sieger hat (z. B. für die Hupe). */
    public void addOnShootoutEnd(Runnable listener) {
        shootoutEndListeners.add(listener);
    }

    public void removeOnShootoutEnd(Runnable listener) {
        shootoutEndListeners.remove(listener);
    }

    /** Bricht das Spiel sofort ab: Uhr stoppt endgültig, ein laufendes Timeout endet. */
    public void abortGame() {
        timeouts.end();
        clock.finish();
    }

    /**
     * Ist das Spiel nach Gleichstand beendbar? Nur nach regulärem Spielende bei Gleichstand,
     * solange weder Verlängerung noch 7-m-Werfen gestartet wurden.
     */
    public boolean canEndGame() {
        return canEndGame.get();
    }

    public ReadOnlyBooleanProperty canEndGameProperty() {
        return canEndGame.getReadOnlyProperty();
    }

    /** Der nächste Abschnitt (Halbzeit/Drittel oder Verlängerung) kann gestartet werden. */
    public ReadOnlyBooleanProperty canStartNextSegmentProperty() {
        return canStartNextSegment.getReadOnlyProperty();
    }

    /**
     * Startet den nächsten Abschnitt: in der Pause die nächste Halbzeit bzw. das nächste Drittel,
     * nach regulärem Spielende die Verlängerung.
     */
    public void startNextSegment() {
        if (!canStartNextSegment.get()) {
            return;
        }
        if (clock.phaseProperty().get() == GameClock.Phase.HALF_TIME) {
            clock.startNextPeriod();
        } else {
            clock.startOvertime();
        }
    }

    /**
     * Beendet das Spiel bei Gleichstand ausdrücklich, ohne Verlängerung und ohne 7-m-Werfen.
     * Danach ist es vorbei ({@link #isOver()}); Verlängerung und 7-m-Werfen sind gesperrt.
     */
    public void endGame() {
        if (!canEndGame()) {
            return;
        }
        markEnded();
    }

    private void markEnded() {
        clock.close();
        ended.set(true);
    }

    /** {@code true}, sobald das Spiel nach Gleichstand ausdrücklich beendet wurde. */
    public ReadOnlyBooleanProperty endedProperty() {
        return ended.getReadOnlyProperty();
    }

    /** Wird aufgerufen, wenn ein Team-Timeout abläuft (z. B. für die Hupe), nicht bei vorzeitigem Ende. */
    public void addOnTimeoutEnd(Runnable listener) {
        timeoutEndListeners.add(listener);
    }

    public void removeOnTimeoutEnd(Runnable listener) {
        timeoutEndListeners.remove(listener);
    }

    /**
     * Das Spiel ist endgültig vorbei: Abschnittsende erreicht (oder abgebrochen) und weder
     * Verlängerung noch 7-m-Werfen stehen noch aus. Bei Gleichstand nach dem regulären
     * Spielende bleibt das Spiel offen, denn das Kampfgericht kann noch verlängern oder werfen
     * lassen — es sei denn, es wurde mit {@link #endGame()} ausdrücklich beendet. Ein
     * 7-m-Werfen ist erst mit seinem Sieger vorbei.
     */
    public boolean isOver() {
        if (ended.get()) {
            return true;
        }
        Shootout current = shootout.get();
        if (current != null) {
            return current.winnerProperty().get() != null;
        }
        return clock.phaseProperty().get() == GameClock.Phase.FINISHED
                && (!clock.canStartOvertime() || homeScore.get() != guestScore.get());
    }

    /** Abbild des Spiels zum Sichern; siehe {@link GameSnapshot}. */
    public GameSnapshot snapshot() {
        List<GameSnapshot.PenaltySnapshot> penaltySnapshots = new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            for (PenaltyTimer timer : penalties(side)) {
                penaltySnapshots.add(new GameSnapshot.PenaltySnapshot(side, timer.playerNumber(),
                        timer.startElapsedMillis(), timer.baseDurationMillis(), timer.isExtended()));
            }
        }
        Shootout current = shootout.get();
        return new GameSnapshot(config.homeName(), config.guestName(), config.mode(),
                config.periodMillis(), config.direction(), config.overtimeFormat(),
                config.overtimeMillis(), config.profile().name(),
                clock.phaseProperty().get(), clock.periodProperty().get(), clock.overtimeCount(),
                clock.elapsedMillisProperty().get(),
                homeScore.get(), guestScore.get(),
                timeouts.usedProperty(TeamSide.HOME).get(), timeouts.usedProperty(TeamSide.GUEST).get(),
                List.copyOf(penaltySnapshots),
                current == null ? null : current.startingTeam(),
                current == null ? List.of() : List.copyOf(current.attempts()),
                ended.get());
    }

    /**
     * Stellt ein gesichertes Spiel wieder her. Eine laufende Uhr kommt pausiert zurück, ein
     * Team-Timeout läuft nicht weiter. Unstimmige Abbilder werden mit einer
     * {@link IllegalArgumentException} abgelehnt.
     */
    public static GameState restore(GameSnapshot snapshot, LongSupplier nanoSource) {
        SportProfile profile = SportProfile.byName(snapshot.sport());
        if (profile == null) {
            throw new IllegalArgumentException("Unbekannte Sportart: " + snapshot.sport());
        }
        if (snapshot.homeName() == null || snapshot.guestName() == null || snapshot.mode() == null
                || snapshot.direction() == null || snapshot.overtimeFormat() == null
                || snapshot.periodMillis() <= 0 || snapshot.overtimeMillis() <= 0
                || snapshot.homeScore() < 0 || snapshot.guestScore() < 0
                || snapshot.homeTimeoutsUsed() < 0 || snapshot.guestTimeoutsUsed() < 0) {
            throw new IllegalArgumentException("Unvollständiges oder ungültiges Spiel-Abbild");
        }
        GameConfig config = new GameConfig(snapshot.homeName(), snapshot.guestName(),
                snapshot.mode(), Duration.ofMillis(snapshot.periodMillis()), snapshot.direction(),
                snapshot.overtimeFormat(), Duration.ofMillis(snapshot.overtimeMillis()), profile);
        GameState state = new GameState(config, nanoSource);
        state.clock.restore(snapshot.phase(), snapshot.period(), snapshot.overtimes(),
                snapshot.elapsedMillis());
        state.homeScore.set(snapshot.homeScore());
        state.guestScore.set(snapshot.guestScore());
        state.timeouts.usedProperty(TeamSide.HOME).set(snapshot.homeTimeoutsUsed());
        state.timeouts.usedProperty(TeamSide.GUEST).set(snapshot.guestTimeoutsUsed());
        long elapsed = snapshot.elapsedMillis();
        for (GameSnapshot.PenaltySnapshot penalty : snapshot.penalties()) {
            if (penalty.side() == null || penalty.startElapsedMillis() < 0
                    || penalty.durationMillis() <= 0) {
                throw new IllegalArgumentException("Ungültige Zeitstrafe im Spiel-Abbild");
            }
            PenaltyTimer timer = state.penalties.add(penalty.side(), penalty.playerNumber(),
                    penalty.startElapsedMillis(), penalty.durationMillis());
            if (penalty.extended()) {
                timer.extend();
            }
            timer.update(elapsed);
        }
        if (snapshot.shootoutStart() != null) {
            Shootout restored = new Shootout(snapshot.shootoutStart());
            for (Shootout.Attempt attempt : snapshot.shootoutAttempts()) {
                if (restored.winnerProperty().get() != null
                        || restored.nextThrowerProperty().get() != attempt.side()) {
                    throw new IllegalArgumentException("Wurffolge des 7-m-Werfens ist unstimmig");
                }
                restored.record(attempt.goal());
            }
            state.shootout.set(restored);
        } else if (!snapshot.shootoutAttempts().isEmpty()) {
            throw new IllegalArgumentException("Würfe ohne 7-m-Werfen im Spiel-Abbild");
        }
        if (snapshot.ended()) {
            if (state.shootout.get() != null || !state.clock.canStartOvertime()
                    || state.homeScore.get() != state.guestScore.get()) {
                throw new IllegalArgumentException("Beendetes Spiel passt nicht zum Spielstand");
            }
            state.markEnded();
        }
        return state;
    }

    public void tick() {
        clock.tick();
        penalties.update(clock.elapsedMillisProperty().get());
        if (timeouts.update(nanoSource.getAsLong(), clock.runningProperty().get())) {
            for (Runnable listener : List.copyOf(timeoutEndListeners)) {
                listener.run();
            }
        }
    }
}
