package de.kmost.scoreboard.ui.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.PenaltyTimer;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.model.TeamTimeout;
import de.kmost.scoreboard.ui.AppIcon;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.TimeFormatter;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Publikumsanzeige. Das Layout hängt an einem festen, unsichtbaren Raster,
 * damit nichts verrutscht, wenn Strafen kommen und gehen oder Namen
 * unterschiedlich lang sind. Außen teilen sich Header, Spielstand und Footer
 * die Fensterhöhe im Verhältnis 10:80:10 — unabhängig von der Schriftart; die
 * konfigurierbaren Größenfaktoren des Themes skalieren Banner-Anteil und
 * Banner-Schrift gemeinsam (z. B. Faktor 2 → 20 % Höhe und doppelte Schrift);
 * ausgeblendete Banner geben ihren Anteil an den Spielstand ab. Zwischen
 * sichtbarem Banner und Spielstand liegt ein kleiner Abstand, der aus dem
 * Spielstand-Anteil kommt. Der Spielstand-Bereich selbst ist wieder ein
 * Prozent-Raster (Anteile bei Standard-Größenfaktoren):
 *
 *   Zeile 1 (38 %):  Strafen Heim | UHR + Status | Strafen Gast
 *   Zeile 2 (38 %):  TORE Heim | Phase | TORE Gast
 *   Zeile 3 (24 %):  Teamname + Timeout-Punkte je Seite
 *
 * Die Basis-Schriftgröße ist an die Höhe des Spielstand-Bereichs gebunden,
 * alle Größen im CSS sind in em — die Schriftgrößen definieren dadurch nur
 * die Verhältnisse innerhalb des Spielstands, nicht das äußere Raster. Die
 * Größenfaktoren für Uhr, Tore und Teamnamen gewichten ihre Zeilen im selben
 * Verhältnis mit und die Basisgröße normalisiert sich über die Gewichtssumme —
 * jedes Element passt dadurch bei jeder Reglerstellung in seine Zeile (ein
 * gleichmäßiges Vergrößern aller drei Faktoren ändert entsprechend nichts,
 * nur die Verhältnisse zueinander zählen).
 */
public class DisplayWindow {

    /** Sichtbarer Abstand zwischen Banner und Spielstand (Anteil der Fensterhöhe);
     *  geht zulasten des Spielstands, damit die Banner-Anteile exakt stimmen. */
    private static final double BANNER_GAP = 0.02;

    // Standard-Schriftgrößen (in em der Basisgröße) der skalierbaren Spielstand-Elemente;
    // display.css setzt für diese Klassen bewusst keine font-size, die kommt von hier
    private static final double CLOCK_EM = 5.2;
    private static final double SCORE_EM = 6.0;
    private static final double TEAM_NAME_EM = 1.1;
    private static final double PENALTY_EM = 1.4;
    private static final double TIMEOUT_EM = 1.0;
    private static final double TIMEOUT_DOTS_EM = 0.85;
    private static final double PHASE_EM = 1.1;

    // Standard-Höhenanteile der drei Spielstand-Zeilen; werden mit den Größenfaktoren
    // gewichtet und auf 100 % normalisiert (Summe bei Standardfaktoren = 1)
    private static final double ROW_CLOCK = 0.38;
    private static final double ROW_SCORE = 0.38;
    private static final double ROW_NAMES = 0.24;

    private final Stage stage = new Stage();
    // Inhalt + Footer untereinander; der Inhalt bekommt die gesamte Resthöhe
    private final BorderPane content = new BorderPane();
    private final VBox root = new VBox(content);
    private final Scene scene;
    private final StringProperty themeCss = new SimpleStringProperty(Theme.defaults().css());
    private final Banner header;
    private final Banner footer;
    // Größenfaktoren innerhalb des Spielstands (1.0 = Standard)
    private final DoubleProperty clockScale = new SimpleDoubleProperty(1);
    private final DoubleProperty scoreScale = new SimpleDoubleProperty(1);
    private final DoubleProperty nameScale = new SimpleDoubleProperty(1);
    private final DoubleProperty penaltyScale = new SimpleDoubleProperty(1);
    private final DoubleProperty timeoutScale = new SimpleDoubleProperty(1);
    private final DoubleProperty statusScale = new SimpleDoubleProperty(1);

    public DisplayWindow(ObjectProperty<GameState> gameState) {
        this(gameState, 1024, 576);
    }

    // Größe nur für die Offscreen-Vorschau in Tests wählbar
    DisplayWindow(ObjectProperty<GameState> gameState, double width, double height) {
        scene = new Scene(root, width, height);
        scene.setFill(Color.BLACK);
        scene.getStylesheets().add(
                getClass().getResource("/de/kmost/scoreboard/display.css").toExternalForm());
        VBox.setVgrow(content, Priority.ALWAYS);
        content.setMinHeight(0);
        header = new Banner(scene, "header");
        footer = new Banner(scene, "footer");
        root.getChildren().add(0, header.node());
        root.getChildren().add(footer.node());
        // Abstand zwischen Banner und Spielstand; wirkt nur zwischen managed Kindern,
        // ausgeblendete Banner erzeugen also weiterhin keinen Leerraum
        root.spacingProperty().bind(scene.heightProperty().multiply(BANNER_GAP));
        // Basis-Schriftgröße: an die Höhe des Spielstand-Bereichs gebunden (Fensterhöhe
        // abzüglich der sichtbaren Banner-Anteile), aber durch die Breite gedeckelt,
        // damit Uhr und Strafen-Chips auch in schmalen Fenstern vollständig in ihre
        // Prozent-Spalten passen statt abgeschnitten zu werden (alle Größen im CSS sind
        // in em, der Faktor 0.029 ist auf die breiteste Zeile — den Strafen-Chip in der
        // 25%-Spalte, abzüglich des äußeren Rasterabstands — ausgelegt; bei 16:9 und
        // breiter greift weiterhin die Höhe; 0.0625 entspricht den früheren 5 % der
        // Fensterhöhe bei zwei sichtbaren Standard-Bannern: 0.05 / 0.8; vergrößerte
        // Strafen-Chips passen sich zusätzlich per fitToWidth in ihre Spalte ein)
        root.styleProperty().bind(Bindings.createStringBinding(
                () -> {
                    double contentHeight = scene.getHeight()
                            * (1 - (header.node().isManaged()
                                    ? header.shareProperty().get() + BANNER_GAP : 0)
                                 - (footer.node().isManaged()
                                    ? footer.shareProperty().get() + BANNER_GAP : 0));
                    // Normalisierung über die Zeilengewichte: vergrößert ein Faktor seine
                    // Zeile, schrumpft die Basisgröße im Gegenzug — die Schrift jedes
                    // Elements behält dadurch ihr Verhältnis zur eigenen Zeilenhöhe
                    return String.format(Locale.US, "-fx-font-size: %.1fpx; ",
                            Math.max(10, Math.min(contentHeight * 0.0625, scene.getWidth() * 0.029))
                                    / rowWeightSum())
                            + themeCss.get();
                },
                scene.heightProperty(), scene.widthProperty(), themeCss,
                header.occupiesSpace(), footer.occupiesSpace(),
                header.shareProperty(), footer.shareProperty(),
                clockScale, scoreScale, nameScale));

        stage.setScene(scene);
        stage.setTitle("Spielstandsanzeige");
        AppIcon.apply(stage);
        stage.setFullScreenExitHint("");
        // Windows legt die Taskleiste über das Vollbild, sobald ein anderes Fenster
        // kurz den Fokus bekommt (z. B. durch die Audio-Ausgabe der Hupe) — im
        // Vollbild bleibt die Anzeige deshalb immer im Vordergrund
        stage.fullScreenProperty().addListener((obs, was, full) -> stage.setAlwaysOnTop(full));

        gameState.addListener((obs, oldState, state) -> rebuild(state));
        rebuild(gameState.get());
    }

    public void showOn(Screen screen, boolean fullScreen) {
        Rectangle2D bounds = screen.getVisualBounds();
        stage.setFullScreen(false);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.show();
        stage.toFront();
        if (fullScreen) {
            stage.setFullScreen(true);
        }
    }

    public void toggleFullScreen() {
        if (stage.isShowing()) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    Scene scene() {
        return scene;
    }

    /**
     * Wendet Theme-Farben und -Schriftart an, indem CSS-Variablen und Schriftfamilie
     * am Wurzelknoten gesetzt werden, und skaliert Header/Footer mit den
     * Größenfaktoren des Themes. Die Schriftart gilt nur hier auf der Anzeige.
     */
    public void applyTheme(Theme theme) {
        themeCss.set(theme.css() + theme.fontCss());
        header.setScale(theme.scale(FontScale.HEADER));
        footer.setScale(theme.scale(FontScale.FOOTER));
        clockScale.set(theme.scale(FontScale.CLOCK));
        scoreScale.set(theme.scale(FontScale.SCORE));
        nameScale.set(theme.scale(FontScale.TEAM_NAME));
        penaltyScale.set(theme.scale(FontScale.PENALTY));
        timeoutScale.set(theme.scale(FontScale.TIMEOUT));
        statusScale.set(theme.scale(FontScale.STATUS));
    }

    /** Header oben in der Anzeige; ohne anzeigbaren Inhalt ausgeblendet. */
    public ObjectProperty<BannerConfig> headerBannerProperty() {
        return header.configProperty();
    }

    /** Footer unten in der Anzeige; ohne anzeigbaren Inhalt ausgeblendet. */
    public ObjectProperty<BannerConfig> footerBannerProperty() {
        return footer.configProperty();
    }

    private void rebuild(GameState state) {
        if (state == null) {
            Label placeholder = new Label("Spielstandsanzeige");
            placeholder.getStyleClass().add("placeholder");
            content.setCenter(placeholder);
            return;
        }
        GameClock clock = state.clock();

        Node clockDisplay = buildClockDisplay(state, clock);

        Label timeoutLabel = new Label();
        timeoutLabel.getStyleClass().add("timeout");
        bindFontSize(timeoutLabel, TIMEOUT_EM, timeoutScale);
        state.activeTimeoutProperty().addListener((obs, oldTimeout, timeout) ->
                updateTimeoutChip(timeoutLabel, timeout));
        updateTimeoutChip(timeoutLabel, state.activeTimeoutProperty().get());

        HBox statusLine = new HBox(timeoutLabel);
        statusLine.setAlignment(Pos.CENTER);
        VBox clockBox = new VBox(clockDisplay, statusLine);
        clockBox.setAlignment(Pos.CENTER);

        GridPane topRow = new GridPane();
        topRow.getColumnConstraints().addAll(
                percentColumn(25), percentColumn(50), percentColumn(25));
        topRow.setAlignment(Pos.CENTER);
        topRow.add(buildPenaltyColumn(state, TeamSide.HOME), 0, 0);
        topRow.add(clockBox, 1, 0);
        topRow.add(buildPenaltyColumn(state, TeamSide.GUEST), 2, 0);

        // Die Halbzeit-Info steht abgekürzt zwischen den Toranzeigen — das spart die
        // Statuszeile unter der Uhr und lässt Luft für Header/Footer
        Label periodLabel = new Label();
        periodLabel.getStyleClass().add("phase");
        bindFontSize(periodLabel, PHASE_EM, statusScale);
        periodLabel.textProperty().bind(Bindings.createStringBinding(
                () -> periodText(state), clock.phaseProperty(), clock.periodProperty()));
        HBox periodBox = new HBox(periodLabel);
        periodBox.setAlignment(Pos.CENTER);

        GridPane scoreRow = new GridPane();
        scoreRow.getColumnConstraints().addAll(
                percentColumn(42), percentColumn(16), percentColumn(42));
        scoreRow.setAlignment(Pos.CENTER);
        scoreRow.add(buildScoreCell(state, TeamSide.HOME), 0, 0);
        scoreRow.add(periodBox, 1, 0);
        scoreRow.add(buildScoreCell(state, TeamSide.GUEST), 2, 0);

        // gleiche Spalten wie die Torzeile, damit die Namen exakt unter den Toren stehen
        GridPane nameRow = new GridPane();
        nameRow.getColumnConstraints().addAll(
                percentColumn(42), percentColumn(16), percentColumn(42));
        nameRow.setAlignment(Pos.TOP_CENTER);
        nameRow.add(buildNameCell(state, TeamSide.HOME), 0, 0);
        nameRow.add(buildNameCell(state, TeamSide.GUEST), 2, 0);

        GridPane outer = new GridPane();
        // Mindesthöhe aus den Label-Texten ignorieren: sonst überragt das Raster den
        // Spielstand-Bereich und wird vom BorderPane mittig darüber hinaus gelegt —
        // die Zeilen sollen exakt die Prozent-Anteile des Bereichs bekommen
        outer.setMinHeight(0);
        outer.getColumnConstraints().add(percentColumn(100));
        outer.getRowConstraints().addAll(
                weightedRow(ROW_CLOCK, clockScale),
                weightedRow(ROW_SCORE, scoreScale),
                weightedRow(ROW_NAMES, nameScale));
        outer.setPadding(new Insets(10, 15, 10, 15));
        outer.add(topRow, 0, 0);
        outer.add(scoreRow, 0, 1);
        outer.add(nameRow, 0, 2);

        content.setCenter(outer);
    }

    /**
     * Spieluhr als Zeile fester Ziffern-Zellen: jede Ziffer steht in einer Zelle
     * mit der Breite der breitesten Ziffer der aktuellen Schrift, der Doppelpunkt
     * behält seine natürliche Breite — beim Zählen bewegt sich dadurch nichts,
     * auch bei Schriften mit unterschiedlich breiten Ziffern.
     */
    private Node buildClockDisplay(GameState state, GameClock clock) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        // Schriftgröße auf dem Container, die Ziffern-Zellen erben sie
        box.styleProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.US, "-fx-font-size: %.2fem; ",
                        CLOCK_EM * clockScale.get()),
                clockScale));
        StringBinding text = Bindings.createStringBinding(
                () -> TimeFormatter.formatClock(clock.elapsedMillisProperty().get(),
                        clock.currentPeriodEndMillis(), state.config().direction()),
                clock.elapsedMillisProperty(), clock.periodProperty());
        // das Binding lebt so lange wie der Knoten (sonst räumt der GC es weg)
        box.getProperties().put("clockText", text);
        text.addListener((obs, oldText, newText) -> updateClockCells(box, newText));
        updateClockCells(box, text.get());
        return box;
    }

    /** Zellen an den Uhr-Text angleichen; die Anzahl ändert sich praktisch nie. */
    private static void updateClockCells(HBox box, String text) {
        if (box.getChildren().size() != text.length()) {
            List<Label> cells = new ArrayList<>();
            for (int i = 0; i < text.length(); i++) {
                Label cell = new Label();
                cell.getStyleClass().add("clock");
                cell.setAlignment(Pos.CENTER);
                cells.add(cell);
            }
            box.getChildren().setAll(cells);
            // Schriftwechsel (Theme-Schrift, em-Basis bei Fenstergröße) ändern die Zellenbreite
            cells.get(0).fontProperty().addListener(
                    (obs, oldFont, font) -> fixClockCellWidths(box));
        }
        for (int i = 0; i < text.length(); i++) {
            ((Label) box.getChildren().get(i)).setText(String.valueOf(text.charAt(i)));
        }
        fixClockCellWidths(box);
    }

    private static void fixClockCellWidths(HBox box) {
        if (box.getChildren().isEmpty()) {
            return;
        }
        double digitWidth = maxDigitWidth(((Label) box.getChildren().get(0)).getFont());
        for (Node child : box.getChildren()) {
            Label cell = (Label) child;
            boolean digit = !cell.getText().isEmpty()
                    && Character.isDigit(cell.getText().charAt(0));
            double width = digit ? digitWidth : Region.USE_COMPUTED_SIZE;
            cell.setMinWidth(width);
            cell.setPrefWidth(width);
            cell.setMaxWidth(width);
        }
    }

    /** Breite der breitesten Ziffer 0–9 in der gegebenen Schrift. */
    private static double maxDigitWidth(Font font) {
        Text probe = new Text();
        probe.setFont(font);
        double max = 0;
        for (char digit = '0'; digit <= '9'; digit++) {
            probe.setText(String.valueOf(digit));
            max = Math.max(max, probe.getLayoutBounds().getWidth());
        }
        return max;
    }

    /** Schriftgröße eines Spielstand-Elements: Standard-em mal Theme-Größenfaktor. */
    private void bindFontSize(Label label, double baseEm, DoubleProperty scale) {
        label.styleProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.US, "-fx-font-size: %.2fem; ", baseEm * scale.get()),
                scale));
    }

    private static ColumnConstraints percentColumn(double percent) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(percent);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    /** Spielstand-Zeile: Standardanteil mal Größenfaktor, normalisiert auf 100 %. */
    private RowConstraints weightedRow(double weight, DoubleProperty scale) {
        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        row.percentHeightProperty().bind(Bindings.createDoubleBinding(
                () -> 100 * weight * scale.get() / rowWeightSum(),
                clockScale, scoreScale, nameScale));
        return row;
    }

    /** Summe der gewichteten Zeilenanteile; 1.0 bei Standard-Größenfaktoren. */
    private double rowWeightSum() {
        return ROW_CLOCK * clockScale.get()
                + ROW_SCORE * scoreScale.get()
                + ROW_NAMES * nameScale.get();
    }

    /**
     * Toranzeige: Tore exakt mittig in der Spalte — der Teamname darunter nutzt
     * dieselben Spalten und steht dadurch zentral unter der Zahl.
     */
    private Node buildScoreCell(GameState state, TeamSide side) {
        Label scoreLabel = new Label();
        scoreLabel.getStyleClass().add("score");
        bindFontSize(scoreLabel, SCORE_EM, scoreScale);
        scoreLabel.textProperty().bind(state.scoreProperty(side).asString());

        StackPane cell = new StackPane(scoreLabel);
        StackPane.setAlignment(scoreLabel, Pos.CENTER);
        return cell;
    }

    private Node buildNameCell(GameState state, TeamSide side) {
        Label nameLabel = new Label(state.config().teamName(side));
        nameLabel.getStyleClass().add("team-name");
        bindFontSize(nameLabel, TEAM_NAME_EM, nameScale);
        nameLabel.setWrapText(true);
        // Breite begrenzen, damit lange Namen umbrechen statt in die andere Hälfte zu laufen
        nameLabel.maxWidthProperty().bind(scene.widthProperty().multiply(0.40));
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setTextAlignment(TextAlignment.CENTER);

        Label timeoutDots = new Label();
        timeoutDots.getStyleClass().add("timeout-dots");
        bindFontSize(timeoutDots, TIMEOUT_DOTS_EM, timeoutScale);
        timeoutDots.textProperty().bind(Bindings.createStringBinding(
                () -> TimeFormatter.formatTimeoutDots(
                        state.timeoutsUsedProperty(side).get(),
                        state.config().profile().teamTimeoutsPerGame()),
                state.timeoutsUsedProperty(side)));

        VBox cell = new VBox(4, nameLabel, timeoutDots);
        cell.setAlignment(Pos.TOP_CENTER);
        return cell;
    }

    /**
     * Laufende Zeitstrafen eines Teams, untereinander am äußeren Fensterrand.
     * Oben bündig statt vertikal zentriert: kommt eine Strafe dazu oder läuft
     * eine aus, bleiben die übrigen Chips an ihrem Platz statt zu springen.
     * Werden die Chips (per Größenfaktor) breiter als ihre Spalte, wird die
     * Spalte proportional eingepasst statt die Zeiten mit „…“ zu kürzen.
     */
    private Node buildPenaltyColumn(GameState state, TeamSide side) {
        VBox column = new VBox(10);
        column.setAlignment(side == TeamSide.HOME ? Pos.TOP_LEFT : Pos.TOP_RIGHT);
        // Mindestmaße der Chips dürfen die 25%-Spalte nicht aufweiten: Überbreite
        // fängt die Einpassung ab, zu viele Chips übereinander schneidet der Clip ab
        column.setMinWidth(0);
        column.setMinHeight(0);
        Scale fit = new Scale(1, 1);
        // passen mehr Chips untereinander, als die Zeile hoch ist, werden sie unten
        // schlicht abgeschnitten statt in die Tor-Zeile zu ragen — das Problem löst
        // sich mit ablaufenden Strafen von selbst (die kürzeste steht ja oben);
        // horizontal ist der Clip bewusst unbegrenzt, Überbreite regelt fitToWidth,
        // die Kante wird um den Einpass-Faktor kompensiert
        Rectangle clipRect = new Rectangle(-100_000, 0, 200_000, 0);
        clipRect.heightProperty().bind(
                Bindings.createDoubleBinding(() -> column.getHeight() / fit.getX(),
                        column.heightProperty(), fit.xProperty()));
        column.setClip(clipRect);
        if (side == TeamSide.GUEST) {
            // Pivot am äußeren Rand: die Chips bleiben beim Einpassen außen bündig
            fit.pivotXProperty().bind(column.widthProperty());
        }
        column.getTransforms().add(fit);
        InvalidationListener refit = obs -> fitToWidth(column, fit);
        column.widthProperty().addListener(refit);
        state.penalties(side).addListener((ListChangeListener<PenaltyTimer>) change ->
                rebuildPenaltyLabels(state, side, column, refit));
        rebuildPenaltyLabels(state, side, column, refit);
        return column;
    }

    /** Skaliert den Inhalt proportional herunter, wenn er breiter ist als die Region. */
    private static void fitToWidth(Region region, Scale fit) {
        double natural = region.prefWidth(-1);
        double available = region.getWidth();
        double factor = natural > available && available > 0 ? available / natural : 1;
        fit.setX(factor);
        fit.setY(factor);
    }

    private void rebuildPenaltyLabels(GameState state, TeamSide side, VBox penaltiesBox,
            InvalidationListener refit) {
        // die älteste Strafe (kürzeste Restzeit) oben — die Reihenfolge bleibt auch
        // zwischen den Neuaufbauten stabil, weil alle Strafen gleich schnell ablaufen
        penaltiesBox.getChildren().setAll(state.penalties(side).stream()
                .sorted(Comparator.comparingLong(timer -> timer.remainingMillisProperty().get()))
                .map(timer -> {
                    // festes Format „NN  M:SS“: alle Panels gleich groß, die Zeiten stehen
                    // bündig untereinander — mit oder ohne Spielernummer. Aufgefüllt wird
                    // mit Ziffernbreiten-Leerzeichen (U+2007), damit das auch in der
                    // konfigurierbaren Theme-Schrift stimmt, nicht nur in Monospace.
                    String number = timer.playerNumber() == null
                            ? "\u2007\u2007"
                            : timer.playerNumber().length() < 2
                                    ? "\u2007" + timer.playerNumber()
                                    : timer.playerNumber();
                    Label label = new Label();
                    label.getStyleClass().add("penalty");
                    // nie mit „…“ kürzen: die Spalte wird stattdessen eingepasst
                    label.setMinWidth(Region.USE_PREF_SIZE);
                    label.layoutBoundsProperty().addListener(refit);
                    bindFontSize(label, PENALTY_EM, penaltyScale);
                    label.textProperty().bind(Bindings.createStringBinding(
                            () -> number + "  "
                                    + TimeFormatter.formatRemaining(timer.remainingMillisProperty().get()),
                            timer.remainingMillisProperty()));
                    return (Node) label;
                })
                .toList());
        refit.invalidated(null);
    }

    /**
     * Der Timeout-Chip erscheint nur während eines Team-Timeouts unter der Uhr.
     * Der Text ist bewusst ein einzeiliger Kurztext mit Seitenpfeil (◀ = Heim,
     * ▶ = Gast) statt des Teamnamens — er kann dadurch nie umbrechen oder das Raster sprengen.
     */
    private void updateTimeoutChip(Label timeoutLabel, TeamTimeout timeout) {
        boolean active = timeout != null;
        timeoutLabel.setVisible(active);
        timeoutLabel.setManaged(active);
        timeoutLabel.textProperty().unbind();
        if (!active) {
            timeoutLabel.setText("");
            return;
        }
        timeoutLabel.textProperty().bind(Bindings.createStringBinding(
                () -> timeout.side() == TeamSide.HOME
                        ? "◀ Team-Timeout " + TimeFormatter.formatRemaining(timeout.remainingMillisProperty().get())
                        : "Team-Timeout " + TimeFormatter.formatRemaining(timeout.remainingMillisProperty().get()) + " ▶",
                timeout.remainingMillisProperty()));
    }

    /** Kurzform zwischen den Toranzeigen: „1. HZ“, „Pause“, „Ende“. */
    private static String periodText(GameState state) {
        GameClock clock = state.clock();
        boolean twoHalves = state.config().mode() == GameMode.TWO_HALVES;
        return switch (clock.phaseProperty().get()) {
            case NOT_STARTED, RUNNING, PAUSED -> twoHalves
                    ? clock.periodProperty().get() + ". HZ"
                    : "";
            case HALF_TIME -> "Pause";
            case FINISHED -> "Ende";
        };
    }
}
