package de.kmost.scoreboard.ui.control;

import java.util.List;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.Shootout;
import de.kmost.scoreboard.model.TeamSide;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
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

    /** Sichtbare Runden der Wurf-Tabelle; ältere Runden verlassen die Tabelle per „…“. */
    private static final int VISIBLE_ROUNDS = 15;

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

        GridPane table = new GridPane();
        table.setHgap(10);
        table.setVgap(2);
        shootout.attempts().addListener((ListChangeListener<Shootout.Attempt>) change ->
                rebuildTable(table, shootout));
        rebuildTable(table, shootout);
        HBox tableLine = new HBox(table);
        tableLine.setAlignment(Pos.CENTER);
        ControlLayout.fitToCellWidth(tableLine, HPos.CENTER);

        pane = new VBox(8, statusLabel, buttonRow, tableLine);
        pane.setAlignment(Pos.CENTER);
    }

    Node node() {
        return pane;
    }

    /**
     * Wurf-Tabelle: oben dezente Rundennummern, darunter je Team die Trefferfolge
     * (● Tor, ○ Fehlwurf). Passen nicht mehr alle Runden hinein, verlassen die
     * ältesten die Tabelle („…“) — die jüngsten Würfe bleiben immer sichtbar.
     */
    private static void rebuildTable(GridPane table, Shootout shootout) {
        table.getChildren().clear();
        List<Shootout.Attempt> home = shootout.attemptsFor(TeamSide.HOME);
        List<Shootout.Attempt> guest = shootout.attemptsFor(TeamSide.GUEST);
        int rounds = Math.max(home.size(), guest.size());
        int firstRound = Math.max(0, rounds - VISIBLE_ROUNDS);
        addCell(table, 0, 1, TeamSide.HOME.label(), "game-shootout-team");
        addCell(table, 0, 2, TeamSide.GUEST.label(), "game-shootout-team");
        int column = 1;
        if (firstRound > 0) {
            addCell(table, column, 1, "…", "game-shootout-symbol");
            addCell(table, column, 2, "…", "game-shootout-symbol");
            column++;
        }
        for (int round = firstRound; round < rounds; round++, column++) {
            addCell(table, column, 0, String.valueOf(round + 1), "game-shootout-number");
            if (round < home.size()) {
                addCell(table, column, 1, home.get(round).goal() ? "●" : "○", "game-shootout-symbol");
            }
            if (round < guest.size()) {
                addCell(table, column, 2, guest.get(round).goal() ? "●" : "○", "game-shootout-symbol");
            }
        }
    }

    private static void addCell(GridPane table, int column, int row, String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        table.add(label, column, row);
        GridPane.setHalignment(label, column == 0 ? HPos.LEFT : HPos.CENTER);
    }
}
