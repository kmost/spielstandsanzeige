package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.GameAutosave;
import de.kmost.scoreboard.store.GameSnapshotStore;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.AppIcon;
import de.kmost.scoreboard.ui.Dialogs;
import de.kmost.scoreboard.ui.FxDialogs;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.TimeFormatter;
import de.kmost.scoreboard.ui.config.ConfigWindow;
import de.kmost.scoreboard.ui.display.DisplayWindow;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Kampfgericht-Konsole: Spiel-Setup, Spielsteuerung und Steuerung der Publikumsanzeige.
 * Das Fenster hält den Spielzustand zusammen (Anlegen, Fortsetzen, Sichern, Schließen);
 * die Oberfläche selbst steckt in {@link SetupPane}, {@link GamePane} und {@link StatusBar}.
 */
public class ControlWindow {

    private final Stage stage;
    private final Horn horn;
    private final Dialogs dialogs;
    private final Runnable exitAction;
    private final ObjectProperty<GameState> gameState = new SimpleObjectProperty<>();
    private final DisplayWindow displayWindow;
    private final BorderPane root = new BorderPane();
    private final ProblemReporter reporter = ProblemReporter.shared();
    private final StatusBar statusBar = new StatusBar(reporter);

    private final TeamRepository teamRepository;
    private final ThemeRepository themeRepository;
    private final GameSnapshotStore snapshotStore;
    private final SetupPane setupPane;
    private GameAutosave autosave;
    private ConfigWindow configWindow;

    public ControlWindow(Stage stage, Horn horn, TeamRepository teamRepository,
                         ThemeRepository themeRepository, GameSnapshotStore snapshotStore) {
        this(stage, horn, teamRepository, themeRepository, snapshotStore, 940, 700);
    }

    // Größe nur für die Offscreen-Vorschau in Tests wählbar
    ControlWindow(Stage stage, Horn horn, TeamRepository teamRepository,
                  ThemeRepository themeRepository, GameSnapshotStore snapshotStore,
                  double width, double height) {
        this(stage, horn, teamRepository, themeRepository, snapshotStore, width, height,
                new FxDialogs(), Platform::exit);
    }

    // Dialoge und Beenden-Aktion sind für Tests austauschbar (vorgegebene Antworten, kein Platform.exit)
    ControlWindow(Stage stage, Horn horn, TeamRepository teamRepository,
                  ThemeRepository themeRepository, GameSnapshotStore snapshotStore,
                  double width, double height, Dialogs dialogs, Runnable exitAction) {
        this.stage = stage;
        this.horn = horn;
        this.dialogs = dialogs;
        this.exitAction = exitAction;
        this.teamRepository = teamRepository;
        this.themeRepository = themeRepository;
        this.snapshotStore = snapshotStore;
        root.setBottom(statusBar.node());
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

        setupPane = new SetupPane(teamRepository, displayWindow, this::createGame, this::openConfig);
        root.setTop(setupPane.node());
        root.setCenter(buildPlaceholder());
        gameState.addListener((obs, oldState, state) ->
                root.setCenter(state == null ? buildPlaceholder() : new GamePane(state, horn, dialogs).node()));

        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(
                getClass().getResource("/de/kmost/scoreboard/control.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Kampfgericht – Spielstandsanzeige");
        AppIcon.apply(stage);
        stage.setOnCloseRequest(this::onCloseRequest);
    }

    /** Beim Schließen eines laufenden Spiels nachfragen; der Stand wird vor dem Beenden gesichert. */
    void onCloseRequest(WindowEvent e) {
        GameState state = gameState.get();
        if (state != null && isInProgress(state)) {
            if (!dialogs.confirm("Das Spiel ist noch nicht beendet. Wirklich beenden? Der Spielstand wird "
                    + "gesichert und beim nächsten Start zum Fortsetzen angeboten.")) {
                e.consume();
                return;
            }
            if (autosave != null) {
                autosave.saveNow();
            }
        }
        exitAction.run();
    }

    /** Ein gestartetes, noch nicht endgültig beendetes Spiel — nur das wird gesichert. */
    private static boolean isInProgress(GameState state) {
        return state.clock().phaseProperty().get() != GameClock.Phase.NOT_STARTED
                && !state.isOver();
    }

    /** Zeigt eine Problemmeldung in der Statuszeile; eine neue Meldung ersetzt die alte. */
    void showProblem(String message) {
        statusBar.showProblem(message);
    }

    public GameState gameState() {
        return gameState.get();
    }

    /** Takt der App: treibt Uhr, Strafen und Timeouts und sichert das Spiel. */
    public void tick() {
        GameState state = gameState.get();
        if (state != null) {
            state.tick();
            if (autosave != null) {
                autosave.tick();
            }
        }
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
        Platform.runLater(this::offerResume);
    }

    private void openConfig() {
        if (configWindow == null) {
            configWindow = new ConfigWindow(stage, displayWindow, themeRepository,
                    teamRepository, horn, this::applyTheme, setupPane::applyDefaultHomeTeam, dialogs);
        }
        configWindow.show();
    }

    /** Bietet ein nach Absturz oder Neustart gesichertes, nicht beendetes Spiel zum Fortsetzen an. */
    void offerResume() {
        GameState restored = snapshotStore.load().map(snapshot -> {
            try {
                return GameState.restore(snapshot, System::nanoTime);
            } catch (IllegalArgumentException e) {
                reporter.report("Gesichertes Spiel unbrauchbar", e);
                snapshotStore.quarantine();
                return null;
            }
        }).orElse(null);
        if (restored == null) {
            return;
        }
        GameClock clock = restored.clock();
        GameConfig config = restored.config();
        String clockText = TimeFormatter.formatClock(clock.elapsedMillisProperty().get(),
                clock.currentPeriodEndMillis(), config.direction());
        if (dialogs.askResume("Laufendes Spiel fortsetzen?", "Es gibt ein nicht beendetes Spiel.",
                config.homeName() + " " + restored.scoreProperty(TeamSide.HOME).get() + " : "
                        + restored.scoreProperty(TeamSide.GUEST).get() + " " + config.guestName()
                        + "\n" + PhaseTexts.phase(restored) + ", " + clockText
                        + "\n\nDie Uhr steht und wird mit „Fortsetzen“ weitergestartet.")) {
            startGame(restored);
        } else {
            snapshotStore.delete();
        }
    }

    void createGame() {
        GameState current = gameState.get();
        if (current != null && !current.isOver()
                && !dialogs.confirm("Das aktuelle Spiel wird verworfen. Neues Spiel anlegen?")) {
            return;
        }
        GameConfig config = setupPane.readConfig();
        snapshotStore.delete(); // die Sicherung des verworfenen Spiels
        startGame(new GameState(config));
    }

    /** Übernimmt ein neues oder wiederhergestelltes Spiel: Hupe anschließen, Sicherung starten. */
    private void startGame(GameState state) {
        state.clock().addOnPeriodEnd(horn::play);
        state.addOnTimeoutEnd(horn::play);
        state.addOnShootoutEnd(horn::play);
        if (autosave != null) {
            autosave.dispose();
        }
        autosave = new GameAutosave(state, snapshotStore);
        gameState.set(state);
        autosave.saveNow(); // wiederhergestelltes Spiel sofort wieder sichern
    }

    private Node buildPlaceholder() {
        Label label = new Label("Noch kein Spiel angelegt – oben konfigurieren und „Spiel anlegen“ drücken.");
        label.getStyleClass().add("game-placeholder");
        BorderPane pane = new BorderPane(label);
        pane.getStyleClass().add("game-pane");
        return pane;
    }
}
