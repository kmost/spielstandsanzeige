package de.kmost.scoreboard.ui.control;

import java.util.List;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.Shootout;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.ShootoutTable;
import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Eigene Zeile für das laufende 7-m-Werfen in voller Breite: Status, die in
 * diesem Moment wichtigsten Knöpfe Tor/Kein Tor (gleich groß und deutlich
 * größer als alles andere) samt Rücknahme von Fehleingaben und die dezent
 * durchnummerierte Wurf-Tabelle beider Teams. Ein Tor zählt auf den
 * Spielstand; am Ende steht der Sieger in der Statuszeile.
 */
final class ShootoutPane {

    private final VBox pane;

    ShootoutPane(GameState state, Shootout shootout) {
        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("game-phase");
        statusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    TeamSide winner = shootout.winnerProperty().get();
                    if (winner != null) {
                        return "🏆 Sieger: " + state.config().teamName(winner);
                    }
                    return (shootout.suddenDeath() ? "Sudden Death — " : "7-m-Werfen — ")
                            + state.config().teamName(shootout.nextThrowerProperty().get())
                            + " wirft";
                },
                shootout.winnerProperty(), shootout.nextThrowerProperty(), shootout.attempts()));

        Button goalButton = new Button("⚽ Tor");
        goalButton.getStyleClass().add("shootout-goal-button");
        goalButton.setOnAction(e -> state.recordShootoutAttempt(true));
        Button missButton = new Button("❌ Kein Tor");
        missButton.getStyleClass().add("shootout-miss-button");
        missButton.setOnAction(e -> state.recordShootoutAttempt(false));
        // gleich große Knöpfe: „Kein Tor“ ist der breitere und behält seine natürliche
        // Breite, „Tor“ übernimmt sie — nichts wird mit „…“ gekürzt
        missButton.setMinWidth(Region.USE_PREF_SIZE);
        goalButton.prefWidthProperty().bind(missButton.widthProperty());
        for (Button button : List.of(goalButton, missButton)) {
            button.disableProperty().bind(shootout.winnerProperty().isNotNull());
        }
        HBox bigButtons = new HBox(10, goalButton, missButton);
        bigButtons.setAlignment(Pos.CENTER);

        Button undoButton = new Button("↩ Wurf zurücknehmen");
        undoButton.setMinWidth(Region.USE_PREF_SIZE);
        undoButton.disableProperty().bind(Bindings.isEmpty(shootout.attempts()));
        undoButton.setOnAction(e -> state.undoShootoutAttempt());

        HBox buttonRow = new HBox(12, bigButtons, undoButton);
        buttonRow.setAlignment(Pos.CENTER);

        HBox tableLine = new HBox(new ShootoutTable(shootout, "game-shootout-number",
                "game-shootout-team", "game-shootout-symbol", 10, 2).node());
        tableLine.setAlignment(Pos.CENTER);
        ControlLayout.fitToCellWidth(tableLine, HPos.CENTER);

        pane = new VBox(8, statusLabel, buttonRow, tableLine);
        pane.setAlignment(Pos.CENTER);
    }

    Node node() {
        return pane;
    }
}
