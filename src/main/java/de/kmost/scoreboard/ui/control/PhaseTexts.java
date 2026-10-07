package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.Shootout;

/** Texte zur aktuellen Spielsituation in der Kampfgericht-Konsole. */
final class PhaseTexts {

    private PhaseTexts() {
    }

    /** Statuszeile unter der Uhr („1. Halbzeit läuft“, „Pausiert“, „Spielende“ …). */
    static String phase(GameState state) {
        GameClock clock = state.clock();
        GameConfig config = state.config();
        GameMode mode = config.mode();
        int period = clock.periodProperty().get();
        return switch (clock.phaseProperty().get()) {
            case NOT_STARTED -> "Bereit";
            case RUNNING -> config.isOvertimePeriod(period)
                    ? overtimeLabel(config, period) + " läuft"
                    : mode.periodCount() > 1
                            ? period + ". " + mode.periodName() + " läuft"
                            : "Spielzeit läuft";
            case PAUSED -> "Pausiert";
            case HALF_TIME -> config.isOvertimePeriod(period)
                    ? "Verlängerungspause" : mode.breakName();
            case FINISHED -> {
                Shootout shootout = state.shootoutProperty().get();
                yield shootout != null && shootout.winnerProperty().get() == null
                        ? "7-m-Werfen" : "Spielende";
            }
        };
    }

    /** Beschriftung des nächsten-Abschnitt-Knopfs passend zur aktuellen Spielsituation. */
    static String nextSegment(GameState state) {
        GameClock clock = state.clock();
        GameConfig config = state.config();
        if (clock.phaseProperty().get() == GameClock.Phase.FINISHED) {
            return "▶ " + config.overtimeNumber(clock.periodProperty().get() + 1)
                    + ". Verlängerung starten";
        }
        int next = clock.periodProperty().get() + 1;
        if (config.isOvertimePeriod(next)) {
            return "⏭ " + config.overtimeHalf(next) + ". Halbzeit der Verlängerung starten";
        }
        return "⏭ " + next + ". " + config.mode().periodName() + " starten";
    }

    /** Name eines Verlängerungs-Abschnitts, z. B. „1. Verlängerung – 2. Halbzeit“. */
    static String overtimeLabel(GameConfig config, int period) {
        String name = config.overtimeNumber(period) + ". Verlängerung";
        return config.overtimeFormat().periodCount() > 1
                ? name + " – " + config.overtimeHalf(period) + ". Halbzeit"
                : name;
    }
}
