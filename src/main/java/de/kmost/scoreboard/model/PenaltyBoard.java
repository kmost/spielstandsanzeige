package de.kmost.scoreboard.model;

import java.util.Comparator;

import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

/**
 * Die laufenden Zeitstrafen beider Teams. Teil von {@link GameState}; kümmert sich um
 * Anlegen, Verlängern, Entfernen, den Ablauf je Tick und die Anzeigereihenfolge.
 */
final class PenaltyBoard {

    /** Älteste Strafe (früheste Ablaufmarke) zuerst. */
    private static final Comparator<PenaltyTimer> ORDER =
            Comparator.comparingLong(timer -> timer.endElapsedMillisProperty().get());

    // der Extraktor meldet Änderungen der Ablaufmarke (Verlängerung, Zeitkorrektur) als Update,
    // damit die sortierten Sichten neu ordnen — ohne Änderung bei jedem Tick
    private final ObservableList<PenaltyTimer> home = newList();
    private final ObservableList<PenaltyTimer> guest = newList();
    private final SortedList<PenaltyTimer> homeSorted = new SortedList<>(home, ORDER);
    private final SortedList<PenaltyTimer> guestSorted = new SortedList<>(guest, ORDER);

    private static ObservableList<PenaltyTimer> newList() {
        return FXCollections.observableArrayList(
                timer -> new Observable[] {timer.endElapsedMillisProperty()});
    }

    /** Strafen eines Teams in der Reihenfolge ihres Anlegens. */
    ObservableList<PenaltyTimer> list(TeamSide side) {
        return side == TeamSide.HOME ? home : guest;
    }

    /** Strafen eines Teams in Anzeigereihenfolge (schreibgeschützt). */
    ObservableList<PenaltyTimer> sorted(TeamSide side) {
        return side == TeamSide.HOME ? homeSorted : guestSorted;
    }

    /** Legt eine Strafe an; eine leere oder blanke Nummer bedeutet „keine Nummer erfasst“. */
    PenaltyTimer add(TeamSide side, String playerNumber, long startElapsedMillis, long durationMillis) {
        String number = playerNumber == null || playerNumber.isBlank() ? null : playerNumber.strip();
        PenaltyTimer timer = new PenaltyTimer(side, number, startElapsedMillis, durationMillis);
        list(side).add(timer);
        return timer;
    }

    void remove(PenaltyTimer timer) {
        home.remove(timer);
        guest.remove(timer);
    }

    /** Aktualisiert alle Restzeiten zur Spielzeit und entfernt abgelaufene Strafen. */
    void update(long elapsedMillis) {
        update(home, elapsedMillis);
        update(guest, elapsedMillis);
    }

    private static void update(ObservableList<PenaltyTimer> penalties, long elapsedMillis) {
        for (PenaltyTimer timer : penalties) {
            timer.update(elapsedMillis);
        }
        penalties.removeIf(PenaltyTimer::isExpired);
    }
}
