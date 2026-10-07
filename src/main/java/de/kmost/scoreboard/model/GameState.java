package de.kmost.scoreboard.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

/**
 * Gesamter Spielzustand: Konfiguration, Uhr, Tore, Zeitstrafen und Team-Timeouts.
 * Kampfgericht-Konsole und Publikumsanzeige beobachten dasselbe GameState-Objekt
 * über Properties und ObservableLists und bleiben so automatisch synchron.
 */
public class GameState {

    /** Älteste Strafe (früheste Ablaufmarke) zuerst. */
    private static final Comparator<PenaltyTimer> PENALTY_ORDER =
            Comparator.comparingLong(timer -> timer.endElapsedMillisProperty().get());

    private final GameConfig config;
    private final GameClock clock;
    private final LongSupplier nanoSource;
    private final IntegerProperty homeScore = new SimpleIntegerProperty(0);
    private final IntegerProperty guestScore = new SimpleIntegerProperty(0);
    private final IntegerProperty homeTimeoutsUsed = new SimpleIntegerProperty(0);
    private final IntegerProperty guestTimeoutsUsed = new SimpleIntegerProperty(0);
    // der Extraktor meldet Änderungen der Ablaufmarke (Verlängerung, Zeitkorrektur) als Update,
    // damit die sortierten Sichten neu ordnen — ohne Änderung bei jedem Tick
    private final ObservableList<PenaltyTimer> homePenalties = penaltyList();
    private final ObservableList<PenaltyTimer> guestPenalties = penaltyList();
    private final SortedList<PenaltyTimer> homePenaltiesSorted = new SortedList<>(homePenalties, PENALTY_ORDER);
    private final SortedList<PenaltyTimer> guestPenaltiesSorted = new SortedList<>(guestPenalties, PENALTY_ORDER);
    private final ObjectProperty<TeamTimeout> activeTimeout = new SimpleObjectProperty<>();
    private final ObjectProperty<Shootout> shootout = new SimpleObjectProperty<>();
    private final ReadOnlyBooleanWrapper ended = new ReadOnlyBooleanWrapper(false);
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

    /**
     * Die Strafen eines Teams in Anzeigereihenfolge: älteste (kürzeste Restzeit) zuerst.
     * Ordnet sich bei Verlängerung und Zeitkorrektur neu; schreibgeschützt.
     */
    public ObservableList<PenaltyTimer> sortedPenalties(TeamSide side) {
        return side == TeamSide.HOME ? homePenaltiesSorted : guestPenaltiesSorted;
    }

    private static ObservableList<PenaltyTimer> penaltyList() {
        return FXCollections.observableArrayList(
                timer -> new Observable[] {timer.endElapsedMillisProperty()});
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

    /** Verlängert eine laufende Zeitstrafe auf die doppelte Dauer (2 → 4 Minuten). */
    public void extendPenalty(PenaltyTimer timer) {
        timer.extend();
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

    /**
     * Ist das Spiel nach Gleichstand beendbar? Nur nach regulärem Spielende bei Gleichstand,
     * solange weder Verlängerung noch 7-m-Werfen gestartet wurden.
     */
    public boolean canEndGame() {
        return !ended.get()
                && shootout.get() == null
                && clock.canStartOvertime()
                && homeScore.get() == guestScore.get();
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

    public void setOnTimeoutEnd(Runnable onTimeoutEnd) {
        this.onTimeoutEnd = onTimeoutEnd;
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
                homeTimeoutsUsed.get(), guestTimeoutsUsed.get(),
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
        state.homeTimeoutsUsed.set(snapshot.homeTimeoutsUsed());
        state.guestTimeoutsUsed.set(snapshot.guestTimeoutsUsed());
        long elapsed = snapshot.elapsedMillis();
        for (GameSnapshot.PenaltySnapshot penalty : snapshot.penalties()) {
            if (penalty.side() == null || penalty.startElapsedMillis() < 0
                    || penalty.durationMillis() <= 0) {
                throw new IllegalArgumentException("Ungültige Zeitstrafe im Spiel-Abbild");
            }
            PenaltyTimer timer = new PenaltyTimer(penalty.side(), penalty.playerNumber(),
                    penalty.startElapsedMillis(), penalty.durationMillis());
            if (penalty.extended()) {
                timer.extend();
            }
            timer.update(elapsed);
            state.penalties(penalty.side()).add(timer);
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
