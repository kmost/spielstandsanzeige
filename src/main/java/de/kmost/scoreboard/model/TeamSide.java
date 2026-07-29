package de.kmost.scoreboard.model;

public enum TeamSide {
    HOME("Heim"),
    GUEST("Gast");

    private final String label;

    TeamSide(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
