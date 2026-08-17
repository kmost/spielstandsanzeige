package de.kmost.scoreboard.model;

/** Aufteilung einer Verlängerung: durchgehend oder in zwei Halbzeiten. */
public enum OvertimeFormat {
    TWO_HALVES("zwei Halbzeiten", 2),
    SINGLE_PERIOD("eine Spielzeit", 1);

    private final String label;
    private final int periodCount;

    OvertimeFormat(String label, int periodCount) {
        this.label = label;
        this.periodCount = periodCount;
    }

    public int periodCount() {
        return periodCount;
    }

    @Override
    public String toString() {
        return label;
    }
}
