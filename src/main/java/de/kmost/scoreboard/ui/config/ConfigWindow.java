package de.kmost.scoreboard.ui.config;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.AppIcon;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import de.kmost.scoreboard.ui.display.DisplayWindow;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Konfigurationsfenster der Publikumsanzeige, nach Inhalt gruppiert: Kopf- und
 * Fußzeile (je Tab Texte und Bilder), Anzeige-Elemente (je Element Farbe und
 * Größe, in Anzeige-Reihenfolge inklusive Header- und Footer-Text),
 * Hintergrund, Schrift und Themes. Jede Änderung wirkt sofort auf der Anzeige
 * und wird als aktiver Zustand gespeichert; die Auswahl kann zusätzlich als
 * benanntes Theme abgelegt und geladen werden.
 */
public class ConfigWindow {

    private final Stage stage = new Stage();
    private final DisplayWindow displayWindow;
    private final ThemeRepository themeRepository;
    private final TeamRepository teamRepository;
    private final Horn horn;
    private final Consumer<Theme> onThemeChange;
    private final Consumer<String> onDefaultHomeChange;
    private static final String SYSTEM_FONT = "System (Standard)";

    private final Map<ThemeColor, ColorPicker> pickers = new EnumMap<>(ThemeColor.class);
    private final ComboBox<String> fontBox = new ComboBox<>();
    private final Map<FontScale, Slider> scaleSliders = new EnumMap<>(FontScale.class);
    private final ComboBox<String> themeBox = new ComboBox<>();
    private final BannerEditor headerEditor;
    private final BannerEditor footerEditor;
    private boolean updatingPickers; // beim programmatischen Setzen nicht je Farbe neu speichern

    public ConfigWindow(Window owner, DisplayWindow displayWindow, ThemeRepository themeRepository,
                        TeamRepository teamRepository, Horn horn, Consumer<Theme> onThemeChange,
                        Consumer<String> onDefaultHomeChange) {
        this.displayWindow = displayWindow;
        this.themeRepository = themeRepository;
        this.teamRepository = teamRepository;
        this.horn = horn;
        this.onThemeChange = onThemeChange;
        this.onDefaultHomeChange = onDefaultHomeChange;
        this.headerEditor = new BannerEditor("header", themeRepository.currentHeader(),
                themeRepository, stage, this::applyBanners);
        this.footerEditor = new BannerEditor("footer", themeRepository.currentFooter(),
                themeRepository, stage, this::applyBanners);

        Theme current = themeRepository.currentTheme();
        VBox content = new VBox(10, buildBannerPane(), buildScoreboardPane(current),
                buildBackgroundPane(current), buildFontPane(current), buildHornPane(),
                buildGameSetupPane(), buildThemePane());
        content.setPadding(new Insets(10));

        stage.initOwner(owner);
        stage.setTitle("Anzeige-Konfiguration");
        AppIcon.apply(stage);
        // Der Inhalt scrollt vertikal, statt das Fenster über den Bildschirm
        // hinauswachsen zu lassen: die Fensterhöhe ist auf den sichtbaren
        // Bereich gedeckelt, die Breite folgt weiter dem Inhalt.
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Scene scene = new Scene(scroll);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMaxHeight(Screen.getPrimary().getVisualBounds().getHeight());
    }

    public void show() {
        if (stage.isShowing()) {
            stage.toFront();
        } else {
            stage.show();
        }
    }

    // nur für die Offscreen-Vorschau in Tests
    Scene scene() {
        return stage.getScene();
    }

    private Node buildBannerPane() {
        Tab headerTab = new Tab("Header (oben)", headerEditor.build());
        Tab footerTab = new Tab("Footer (unten)", footerEditor.build());
        TabPane tabs = new TabPane(headerTab, footerTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        return fixedPane("Kopf- und Fußzeile", tabs);
    }

    private static TitledPane fixedPane(String title, Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        return pane;
    }

    private void applyBanners() {
        displayWindow.headerBannerProperty().set(headerEditor.config());
        displayWindow.footerBannerProperty().set(footerEditor.config());
        persistCurrent();
    }


    /** Bereich „Schrift“: die Schriftart der Anzeige. */
    private Node buildFontPane(Theme current) {
        GridPane grid = themedGrid();
        grid.add(new Label("Schriftart:"), 0, 0);
        grid.add(buildFontBox(current), 1, 0);

        return fixedPane("Schrift", grid);
    }

    /**
     * Bereich „Anzeige-Elemente“: je Element Farbe und Größe in einer Zeile,
     * in Anzeige-Reihenfolge von oben (Header) nach unten (Footer).
     */
    private Node buildScoreboardPane(Theme current) {
        GridPane grid = themedGrid();
        grid.add(new Label("Farbe"), 1, 0);
        grid.add(new Label("Größe"), 2, 0);
        int row = 1;
        row = addScoreboardRow(grid, row, ThemeColor.HEADER, FontScale.HEADER, current);
        row = addScoreboardRow(grid, row, ThemeColor.CLOCK, FontScale.CLOCK, current);
        row = addScoreboardRow(grid, row, ThemeColor.PENALTY, FontScale.PENALTY, current);
        row = addScoreboardRow(grid, row, ThemeColor.TIMEOUT, FontScale.TIMEOUT, current);
        row = addScoreboardRow(grid, row, ThemeColor.SCORE, FontScale.SCORE, current);
        row = addScoreboardRow(grid, row, ThemeColor.STATUS, FontScale.STATUS, current);
        row = addScoreboardRow(grid, row, ThemeColor.TEAM_NAME, FontScale.TEAM_NAME, current);
        addScoreboardRow(grid, row, ThemeColor.FOOTER, FontScale.FOOTER, current);

        return fixedPane("Anzeige-Elemente", grid);
    }

    private int addScoreboardRow(GridPane grid, int row, ThemeColor color, FontScale scale,
            Theme current) {
        grid.add(new Label(color.label() + ":"), 0, row);
        grid.add(buildColorPicker(color, current), 1, row);
        if (scale != null) {
            grid.add(buildScaleSlider(scale, current.scale(scale)), 2, row);
        }
        return row + 1;
    }

    /** Bereich „Hintergrund“: der Verlauf der Anzeige von oben nach unten. */
    private Node buildBackgroundPane(Theme current) {
        GridPane grid = themedGrid();
        grid.addRow(0,
                new Label("Oben:"), buildColorPicker(ThemeColor.BACKGROUND_TOP, current),
                new Label("Mitte:"), buildColorPicker(ThemeColor.BACKGROUND_MIDDLE, current),
                new Label("Unten:"), buildColorPicker(ThemeColor.BACKGROUND_BOTTOM, current));

        return fixedPane("Hintergrund", grid);
    }

    private ColorPicker buildColorPicker(ThemeColor color, Theme current) {
        ColorPicker picker = new ColorPicker(current.color(color));
        picker.setPrefWidth(130);
        picker.valueProperty().addListener((obs, oldValue, value) -> {
            if (!updatingPickers) {
                applyAndPersist();
            }
        });
        pickers.put(color, picker);
        return picker;
    }

    /**
     * Bereich „Hupe“: eingebauter Ton (zur Laufzeit generiert) oder eine externe
     * Audiodatei (WAV/AIFF). Die Wahl wirkt sofort und wird gespeichert; „Test“
     * spielt den aktuellen Ton ab.
     */
    private Node buildHornPane() {
        ComboBox<Horn.Tone> toneBox = new ComboBox<>();
        toneBox.getItems().setAll(Horn.Tone.values());
        toneBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Horn.Tone tone) {
                return tone == null ? "" : tone.label();
            }

            @Override
            public Horn.Tone fromString(String label) {
                return null; // nicht editierbar
            }
        });
        toneBox.setValue(Horn.toneOrDefault(themeRepository.hornTone()));

        Label fileLabel = new Label();
        File storedFile = themeRepository.hornFile();
        if (storedFile != null) {
            fileLabel.setText(storedFile.getName());
        }

        toneBox.valueProperty().addListener((obs, oldTone, tone) -> {
            if (tone != null) {
                horn.useTone(tone);
                themeRepository.saveHorn(tone.name(), null);
                fileLabel.setText("");
                horn.play();
            }
        });

        Button fileButton = new Button("📁 Datei…");
        fileButton.setOnAction(e -> chooseHornFile(toneBox, fileLabel));
        Button testButton = new Button("🔊 Test");
        testButton.setOnAction(e -> horn.play());

        GridPane grid = themedGrid();
        grid.addRow(0, new Label("Ton:"), toneBox, fileButton, fileLabel, testButton);

        return fixedPane("Hupe", grid);
    }

    private void chooseHornFile(ComboBox<Horn.Tone> toneBox, Label fileLabel) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Hupen-Sound wählen");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Audio (WAV, AIFF, AU)",
                        "*.wav", "*.aif", "*.aiff", "*.au"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        if (horn.useFile(file)) {
            themeRepository.saveHorn(toneBox.getValue().name(), file);
            fileLabel.setText(file.getName());
            horn.play();
        } else {
            warn("Audiodatei konnte nicht geladen werden (unterstützt: WAV, AIFF, AU).");
        }
    }

    /**
     * Bereich „Spiel-Setup“: das Standard-Heimteam, das beim Anlegen eines Spiels
     * im Heim-Feld vorbelegt ist. Gespeichert wird bei Enter oder Fokusverlust;
     * leer lassen entfernt die Vorbelegung.
     */
    private Node buildGameSetupPane() {
        TextField field = new TextField(teamRepository.defaultHomeTeam());
        field.setPromptText("kein Team vorbelegt");
        field.setPrefWidth(280);
        Runnable save = () -> {
            String name = field.getText() == null ? "" : field.getText().strip();
            teamRepository.saveDefaultHomeTeam(name);
            onDefaultHomeChange.accept(name);
        };
        field.setOnAction(e -> save.run());
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                save.run();
            }
        });

        GridPane grid = themedGrid();
        grid.addRow(0, new Label("Standard-Heimteam:"), field);

        return fixedPane("Spiel-Setup", grid);
    }

    private static GridPane themedGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        return grid;
    }

    /**
     * Prozent-Slider für eine Schriftgröße; 100 % = Standard. Während des Ziehens
     * wirkt der Wert live auf die Anzeige, gespeichert wird erst beim Loslassen.
     */
    private Node buildScaleSlider(FontScale scale, double value) {
        Slider slider = new Slider(50, 250, value * 100);
        slider.setBlockIncrement(10);
        slider.setPrefWidth(110);
        Label readout = new Label();
        readout.setMinWidth(45);
        readout.textProperty().bind(slider.valueProperty().asString(Locale.ROOT, "%.0f %%"));
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingPickers) {
                return;
            }
            applyPicked();
            if (!slider.isValueChanging()) {
                persistCurrent();
            }
        });
        slider.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing && !updatingPickers) {
                persistCurrent();
            }
        });
        scaleSliders.put(scale, slider);
        HBox box = new HBox(6, slider, readout);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Schriftart der Anzeige: Systemschrift oder eine installierte Schriftfamilie.
     * Gilt für alle Elemente der Anzeige, auch für Uhr, Tore und Straf-Zeiten.
     */
    private Node buildFontBox(Theme current) {
        fontBox.getItems().add(SYSTEM_FONT);
        fontBox.getItems().addAll(javafx.scene.text.Font.getFamilies());
        fontBox.setValue(current.fontFamily().isEmpty() ? SYSTEM_FONT : current.fontFamily());
        fontBox.setPrefWidth(280);
        // jede Familie in ihrer eigenen Schrift anzeigen, als kleine Vorschau
        fontBox.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String family, boolean empty) {
                super.updateItem(family, empty);
                setText(empty ? null : family);
                setFont(empty || SYSTEM_FONT.equals(family)
                        ? javafx.scene.text.Font.getDefault()
                        : javafx.scene.text.Font.font(family, 13));
            }
        });
        fontBox.valueProperty().addListener((obs, oldValue, value) -> {
            if (!updatingPickers) {
                applyAndPersist();
            }
        });
        return fontBox;
    }

    private Node buildThemePane() {
        themeBox.setEditable(true);
        themeBox.setPromptText("Theme-Name");
        themeBox.setPrefWidth(200);
        themeBox.getItems().setAll(themeRepository.themeNames());

        Button loadButton = new Button("📂 Laden");
        loadButton.setOnAction(e -> loadTheme());
        Button saveButton = new Button("💾 Speichern");
        saveButton.setOnAction(e -> saveTheme());
        Button deleteButton = new Button("🗑 Löschen");
        deleteButton.setOnAction(e -> deleteTheme());
        Button defaultsButton = new Button("↺ Standard");
        defaultsButton.setOnAction(e -> setPickers(Theme.defaults()));

        HBox row = new HBox(8, themeBox, loadButton, saveButton, deleteButton, defaultsButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        return fixedPane("Themes", row);
    }

    // --- Aktionen ---

    private void loadTheme() {
        String name = themeName();
        if (name.isEmpty()) {
            return;
        }
        Theme theme = themeRepository.loadTheme(name);
        if (theme == null) {
            warn("Theme „" + name + "“ wurde nicht gefunden.");
            return;
        }
        setPickers(theme);
    }

    private void saveTheme() {
        String name = themeName();
        if (name.isEmpty()) {
            warn("Bitte zuerst einen Theme-Namen eingeben.");
            return;
        }
        themeRepository.saveTheme(name, pickedTheme());
        refreshThemeNames(name);
    }

    private void deleteTheme() {
        String name = themeName();
        if (name.isEmpty()) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Theme „" + name + "“ wirklich löschen?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            themeRepository.deleteTheme(name);
            refreshThemeNames(null);
        }
    }

    // --- Hilfen ---

    private String themeName() {
        String name = themeBox.getEditor().getText();
        return name == null ? "" : name.strip();
    }

    private void refreshThemeNames(String select) {
        themeBox.getItems().setAll(themeRepository.themeNames());
        if (select != null) {
            themeBox.getSelectionModel().select(select);
        } else {
            themeBox.getEditor().clear();
        }
    }

    private Theme pickedTheme() {
        Map<ThemeColor, Color> colors = new EnumMap<>(ThemeColor.class);
        pickers.forEach((color, picker) -> colors.put(color, picker.getValue()));
        Map<FontScale, Double> scales = new EnumMap<>(FontScale.class);
        scaleSliders.forEach((scale, slider) -> scales.put(scale, slider.getValue() / 100.0));
        String font = fontBox.getValue();
        return new Theme(colors, font == null || SYSTEM_FONT.equals(font) ? "" : font, scales);
    }

    private void setPickers(Theme theme) {
        updatingPickers = true;
        pickers.forEach((color, picker) -> picker.setValue(theme.color(color)));
        fontBox.setValue(theme.fontFamily().isEmpty() ? SYSTEM_FONT : theme.fontFamily());
        scaleSliders.forEach((scale, slider) -> slider.setValue(theme.scale(scale) * 100));
        updatingPickers = false;
        applyAndPersist();
    }

    /** Auswahl auf Anzeige und Kampfgericht anwenden, ohne zu speichern. */
    private void applyPicked() {
        Theme theme = pickedTheme();
        displayWindow.applyTheme(theme);
        onThemeChange.accept(theme);
    }

    private void applyAndPersist() {
        applyPicked();
        persistCurrent();
    }

    private void persistCurrent() {
        themeRepository.saveCurrent(pickedTheme(), headerEditor.config(), footerEditor.config());
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
