package de.kmost.scoreboard.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Team-Timeouts eines Spiels: wie viele jedes Team genutzt hat und welches gerade läuft.
 * Teil von {@link GameState}; die Regeln, wann ein Timeout starten darf, stehen dort.
 */
final class TimeoutTracker {

    private final IntegerProperty homeUsed = new SimpleIntegerProperty(0);
    private final IntegerProperty guestUsed = new SimpleIntegerProperty(0);
    private final ObjectProperty<TeamTimeout> active = new SimpleObjectProperty<>();

    IntegerProperty usedProperty(TeamSide side) {
        return side == TeamSide.HOME ? homeUsed : guestUsed;
    }

    ObjectProperty<TeamTimeout> activeProperty() {
        return active;
    }

    /** Zählt ein Timeout für das Team und startet seinen Echtzeit-Countdown. */
    void start(TeamSide side, long startNanos, long durationMillis) {
        usedProperty(side).set(usedProperty(side).get() + 1);
        active.set(new TeamTimeout(side, startNanos, durationMillis));
    }

    void end() {
        active.set(null);
    }

    /**
     * Treibt den Countdown. Startet das Kampfgericht die Uhr wieder, ist das Timeout beendet
     * (ohne Signal). Liefert {@code true}, wenn es gerade abgelaufen ist (mit Signal).
     */
    boolean update(long nowNanos, boolean clockRunning) {
        TeamTimeout timeout = active.get();
        if (timeout == null) {
            return false;
        }
        if (clockRunning) {
            active.set(null);
            return false;
        }
        timeout.update(nowNanos);
        if (timeout.isExpired()) {
            active.set(null);
            return true;
        }
        return false;
    }
}
