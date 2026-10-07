package de.kmost.scoreboard.ui.display;

/**
 * Alle Layout-Konstanten der Publikumsanzeige an einer Stelle. Die Zonen und Formeln,
 * in denen sie wirken, beschreibt {@code docs/layout.md}; hier steht jeweils, woher ein
 * Wert kommt. Werte nur hier ändern — {@code DisplayWindowTest} prüft das Raster.
 */
final class DisplayLayout {

    private DisplayLayout() {
    }

    // --- Äußeres Raster: Header : Spielstand : Footer ---

    /** Höhenanteil eines sichtbaren Banners bei Standardgröße (Raster 10:80:10). */
    static final double BANNER_SHARE = 0.10;

    /**
     * Sichtbarer Abstand zwischen Banner und Spielstand (Anteil der Fensterhöhe); geht
     * zulasten des Spielstands, damit die Banner-Anteile exakt stimmen.
     */
    static final double BANNER_GAP = 0.02;

    // --- Banner ---

    /** Schriftgröße als Anteil der Bannerhöhe (0.45 · 10 % = 4,5 % der Fensterhöhe). */
    static final double BANNER_FONT_SHARE = 0.45;

    /** Abstand zwischen den Banner-Slots als Anteil der Fensterbreite (1,5 %). */
    static final double BANNER_SLOT_SPACING_SHARE = 0.015;

    /** Innenabstand des Banners oben/unten in px. */
    static final double BANNER_PADDING_VERTICAL = 2;

    /** Innenabstand des Banners links/rechts in px. */
    static final double BANNER_PADDING_HORIZONTAL = 15;

    // --- Basis-Schriftgröße des Spielstands ---

    /**
     * Basisgröße (em) als Anteil der Spielstand-Höhe: 0.0625 entspricht den früheren 5 %
     * der Fensterhöhe bei zwei sichtbaren Standard-Bannern (0.05 / 0.8).
     */
    static final double BASE_FONT_HEIGHT_SHARE = 0.0625;

    /**
     * Deckel der Basisgröße über die Fensterbreite, damit Uhr und Strafen-Chips auch in
     * schmalen Fenstern in ihre Prozent-Spalten passen: ausgelegt auf die breiteste
     * Zeile — den Strafen-Chip in der 25-%-Spalte abzüglich des äußeren Rasterabstands.
     * Bei 16:9 und breiter greift weiterhin die Höhe.
     */
    static final double BASE_FONT_WIDTH_SHARE = 0.029;

    /** Kleinste Basisgröße in px, damit extrem kleine Fenster nicht auf 0 schrumpfen. */
    static final double BASE_FONT_MIN_PX = 10;

    // --- Standard-Schriftgrößen (em der Basisgröße) der skalierbaren Elemente ---
    // display.css setzt für diese Klassen bewusst keine font-size, die kommt von hier

    static final double CLOCK_EM = 5.2;
    static final double SCORE_EM = 6.0;
    static final double TEAM_NAME_EM = 1.1;
    static final double PENALTY_EM = 1.4;
    static final double TIMEOUT_EM = 1.0;
    static final double TIMEOUT_DOTS_EM = 0.85;
    static final double PHASE_EM = 1.1;

    // --- Spielstand-Zeilen ---

    /**
     * Standard-Höhenanteile der drei Spielstand-Zeilen (Uhr, Tore, Teamnamen); werden mit
     * den Größenfaktoren gewichtet und auf 100 % normalisiert (Summe bei Standardfaktoren = 1).
     */
    static final double ROW_CLOCK = 0.38;
    static final double ROW_SCORE = 0.38;
    static final double ROW_NAMES = 0.24;

    /** Spalten der Uhr-Zeile in Prozent: Strafen Heim | Uhr | Strafen Gast. */
    static final double[] CLOCK_ROW_COLUMNS = {25, 50, 25};

    /**
     * Spalten der Tor- und Namenszeile in Prozent: Heim | Phase | Gast. Beide Zeilen
     * teilen sich dieselben Spalten, damit die Namen exakt unter den Toren stehen.
     */
    static final double[] SCORE_ROW_COLUMNS = {42, 16, 42};

    /** Äußerer Rasterabstand des Spielstands in px: oben, rechts, unten, links. */
    static final double GRID_PADDING_VERTICAL = 10;
    static final double GRID_PADDING_HORIZONTAL = 15;

    /** Höchstbreite eines Teamnamens als Anteil der Fensterbreite. */
    static final double TEAM_NAME_MAX_WIDTH_SHARE = 0.40;

    /** Abstand zwischen Teamname, Timeout-Punkten und 7-m-Zeile in px. */
    static final double NAME_CELL_SPACING = 4;

    /** Abstand zwischen den untereinander stehenden Strafen-Chips in px. */
    static final double PENALTY_COLUMN_SPACING = 10;

    /** So viele 7-m-Würfe je Team zeigt die Anzeige; ältere werden mit „…“ verdrängt. */
    static final int SHOOTOUT_VISIBLE_ATTEMPTS = 7;
}
