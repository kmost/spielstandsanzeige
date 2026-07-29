package de.kmost.scoreboard.ui;

/**
 * Alle über Themes skalierbaren Schriftgrößen der Publikumsanzeige, als Faktor
 * (1.0 = Standard). Header und Footer skalieren zusätzlich ihren Höhenanteil am
 * äußeren Raster mit; die übrigen wirken innerhalb des Spielstand-Bereichs.
 * Jeder Eintrag hat einen stabilen Schlüssel für die Persistenz.
 */
public enum FontScale {
    HEADER("headerScale"),
    FOOTER("footerScale"),
    CLOCK("clockScale"),
    SCORE("scoreScale"),
    TEAM_NAME("nameScale"),
    PENALTY("penaltyScale"),
    TIMEOUT("timeoutScale"),
    STATUS("statusScale");

    private final String key;

    FontScale(String key) {
        this.key = key;
    }

    /** Stabiler Schlüssel in den Properties-Dateien. */
    public String key() {
        return key;
    }
}
