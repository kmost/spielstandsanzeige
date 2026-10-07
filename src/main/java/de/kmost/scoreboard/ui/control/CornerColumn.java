package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.ui.Dialogs;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Äußere Spalte der oberen Zeile: links die Hupe, rechts der Spielabbruch —
 * darunter jeweils die laufenden Zeitstrafen des Teams wie auf der Anzeige.
 */
final class CornerColumn {

    private final VBox column;

    CornerColumn(GameState state, TeamSide side, Horn horn, Dialogs dialogs) {
        Button cornerButton;
        if (side == TeamSide.HOME) {
            cornerButton = new Button("📢 Hupe");
            cornerButton.getStyleClass().add("big-button");
            cornerButton.setOnAction(e -> horn.play());
        } else {
            cornerButton = new Button("⏹ Spiel abbrechen");
            cornerButton.getStyleClass().add("big-button");
            cornerButton.disableProperty().bind(
                    state.clock().phaseProperty().isEqualTo(GameClock.Phase.FINISHED));
            cornerButton.setOnAction(e -> {
                if (dialogs.confirm("Das Spiel wirklich abbrechen? Die Uhr stoppt endgültig.")) {
                    state.abortGame();
                }
            });
        }

        cornerButton.setMinWidth(Region.USE_PREF_SIZE);
        column = new VBox(10, cornerButton, new PenaltyList(state, side).node());
        column.setAlignment(side == TeamSide.HOME ? Pos.TOP_LEFT : Pos.TOP_RIGHT);
        // Mindestbreite der Knöpfe darf die Zelle nicht aufweiten (sonst ragt „→ 4 Min“ bei
        // schmalem Fenster über den Rand): zu breiter Inhalt wird stattdessen eingepasst
        column.setMinWidth(0);
        ControlLayout.fitToCellWidth(column, side == TeamSide.HOME ? HPos.LEFT : HPos.RIGHT);
    }

    Node node() {
        return column;
    }
}
