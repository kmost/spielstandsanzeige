package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamSide;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Toranzeige der Spielhälfte: großer Spielstand, darunter große Tor-Buttons. */
final class ScoreCell {

    private final VBox cell;

    ScoreCell(GameState state, TeamSide side) {
        Label scoreLabel = new Label();
        scoreLabel.textProperty().bind(state.scoreProperty(side).asString());
        scoreLabel.getStyleClass().add("game-score");

        Button plusButton = new Button("➕ Tor");
        plusButton.getStyleClass().add("goal-button");
        plusButton.setMinWidth(Region.USE_PREF_SIZE);
        plusButton.setOnAction(e -> state.addGoal(side));
        Button minusButton = new Button("➖ Tor");
        minusButton.getStyleClass().add("goal-button");
        minusButton.setMinWidth(Region.USE_PREF_SIZE);
        minusButton.setOnAction(e -> state.removeGoal(side));
        HBox goalButtons = new HBox(10, plusButton, minusButton);
        goalButtons.setAlignment(Pos.CENTER);

        cell = new VBox(8, scoreLabel, goalButtons);
        cell.setAlignment(Pos.CENTER);
        ControlLayout.fitToCellWidth(cell, HPos.CENTER);
    }

    Node node() {
        return cell;
    }
}
