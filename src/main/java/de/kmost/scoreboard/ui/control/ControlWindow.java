package de.kmost.scoreboard.ui.control;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.PenaltyTimer;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.model.TeamTimeout;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.AppIcon;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.TimeFormatter;
import de.kmost.scoreboard.ui.config.ConfigWindow;
import de.kmost.scoreboard.ui.display.DisplayWindow;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.beans.InvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Kampfgericht-Konsole: Spiel-Setup, Spielsteuerung und Steuerung der Publikumsanzeige. */
public class ControlWindow {

    private final Stage stage;
    private final Horn horn;
    private final ObjectProperty<GameState> gameState = new SimpleObjectProperty<>();
    private final DisplayWindow displayWindow;
    private final BorderPane root = new BorderPane();

    private final TeamRepository teamRepository;
    private final ThemeRepository themeRepository;
    private ConfigWindow configWindow;
    private final ObservableList<String> knownTeams = FXCollections.observableArrayList();
    private final Map<TeamSide, TeamNamePicker> teamPickers = new EnumMap<>(TeamSide.class);
    private final ComboBox<GameMode> modeBox = new ComboBox<>();
    private final Spinner<Integer> minutesSpinner =
            new Spinner<>(1, 120, (int) SportProfile.HANDBALL.defaultPeriodDuration().toMinutes());
    private final ComboBox<ClockDirection> directionBox = new ComboBox<>();
    private final ComboBox<Screen> screenBox = new ComboBox<>();
    /** Konfiguriertes Standard-Heimteam; steht beim Setup im Heim-Feld vorbelegt. */
    private String defaultHomeTeam;

    public ControlWindow(Stage stage, Horn horn, TeamRepository teamRepository,
                         ThemeRepository themeRepository) {
        this(stage, horn, teamRepository, themeRepository, 940, 700);
    }

    // Größe nur für die Offscreen-Vorschau in Tests wählbar
    ControlWindow(Stage stage, Horn horn, TeamRepository teamRepository,
                  ThemeRepository themeRepository, double width, double height) {
        this.stage = stage;
        this.horn = horn;
        this.teamRepository = teamRepository;
        this.themeRepository = themeRepository;
        this.knownTeams.setAll(teamRepository.teamNames());
        this.defaultHomeTeam = teamRepository.defaultHomeTeam();
        this.displayWindow = new DisplayWindow(gameState);
        displayWindow.applyTheme(themeRepository.currentTheme());
        displayWindow.headerBannerProperty().set(themeRepository.currentHeader());
        displayWindow.footerBannerProperty().set(themeRepository.currentFooter());
        applyTheme(themeRepository.currentTheme());
        // gespeicherte Hupen-Auswahl wiederherstellen; eine nicht mehr ladbare
        // externe Datei fällt still auf den gespeicherten eingebauten Ton zurück
        if (!horn.useFile(themeRepository.hornFile())) {
            horn.useTone(Horn.toneOrDefault(themeRepository.hornTone()));
        }

        root.setTop(buildSetupPane());
        root.setCenter(buildPlaceholder());
        gameState.addListener((obs, oldState, state) -> root.setCenter(buildGamePane(state)));

        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(
                getClass().getResource("/de/kmost/scoreboard/control.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Kampfgericht – Spielstandsanzeige");
        AppIcon.apply(stage);
        stage.setOnCloseRequest(e -> Platform.exit());
    }

    public GameState gameState() {
        return gameState.get();
    }

    // nur für die Offscreen-Vorschau in Tests: Spielzustand setzen und Szene rendern
    ObjectProperty<GameState> gameStateProperty() {
        return gameState;
    }

    Scene scene() {
        return stage.getScene();
    }

    /** Färbt die Spielsteuerung in den Theme-Farben der Anzeige (CSS-Variablen am Root). */
    private void applyTheme(Theme theme) {
        root.setStyle(theme.css());
    }

    public void show() {
        stage.show();
    }

    // --- Setup ---

    private Node buildSetupPane() {
        modeBox.getItems().setAll(GameMode.values());
        modeBox.setValue(GameMode.TWO_HALVES);
        directionBox.getItems().setAll(ClockDirection.values());
        directionBox.setValue(ClockDirection.UP);
        minutesSpinner.setEditable(true);
        minutesSpinner.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                minutesSpinner.increment(0); // eingetippten Wert übernehmen
            }
        });

        // Heim und Gast als gleich breite Karten nebeneinander — wie die Spielhälften
        HBox teamCards = new HBox(12, buildTeamCard(TeamSide.HOME), buildTeamCard(TeamSide.GUEST));

        Button createButton = new Button("✚ Spiel anlegen");
        createButton.getStyleClass().add("create-button");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> createGame());

        minutesSpinner.setPrefWidth(80);
        Region paramsSpacer = new Region();
        HBox.setHgrow(paramsSpacer, Priority.ALWAYS);
        HBox paramsRow = new HBox(10,
                new Label("Modus:"), modeBox,
                new Label("Periodendauer (min):"), minutesSpinner,
                new Label("Uhr:"), directionBox,
                paramsSpacer, createButton);
        paramsRow.setAlignment(Pos.CENTER_LEFT);

        screenBox.setItems(Screen.getScreens());
        screenBox.setButtonCell(screenCell());
        screenBox.setCellFactory(list -> screenCell());
        screenBox.getSelectionModel().select(Screen.getScreens().size() > 1 ? 1 : 0);

        Button openDisplayButton = new Button("🖥 Anzeige öffnen");
        openDisplayButton.setOnAction(e -> {
            Screen screen = screenBox.getValue() != null ? screenBox.getValue() : Screen.getPrimary();
            displayWindow.showOn(screen, screen != Screen.getPrimary());
        });
        Button fullScreenButton = new Button("⛶ Vollbild umschalten");
        fullScreenButton.setOnAction(e -> displayWindow.toggleFullScreen());

        Button configButton = new Button("🎨 Konfiguration…");
        configButton.setOnAction(e -> {
            if (configWindow == null) {
                configWindow = new ConfigWindow(stage, displayWindow, themeRepository,
                        teamRepository, horn, this::applyTheme, this::applyDefaultHomeTeam);
            }
            configWindow.show();
        });

        HBox displayRow = new HBox(10, new Label("Publikumsanzeige:"), screenBox, openDisplayButton,
                fullScreenButton, configButton);
        displayRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, teamCards, paramsRow, new Separator(), displayRow);
        content.setPadding(new Insets(10));

        TitledPane pane = new TitledPane("Spiel-Einstellungen", content);
        pane.setCollapsible(false);
        return pane;
    }

    /** Karte einer Mannschaft im Setup: Titel und Teamnamen-Auswahl. */
    private Node buildTeamCard(TeamSide side) {
        Label title = new Label(side.label());
        title.getStyleClass().add("team-card-title");

        TeamNamePicker picker = new TeamNamePicker(knownTeams, side.label());
        if (side == TeamSide.HOME) {
            picker.setText(defaultHomeTeam);
        }
        teamPickers.put(side, picker);

        VBox card = new VBox(8, title, picker.node());
        card.getStyleClass().add("team-card");
        card.setPadding(new Insets(10, 12, 12, 12));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /**
     * Übernimmt ein in der Konfiguration geändertes Standard-Heimteam sofort ins
     * Heim-Feld — aber nur, solange dort nichts anderes eingetragen wurde.
     */
    private void applyDefaultHomeTeam(String name) {
        TeamNamePicker picker = teamPickers.get(TeamSide.HOME);
        String current = picker.text() == null ? "" : picker.text().strip();
        if (current.isEmpty() || current.equals(defaultHomeTeam)) {
            picker.setText(name);
        }
        defaultHomeTeam = name;
    }

    /** Editierbares Dropdown mit Textfilter über alle gespeicherten Teams. */
    private String teamName(TeamSide side) {
        TeamNamePicker picker = teamPickers.get(side);
        return picker == null ? side.label() : orDefault(picker.text(), side.label());
    }

    private static ListCell<Screen> screenCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Screen screen, boolean empty) {
                super.updateItem(screen, empty);
                if (empty || screen == null) {
                    setText(null);
                } else {
                    Rectangle2D b = screen.getBounds();
                    int index = Screen.getScreens().indexOf(screen) + 1;
                    setText("Bildschirm " + index + " (" + (int) b.getWidth() + "×" + (int) b.getHeight() + ")"
                            + (screen == Screen.getPrimary() ? " – Hauptbildschirm" : ""));
                }
            }
        };
    }

    private void createGame() {
        GameState current = gameState.get();
        if (current != null && current.clock().phaseProperty().get() != GameClock.Phase.FINISHED) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Das aktuelle Spiel wird verworfen. Neues Spiel anlegen?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        String homeName = teamName(TeamSide.HOME);
        String guestName = teamName(TeamSide.GUEST);
        rememberTeam(homeName, TeamSide.HOME);
        rememberTeam(guestName, TeamSide.GUEST);
        knownTeams.setAll(teamRepository.teamNames());
        GameConfig config = new GameConfig(
                homeName,
                guestName,
                modeBox.getValue(),
                Duration.ofMinutes(minutesSpinner.getValue()),
                directionBox.getValue(),
                SportProfile.HANDBALL);
        GameState state = new GameState(config);
        state.clock().setOnPeriodEnd(horn::play);
        state.setOnTimeoutEnd(horn::play);
        gameState.set(state);
    }

    private static String orDefault(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text.strip();
    }

    /** Speichert das Team in der Datenbank; Standardnamen (Heim/Gast) werden nicht gemerkt. */
    private void rememberTeam(String name, TeamSide side) {
        if (!name.equals(side.label())) {
            teamRepository.saveTeam(name);
        }
    }

    // --- Spielsteuerung ---

    private Node buildPlaceholder() {
        Label label = new Label("Noch kein Spiel angelegt – oben konfigurieren und „Spiel anlegen“ drücken.");
        label.getStyleClass().add("game-placeholder");
        BorderPane pane = new BorderPane(label);
        pane.getStyleClass().add("game-pane");
        return pane;
    }

    /**
     * Spielsteuerung im Raster der Publikumsanzeige (Strafen außen | Uhr mittig |
     * Tore in den Spielhälften | Teamnamen darunter), ergänzt um die Bedienelemente:
     * Hupe oben links, Spielabbruch oben rechts, Uhr-Steuerung unter der Uhr,
     * Tor-/Strafen-/Timeout-Bedienung in der jeweiligen Spielhälfte.
     */
    private Node buildGamePane(GameState state) {
        if (state == null) {
            return buildPlaceholder();
        }

        // die 100%-Zeilen lassen die Zellen ihre komplette Raster-Zone füllen —
        // die Kinder verteilen sich per eigener Ausrichtung darin
        GridPane topRow = new GridPane();
        topRow.getColumnConstraints().addAll(
                percentColumn(25), percentColumn(50), percentColumn(25));
        topRow.getRowConstraints().add(percentRow(100));
        topRow.add(buildCornerColumn(state, TeamSide.HOME), 0, 0);
        topRow.add(buildClockBox(state), 1, 0);
        topRow.add(buildCornerColumn(state, TeamSide.GUEST), 2, 0);

        GridPane scoreRow = new GridPane();
        scoreRow.getColumnConstraints().addAll(percentColumn(50), percentColumn(50));
        scoreRow.getRowConstraints().add(percentRow(100));
        scoreRow.add(buildScoreCell(state, TeamSide.HOME), 0, 0);
        scoreRow.add(buildScoreCell(state, TeamSide.GUEST), 1, 0);

        GridPane nameRow = new GridPane();
        nameRow.getColumnConstraints().addAll(percentColumn(50), percentColumn(50));
        nameRow.getRowConstraints().add(percentRow(100));
        nameRow.add(buildTeamControls(state, TeamSide.HOME), 0, 0);
        nameRow.add(buildTeamControls(state, TeamSide.GUEST), 1, 0);

        // die drei Zeilen füllen den Content-Bereich immer vollständig, im
        // Standard-Verhältnis 45:30:25 — wie das Prozent-Raster der Anzeige
        GridPane pane = new GridPane();
        pane.getStyleClass().add("game-pane");
        pane.setPadding(new Insets(15));
        pane.setMinHeight(0);
        pane.getColumnConstraints().add(percentColumn(100));
        pane.getRowConstraints().addAll(percentRow(45), percentRow(30), percentRow(25));
        pane.add(topRow, 0, 0);
        pane.add(scoreRow, 0, 1);
        pane.add(nameRow, 0, 2);
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
        return pane;
    }

    private static RowConstraints percentRow(double percent) {
        RowConstraints row = new RowConstraints();
        row.setPercentHeight(percent);
        row.setVgrow(Priority.ALWAYS);
        return row;
    }

    /**
     * Passt den Inhalt einer Raster-Zelle per Skalierung ein, sobald er breiter
     * ist als der ihm zugeteilte Platz — analog zur Einpassung auf der
     * Publikumsanzeige: nichts wird mit „…“ gekürzt, nichts ragt in
     * Nachbarzellen. Damit das funktioniert, müssen die Kinder ihre bevorzugte
     * Breite als Mindestbreite behalten (USE_PREF_SIZE) statt gestaucht zu
     * werden. Der Pivot bestimmt, welche Kante beim Einpassen stehen bleibt.
     */
    private static <T extends Region> T fitToCellWidth(T content, HPos anchor) {
        Scale fit = new Scale(1, 1);
        if (anchor == HPos.RIGHT) {
            fit.pivotXProperty().bind(content.widthProperty());
        }
        // LEFT/CENTER: Pivot 0 — überbreiter Inhalt beginnt links und füllt
        // eingepasst genau die Zellbreite
        fit.pivotYProperty().bind(content.heightProperty().divide(2));
        content.getTransforms().add(fit);
        InvalidationListener refit = obs -> {
            double natural = content.prefWidth(-1);
            double available = content.getWidth();
            double factor = natural > available && available > 0 ? available / natural : 1;
            fit.setX(factor);
            fit.setY(factor);
        };
        content.widthProperty().addListener(refit);
        for (Node child : content.getChildrenUnmodifiable()) {
            child.layoutBoundsProperty().addListener(refit);
        }
        // dynamisch neu aufgebaute Kinder (Strafen, Timeout-Zeile) mitverfolgen
        content.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                for (Node added : change.getAddedSubList()) {
                    added.layoutBoundsProperty().addListener(refit);
                }
            }
            refit.invalidated(null);
        });
        return content;
    }

    private static ColumnConstraints percentColumn(double percent) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(percent);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    /** Uhr + Status mittig wie auf der Anzeige, darunter die Uhr-Steuerung. */
    private Node buildClockBox(GameState state) {
        GameClock clock = state.clock();

        Label clockLabel = new Label();
        clockLabel.textProperty().bind(Bindings.createStringBinding(
                () -> TimeFormatter.formatClock(clock.elapsedMillisProperty().get(),
                        clock.currentPeriodEndMillis(), state.config().direction()),
                clock.elapsedMillisProperty(), clock.periodProperty()));
        clockLabel.getStyleClass().add("game-clock");
        clockLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox clockLine = new HBox(clockLabel);
        clockLine.setAlignment(Pos.CENTER);
        fitToCellWidth(clockLine, HPos.LEFT);

        Label phaseLabel = new Label();
        phaseLabel.textProperty().bind(Bindings.createStringBinding(
                () -> phaseText(state), clock.phaseProperty(), clock.periodProperty()));
        phaseLabel.getStyleClass().add("game-phase");

        Button startPauseButton = new Button();
        startPauseButton.setMinWidth(Region.USE_PREF_SIZE);
        startPauseButton.getStyleClass().add("big-button");
        startPauseButton.textProperty().bind(Bindings.createStringBinding(
                () -> switch (clock.phaseProperty().get()) {
                    case RUNNING -> "⏸ Pause";
                    case PAUSED -> "▶ Fortsetzen";
                    default -> "▶ Start";
                }, clock.phaseProperty()));
        startPauseButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> clock.phaseProperty().get() == GameClock.Phase.HALF_TIME
                        || clock.phaseProperty().get() == GameClock.Phase.FINISHED,
                clock.phaseProperty()));
        startPauseButton.setOnAction(e -> {
            if (clock.runningProperty().get()) {
                clock.pause();
            } else {
                clock.start();
            }
        });

        Button nextPeriodButton = new Button("⏭ 2. Halbzeit starten");
        nextPeriodButton.getStyleClass().add("big-button");
        nextPeriodButton.disableProperty().bind(
                clock.phaseProperty().isNotEqualTo(GameClock.Phase.HALF_TIME));
        nextPeriodButton.setOnAction(e -> clock.startNextPeriod());

        Button setTimeButton = new Button("🕑 Zeit stellen…");
        setTimeButton.getStyleClass().add("big-button");
        setTimeButton.disableProperty().bind(
                clock.phaseProperty().isEqualTo(GameClock.Phase.FINISHED));
        setTimeButton.setOnAction(e -> correctClock(state));

        nextPeriodButton.setMinWidth(Region.USE_PREF_SIZE);
        setTimeButton.setMinWidth(Region.USE_PREF_SIZE);
        HBox clockButtons = new HBox(10, startPauseButton, nextPeriodButton, setTimeButton);
        clockButtons.setAlignment(Pos.CENTER);
        fitToCellWidth(clockButtons, HPos.LEFT);

        VBox timeoutBox = new VBox(4);
        timeoutBox.setAlignment(Pos.CENTER);
        fitToCellWidth(timeoutBox, HPos.LEFT);
        state.activeTimeoutProperty().addListener((obs, oldTimeout, timeout) ->
                rebuildTimeoutRow(state, timeoutBox));
        rebuildTimeoutRow(state, timeoutBox);

        VBox clockBox = new VBox(6, clockLine, phaseLabel, clockButtons, timeoutBox);
        // mittig in der Raster-Zeile (wie die Uhr auf der Anzeige), damit bei
        // großen Fenstern kein Loch zwischen Uhr-Gruppe und Tor-Zeile entsteht
        clockBox.setAlignment(Pos.CENTER);
        return clockBox;
    }

    /**
     * Spielzeit manuell stellen: Eingabe im Anzeigeformat der Uhr (vorwärts =
     * gespielte Zeit, rückwärts = Restzeit der Periode), begrenzt auf die
     * aktuelle Periode. Aus der Halbzeitpause heraus öffnet die Korrektur die
     * Periode wieder (weiter mit „Fortsetzen“).
     */
    private void correctClock(GameState state) {
        GameClock clock = state.clock();
        boolean countUp = state.config().direction() == ClockDirection.UP;
        TextInputDialog dialog = new TextInputDialog(TimeFormatter.formatClock(
                clock.elapsedMillisProperty().get(), clock.currentPeriodEndMillis(),
                state.config().direction()));
        dialog.setTitle("Spielzeit stellen");
        dialog.setHeaderText(countUp
                ? "Gespielte Zeit (MM:SS) — bei zwei Halbzeiten läuft die 2. ab "
                        + TimeFormatter.formatClock(state.config().periodMillis(),
                                0, ClockDirection.UP)
                : "Restzeit der aktuellen Periode (MM:SS)");
        dialog.setContentText("Zeit:");
        dialog.showAndWait().ifPresent(text -> {
            try {
                long shown = TimeFormatter.parseClockInput(text);
                clock.setElapsed(countUp ? shown : clock.currentPeriodEndMillis() - shown);
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.WARNING, ex.getMessage());
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });
    }

    /**
     * Äußere Spalte der oberen Zeile: links die Hupe, rechts der Spielabbruch —
     * darunter jeweils die laufenden Zeitstrafen des Teams wie auf der Anzeige.
     */
    private Node buildCornerColumn(GameState state, TeamSide side) {
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
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Das Spiel wirklich abbrechen? Die Uhr stoppt endgültig.",
                        ButtonType.OK, ButtonType.CANCEL);
                confirm.setHeaderText(null);
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    state.abortGame();
                }
            });
        }

        VBox penaltiesBox = new VBox(4);
        penaltiesBox.setAlignment(side == TeamSide.HOME ? Pos.TOP_LEFT : Pos.TOP_RIGHT);
        state.penalties(side).addListener((ListChangeListener<PenaltyTimer>) change ->
                rebuildPenaltyRows(state, side, penaltiesBox));
        rebuildPenaltyRows(state, side, penaltiesBox);

        cornerButton.setMinWidth(Region.USE_PREF_SIZE);
        VBox column = new VBox(10, cornerButton, penaltiesBox);
        column.setAlignment(side == TeamSide.HOME ? Pos.TOP_LEFT : Pos.TOP_RIGHT);
        fitToCellWidth(column, side == TeamSide.HOME ? HPos.LEFT : HPos.RIGHT);
        return column;
    }

    /** Toranzeige der Spielhälfte: großer Spielstand, darunter große Tor-Buttons. */
    private Node buildScoreCell(GameState state, TeamSide side) {
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

        VBox cell = new VBox(8, scoreLabel, goalButtons);
        cell.setAlignment(Pos.CENTER);
        return fitToCellWidth(cell, HPos.CENTER);
    }

    /** Untere Zeile je Spielhälfte: Teamname, Strafen-Eingabe und Timeout mit Counter. */
    private Node buildTeamControls(GameState state, TeamSide side) {
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
        timeoutButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> state.activeTimeoutProperty().get() != null
                        || (state.clock().phaseProperty().get() != GameClock.Phase.RUNNING
                            && state.clock().phaseProperty().get() != GameClock.Phase.PAUSED),
                state.activeTimeoutProperty(), state.clock().phaseProperty()));
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

        VBox pane = new VBox(8, nameLabel, penaltyEntry, timeoutRow);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setPadding(new Insets(10));
        return fitToCellWidth(pane, HPos.CENTER);
    }

    private void rebuildPenaltyRows(GameState state, TeamSide side, VBox penaltiesBox) {
        // gleiche Reihenfolge wie auf der Anzeige: älteste Strafe (kürzeste Restzeit) oben
        penaltiesBox.getChildren().setAll(state.penalties(side).stream()
                .sorted(Comparator.comparingLong(timer -> timer.remainingMillisProperty().get()))
                .map(timer -> penaltyRow(state, timer))
                .toList());
    }

    /** Eine Zeitstrafen-Zeile ist als Ganzes klickbar: ein Klick bricht die Strafe ab. */
    private Node penaltyRow(GameState state, PenaltyTimer timer) {
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

    /** Der Team-Timeout-Counter ist als Ganzes klickbar: ein Klick beendet das Timeout. */
    private void rebuildTimeoutRow(GameState state, VBox timeoutBox) {
        TeamTimeout timeout = state.activeTimeoutProperty().get();
        if (timeout == null) {
            timeoutBox.getChildren().clear();
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
        timeoutBox.getChildren().setAll(row);
    }

    private static String phaseText(GameState state) {
        GameClock clock = state.clock();
        boolean twoHalves = state.config().mode() == GameMode.TWO_HALVES;
        return switch (clock.phaseProperty().get()) {
            case NOT_STARTED -> "Bereit";
            case RUNNING -> twoHalves
                    ? clock.periodProperty().get() + ". Halbzeit läuft"
                    : "Spielzeit läuft";
            case PAUSED -> "Pausiert";
            case HALF_TIME -> "Halbzeitpause";
            case FINISHED -> "Spielende";
        };
    }
}
