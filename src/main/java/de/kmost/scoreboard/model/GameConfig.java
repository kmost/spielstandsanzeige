package de.kmost.scoreboard.model;

import java.time.Duration;

public record GameConfig(String homeName,
                         String guestName,
                         GameMode mode,
                         Duration periodDuration,
                         ClockDirection direction,
                         SportProfile profile) {

    public long periodMillis() {
        return periodDuration.toMillis();
    }

    public String teamName(TeamSide side) {
        return side == TeamSide.HOME ? homeName : guestName;
    }
}
