package de.kmost.scoreboard.model;

import java.time.Duration;

/**
 * Vorgabewerte der Sportart. Die App spielt Handball: {@link #HANDBALL} ist das einzige
 * Profil, das Setup bietet keine Auswahl. Das Profil ist trotzdem ein eigener Typ, weil
 * {@link GameConfig} es trägt und eine gesicherte Partie die Sportart beim Namen speichert
 * ({@link #byName}). Was für eine zweite Sportart fehlt, steht in CONTRIBUTING.md.
 */
public record SportProfile(String name,
                           Duration defaultPeriodDuration,
                           Duration defaultOvertimePeriodDuration,
                           Duration penaltyDuration,
                           Duration teamTimeoutDuration,
                           int teamTimeoutsPerGame) {

    public static final SportProfile HANDBALL = new SportProfile(
            "Handball", Duration.ofMinutes(30), Duration.ofMinutes(5),
            Duration.ofMinutes(2), Duration.ofMinutes(1), 3);

    /** Profil zum gespeicherten Namen (gesicherte Partie); {@code null}, wenn die Sportart unbekannt ist. */
    public static SportProfile byName(String name) {
        return HANDBALL.name().equals(name) ? HANDBALL : null;
    }
}
