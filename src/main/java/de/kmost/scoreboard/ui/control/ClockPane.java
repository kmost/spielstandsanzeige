package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.Dialogs;
import de.kmost.scoreboard.ui.TimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** Uhr + Status mittig wie auf der Anzeige, darunter die Uhr-Steuerung und ein laufendes Timeout. */
final class ClockPane {

    private final GameState state;
    private final Dialogs dialogs;
    private final VBox box;

    ClockPane(GameState state, Dialogs dialogs) {
        this.state = state;
        this.dialogs = dialogs;
        GameClock clock = state.clock();

        Label clockLabel = new Label();
        clockLabel.textProperty().bind(Bindings.createStringBinding(
                () -> TimeFormatter.formatClock(clock.elapsedMillisProperty().get(),
                        clock.currentPeriodEndMillis(), state.config().direction()),
                clock.elapsedMillisProperty(), clock.periodProperty()));
        clockLabel.getStyleClass().add("game-clock");
        clockLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox clockLine = new HBox(clockLabel);
        clockLine.setAlignment(Pos.CENTER);
        ControlLayout.fitToCellWidth(clockLine, HPos.LEFT);

        // per Listener statt Binding aktualisiert, weil der Text auch vom Sieger
        // eines erst später gestarteten 7-m-Werfens abhängt
        Label phaseLabel = new Label();
        phaseLabel.getStyleClass().add("game-phase");
        Runnable updatePhase = () -> phaseLabel.setText(PhaseTexts.phase(state));
        clock.phaseProperty().addListener(obs -> updatePhase.run());
        clock.periodProperty().addListener(obs -> updatePhase.run());
        state.shootoutProperty().addListener((obs, oldShootout, shootout) -> {
            if (shootout != null) {
                shootout.winnerProperty().addListener(o -> updatePhase.run());
            }
            updatePhase.run();
        });
        updatePhase.run();

        Button startPauseButton = new Button();
        startPauseButton.setMinWidth(Region.USE_PREF_SIZE);
        startPauseButton.getStyleClass().add("big-button");
        startPauseButton.textProperty().bind(Bindings.createStringBinding(
                () -> switch (clock.phaseProperty().get()) {
                    case RUNNING -> "⏸ Pause";
                    case PAUSED -> "▶ Fortsetzen";
                    default -> "▶ Start";
                }, clock.phaseProperty()));
        startPauseButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> clock.phaseProperty().get() == GameClock.Phase.HALF_TIME
                        || clock.phaseProperty().get() == GameClock.Phase.FINISHED,
                clock.phaseProperty()));
        startPauseButton.setOnAction(e -> {
            if (clock.runningProperty().get()) {
                clock.pause();
            } else {
                clock.start();
            }
        });

        // ein Knopf für den jeweils nächsten Abschnitt: in der Pause die nächste
        // Halbzeit bzw. das nächste Drittel, nach regulärem Spielende die Verlängerung
        Button nextPeriodButton = new Button();
        nextPeriodButton.textProperty().bind(Bindings.createStringBinding(
                () -> PhaseTexts.nextSegment(state),
                clock.phaseProperty(), clock.periodProperty()));
        nextPeriodButton.getStyleClass().add("big-button");
        nextPeriodButton.disableProperty().bind(state.canStartNextSegmentProperty().not());
        nextPeriodButton.setOnAction(e -> state.startNextSegment());

        Button setTimeButton = new Button("🕑 Zeit stellen…");
        setTimeButton.getStyleClass().add("big-button");
        setTimeButton.disableProperty().bind(
                clock.phaseProperty().isEqualTo(GameClock.Phase.FINISHED));
        setTimeButton.setOnAction(e -> correctClock());

        // 7-m-Werfen: wie die Verlängerung erst nach regulärem Spielende möglich
        Button shootoutButton = new Button("🥅 7-m-Werfen…");
        shootoutButton.getStyleClass().add("big-button");
        shootoutButton.disableProperty().bind(state.canStartShootoutProperty().not());
        shootoutButton.setOnAction(e -> startShootout());

        // Unentschieden nach regulärem Ende: das Kampfgericht kann das Spiel auch ohne
        // Verlängerung und ohne 7-m-Werfen beenden; der Knopf erscheint nur in diesem Zustand
        Button endGameButton = new Button("🏁 Beenden");
        endGameButton.setTooltip(new Tooltip("Spiel bei Unentschieden beenden"));
        endGameButton.getStyleClass().add("big-button");
        endGameButton.setMinWidth(Region.USE_PREF_SIZE);
        endGameButton.visibleProperty().bind(state.canEndGameProperty());
        endGameButton.managedProperty().bind(endGameButton.visibleProperty());
        // „Zeit stellen…“ ist nach Spielende ohnehin gesperrt: Platz für „Beenden“ in schmalen Fenstern
        setTimeButton.visibleProperty().bind(endGameButton.visibleProperty().not());
        setTimeButton.managedProperty().bind(setTimeButton.visibleProperty());
        endGameButton.setOnAction(e -> {
            if (dialogs.confirm("Unentschieden stehen lassen und Spiel beenden? Danach sind weder "
                    + "Verlängerung noch 7-m-Werfen möglich.")) {
                state.endGame();
            }
        });

        nextPeriodButton.setMinWidth(Region.USE_PREF_SIZE);
        setTimeButton.setMinWidth(Region.USE_PREF_SIZE);
        shootoutButton.setMinWidth(Region.USE_PREF_SIZE);
        HBox clockButtons = new HBox(10, startPauseButton, nextPeriodButton, shootoutButton,
                endGameButton, setTimeButton);
        clockButtons.setAlignment(Pos.CENTER);
        // die Knopfzeile darf schmaler werden als die Summe ihrer Mindestbreiten: In der letzten
        // Periode („1. Verlängerung starten“) ist sie breiter als ihre Zelle und wird per
        // fitToCellWidth eingepasst — sonst ragt sie in die Nachbarspalte, deren Fläche die
        // Klicks auf die rechten Knöpfe abfängt
        clockButtons.setMinWidth(0);
        ControlLayout.fitToCellWidth(clockButtons, HPos.LEFT);

        box = new VBox(6, clockLine, phaseLabel, clockButtons, new TimeoutBar(state).node());
        // mittig in der Raster-Zeile (wie die Uhr auf der Anzeige), damit bei
        // großen Fenstern kein Loch zwischen Uhr-Gruppe und Tor-Zeile entsteht
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(0);
    }

    Node node() {
        return box;
    }

    /**
     * Spielzeit manuell stellen: Eingabe im Anzeigeformat der Uhr (vorwärts =
     * gespielte Zeit, rückwärts = Restzeit der Periode), begrenzt auf die
     * aktuelle Periode. Aus der Halbzeitpause heraus öffnet die Korrektur die
     * Periode wieder (weiter mit „Fortsetzen“).
     */
    private void correctClock() {
        GameClock clock = state.clock();
        boolean countUp = state.config().direction() == ClockDirection.UP;
        int period = clock.periodProperty().get();
        String segment = state.config().isOvertimePeriod(period)
                ? PhaseTexts.overtimeLabel(state.config(), period)
                : state.config().mode().periodName() + " " + period;
        boolean showSegment = state.config().mode().periodCount() > 1
                || state.config().isOvertimePeriod(period);
        String header = countUp
                ? "Gespielte Zeit (MM:SS)" + (showSegment
                        ? " — " + segment + " läuft ab "
                                + TimeFormatter.formatClock(clock.currentPeriodStartMillis(), 0,
                                        ClockDirection.UP)
                        : "")
                : "Restzeit der aktuellen Periode (MM:SS)";
        dialogs.askText("Spielzeit stellen", header, "Zeit:", TimeFormatter.formatClock(
                clock.elapsedMillisProperty().get(), clock.currentPeriodEndMillis(),
                state.config().direction())).ifPresent(text -> {
            try {
                long shown = TimeFormatter.parseClockInput(text);
                clock.setElapsed(countUp ? shown : clock.currentPeriodEndMillis() - shown);
            } catch (IllegalArgumentException ex) {
                dialogs.warn(ex.getMessage());
            }
        });
    }

    /** Startteam abfragen (Münzwurf) und das 7-m-Werfen beginnen. */
    private void startShootout() {
        dialogs.choose("7-m-Werfen", "Welches Team wirft zuerst?", List.of(
                state.config().teamName(TeamSide.HOME) + " beginnt",
                state.config().teamName(TeamSide.GUEST) + " beginnt")).ifPresent(choice ->
                state.startShootout(choice == 0 ? TeamSide.HOME : TeamSide.GUEST));
    }
}
