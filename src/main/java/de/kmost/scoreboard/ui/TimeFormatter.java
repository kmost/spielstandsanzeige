package de.kmost.scoreboard.ui;

import de.kmost.scoreboard.model.ClockDirection;

public final class TimeFormatter {

    private TimeFormatter() {
    }

    /**
     * Formatiert die Spieluhr. Vorwärts wird die verbrauchte Gesamtspielzeit gezeigt
     * (2. Halbzeit läuft ab 30:00 weiter), rückwärts die Restzeit der aktuellen Periode
     * (2. Halbzeit startet wieder bei 30:00). Der Countdown rundet auf ganze Sekunden
     * auf, damit 0:00 erst bei tatsächlichem Ablauf erscheint; vorwärts wird abgerundet.
     */
    public static String formatClock(long elapsedMillis, long periodEndMillis, ClockDirection direction) {
        long seconds = direction == ClockDirection.UP
                ? elapsedMillis / 1000
                : Math.max(0, periodEndMillis - elapsedMillis + 999) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Liest eine Uhr-Eingabe im Anzeigeformat „MM:SS“ (auch „M:SS“; nur Minuten
     * ohne Doppelpunkt sind ebenfalls erlaubt) und liefert Millisekunden.
     * Ungültige Eingaben lösen eine IllegalArgumentException aus.
     */
    public static long parseClockInput(String text) {
        String input = text == null ? "" : text.strip();
        if (input.matches("\\d{1,3}")) {
            return Long.parseLong(input) * 60_000;
        }
        if (!input.matches("\\d{1,3}:[0-5]?\\d")) {
            throw new IllegalArgumentException("Zeit bitte als MM:SS eingeben, z. B. 12:34");
        }
        String[] parts = input.split(":");
        return (Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1])) * 1000;
    }

    /** Formatiert die Restzeit einer Zeitstrafe oder eines Timeouts, aufgerundet auf ganze Sekunden. */
    public static String formatRemaining(long remainingMillis) {
        long seconds = Math.max(0, remainingMillis + 999) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Genutzte Team-Timeouts als Punkte, z. B. „●●○" bei 2 von 3. */
    public static String formatTimeoutDots(int used, int total) {
        int shown = Math.min(used, total);
        return "●".repeat(shown) + "○".repeat(total - shown)
                + (used > total ? " +" + (used - total) : "");
    }
}
