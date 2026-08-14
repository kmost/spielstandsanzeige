package de.kmost.scoreboard.model;

public enum GameMode {
    SINGLE_PERIOD("Eine durchgehende Spielzeit", 1, "Periode", "P"),
    TWO_HALVES("Zwei Halbzeiten", 2, "Halbzeit", "HZ"),
    THREE_THIRDS("Drei Drittel", 3, "Drittel", "Drittel");

    private final String label;
    private final int periodCount;
    private final String periodName;
    private final String periodAbbreviation;

    GameMode(String label, int periodCount, String periodName, String periodAbbreviation) {
        this.label = label;
        this.periodCount = periodCount;
        this.periodName = periodName;
        this.periodAbbreviation = periodAbbreviation;
    }

    public int periodCount() {
        return periodCount;
    }

    /** Name eines Spielabschnitts („Halbzeit“, „Drittel“) für Texte in der Bedienung. */
    public String periodName() {
        return periodName;
    }

    /** Kurzform für die Publikumsanzeige („HZ“, „Drittel“). */
    public String periodAbbreviation() {
        return periodAbbreviation;
    }

    /** Name der Pause zwischen zwei Abschnitten („Halbzeitpause“, „Drittelpause“). */
    public String breakName() {
        return periodName + "pause";
    }

    @Override
    public String toString() {
        return label;
    }
}
