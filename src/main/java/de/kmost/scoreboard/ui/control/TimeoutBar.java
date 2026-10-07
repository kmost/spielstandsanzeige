package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamTimeout;
import de.kmost.scoreboard.ui.TimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Der Team-Timeout-Counter ist als Ganzes klickbar: ein Klick beendet das Timeout. */
final class TimeoutBar {

    private final GameState state;
    private final VBox box = new VBox(4);

    TimeoutBar(GameState state) {
        this.state = state;
        box.setAlignment(Pos.CENTER);
        ControlLayout.fitToCellWidth(box, HPos.LEFT);
        state.activeTimeoutProperty().addListener((obs, oldTimeout, timeout) -> rebuild());
        rebuild();
    }

    Node node() {
        return box;
    }

    private void rebuild() {
        TeamTimeout timeout = state.activeTimeoutProperty().get();
        if (timeout == null) {
            box.getChildren().clear();
            return;
        }
        Button row = new Button();
        row.textProperty().bind(Bindings.createStringBinding(
                () -> "🟩 Team-Timeout " + state.config().teamName(timeout.side()) + ": "
                        + TimeFormatter.formatRemaining(timeout.remainingMillisProperty().get()) + "  ✕",
                timeout.remainingMillisProperty()));
        row.getStyleClass().add("game-timeout-row");
        row.setMinWidth(Region.USE_PREF_SIZE);
        row.setTooltip(new Tooltip("Klicken, um das Timeout zu beenden"));
        row.setOnAction(e -> state.endTeamTimeout());
        box.getChildren().setAll(row);
    }
}
