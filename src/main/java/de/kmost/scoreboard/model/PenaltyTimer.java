package de.kmost.scoreboard.model;

import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;

/**
 * Ein Zeitstrafen-Counter. Er merkt sich nur die Spielzeit-Marke seines Starts;
 * die Restzeit ergibt sich aus der verbrauchten Spielzeit. Dadurch pausiert er
 * automatisch mit der Spieluhr und läuft über die Halbzeitpause hinweg korrekt weiter.
 * <p>
 * Wird die Spieluhr hinter den Start der Strafe zurückgestellt, rückt die Startmarke mit:
 * Die Strafe beginnt dann neu zu laufen, ihre Restzeit ist nie größer als ihre Dauer.
 * Bei einer Vorwärtskorrektur läuft sie wie die Spielzeit ab.
 */
public class PenaltyTimer {

    private final TeamSide side;
    private final String playerNumber; // darf null sein (keine Nummer erfasst)
    private long startElapsedMillis;
    private final long baseDurationMillis;
    private long durationMillis;
    private final ReadOnlyLongWrapper remainingMillis;
    /** Spielzeit-Marke, bei der die Strafe abläuft; ändert sich nur bei Verlängerung und Zeitkorrektur. */
    private final ReadOnlyLongWrapper endElapsedMillis;

    public PenaltyTimer(TeamSide side, String playerNumber, long startElapsedMillis, long durationMillis) {
        this.side = side;
        this.playerNumber = playerNumber;
        this.startElapsedMillis = startElapsedMillis;
        this.baseDurationMillis = durationMillis;
        this.durationMillis = durationMillis;
        this.remainingMillis = new ReadOnlyLongWrapper(durationMillis);
        this.endElapsedMillis = new ReadOnlyLongWrapper(startElapsedMillis + durationMillis);
    }

    void update(long elapsedMillis) {
        if (elapsedMillis < startElapsedMillis) {
            // Uhr wurde hinter den Start zurückgestellt: Strafe beginnt neu zu laufen
            startElapsedMillis = elapsedMillis;
        }
        endElapsedMillis.set(startElapsedMillis + durationMillis);
        remainingMillis.set(Math.max(0, endElapsedMillis.get() - elapsedMillis));
    }

    /**
     * Verlängert die Strafe auf die doppelte Gesamtdauer (2 → 4 Minuten), gerechnet ab dem
     * ursprünglichen Start. Gibt es schon eine Verlängerung oder ist die Strafe abgelaufen,
     * passiert nichts.
     */
    void extend() {
        if (isExtended() || isExpired()) {
            return;
        }
        durationMillis = 2 * baseDurationMillis;
        endElapsedMillis.set(startElapsedMillis + durationMillis);
        remainingMillis.set(remainingMillis.get() + baseDurationMillis);
    }

    long startElapsedMillis() {
        return startElapsedMillis;
    }

    long baseDurationMillis() {
        return baseDurationMillis;
    }

    public boolean isExtended() {
        return durationMillis > baseDurationMillis;
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

    /**
     * Spielzeit-Marke des Ablaufs. Anders als die Restzeit ändert sie sich nicht mit jedem Tick;
     * die Reihenfolge der Strafen nach Ablauf ist deshalb stabil und genau dann neu zu bestimmen,
     * wenn sich dieser Wert ändert (Verlängerung, Zeitkorrektur).
     */
    public ReadOnlyLongProperty endElapsedMillisProperty() {
        return endElapsedMillis.getReadOnlyProperty();
    }

    public ReadOnlyLongProperty remainingMillisProperty() {
        return remainingMillis.getReadOnlyProperty();
    }
}
