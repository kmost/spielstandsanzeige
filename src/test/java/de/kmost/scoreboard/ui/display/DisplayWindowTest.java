package de.kmost.scoreboard.ui.display;

import static de.kmost.scoreboard.ui.FxTestSupport.all;
import static de.kmost.scoreboard.ui.FxTestSupport.fx;
import static de.kmost.scoreboard.ui.FxTestSupport.layout;
import static de.kmost.scoreboard.ui.FxTestSupport.sceneBounds;
import static de.kmost.scoreboard.ui.FxTestSupport.shown;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.FxTestSupport;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Raster der Publikumsanzeige (docs/layout.md) bei verschiedenen Fenstergrößen. */
@Tag("ui")
class DisplayWindowTest {

    private static final double TOLERANCE = 2.0;

    @BeforeAll
    static void toolkit() {
        FxTestSupport.assumeToolkit();
    }

    private final ObjectProperty<GameState> stateProperty = new SimpleObjectProperty<>();

    private DisplayWindow window(double width, double height) {
        return fx(() -> new DisplayWindow(stateProperty, width, height));
    }

    private static GameState newState(String home, String guest) {
        return new GameState(new GameConfig(home, guest, GameMode.TWO_HALVES,
                Duration.ofMinutes(30), ClockDirection.UP, SportProfile.HANDBALL));
    }

    private static BannerConfig text(String text) {
        return new BannerConfig(List.of(text), List.of());
    }

    private static List<String> texts(DisplayWindow window, String styleClass) {
        return fx(() -> all(window.scene().getRoot(), Label.class).stream()
                .filter(l -> l.getStyleClass().contains(styleClass))
                .map(Label::getText).collect(Collectors.toList()));
    }

    // --- Zonen ---

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1024,576", "1280,720", "1920,1080", "800,600", "1024,768"})
    void headerScoreboardFooterFollowTenEightyTenRatio(double width, double height) {
        DisplayWindow window = window(width, height);
        fx(() -> {
            window.headerBannerProperty().set(text("Willkommen"));
            window.footerBannerProperty().set(text("www.example.org"));
            layout(window.scene());
            VBox root = (VBox) window.scene().getRoot();
            Node header = root.getChildren().get(0);
            Node content = root.getChildren().get(1);
            Node footer = root.getChildren().get(2);
            assertEquals(0.10 * height, sceneBounds(header).getHeight(), TOLERANCE, "Header");
            assertEquals(0.10 * height, sceneBounds(footer).getHeight(), TOLERANCE, "Footer");
            // 2 % Abstand je Banner gehen zulasten des Spielstands: 100 − 10 − 10 − 2·2 = 76 %
            assertEquals(0.76 * height, sceneBounds(content).getHeight(), TOLERANCE, "Spielstand");
            assertEquals(height, sceneBounds(footer).getMaxY() + 0, TOLERANCE, "Footer reicht bis zum unteren Rand");
        });
    }

    @Test
    void bannerWithoutContentIsHiddenAndLeavesItsShareToTheScoreboard() {
        DisplayWindow window = window(1280, 720);
        fx(() -> {
            window.headerBannerProperty().set(text("Nur oben"));
            layout(window.scene());
            VBox root = (VBox) window.scene().getRoot();
            Node footer = root.getChildren().get(2);
            Node content = root.getChildren().get(1);
            assertFalse(footer.isManaged(), "leerer Footer belegt keinen Platz");
            // 100 − 10 (Header) − 2 (Abstand) = 88 %
            assertEquals(0.88 * 720, sceneBounds(content).getHeight(), TOLERANCE);
        });
    }

    @Test
    void withoutBannersTheScoreboardFillsTheWholeWindow() {
        DisplayWindow window = window(1280, 720);
        fx(() -> {
            layout(window.scene());
            Node content = ((VBox) window.scene().getRoot()).getChildren().get(1);
            assertEquals(720, sceneBounds(content).getHeight(), TOLERANCE);
        });
    }

    // --- Inhalt ---

    @Test
    void showsPlaceholderWithoutGame() {
        DisplayWindow window = window(1024, 576);
        fx(() -> layout(window.scene()));
        assertEquals(List.of("Spielstandsanzeige"), texts(window, "placeholder"));
    }

    @Test
    void showsScoresNamesClockAndPeriod() {
        DisplayWindow window = window(1024, 576);
        GameState state = newState("HSG Tarp", "TSV Wanderup");
        fx(() -> {
            stateProperty.set(state);
            state.addGoal(TeamSide.HOME);
            state.addGoal(TeamSide.HOME);
            state.addGoal(TeamSide.HOME);
            state.addGoal(TeamSide.GUEST);
            layout(window.scene());
        });
        assertEquals(List.of("3", "1"), texts(window, "score"));
        assertEquals(List.of("HSG Tarp", "TSV Wanderup"), texts(window, "team-name"));
        assertEquals("00:00", String.join("", texts(window, "clock")));
    }

    @Test
    void clockFollowsElapsedTime() {
        DisplayWindow window = window(1024, 576);
        GameState state = newState("Heim", "Gast");
        fx(() -> {
            stateProperty.set(state);
            state.clock().start();
            state.clock().pause();
            state.clock().setElapsed(754_000);
            layout(window.scene());
        });
        assertEquals("12:34", String.join("", texts(window, "clock")));
    }

    @Test
    void timeoutChipAppearsWhileTimeoutRuns() {
        DisplayWindow window = window(1024, 576);
        GameState state = newState("Heim", "Gast");
        fx(() -> {
            stateProperty.set(state);
            state.clock().start();
            state.startTeamTimeout(TeamSide.GUEST);
            layout(window.scene());
        });
        List<String> shown = texts(window, "timeout");
        assertEquals(1, shown.size());
        // der Pfeil zeigt zur Seite des Teams (Gast = rechts)
        assertEquals("Team-Timeout 1:00 ▶", shown.get(0));
        fx(() -> {
            state.endTeamTimeout();
            layout(window.scene());
        });
        assertTrue(texts(window, "timeout").stream().allMatch(String::isBlank));
    }

    // --- nichts wird abgeschnitten ---

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1024,576", "1920,1080", "800,600", "1024,768", "640,480"})
    void penaltyChipsStayInsideTheirColumns(double width, double height) {
        DisplayWindow window = window(width, height);
        GameState state = newState("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III");
        fx(() -> {
            stateProperty.set(state);
            state.clock().start();
            for (String number : List.of("7", "11", "23", "99")) {
                state.addPenalty(TeamSide.HOME, number);
            }
            state.addPenalty(TeamSide.GUEST, "13");
            state.addPenalty(TeamSide.GUEST, null);
            state.extendPenalty(state.penalties(TeamSide.GUEST).get(0));
            layout(window.scene());

            double padding = 15;
            double inner = width - 2 * padding;
            List<Label> chips = all(window.scene().getRoot(), Label.class).stream()
                    .filter(l -> l.getStyleClass().contains("penalty")).toList();
            assertEquals(6, chips.size());
            int homeCount = 0;
            for (Label chip : chips) {
                Bounds b = sceneBounds(chip);
                boolean home = b.getCenterX() < width / 2;
                homeCount += home ? 1 : 0;
                // zur Mitte hin darf ein Chip die Spaltengrenze nicht überschreiten; nach außen
                // darf er (schrift- und plattformabhängig) in den äußeren Rasterrand reichen,
                // muss aber vollständig im Fenster bleiben
                double min = home ? 0 : padding + 0.75 * inner;
                double max = home ? padding + 0.25 * inner : width;
                assertTrue(b.getMinX() >= min - 1 && b.getMaxX() <= max + 1,
                        "Chip „" + chip.getText() + "“ ragt aus seiner Spalte: " + b + " (erlaubt "
                                + min + ".." + max + ")");
            }
            assertEquals(4, homeCount);
        });
    }

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1024,576", "800,600", "1920,1080"})
    void everyVisibleLabelStaysInsideTheWindow(double width, double height) {
        DisplayWindow window = window(width, height);
        GameState state = newState("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III");
        fx(() -> {
            window.headerBannerProperty().set(text("Herzlich Willkommen bei der HSG Tarp-Wanderup!"));
            window.footerBannerProperty().set(text("www.hsg-tarp-wanderup.de"));
            stateProperty.set(state);
            state.clock().start();
            state.addPenalty(TeamSide.HOME, "7");
            state.addPenalty(TeamSide.GUEST, "13");
            for (int i = 0; i < 12; i++) {
                state.addGoal(i % 2 == 0 ? TeamSide.HOME : TeamSide.GUEST);
            }
            layout(window.scene());
            for (Label label : all(window.scene().getRoot(), Label.class)) {
                if (!shown(label) || label.getText() == null || label.getText().isEmpty()) {
                    continue;
                }
                Bounds b = sceneBounds(label);
                assertTrue(b.getMinX() >= -1 && b.getMaxX() <= width + 1
                                && b.getMinY() >= -1 && b.getMaxY() <= height + 1,
                        "Label „" + label.getText() + "“ liegt außerhalb: " + b);
            }
        });
    }

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1024,576", "640,480"})
    void bannerShrinksToFitWhenContentIsWiderThanTheWindow(double width, double height) {
        DisplayWindow window = window(width, height);
        fx(() -> {
            window.headerBannerProperty().set(new BannerConfig(List.of(
                    "Herzlich Willkommen zum Heimspiel der HSG Tarp-Wanderup gegen den Gast aus der Nachbarschaft"),
                    List.of()));
            layout(window.scene());
            Node header = ((VBox) window.scene().getRoot()).getChildren().get(0);
            List<Label> labels = all((javafx.scene.Parent) header, Label.class);
            assertFalse(labels.isEmpty());
            for (Label label : labels) {
                Bounds b = sceneBounds(label);
                assertTrue(b.getMinX() >= -1 && b.getMaxX() <= width + 1,
                        "Bannertext ragt aus dem Fenster: " + b);
                assertFalse(label.getText().endsWith("…"), "Banner darf nicht mit „…“ kürzen");
            }
        });
    }

    @Test
    void overtimePeriodTextFitsItsColumn() {
        DisplayWindow window = window(1024, 576);
        GameState state = newState("Heim", "Gast");
        fx(() -> {
            stateProperty.set(state);
            state.clock().start();
            for (int period = 1; period <= 2; period++) {
                state.clock().setElapsed(period * state.config().periodMillis());
                state.clock().tick();
                if (period == 1) {
                    state.clock().startNextPeriod();
                }
            }
            state.clock().startOvertime();
            layout(window.scene());
            for (Label label : all(window.scene().getRoot(), Label.class)) {
                if (label.getStyleClass().contains("phase")) {
                    assertFalse(label.getText().isBlank());
                    Bounds b = sceneBounds(label);
                    // mittlere Spalte der Torzeile: 16 % der inneren Breite, mittig
                    double inner = 1024 - 30;
                    double center = 1024 / 2.0;
                    assertTrue(b.getMinX() >= center - 0.08 * inner - 2
                            && b.getMaxX() <= center + 0.08 * inner + 2,
                            "Phasentext ragt aus seiner Spalte: " + b + " / " + label.getText());
                }
            }
        });
    }
}
