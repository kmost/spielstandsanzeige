package de.kmost.scoreboard.model;

import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;

/**
 * Ein Zeitstrafen-Counter. Er merkt sich nur die Spielzeit-Marke seines Starts;
 * die Restzeit ergibt sich aus der verbrauchten Spielzeit. Dadurch pausiert er
 * automatisch mit der Spieluhr und läuft über die Halbzeitpause hinweg korrekt weiter.
 */
public class PenaltyTimer {

    private final TeamSide side;
    private final String playerNumber; // darf null sein (keine Nummer erfasst)
    private final long startElapsedMillis;
    private final long durationMillis;
    private final ReadOnlyLongWrapper remainingMillis;

    public PenaltyTimer(TeamSide side, String playerNumber, long startElapsedMillis, long durationMillis) {
        this.side = side;
        this.playerNumber = playerNumber;
        this.startElapsedMillis = startElapsedMillis;
        this.durationMillis = durationMillis;
        this.remainingMillis = new ReadOnlyLongWrapper(durationMillis);
    }

    void update(long elapsedMillis) {
        remainingMillis.set(Math.max(0, startElapsedMillis + durationMillis - elapsedMillis));
    }

    public boolean isExpired() {
        return remainingMillis.get() <= 0;
    }

    public TeamSide side() {
        return side;
    }

    public String playerNumber() {
        return playerNumber;
    }

    public ReadOnlyLongProperty remainingMillisProperty() {
        return remainingMillis.getReadOnlyProperty();
    }
}
