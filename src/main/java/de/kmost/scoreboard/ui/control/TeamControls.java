package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.TimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Untere Zeile je Spielhälfte: Teamname, Strafen-Eingabe und Timeout mit Counter. */
final class TeamControls {

    private final VBox pane;

    TeamControls(GameState state, TeamSide side) {
        Label nameLabel = new Label(state.config().teamName(side) + " (" + side.label() + ")");
        nameLabel.getStyleClass().add("game-team-name");
        nameLabel.setMinWidth(Region.USE_PREF_SIZE);

        TextField numberField = new TextField();
        numberField.setPromptText("Nr.");
        numberField.setPrefColumnCount(3);
        Button penaltyButton = new Button("⏱ 2 Minuten");
        penaltyButton.setOnAction(e -> {
            state.addPenalty(side, numberField.getText());
            numberField.clear();
        });
        HBox penaltyEntry = new HBox(8, numberField, penaltyButton);
        penaltyEntry.setAlignment(Pos.CENTER);

        Button timeoutButton = new Button("🟩 Team-Timeout");
        timeoutButton.disableProperty().bind(state.canStartTeamTimeoutProperty().not());
        timeoutButton.setOnAction(e -> state.startTeamTimeout(side));

        Label timeoutsLabel = new Label();
        timeoutsLabel.getStyleClass().add("game-timeout-dots");
        timeoutsLabel.textProperty().bind(Bindings.createStringBinding(
                () -> TimeFormatter.formatTimeoutDots(
                        state.timeoutsUsedProperty(side).get(),
                        state.config().profile().teamTimeoutsPerGame()),
                state.timeoutsUsedProperty(side)));

        HBox timeoutRow = new HBox(8, timeoutButton, timeoutsLabel);
        timeoutRow.setAlignment(Pos.CENTER);

        pane = new VBox(8, nameLabel, penaltyEntry, timeoutRow);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setPadding(new Insets(10));
        ControlLayout.fitToCellWidth(pane, HPos.CENTER);
    }

    Node node() {
        return pane;
    }
}
