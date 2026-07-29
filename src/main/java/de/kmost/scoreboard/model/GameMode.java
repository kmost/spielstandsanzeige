package de.kmost.scoreboard.model;

public enum GameMode {
    SINGLE_PERIOD("Eine durchgehende Spielzeit", 1),
    TWO_HALVES("Zwei Halbzeiten", 2);

    private final String label;
    private final int periodCount;

    GameMode(String label, int periodCount) {
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
