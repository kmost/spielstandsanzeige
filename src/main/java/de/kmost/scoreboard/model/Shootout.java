package de.kmost.scoreboard.model;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 7-m-Werfen nach unentschiedenem Spiel: 5 Schützen je Team werfen abwechselnd,
 * das Kampfgericht meldet nur Tor oder Fehlwurf. Die Klasse führt die
 * Wurfreihenfolge, beendet vorzeitig bei uneinholbarem Vorsprung und geht bei
 * Gleichstand nach 5 Schützen in Sudden Death, wobei jeweils die andere
 * Mannschaft beginnt. Die Tore selbst verbucht {@link GameState} auf den
 * Spielstand (Endergebnis inklusive 7-m-Werfen, wie im Handball üblich).
 */
public class Shootout {

    public static final int THROWERS_PER_TEAM = 5;

    public record Attempt(TeamSide side, boolean goal) {
    }

    private final TeamSide startingTeam;
    private final ObservableList<Attempt> attempts = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<TeamSide> nextThrower = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<TeamSide> winner = new ReadOnlyObjectWrapper<>();

    public Shootout(TeamSide startingTeam) {
        this.startingTeam = startingTeam;
        update();
    }

    /** Verbucht den nächsten Wurf für das Team, das laut Reihenfolge dran ist. */
    public void record(boolean goal) {
        if (winner.get() != null) {
            return;
        }
        attempts.add(new Attempt(nextThrower.get(), goal));
        update();
    }

    /** Nimmt den letzten Wurf zurück (Fehleingabe); auch ein schon ermittelter Sieger wird widerrufen. */
    public Attempt undoLast() {
        if (attempts.isEmpty()) {
            return null;
        }
        Attempt removed = attempts.remove(attempts.size() - 1);
        update();
        return removed;
    }

    public int attemptCount(TeamSide side) {
        return (int) attempts.stream().filter(attempt -> attempt.side() == side).count();
    }

    public int goalCount(TeamSide side) {
        return (int) attempts.stream()
                .filter(attempt -> attempt.side() == side && attempt.goal()).count();
    }

    /** Gleichstand nach 5 Schützen je Team: es wird geworfen, bis ein Paar entscheidet. */
    public boolean suddenDeath() {
        return winner.get() == null
                && attemptCount(TeamSide.HOME) >= THROWERS_PER_TEAM
                && attemptCount(TeamSide.GUEST) >= THROWERS_PER_TEAM;
    }

    /** Trefferfolge eines Teams als Symbole: ● Tor, ○ Fehlwurf. */
    public String symbols(TeamSide side) {
        StringBuilder text = new StringBuilder();
        for (Attempt attempt : attempts) {
            if (attempt.side() == side) {
                text.append(text.isEmpty() ? "" : " ").append(attempt.goal() ? "●" : "○");
            }
        }
        return text.toString();
    }

    public ObservableList<Attempt> attempts() {
        return FXCollections.unmodifiableObservableList(attempts);
    }

    /** Team, das den nächsten Wurf hat; {@code null}, sobald der Sieger feststeht. */
    public ReadOnlyObjectProperty<TeamSide> nextThrowerProperty() {
        return nextThrower.getReadOnlyProperty();
    }

    /** Sieger des Werfens; {@code null}, solange es läuft. */
    public ReadOnlyObjectProperty<TeamSide> winnerProperty() {
        return winner.getReadOnlyProperty();
    }

    private void update() {
        winner.set(computeWinner());
        nextThrower.set(winner.get() == null ? computeNextThrower() : null);
    }

    private TeamSide computeNextThrower() {
        int home = attemptCount(TeamSide.HOME);
        int guest = attemptCount(TeamSide.GUEST);
        if (home != guest) {
            return home < guest ? TeamSide.HOME : TeamSide.GUEST;
        }
        return pairStarter(home);
    }

    /** Wer ein Wurf-Paar eröffnet: regulär das Startteam, im Sudden Death im Wechsel die andere Mannschaft. */
    private TeamSide pairStarter(int pairIndex) {
        if (pairIndex < THROWERS_PER_TEAM) {
            return startingTeam;
        }
        return (pairIndex - THROWERS_PER_TEAM) % 2 == 0
                ? startingTeam.opposite() : startingTeam;
    }

    private TeamSide computeWinner() {
        int home = attemptCount(TeamSide.HOME);
        int guest = attemptCount(TeamSide.GUEST);
        int homeGoals = goalCount(TeamSide.HOME);
        int guestGoals = goalCount(TeamSide.GUEST);
        if (home <= THROWERS_PER_TEAM && guest <= THROWERS_PER_TEAM) {
            // reguläres Werfen: entschieden, sobald der Rückstand größer ist als
            // die verbleibenden Würfe des anderen Teams
            if (homeGoals > guestGoals + (THROWERS_PER_TEAM - guest)) {
                return TeamSide.HOME;
            }
            if (guestGoals > homeGoals + (THROWERS_PER_TEAM - home)) {
                return TeamSide.GUEST;
            }
            return null;
        }
        // Sudden Death: nach jedem vollständigen Wurf-Paar entscheidet die Differenz
        if (home == guest && homeGoals != guestGoals) {
            return homeGoals > guestGoals ? TeamSide.HOME : TeamSide.GUEST;
        }
        return null;
    }
}
