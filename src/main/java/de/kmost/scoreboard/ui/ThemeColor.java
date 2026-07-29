package de.kmost.scoreboard.ui;

import javafx.scene.paint.Color;

/**
 * Alle über Themes einstellbaren Farben der Publikumsanzeige. Jede Farbe hat einen
 * stabilen Schlüssel für die Persistenz und eine CSS-Variable (looked-up color),
 * unter der display.css sie referenziert.
 */
public enum ThemeColor {
    BACKGROUND_TOP("bgTop", "Hintergrund oben", "-color-bg-top", Color.web("#05070c")),
    BACKGROUND_MIDDLE("bgMiddle", "Hintergrund Mitte", "-color-bg-middle", Color.web("#0d1017")),
    BACKGROUND_BOTTOM("bgBottom", "Hintergrund unten", "-color-bg-bottom", Color.web("#141926")),
    CLOCK("clock", "Spieluhr", "-color-clock", Color.web("#ffd54a")),
    STATUS("status", "Statuszeile", "-color-status", Color.web("#8b93a3")),
    TEAM_NAME("teamName", "Teamnamen", "-color-team-name", Color.web("#d7dce5")),
    SCORE("score", "Tore", "-color-score", Color.WHITE),
    PENALTY("penalty", "Zeitstrafen", "-color-penalty", Color.web("#ff6b6b")),
    TIMEOUT("timeout", "Team-Timeout", "-color-timeout", Color.web("#6fdc6f")),
    HEADER("header", "Header-Text", "-color-header", Color.web("#8b93a3")),
    FOOTER("footer", "Footer-Text", "-color-footer", Color.web("#8b93a3"));

    private final String key;
    private final String label;
    private final String cssVariable;
    private final Color defaultColor;

    ThemeColor(String key, String label, String cssVariable, Color defaultColor) {
        this.key = key;
        this.label = label;
        this.cssVariable = cssVariable;
        this.defaultColor = defaultColor;
    }

    /** Stabiler Schlüssel in den Properties-Dateien. */
    public String key() {
        return key;
    }

    /** Beschriftung im Konfigurationsfenster. */
    public String label() {
        return label;
    }

    /** Name der CSS-Variable in display.css. */
    public String cssVariable() {
        return cssVariable;
    }

    public Color defaultColor() {
        return defaultColor;
    }
}