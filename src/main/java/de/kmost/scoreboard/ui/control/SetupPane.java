package de.kmost.scoreboard.ui.control;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.OvertimeFormat;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.ui.display.DisplayWindow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

/** Spiel-Setup oben in der Konsole: Teams, Spielmodus, Verlängerung und Steuerung der Anzeige. */
final class SetupPane {

    private final TeamRepository teamRepository;
    private final DisplayWindow displayWindow;
    private final ObservableList<String> knownTeams = FXCollections.observableArrayList();
    private final Map<TeamSide, TeamNamePicker> teamPickers = new EnumMap<>(TeamSide.class);
    private final ComboBox<GameMode> modeBox = new ComboBox<>();
    private final Spinner<Integer> minutesSpinner =
            new Spinner<>(1, 120, (int) SportProfile.HANDBALL.defaultPeriodDuration().toMinutes());
    private final ComboBox<ClockDirection> directionBox = new ComboBox<>();
    private final ComboBox<OvertimeFormat> overtimeFormatBox = new ComboBox<>();
    private final Spinner<Integer> overtimeMinutesSpinner = new Spinner<>(1, 60,
            (int) SportProfile.HANDBALL.defaultOvertimePeriodDuration().toMinutes());
    private final ComboBox<Screen> screenBox = new ComboBox<>();
    /** Konfiguriertes Standard-Heimteam; steht beim Setup im Heim-Feld vorbelegt. */
    private String defaultHomeTeam;
    private final TitledPane pane;

    SetupPane(TeamRepository teamRepository, DisplayWindow displayWindow,
              Runnable onCreateGame, Runnable onOpenConfig) {
        this.teamRepository = teamRepository;
        this.displayWindow = displayWindow;
        this.knownTeams.setAll(teamRepository.teamNames());
        this.defaultHomeTeam = teamRepository.defaultHomeTeam();
        this.pane = build(onCreateGame, onOpenConfig);
    }

    Node node() {
        return pane;
    }

    private TitledPane build(Runnable onCreateGame, Runnable onOpenConfig) {
        modeBox.getItems().setAll(GameMode.values());
        modeBox.setValue(GameMode.TWO_HALVES);
        directionBox.getItems().setAll(ClockDirection.values());
        directionBox.setValue(ClockDirection.UP);
        overtimeFormatBox.getItems().setAll(OvertimeFormat.values());
        overtimeFormatBox.setValue(OvertimeFormat.TWO_HALVES);
        for (Spinner<Integer> spinner : List.of(minutesSpinner, overtimeMinutesSpinner)) {
            spinner.setEditable(true);
            spinner.focusedProperty().addListener((obs, was, is) -> {
                if (!is) {
                    spinner.increment(0); // eingetippten Wert übernehmen
                }
            });
        }

        // Heim und Gast als gleich breite Karten nebeneinander — wie die Spielhälften
        HBox teamCards = new HBox(12, buildTeamCard(TeamSide.HOME), buildTeamCard(TeamSide.GUEST));

        Button createButton = new Button("✚ Spiel anlegen");
        createButton.getStyleClass().add("create-button");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> onCreateGame.run());

        minutesSpinner.setPrefWidth(80);
        HBox paramsRow = new HBox(10,
                new Label("Modus:"), modeBox,
                new Label("Periodendauer (min):"), minutesSpinner,
                new Label("Uhr:"), directionBox);
        paramsRow.setAlignment(Pos.CENTER_LEFT);

        // Verlängerungs-Voreinstellung; genutzt wird sie nur, wenn das Kampfgericht
        // nach Spielende tatsächlich „Verlängerung starten“ drückt
        overtimeMinutesSpinner.setPrefWidth(70);
        Region overtimeSpacer = new Region();
        HBox.setHgrow(overtimeSpacer, Priority.ALWAYS);
        HBox overtimeRow = new HBox(10,
                new Label("Verlängerung (falls nötig):"), overtimeFormatBox,
                new Label("à"), overtimeMinutesSpinner, new Label("min"),
                overtimeSpacer, createButton);
        overtimeRow.setAlignment(Pos.CENTER_LEFT);

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
        configButton.setOnAction(e -> onOpenConfig.run());

        HBox displayRow = new HBox(10, new Label("Publikumsanzeige:"), screenBox, openDisplayButton,
                fullScreenButton, configButton);
        displayRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, teamCards, paramsRow, overtimeRow, new Separator(), displayRow);
        content.setPadding(new Insets(10));

        TitledPane titled = new TitledPane("Spiel-Einstellungen", content);
        titled.setCollapsible(false);
        return titled;
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
    void applyDefaultHomeTeam(String name) {
        TeamNamePicker picker = teamPickers.get(TeamSide.HOME);
        String current = picker.text() == null ? "" : picker.text().strip();
        if (current.isEmpty() || current.equals(defaultHomeTeam)) {
            picker.setText(name);
        }
        defaultHomeTeam = name;
    }

    /**
     * Liest die Eingaben als Spielkonfiguration und merkt die Teamnamen in der Team-Datenbank
     * (Standardnamen „Heim“/„Gast“ nicht).
     */
    GameConfig readConfig() {
        String homeName = teamName(TeamSide.HOME);
        String guestName = teamName(TeamSide.GUEST);
        rememberTeam(homeName, TeamSide.HOME);
        rememberTeam(guestName, TeamSide.GUEST);
        knownTeams.setAll(teamRepository.teamNames());
        return new GameConfig(
                homeName,
                guestName,
                modeBox.getValue(),
                Duration.ofMinutes(minutesSpinner.getValue()),
                directionBox.getValue(),
                overtimeFormatBox.getValue(),
                Duration.ofMinutes(overtimeMinutesSpinner.getValue()),
                SportProfile.HANDBALL);
    }

    private String teamName(TeamSide side) {
        TeamNamePicker picker = teamPickers.get(side);
        return picker == null ? side.label() : orDefault(picker.text(), side.label());
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
}
