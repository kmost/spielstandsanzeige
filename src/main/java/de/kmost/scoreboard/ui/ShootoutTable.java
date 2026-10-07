package de.kmost.scoreboard.ui;

import java.util.List;

import de.kmost.scoreboard.model.Shootout;
import de.kmost.scoreboard.model.TeamSide;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Wurf-Tabelle des 7-m-Werfens, gemeinsam für Kampfgericht-Konsole und Publikumsanzeige:
 * oben dezente Rundennummern, darunter je Team die Trefferfolge (⚽ Tor, ✋ Fehlwurf).
 * Die ersten fünf Runden (die regulären Schützen) stehen von Anfang an in der Tabelle, auch
 * solange noch nicht geworfen wurde; ihre Zellen behalten den Platz der Symbole, damit sich
 * beim Werfen nichts verschiebt. Passen nicht mehr alle Runden hinein, verlassen die ältesten die Tabelle („…“) —
 * die jüngsten Würfe bleiben immer sichtbar. Aussehen (Schriftgrößen, Farben) bestimmen
 * die übergebenen Style-Klassen; die Tabelle aktualisiert sich bei jedem Wurf und bei
 * „Wurf zurücknehmen“ selbst.
 */
public final class ShootoutTable {

    /** Sichtbare Runden; ältere Runden verlassen die Tabelle per „…“. */
    public static final int VISIBLE_ROUNDS = 10;

    /** Treffer: Ball — auch auf dem Knopf „Tor“ der Konsole. */
    public static final String GOAL_SYMBOL = "⚽";

    /** Fehlwurf: Hand (der Torwart hat gehalten) — auch auf dem Knopf „Kein Tor“ der Konsole. */
    public static final String MISS_SYMBOL = "✋";

    private final Shootout shootout;
    private final String numberStyle;
    private final String teamStyle;
    private final String symbolStyle;
    private final GridPane table = new GridPane();
    // eigene Referenz: der Listener hängt an der Wurf-Liste des Shootouts und lebt so lange wie dieses
    private final ListChangeListener<Shootout.Attempt> onAttempts = change -> rebuild();

    /**
     * @param numberStyle Style-Klasse der Rundennummern
     * @param teamStyle   Style-Klasse der Zeilenbeschriftung („Heim“, „Gast“)
     * @param symbolStyle Style-Klasse der Trefferfolge-Symbole und der Auslassung „…“
     * @param hgap        waagerechter Abstand der Spalten in px
     * @param vgap        senkrechter Abstand der Zeilen in px
     */
    public ShootoutTable(Shootout shootout, String numberStyle, String teamStyle,
                         String symbolStyle, double hgap, double vgap) {
        this.shootout = shootout;
        this.numberStyle = numberStyle;
        this.teamStyle = teamStyle;
        this.symbolStyle = symbolStyle;
        table.setHgap(hgap);
        table.setVgap(vgap);
        shootout.attempts().addListener(onAttempts);
        rebuild();
    }

    public GridPane node() {
        return table;
    }

    private void rebuild() {
        table.getChildren().clear();
        List<Shootout.Attempt> home = shootout.attemptsFor(TeamSide.HOME);
        List<Shootout.Attempt> guest = shootout.attemptsFor(TeamSide.GUEST);
        int rounds = Math.max(Shootout.THROWERS_PER_TEAM, Math.max(home.size(), guest.size()));
        int firstRound = Math.max(0, rounds - VISIBLE_ROUNDS);
        addCell(0, 1, TeamSide.HOME.label(), teamStyle);
        addCell(0, 2, TeamSide.GUEST.label(), teamStyle);
        int column = 1;
        if (firstRound > 0) {
            addCell(column, 1, "…", symbolStyle);
            addCell(column, 2, "…", symbolStyle);
            column++;
        }
        for (int round = firstRound; round < rounds; round++, column++) {
            addNumber(column, round + 1);
            addThrow(column, 1, round < home.size() ? home.get(round) : null);
            addThrow(column, 2, round < guest.size() ? guest.get(round) : null);
        }
    }

    /**
     * Wurf eines Teams in der Runde; {@code null} = noch nicht geworfen. Die Zelle enthält beide
     * Symbole übereinander und zeigt nur das passende: So hat jede Zelle immer die Breite des
     * breiteren Symbols, und weder ein Fehlwurf noch ein späterer Wurf verschiebt die Spalten.
     */
    private void addThrow(int column, int row, Shootout.Attempt attempt) {
        Label goal = symbolLabel(GOAL_SYMBOL, attempt != null && attempt.goal());
        Label miss = symbolLabel(MISS_SYMBOL, attempt != null && !attempt.goal());
        addNode(column, row, new StackPane(goal, miss));
    }

    /** Rundennummer; ein unsichtbares „88“ hält die Spalte auch bei einstelligen Nummern gleich breit. */
    private void addNumber(int column, int round) {
        Label number = label(String.valueOf(round), numberStyle);
        Label widest = label("88", numberStyle);
        widest.setVisible(false);
        addNode(column, 0, new StackPane(widest, number));
    }

    private Label symbolLabel(String symbol, boolean visible) {
        Label label = label(symbol, symbolStyle);
        label.setVisible(visible);
        return label;
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        // nie mit „…“ kürzen: zu breite Tabellen werden vom Aufrufer eingepasst
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private void addNode(int column, int row, Node node) {
        table.add(node, column, row);
        GridPane.setHalignment(node, HPos.CENTER);
    }

    private void addCell(int column, int row, String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        // nie mit „…“ kürzen: zu breite Tabellen werden vom Aufrufer eingepasst
        label.setMinWidth(Region.USE_PREF_SIZE);
        table.add(label, column, row);
        GridPane.setHalignment(label, column == 0 ? HPos.LEFT : HPos.CENTER);
    }
}
