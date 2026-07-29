package de.kmost.scoreboard.model;

import java.time.Duration;

/**
 * Vorgabewerte je Sportart. Weitere Sportarten werden als zusätzliche Konstanten ergänzt
 * und im Setup zur Auswahl angeboten.
 */
public record SportProfile(String name,
                           Duration defaultPeriodDuration,
                           Duration penaltyDuration,
                           Duration teamTimeoutDuration,
                           int teamTimeoutsPerGame) {

    public static final SportProfile HANDBALL = new SportProfile(
            "Handball", Duration.ofMinutes(30), Duration.ofMinutes(2), Duration.ofMinutes(1), 3);
}
