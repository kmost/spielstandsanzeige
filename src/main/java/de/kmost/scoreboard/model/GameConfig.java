package de.kmost.scoreboard.model;

import java.time.Duration;

public record GameConfig(String homeName,
                         String guestName,
                         GameMode mode,
                         Duration periodDuration,
                         ClockDirection direction,
                         OvertimeFormat overtimeFormat,
                         Duration overtimePeriodDuration,
                         SportProfile profile) {

    /** Verlängerung mit den Standardwerten der Sportart (zwei Halbzeiten à Profildauer). */
    public GameConfig(String homeName, String guestName, GameMode mode, Duration periodDuration,
                      ClockDirection direction, SportProfile profile) {
        this(homeName, guestName, mode, periodDuration, direction,
                OvertimeFormat.TWO_HALVES, profile.defaultOvertimePeriodDuration(), profile);
    }

    public long periodMillis() {
        return periodDuration.toMillis();
    }

    public long overtimeMillis() {
        return overtimePeriodDuration.toMillis();
    }

    public String teamName(TeamSide side) {
        return side == TeamSide.HOME ? homeName : guestName;
    }

    /**
     * Perioden zählen über das reguläre Spiel hinaus weiter (bei zwei Halbzeiten
     * mit Verlängerung in Halbzeiten: 1–2 regulär, 3–4 erste Verlängerung, …);
     * die folgenden Helfer ordnen eine Periodennummer der Verlängerung zu.
     */
    public int regulationPeriodCount() {
        return mode.periodCount();
    }

    public boolean isOvertimePeriod(int period) {
        return period > regulationPeriodCount();
    }

    /** Nummer der Verlängerung (1-basiert), zu der die Periode gehört. */
    public int overtimeNumber(int period) {
        return (period - regulationPeriodCount() - 1) / overtimeFormat.periodCount() + 1;
    }

    /** Halbzeit innerhalb der Verlängerung (1-basiert). */
    public int overtimeHalf(int period) {
        return (period - regulationPeriodCount() - 1) % overtimeFormat.periodCount() + 1;
    }
}
