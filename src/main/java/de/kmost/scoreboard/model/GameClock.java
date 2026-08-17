package de.kmost.scoreboard.model;

import java.util.function.LongSupplier;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Spieluhr. {@code elapsedMillis} ist die gesamte verbrauchte Spielzeit, monoton über alle
 * Perioden (bei 2×30 min also 0 bis 60 min). Die Uhr wird von außen per {@link #tick()}
 * getrieben; die Zeit selbst kommt aus der Nanosekunden-Quelle, nicht aus der Tick-Frequenz.
 */
public class GameClock {

    public enum Phase { NOT_STARTED, RUNNING, PAUSED, HALF_TIME, FINISHED }

    private final GameConfig config;
    private final LongSupplier nanoSource;

    private long accumulatedMillis;
    private long startNanos;
    private Runnable onPeriodEnd;
    /** Anzahl der gestarteten Verlängerungen; jede verlängert den Spielplan um ihre Abschnitte. */
    private int overtimes;

    private final ReadOnlyLongWrapper elapsedMillis = new ReadOnlyLongWrapper(0);
    private final ReadOnlyIntegerWrapper period = new ReadOnlyIntegerWrapper(1);
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<Phase> phase = new ReadOnlyObjectWrapper<>(Phase.NOT_STARTED);

    public GameClock(GameConfig config) {
        this(config, System::nanoTime);
    }

    public GameClock(GameConfig config, LongSupplier nanoSource) {
        this.config = config;
        this.nanoSource = nanoSource;
    }

    /** Startet die Uhr bzw. setzt sie nach einer Pause fort. */
    public void start() {
        Phase p = phase.get();
        if (p != Phase.NOT_STARTED && p != Phase.PAUSED) {
            return;
        }
        startNanos = nanoSource.getAsLong();
        running.set(true);
        phase.set(Phase.RUNNING);
    }

    public void pause() {
        if (phase.get() != Phase.RUNNING) {
            return;
        }
        accumulatedMillis = currentElapsed();
        running.set(false);
        phase.set(Phase.PAUSED);
    }

    /** Startet die nächste Periode (2. Halbzeit bzw. nächstes Drittel); nur aus der Pause heraus erlaubt. */
    public void startNextPeriod() {
        if (phase.get() != Phase.HALF_TIME) {
            return;
        }
        period.set(period.get() + 1);
        startNanos = nanoSource.getAsLong();
        running.set(true);
        phase.set(Phase.RUNNING);
    }

    /**
     * Startet die nächste Verlängerung: Die erste Halbzeit der Verlängerung läuft sofort los
     * (Klick = Anpfiff, wie bei {@link #startNextPeriod()}). Nur nach regulärem Spielende bzw.
     * nach dem Ende einer Verlängerung erlaubt — nach einem Spielabbruch (Uhr steht vor dem
     * Abschnittsende) bleibt das Spiel beendet.
     */
    public void startOvertime() {
        if (!canStartOvertime()) {
            return;
        }
        overtimes++;
        period.set(period.get() + 1);
        accumulatedMillis = elapsedMillis.get();
        startNanos = nanoSource.getAsLong();
        running.set(true);
        phase.set(Phase.RUNNING);
    }

    /** Eine Verlängerung ist möglich, wenn das Spiel sein Abschnittsende regulär erreicht hat. */
    public boolean canStartOvertime() {
        return phase.get() == Phase.FINISHED
                && elapsedMillis.get() == currentPeriodEndMillis();
    }

    /** Beendet das Spiel sofort (Spielabbruch): Die Uhr stoppt endgültig bei der aktuellen Zeit. */
    public void finish() {
        if (phase.get() == Phase.FINISHED) {
            return;
        }
        if (phase.get() == Phase.RUNNING) {
            accumulatedMillis = currentElapsed();
            elapsedMillis.set(accumulatedMillis);
        }
        running.set(false);
        phase.set(Phase.FINISHED);
    }

    /**
     * Stellt die Uhr manuell auf eine Zeit innerhalb der aktuellen Periode
     * (z. B. nach Fehlstart oder verpasstem Stopp); Werte außerhalb werden auf
     * die Periodengrenzen begrenzt. Läuft die Uhr, läuft sie ab der neuen Zeit
     * weiter. Aus der Halbzeitpause heraus wird die Periode wieder geöffnet
     * (Phase PAUSED); nach Spielende ist keine Korrektur mehr möglich.
     */
    public void setElapsed(long millis) {
        if (phase.get() == Phase.FINISHED) {
            return;
        }
        long clamped = Math.clamp(millis, currentPeriodStartMillis(), currentPeriodEndMillis());
        accumulatedMillis = clamped;
        startNanos = nanoSource.getAsLong();
        elapsedMillis.set(clamped);
        if (phase.get() == Phase.HALF_TIME && clamped < currentPeriodEndMillis()) {
            phase.set(Phase.PAUSED);
        }
    }

    public void tick() {
        if (!running.get()) {
            return;
        }
        long elapsed = currentElapsed();
        long periodEnd = currentPeriodEndMillis();
        if (elapsed >= periodEnd) {
            accumulatedMillis = periodEnd;
            running.set(false);
            phase.set(period.get() >= scheduledPeriodCount() ? Phase.FINISHED : Phase.HALF_TIME);
            elapsedMillis.set(periodEnd);
            if (onPeriodEnd != null) {
                onPeriodEnd.run();
            }
        } else {
            elapsedMillis.set(elapsed);
        }
    }

    private long currentElapsed() {
        return accumulatedMillis + (nanoSource.getAsLong() - startNanos) / 1_000_000;
    }

    /** Alle bisher angesetzten Abschnitte: reguläre Perioden plus die der Verlängerungen. */
    private int scheduledPeriodCount() {
        return config.regulationPeriodCount() + overtimes * config.overtimeFormat().periodCount();
    }

    public long currentPeriodStartMillis() {
        return periodStartMillis(period.get());
    }

    public long currentPeriodEndMillis() {
        return periodStartMillis(period.get())
                + (config.isOvertimePeriod(period.get()) ? config.overtimeMillis() : config.periodMillis());
    }

    private long periodStartMillis(int period) {
        int regular = config.regulationPeriodCount();
        if (period <= regular) {
            return (long) (period - 1) * config.periodMillis();
        }
        return (long) regular * config.periodMillis()
                + (long) (period - regular - 1) * config.overtimeMillis();
    }

    public void setOnPeriodEnd(Runnable onPeriodEnd) {
        this.onPeriodEnd = onPeriodEnd;
    }

    public ReadOnlyLongProperty elapsedMillisProperty() {
        return elapsedMillis.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty periodProperty() {
        return period.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Phase> phaseProperty() {
        return phase.getReadOnlyProperty();
    }
}
