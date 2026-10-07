package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.PenaltyTimer;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.TimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Laufende Zeitstrafen eines Teams als klickbare Zeilen: ein Klick auf die Zeile bricht die
 * Strafe ab, „→ 4 Min“ verlängert sie. Die Reihenfolge ist die des Models (älteste Strafe oben).
 */
final class PenaltyList {

    private final GameState state;
    private final VBox box = new VBox(4);

    PenaltyList(GameState state, TeamSide side) {
        this.state = state;
        box.setAlignment(side == TeamSide.HOME ? Pos.TOP_LEFT : Pos.TOP_RIGHT);
        state.sortedPenalties(side).addListener((ListChangeListener<PenaltyTimer>) change -> rebuild(side));
        rebuild(side);
    }

    Node node() {
        return box;
    }

    private void rebuild(TeamSide side) {
        // gleiche Reihenfolge wie auf der Anzeige: älteste Strafe (kürzeste Restzeit) oben
        box.getChildren().setAll(state.sortedPenalties(side).stream()
                .map(this::entry)
                .toList());
    }

    /** Strafen-Zeile plus Knopf, der die Strafe auf die doppelte Dauer verlängert. */
    private Node entry(PenaltyTimer timer) {
        Button extendButton = new Button("→ 4 Min");
        extendButton.setTooltip(new Tooltip("Zeitstrafe auf 4 Minuten verlängern"));
        extendButton.disableProperty().bind(Bindings.createBooleanBinding(
                timer::isExtended, timer.remainingMillisProperty()));
        extendButton.setOnAction(e -> state.extendPenalty(timer));
        extendButton.setMinWidth(Region.USE_PREF_SIZE);
        HBox entry = new HBox(4, row(timer), extendButton);
        entry.setAlignment(Pos.CENTER_LEFT);
        return entry;
    }

    /** Eine Zeitstrafen-Zeile ist als Ganzes klickbar: ein Klick bricht die Strafe ab. */
    private Node row(PenaltyTimer timer) {
        // kompakt wie auf der Anzeige („Nr. + Zeit“), damit nichts abgeschnitten wird
        String prefix = timer.playerNumber() == null
                ? "⏱ "
                : "⏱ Nr. " + timer.playerNumber() + "  ";
        Button row = new Button();
        row.textProperty().bind(Bindings.createStringBinding(
                () -> prefix + TimeFormatter.formatRemaining(timer.remainingMillisProperty().get()) + "  ✕",
                timer.remainingMillisProperty()));
        row.getStyleClass().add("game-penalty-row");
        row.setMinWidth(Region.USE_PREF_SIZE);
        row.setTooltip(new Tooltip("Klicken, um die Zeitstrafe abzubrechen"));
        row.setOnAction(e -> state.removePenalty(timer));
        return row;
    }
}
