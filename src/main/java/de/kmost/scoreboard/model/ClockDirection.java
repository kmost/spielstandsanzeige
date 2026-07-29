package de.kmost.scoreboard.model;

public enum ClockDirection {
    UP("Vorwärts (0:00 → Ende)"),
    DOWN("Rückwärts (Ende → 0:00)");

    private final String label;

    ClockDirection(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
