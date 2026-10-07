package de.kmost.scoreboard.model;

import java.util.List;

/**
 * Serialisierbares Abbild eines laufenden Spiels, aus dem {@link GameState#restore} das Spiel
 * nach einem Absturz oder Neustart wiederherstellt. Nur einfache Werte (Zahlen, Texte, Enums),
 * keine Properties und keine Zeitquelle: Die Uhr wird als verbrauchte Spielzeit gesichert,
 * eine laufende Uhr kommt pausiert zurück. Ein laufendes Team-Timeout (Echtzeit-Countdown)
 * wird nicht gesichert; die Zahl der genutzten Timeouts bleibt erhalten. {@code ended} markiert
 * ein nach Gleichstand ausdrücklich beendetes Spiel (keine Verlängerung, kein 7-m-Werfen mehr).
 */
public record GameSnapshot(String homeName,
                           String guestName,
                           GameMode mode,
                           long periodMillis,
                           ClockDirection direction,
                           OvertimeFormat overtimeFormat,
                           long overtimeMillis,
                           String sport,
                           GameClock.Phase phase,
                           int period,
                           int overtimes,
                           long elapsedMillis,
                           int homeScore,
                           int guestScore,
                           int homeTimeoutsUsed,
                           int guestTimeoutsUsed,
                           List<PenaltySnapshot> penalties,
                           TeamSide shootoutStart,
                           List<Shootout.Attempt> shootoutAttempts,
                           boolean ended) {

    /** Spiel-Abbild ohne „beendet“-Markierung (Spiel noch nicht ausdrücklich beendet). */
    public GameSnapshot(String homeName, String guestName, GameMode mode, long periodMillis,
                        ClockDirection direction, OvertimeFormat overtimeFormat, long overtimeMillis,
                        String sport, GameClock.Phase phase, int period, int overtimes,
                        long elapsedMillis, int homeScore, int guestScore, int homeTimeoutsUsed,
                        int guestTimeoutsUsed, List<PenaltySnapshot> penalties,
                        TeamSide shootoutStart, List<Shootout.Attempt> shootoutAttempts) {
        this(homeName, guestName, mode, periodMillis, direction, overtimeFormat, overtimeMillis,
                sport, phase, period, overtimes, elapsedMillis, homeScore, guestScore,
                homeTimeoutsUsed, guestTimeoutsUsed, penalties, shootoutStart, shootoutAttempts,
                false);
    }

    /** Eine Zeitstrafe: Startmarke in Spielzeit, ursprüngliche Dauer und ob sie verlängert wurde. */
    public record PenaltySnapshot(TeamSide side,
                                  String playerNumber,
                                  long startElapsedMillis,
                                  long durationMillis,
                                  boolean extended) {
    }
}
