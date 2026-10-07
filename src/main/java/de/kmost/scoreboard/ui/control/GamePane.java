package de.kmost.scoreboard.ui.control;

import static de.kmost.scoreboard.ui.control.ControlLayout.percentColumn;
import static de.kmost.scoreboard.ui.control.ControlLayout.percentRow;

import java.util.Locale;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.Shootout;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.ui.Dialogs;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;

/**
 * Spielsteuerung im Raster der Publikumsanzeige (Strafen außen | Uhr mittig |
 * Tore in den Spielhälften | Teamnamen darunter), ergänzt um die Bedienelemente:
 * Hupe oben links, Spielabbruch oben rechts, Uhr-Steuerung unter der Uhr,
 * Tor-/Strafen-/Timeout-Bedienung in der jeweiligen Spielhälfte.
 *
 * <p>Beim Start des 7-m-Werfens bekommt die Wurf-Steuerung eine eigene Zeile; die übrigen
 * Zeilen bleiben bestehen und werden nur neu im Raster verteilt.
 */
final class GamePane {

    private final GameState state;
    private final GridPane pane = new GridPane();
    private final GridPane topRow;
    private final GridPane scoreRow;
    private final GridPane nameRow;

    GamePane(GameState state, Horn horn, Dialogs dialogs) {
        this.state = state;

        // die 100%-Zeilen lassen die Zellen ihre komplette Raster-Zone füllen —
        // die Kinder verteilen sich per eigener Ausrichtung darin
        topRow = new GridPane();
        // wie beim äußeren Raster: überbreite Inhalte werden eingepasst statt
        // die Mindestbreite der Zeile (und damit des Fensters) aufzuweiten
        topRow.setMinWidth(0);
        topRow.getColumnConstraints().addAll(
                percentColumn(25), percentColumn(50), percentColumn(25));
        topRow.getRowConstraints().add(percentRow(100));
        topRow.add(new CornerColumn(state, TeamSide.HOME, horn, dialogs).node(), 0, 0);
        topRow.add(new ClockPane(state, dialogs).node(), 1, 0);
        topRow.add(new CornerColumn(state, TeamSide.GUEST, horn, dialogs).node(), 2, 0);

        scoreRow = halves(new ScoreCell(state, TeamSide.HOME).node(),
                new ScoreCell(state, TeamSide.GUEST).node());
        nameRow = halves(new TeamControls(state, TeamSide.HOME).node(),
                new TeamControls(state, TeamSide.GUEST).node());

        // die Zeilen füllen den Content-Bereich immer vollständig, im
        // Standard-Verhältnis 45:30:25 — wie das Prozent-Raster der Anzeige
        pane.getStyleClass().add("game-pane");
        pane.setPadding(new Insets(15));
        pane.setMinHeight(0);
        // Mindestbreiten überbreiter Zeilen (z. B. „1. Halbzeit der Verlängerung
        // starten“) nicht nach außen tragen: eingepasst wird per fitToCellWidth
        pane.setMinWidth(0);
        pane.getColumnConstraints().add(percentColumn(100));
        arrange(state.shootoutProperty().get());
        state.shootoutProperty().addListener((obs, oldShootout, shootout) -> arrange(shootout));

        // Basis-Schriftgröße an die Höhe des Spielbereichs selbst gebunden
        // (nicht ans Fenster: der feste Setup-Bereich oben ließe die Zonen sonst
        // schneller wachsen als die Schrift). 13 px bei Standardgröße 940×700
        // (Spielbereich ≈ 485 px hoch); alle .game-Größen sind in em, Buttons
        // und Labels wachsen dadurch im gleichen Verhältnis wie ihre Zonen —
        // bewusst ohne eigene Regler je Element wie auf der Publikumsanzeige.
        // Eine Breiten-Deckelung gibt es nicht: zu breite Zeilen passen sich
        // per fitToCellWidth in ihre Zellen ein.
        pane.styleProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.US, "-fx-font-size: %.1fpx; ",
                        Math.max(10, pane.getHeight() * 0.0268)),
                pane.heightProperty()));
    }

    Node node() {
        return pane;
    }

    private static GridPane halves(Node home, Node guest) {
        GridPane row = new GridPane();
        row.setMinWidth(0);
        row.getColumnConstraints().addAll(percentColumn(50), percentColumn(50));
        row.getRowConstraints().add(percentRow(100));
        row.add(home, 0, 0);
        row.add(guest, 1, 0);
        return row;
    }

    /** Verteilt die Zeilen im Raster; beim 7-m-Werfen kommt die Wurf-Steuerung als eigene Zeile dazu. */
    private void arrange(Shootout shootout) {
        pane.getChildren().clear();
        pane.getRowConstraints().clear();
        if (shootout == null) {
            pane.getRowConstraints().addAll(percentRow(45), percentRow(30), percentRow(25));
            pane.add(topRow, 0, 0);
            pane.add(scoreRow, 0, 1);
            pane.add(nameRow, 0, 2);
        } else {
            // beim 7-m-Werfen bekommt die Wurf-Steuerung eine eigene Zeile in voller Breite
            pane.getRowConstraints().addAll(
                    percentRow(32), percentRow(25), percentRow(26), percentRow(17));
            pane.add(topRow, 0, 0);
            pane.add(new ShootoutPane(state, shootout).node(), 0, 1);
            pane.add(scoreRow, 0, 2);
            pane.add(nameRow, 0, 3);
        }
    }
}
