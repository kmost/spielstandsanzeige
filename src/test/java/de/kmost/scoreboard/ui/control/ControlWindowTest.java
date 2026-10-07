package de.kmost.scoreboard.ui.control;

import static de.kmost.scoreboard.ui.FxTestSupport.all;
import static de.kmost.scoreboard.ui.FxTestSupport.button;
import static de.kmost.scoreboard.ui.FxTestSupport.buttonsStartingWith;
import static de.kmost.scoreboard.ui.FxTestSupport.fx;
import static de.kmost.scoreboard.ui.FxTestSupport.layout;
import static de.kmost.scoreboard.ui.FxTestSupport.sceneBounds;
import static de.kmost.scoreboard.ui.FxTestSupport.shown;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.GameSnapshotStore;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.SettingsStores;
import de.kmost.scoreboard.ui.FakeDialogs;
import de.kmost.scoreboard.ui.FxTestSupport;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Zustandswechsel und Knopf-Logik der Kampfgericht-Konsole, ohne sichtbares Fenster. */
@Tag("ui")
class ControlWindowTest {

    @TempDir
    Path dir;

    private FakeDialogs dialogs;
    private AtomicInteger exits;
    private GameSnapshotStore snapshots;
    private ControlWindow control;

    @BeforeAll
    static void toolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void setUp() {
        dialogs = new FakeDialogs();
        exits = new AtomicInteger();
        snapshots = new GameSnapshotStore(dir);
        control = newWindow(940, 700);
    }

    private ControlWindow newWindow(double width, double height) {
        return fx(() -> new ControlWindow(new Stage(), quietHorn(), new TeamRepository(dir),
                SettingsStores.in(dir), snapshots, width, height, dialogs, exits::incrementAndGet));
    }

    // --- Hilfen ---

    /** Hupe, die ihre Probleme (z. B. kein Audiogerät auf dem CI-Rechner) nicht in die Statuszeile meldet. */
    private Horn quietHorn() {
        return new Horn(new ProblemReporter(dir.resolve("horn.log")));
    }

    /** Wurzel nach vollständigem CSS-/Layout-Durchlauf (Skins, z. B. von TitledPane, entstehen erst dabei). */
    private javafx.scene.Parent root() {
        layout(control.scene());
        return control.scene().getRoot();
    }

    private void click(String prefix) {
        fx(() -> {
            button(root(), prefix).fire();
            layout(control.scene());
        });
    }

    private Button find(String prefix) {
        return fx(() -> button(root(), prefix));
    }

    private GameState createGame() {
        click("✚ Spiel anlegen");
        return control.gameState();
    }

    private void onFx(Runnable action) {
        fx(() -> {
            action.run();
            layout(control.scene());
        });
    }

    /** Spielt das reguläre Spielende (zwei Halbzeiten) durch; Stand bleibt wie er ist. */
    private void finishRegulation(GameState state) {
        onFx(() -> {
            GameClock clock = state.clock();
            clock.start();
            int periods = state.config().mode().periodCount();
            for (int period = 1; period <= periods; period++) {
                clock.setElapsed((long) period * state.config().periodMillis());
                clock.tick();
                if (clock.phaseProperty().get() == GameClock.Phase.HALF_TIME) {
                    clock.startNextPeriod();
                }
            }
        });
    }

    // --- Setup → Spiel ---

    @Test
    void showsPlaceholderUntilGameIsCreated() {
        fx(() -> layout(control.scene()));
        assertNull(control.gameState());
        assertTrue(fx(() -> all(root(), Label.class).stream()
                .anyMatch(l -> l.getText().startsWith("Noch kein Spiel angelegt"))));
        assertTrue(fx(() -> buttonsStartingWith(root(), "▶ Start").isEmpty()));
    }

    @Test
    void creatingGameBuildsControlsAndSavesNothingBeforeStart() {
        GameState state = createGame();
        assertNotNull(state);
        assertEquals("Heim", state.config().homeName());
        assertEquals(30 * 60_000L, state.config().periodMillis());
        assertEquals("▶ Start", find("▶ Start").getText());
        assertTrue(fx(() -> all(root(), Label.class).stream()
                .anyMatch(l -> "Heim (Heim)".equals(l.getText()))));
        assertTrue(snapshots.load().isEmpty(), "ein nicht gestartetes Spiel wird nicht gesichert");
    }

    @Test
    void creatingGameAgainAsksBeforeDiscardingRunningGame() {
        GameState first = createGame();
        onFx(() -> first.clock().start());

        dialogs.confirmAnswer = false;
        click("✚ Spiel anlegen");
        assertEquals(1, dialogs.confirms.size());
        assertTrue(dialogs.confirms.get(0).contains("verworfen"));
        assertSame(first, control.gameState());

        dialogs.confirmAnswer = true;
        click("✚ Spiel anlegen");
        assertNotSame(first, control.gameState());
    }

    @Test
    void creatingGameAfterFinishedGameDoesNotAsk() {
        GameState first = createGame();
        onFx(first::abortGame);
        click("✚ Spiel anlegen");
        assertTrue(dialogs.confirms.isEmpty());
        assertNotSame(first, control.gameState());
    }

    // --- Uhr-Knöpfe ---

    @Test
    void startPauseButtonFollowsClockPhase() {
        GameState state = createGame();
        assertFalse(find("▶ Start").isDisabled());
        click("▶ Start");
        assertEquals(GameClock.Phase.RUNNING, state.clock().phaseProperty().get());
        assertEquals("⏸ Pause", find("⏸ Pause").getText());
        click("⏸ Pause");
        assertEquals(GameClock.Phase.PAUSED, state.clock().phaseProperty().get());
        assertEquals("▶ Fortsetzen", find("▶ Fortsetzen").getText());
    }

    @Test
    void nextPeriodButtonIsOnlyEnabledInHalfTimeBreak() {
        GameState state = createGame();
        assertTrue(find("⏭ 2. Halbzeit starten").isDisabled());
        onFx(() -> {
            state.clock().start();
            state.clock().setElapsed(state.config().periodMillis());
            state.clock().tick();
        });
        assertEquals(GameClock.Phase.HALF_TIME, state.clock().phaseProperty().get());
        assertFalse(find("⏭ 2. Halbzeit starten").isDisabled());
        assertTrue(find("▶ Start").isDisabled(), "in der Pause startet nur der nächste Abschnitt");
        click("⏭ 2. Halbzeit starten");
        assertEquals(GameClock.Phase.RUNNING, state.clock().phaseProperty().get());
        assertEquals(2, state.clock().periodProperty().get());
    }

    @Test
    void drawAfterRegulationOffersOvertimeShootoutAndEnd() {
        GameState state = createGame();
        finishRegulation(state);

        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertFalse(find("▶ 1. Verlängerung starten").isDisabled());
        assertFalse(find("🥅 7-m-Werfen…").isDisabled());
        assertTrue(fx(() -> shown(button(root(), "🏁 Beenden"))));
        assertFalse(fx(() -> shown(button(root(), "🕑 Zeit stellen…"))));

        // mit Torvorsprung ist das Spiel entschieden: „Beenden“ entfällt, „Zeit stellen…“ kehrt (gesperrt) zurück;
        // Verlängerung und 7-m-Werfen bleiben der Entscheidung des Kampfgerichts überlassen
        onFx(() -> state.addGoal(TeamSide.HOME));
        assertFalse(fx(() -> shown(button(root(), "🏁 Beenden"))));
        assertTrue(fx(() -> shown(button(root(), "🕑 Zeit stellen…"))));
        assertTrue(find("🕑 Zeit stellen…").isDisabled());
        assertTrue(state.isOver());
    }

    @Test
    void endGameAsksAndOnlyEndsOnConfirmation() {
        GameState state = createGame();
        finishRegulation(state);

        dialogs.confirmAnswer = false;
        click("🏁 Beenden");
        assertTrue(dialogs.confirms.get(0).contains("Unentschieden"));
        assertFalse(state.isOver());

        dialogs.confirmAnswer = true;
        click("🏁 Beenden");
        assertTrue(state.isOver());
        assertFalse(fx(() -> shown(button(root(), "🏁 Beenden"))));
        assertTrue(find("▶ 1. Verlängerung starten").isDisabled());
        assertTrue(find("🥅 7-m-Werfen…").isDisabled());
    }

    @Test
    void abortGameAsksAndStopsClock() {
        GameState state = createGame();
        click("▶ Start");

        dialogs.confirmAnswer = false;
        click("⏹ Spiel abbrechen");
        assertEquals(GameClock.Phase.RUNNING, state.clock().phaseProperty().get());

        dialogs.confirmAnswer = true;
        click("⏹ Spiel abbrechen");
        assertEquals(GameClock.Phase.FINISHED, state.clock().phaseProperty().get());
        assertTrue(find("⏹ Spiel abbrechen").isDisabled());
    }

    @Test
    void correctClockAppliesInputAndWarnsOnInvalidText() {
        GameState state = createGame();
        click("▶ Start");
        click("⏸ Pause");

        dialogs.textAnswer = Optional.of("10:00");
        click("🕑 Zeit stellen…");
        assertEquals(600_000, state.clock().elapsedMillisProperty().get());
        assertEquals(1, dialogs.textPrompts.size());

        dialogs.textAnswer = Optional.of("kein Zeitwert");
        click("🕑 Zeit stellen…");
        assertEquals(1, dialogs.warnings.size());
        assertEquals(600_000, state.clock().elapsedMillisProperty().get());

        dialogs.textAnswer = Optional.empty();
        click("🕑 Zeit stellen…");
        assertEquals(1, dialogs.warnings.size());
    }

    // --- 7-m-Werfen ---

    @Test
    void shootoutStartsWithChosenTeamAndAddsItsOwnRow() {
        GameState state = createGame();
        finishRegulation(state);

        dialogs.choiceAnswer = Optional.empty();
        click("🥅 7-m-Werfen…");
        assertNull(state.shootoutProperty().get(), "Abbrechen startet nichts");
        assertEquals(java.util.List.of("Heim beginnt", "Gast beginnt"), dialogs.choices.get(0));

        Button startButton = find("▶ Start");
        dialogs.choiceAnswer = Optional.of(1);
        click("🥅 7-m-Werfen…");
        assertNotNull(state.shootoutProperty().get());
        assertSame(startButton, find("▶ Start"), "die übrige Spielsteuerung wird nicht neu aufgebaut");
        assertEquals(TeamSide.GUEST, state.shootoutProperty().get().nextThrowerProperty().get());
        assertTrue(fx(() -> all(root(), Label.class).stream()
                .anyMatch(l -> l.getText() != null && l.getText().startsWith("7-m-Werfen — Gast wirft"))));

        assertTrue(find("↩ Wurf zurücknehmen").isDisabled());
        click("⚽ Tor");
        assertEquals(1, state.scoreProperty(TeamSide.GUEST).get());
        assertFalse(find("↩ Wurf zurücknehmen").isDisabled());
        click("❌ Kein Tor");
        click("↩ Wurf zurücknehmen");
        assertEquals(1, state.shootoutProperty().get().attempts().size());
        click("↩ Wurf zurücknehmen");
        assertEquals(0, state.scoreProperty(TeamSide.GUEST).get());
        assertTrue(find("↩ Wurf zurücknehmen").isDisabled());
    }

    @Test
    void shootoutWinnerIsShownAndDisablesThrowButtons() {
        GameState state = createGame();
        finishRegulation(state);
        dialogs.choiceAnswer = Optional.of(0);
        click("🥅 7-m-Werfen…");
        // Heim trifft immer, Gast nie → der Sieger steht nach wenigen Würfen fest
        for (int i = 0; i < 20 && state.shootoutProperty().get().winnerProperty().get() == null; i++) {
            click(i % 2 == 0 ? "⚽ Tor" : "❌ Kein Tor");
        }
        assertEquals(TeamSide.HOME, state.shootoutProperty().get().winnerProperty().get());
        assertTrue(find("⚽ Tor").isDisabled());
        assertTrue(find("❌ Kein Tor").isDisabled());
        assertTrue(fx(() -> all(root(), Label.class).stream()
                .anyMatch(l -> l.getText() != null && l.getText().startsWith("🏆 Sieger: Heim"))));
        // die Phasenzeile unter der Uhr folgt dem entschiedenen 7-m-Werfen
        assertTrue(fx(() -> all(root(), Label.class).stream()
                .anyMatch(l -> l.getStyleClass().contains("game-phase") && "Spielende".equals(l.getText()))));
    }

    @Test
    void gameWithShootoutAlreadyRunningShowsItsRowImmediately() {
        GameState state = createGame();
        finishRegulation(state);
        onFx(() -> state.startShootout(TeamSide.HOME));
        // wie beim Fortsetzen eines gesicherten Spiels: Fenster bekommt ein Spiel mit laufendem 7-m-Werfen
        GameState restored = GameState.restore(state.snapshot(), System::nanoTime);
        onFx(() -> control.gameStateProperty().set(restored));
        assertEquals(1, fx(() -> buttonsStartingWith(root(), "⚽ Tor").size()));
        assertTrue(fx(() -> all(root(), Label.class).stream()
                .anyMatch(l -> l.getText() != null && l.getText().startsWith("7-m-Werfen — Heim wirft"))));
    }

    // --- Strafen und Timeouts ---

    @Test
    void penaltyRowsExtendAndCancel() {
        GameState state = createGame();
        onFx(() -> state.clock().start());
        fx(() -> {
            all(root(), TextField.class).stream()
                    .filter(f -> "Nr.".equals(f.getPromptText())).findFirst().orElseThrow().setText("7");
            buttonsStartingWith(root(), "⏱ 2 Minuten").get(0).fire();
            layout(control.scene());
        });
        assertEquals(1, state.penalties(TeamSide.HOME).size());
        assertEquals("7", state.penalties(TeamSide.HOME).get(0).playerNumber());
        assertEquals("⏱ Nr. 7  2:00  ✕", find("⏱ Nr. 7").getText());

        Button extend = find("→ 4 Min");
        assertFalse(extend.isDisabled());
        fx(() -> extend.fire());
        assertTrue(state.penalties(TeamSide.HOME).get(0).isExtended());
        assertEquals("⏱ Nr. 7  4:00  ✕", fx(() -> button(root(), "⏱ Nr. 7").getText()));
        assertTrue(find("→ 4 Min").isDisabled());

        click("⏱ Nr. 7");
        assertTrue(state.penalties(TeamSide.HOME).isEmpty());
        assertTrue(fx(() -> buttonsStartingWith(root(), "→ 4 Min").isEmpty()));
    }

    @Test
    void penaltiesOfBothSidesAreListedSortedByRemainingTime() {
        GameState state = createGame();
        onFx(() -> {
            state.addPenalty(TeamSide.HOME, "5");
            state.addPenalty(TeamSide.GUEST, "9");
        });
        assertEquals(1, fx(() -> buttonsStartingWith(root(), "⏱ Nr. 5").size()));
        assertEquals(1, fx(() -> buttonsStartingWith(root(), "⏱ Nr. 9").size()));
    }

    @Test
    void extendButtonOfGuestPenaltyStaysInsideNarrowWindow() {
        control = newWindow(760, 700);
        GameState state = createGame();
        onFx(() -> {
            state.clock().start();
            state.addPenalty(TeamSide.GUEST, "13");
            state.addPenalty(TeamSide.HOME, "7");
        });
        fx(() -> {
            double width = control.scene().getWidth();
            for (Button b : buttonsStartingWith(root(), "→ 4 Min")) {
                Bounds bounds = sceneBounds(b);
                assertTrue(bounds.getMinX() >= -1 && bounds.getMaxX() <= width + 1,
                        "„→ 4 Min“ ragt aus dem Fenster: " + bounds + " bei Breite " + width);
            }
        });
    }

    @Test
    void teamTimeoutStartsPausesClockAndEndsOnClick() {
        GameState state = createGame();
        assertTrue(fx(() -> buttonsStartingWith(root(), "🟩 Team-Timeout").get(0).isDisabled()),
                "vor dem Spielstart kein Timeout");
        click("▶ Start");
        fx(() -> {
            buttonsStartingWith(root(), "🟩 Team-Timeout").get(0).fire();
            layout(control.scene());
        });
        assertEquals(1, state.timeoutsUsedProperty(TeamSide.HOME).get());
        assertEquals(GameClock.Phase.PAUSED, state.clock().phaseProperty().get());
        assertNotNull(state.activeTimeoutProperty().get());
        assertEquals(1, fx(() -> buttonsStartingWith(root(), "🟩 Team-Timeout Heim:").size()));
        // solange eines läuft, ist kein zweites startbar
        assertTrue(fx(() -> buttonsStartingWith(root(), "🟩 Team-Timeout ").stream()
                .filter(b -> !b.getText().contains(":")).allMatch(Button::isDisabled)));

        click("🟩 Team-Timeout Heim:");
        assertNull(state.activeTimeoutProperty().get());
    }

    // --- Schließen, Fortsetzen, Statuszeile ---

    private WindowEvent closeRequest() {
        return new WindowEvent(null, WindowEvent.WINDOW_CLOSE_REQUEST);
    }

    @Test
    void closingWithoutGameExitsImmediately() {
        WindowEvent event = closeRequest();
        fx(() -> control.onCloseRequest(event));
        assertEquals(1, exits.get());
        assertFalse(event.isConsumed());
        assertTrue(dialogs.confirms.isEmpty());
    }

    @Test
    void closingRunningGameAsksAndSavesBeforeExit() {
        GameState state = createGame();
        click("▶ Start");
        onFx(() -> state.addGoal(TeamSide.HOME));

        dialogs.confirmAnswer = false;
        WindowEvent refused = closeRequest();
        fx(() -> control.onCloseRequest(refused));
        assertTrue(refused.isConsumed());
        assertEquals(0, exits.get());

        dialogs.confirmAnswer = true;
        fx(() -> control.onCloseRequest(closeRequest()));
        assertEquals(1, exits.get());
        assertEquals(1, snapshots.load().orElseThrow().homeScore());
    }

    @Test
    void closingFinishedGameDoesNotAsk() {
        GameState state = createGame();
        onFx(state::abortGame);
        fx(() -> control.onCloseRequest(closeRequest()));
        assertEquals(1, exits.get());
        assertTrue(dialogs.confirms.isEmpty());
    }

    @Test
    void savedGameIsOfferedAndResumedOrDiscarded() {
        GameState state = createGame();
        click("▶ Start");
        onFx(() -> {
            state.addGoal(TeamSide.GUEST);
            state.addGoal(TeamSide.GUEST);
        });
        fx(() -> snapshots.save(state.snapshot()));
        control = newWindow(940, 700); // „Neustart“ mit demselben Datenverzeichnis

        dialogs.resumeAnswer = false;
        fx(control::offerResume);
        assertEquals(1, dialogs.resumes.size());
        assertTrue(dialogs.resumes.get(0).contains("0 : 2"));
        assertNull(control.gameState());
        assertTrue(snapshots.load().isEmpty(), "Verwerfen löscht die Sicherung");

        fx(() -> snapshots.save(state.snapshot()));
        dialogs.resumeAnswer = true;
        fx(control::offerResume);
        assertNotNull(control.gameState());
        assertEquals(2, control.gameState().scoreProperty(TeamSide.GUEST).get());
        assertEquals(GameClock.Phase.PAUSED, control.gameState().clock().phaseProperty().get());
    }

    @Test
    void noOfferWithoutSavedGame() {
        fx(control::offerResume);
        assertTrue(dialogs.resumes.isEmpty());
    }

    @Test
    void statusLineShowsProblemsOnlyWhenReported() {
        fx(() -> layout(control.scene()));
        Label status = fx(() -> all(root(), Label.class).stream()
                .filter(l -> l.getStyleClass().contains("status-line")).findFirst().orElseThrow());
        assertFalse(fx(() -> shown(status)));
        fx(() -> control.showProblem("Teams konnten nicht gespeichert werden"));
        assertTrue(fx(() -> shown(status)));
        assertTrue(fx(status::getText).contains("Teams konnten nicht gespeichert werden"));
        fx(() -> control.showProblem("Zweite Meldung"));
        assertTrue(fx(status::getText).contains("Zweite Meldung"));
        assertFalse(fx(status::getText).contains("Teams konnten"));
    }
}
