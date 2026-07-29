package de.kmost.scoreboard.model;

import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;

/**
 * Ein laufendes Team-Timeout. Anders als Zeitstrafen läuft der Countdown in Echtzeit,
 * denn die Spieluhr steht während des Timeouts.
 */
public class TeamTimeout {

    private final TeamSide side;
    private final long startNanos;
    private final long durationMillis;
    private final ReadOnlyLongWrapper remainingMillis;

    public TeamTimeout(TeamSide side, long startNanos, long durationMillis) {
        this.side = side;
        this.startNanos = startNanos;
        this.durationMillis = durationMillis;
        this.remainingMillis = new ReadOnlyLongWrapper(durationMillis);
    }

    void update(long nowNanos) {
        remainingMillis.set(Math.max(0, durationMillis - (nowNanos - startNanos) / 1_000_000));
    }

    public boolean isExpired() {
        return remainingMillis.get() <= 0;
    }

    public TeamSide side() {
        return side;
    }

    public ReadOnlyLongProperty remainingMillisProperty() {
        return remainingMillis.getReadOnlyProperty();
    }
}
