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
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.FxTestSupport;
import de.kmost.scoreboard.ui.ShootoutTable;
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
                .filter(l -> l.getStyleClass().contains(styleClass) && shown(l))
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

    // --- 7-m-Werfen: Wurf-Liste in der Mittelspalte ---

    /** Spielt das reguläre Ende durch (Unentschieden) und startet das 7-m-Werfen; Heim beginnt. */
    private static void startShootout(GameState state) {
        state.clock().start();
        for (int period = 1; period <= state.config().mode().periodCount(); period++) {
            state.clock().setElapsed((long) period * state.config().periodMillis());
            state.clock().tick();
            if (state.clock().phaseProperty().get() == GameClock.Phase.HALF_TIME) {
                state.clock().startNextPeriod();
            }
        }
        state.startShootout(TeamSide.HOME);
    }

    private static List<Label> labels(DisplayWindow window, String styleClass) {
        return all(window.scene().getRoot(), Label.class).stream()
                .filter(l -> l.getStyleClass().contains(styleClass) && shown(l)).toList();
    }

    /** Umschließende Fläche aller Labels dieser Klasse. */
    private static Bounds union(List<Label> labels) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Label label : labels) {
            Bounds b = sceneBounds(label);
            minX = Math.min(minX, b.getMinX());
            minY = Math.min(minY, b.getMinY());
            maxX = Math.max(maxX, b.getMaxX());
            maxY = Math.max(maxY, b.getMaxY());
        }
        return new javafx.geometry.BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    private static List<Label> listLabels(DisplayWindow window) {
        List<Label> all = new java.util.ArrayList<>(labels(window, "shootout-number"));
        all.addAll(labels(window, "shootout-team"));
        all.addAll(labels(window, "shootout-symbol"));
        return all;
    }

    private static List<String> symbols(DisplayWindow window, TeamSide side) {
        // Symbole stehen zeilenweise: Heim-Zeile oberhalb der Gast-Zeile
        List<Label> symbols = labels(window, "shootout-symbol");
        if (symbols.isEmpty()) {
            return List.of();
        }
        double middle = union(labels(window, "shootout-team")).getCenterY();
        return symbols.stream()
                .filter(l -> (sceneBounds(l).getCenterY() < middle) == (side == TeamSide.HOME))
                .sorted(java.util.Comparator.comparingDouble(l -> sceneBounds(l).getMinX()))
                .map(Label::getText).toList();
    }

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1280,720", "1920,1080", "1024,768", "640,480"})
    void shootoutListSitsBelowTheHalvedClockInTheCenterColumn(double width, double height) {
        // Ausgangswerte ohne 7-m-Werfen: Uhr und Strafen
        DisplayWindow plain = window(width, height);
        GameState plainState = newState("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III");
        Bounds plainClock = fx(() -> {
            stateProperty.set(plainState);
            plainState.clock().start();
            plainState.addPenalty(TeamSide.HOME, "7");
            plainState.addPenalty(TeamSide.HOME, null);
            plainState.addPenalty(TeamSide.GUEST, "13");
            layout(plain.scene());
            return union(labels(plain, "clock"));
        });
        List<Bounds> plainPenalties = fx(() -> labels(plain, "penalty").stream()
                .map(l -> sceneBounds(l)).toList());
        assertTrue(texts(plain, "shootout-team").isEmpty(), "ohne 7-m-Werfen keine Liste");
        double plainScoreFont = fx(() -> labels(plain, "score").get(0).getFont().getSize());
        double plainScoreRow = fx(() -> sceneBounds(labels(plain, "score").get(0).getParent()).getHeight());
        double plainNameY = fx(() -> sceneBounds(labels(plain, "team-name").get(0)).getMinY());
        double plainDotsY = fx(() -> sceneBounds(labels(plain, "timeout-dots").get(0)).getMinY());

        ObjectProperty<GameState> property = new SimpleObjectProperty<>();
        DisplayWindow window = fx(() -> new DisplayWindow(property, width, height));
        GameState state = newState("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III");
        fx(() -> {
            property.set(state);
            startShootout(state);
            state.addPenalty(TeamSide.HOME, "7");
            state.addPenalty(TeamSide.HOME, null);
            state.addPenalty(TeamSide.GUEST, "13");
            state.recordShootoutAttempt(true);
            state.recordShootoutAttempt(false);
            state.recordShootoutAttempt(true);
            layout(window.scene());

            Bounds clock = union(labels(window, "clock"));
            Bounds list = union(listLabels(window));
            // das Uhr-Panel ist (ungefähr) halb so hoch wie ohne 7-m-Werfen
            assertEquals(0.5, clock.getHeight() / plainClock.getHeight(), 0.1, "Uhr halbiert");
            // darunter liegt die Liste, ohne die Uhr zu berühren
            assertTrue(list.getMinY() >= clock.getMaxY() - 1,
                    "Liste beginnt unter der Uhr: Liste " + list + " Uhr " + clock);
            // und sie bleibt in der 50-%-Mittelspalte
            double inner = width - 30;
            assertTrue(list.getMinX() >= 15 + 0.25 * inner - 1 && list.getMaxX() <= 15 + 0.75 * inner + 1,
                    "Liste liegt in der Mittelspalte: " + list);
            // und endet vor der Torzeile
            double scoreTop = labels(window, "score").stream().mapToDouble(l -> sceneBounds(l).getMinY())
                    .min().orElseThrow();
            assertTrue(list.getMaxY() <= scoreTop + 1, "Liste endet vor der Torzeile: " + list);

            // die Tore-Zeile ist beim 7-m-Werfen um 33 % kleiner (Zeilenhöhe und Torzahl-Schrift) ...
            Label score = labels(window, "score").get(0);
            assertEquals(0.67, score.getFont().getSize() / plainScoreFont, 0.01, "Torzahl-Schrift × 0,67");
            assertEquals(0.67, sceneBounds(score.getParent()).getHeight() / plainScoreRow, 0.02,
                    "Tore-Zeile × 0,67");
            // ... die Teamnamen-Zeile bleibt dagegen genau, wo sie war
            assertEquals(plainNameY, sceneBounds(labels(window, "team-name").get(0)).getMinY(), 1.5, "Teamname y");
            assertEquals(plainDotsY, sceneBounds(labels(window, "timeout-dots").get(0)).getMinY(), 1.5,
                    "Timeout-Punkte y");
            // die frei gewordene Höhe gehört der Wurf-Liste: sie ist deutlich höher als die Uhr (vorher etwa
            // gleich hoch); in kleinen Fenstern begrenzt die Breite die Liste, und die Schrift des CI-Rechners
            // (Linux, andere Emoji-Schrift) fällt etwas breiter aus als auf dem Mac — deshalb nur 1,25 statt 1,4
            assertTrue(list.getHeight() >= 1.25 * clock.getHeight(),
                    "Liste nutzt die zusätzliche Höhe: Liste " + list + " Uhr " + clock);

            // die Strafen-Spalten behalten Größe und Spalte; vertikal sitzt ihr Block mittig in der nun
            // höheren Zeile, verschiebt sich dabei aber nur um wenige Pixel (höchstens 2 % der Fensterhöhe)
            List<Label> penalties = labels(window, "penalty");
            assertEquals(plainPenalties.size(), penalties.size());
            for (int i = 0; i < penalties.size(); i++) {
                Bounds before = plainPenalties.get(i);
                Bounds after = sceneBounds(penalties.get(i));
                assertEquals(before.getMinX(), after.getMinX(), 1, "Strafen-Chip " + i + " x");
                assertEquals(before.getWidth(), after.getWidth(), 1, "Strafen-Chip " + i + " Breite");
                assertEquals(before.getHeight(), after.getHeight(), 1, "Strafen-Chip " + i + " Höhe");
                assertEquals(before.getMinY(), after.getMinY(), 0.02 * height, "Strafen-Chip " + i + " y");
                assertTrue(after.getMaxY() <= scoreTop, "Strafen-Chip " + i + " reicht nicht in die Tor-Zeile");
            }
        });
    }

    @Test
    void shootoutListFollowsEveryThrowAndUndoAndStaysAfterTheWinner() {
        DisplayWindow window = window(1280, 720);
        GameState state = newState("Heim", "Gast");
        fx(() -> {
            stateProperty.set(state);
            layout(window.scene());
        });
        assertTrue(listLabels(window).isEmpty(), "vor dem 7-m-Werfen keine Liste");

        fx(() -> {
            startShootout(state);
            layout(window.scene());
        });
        assertEquals(List.of("Heim", "Gast"), texts(window, "shootout-team"));
        assertEquals(List.of("1", "2", "3", "4", "5"), texts(window, "shootout-number"),
                "die ersten fünf Runden stehen von Anfang an in der Liste");
        assertTrue(symbols(window, TeamSide.HOME).isEmpty());

        fx(() -> {
            state.recordShootoutAttempt(true);  // Heim
            state.recordShootoutAttempt(false); // Gast
            state.recordShootoutAttempt(false); // Heim
            layout(window.scene());
        });
        assertEquals(List.of("⚽", "✋"), symbols(window, TeamSide.HOME));
        assertEquals(List.of("✋"), symbols(window, TeamSide.GUEST));
        assertEquals(List.of("1", "2", "3", "4", "5"), texts(window, "shootout-number"));

        fx(() -> {
            state.undoShootoutAttempt();
            layout(window.scene());
        });
        assertEquals(List.of("⚽"), symbols(window, TeamSide.HOME));
        assertEquals(List.of("✋"), symbols(window, TeamSide.GUEST));

        // Heim trifft immer, Gast nie: irgendwann steht der Sieger fest
        fx(() -> {
            while (state.shootoutProperty().get().winnerProperty().get() == null) {
                state.recordShootoutAttempt(
                        state.shootoutProperty().get().nextThrowerProperty().get() == TeamSide.HOME);
            }
            layout(window.scene());
        });
        assertFalse(symbols(window, TeamSide.HOME).isEmpty(), "Liste bleibt nach dem Sieger sichtbar");
        assertEquals(List.of("Ende"), texts(window, "phase"));
    }

    @Test
    void shootoutListColumnsKeepTheirWidthWhateverIsThrown() {
        DisplayWindow window = window(1280, 720);
        GameState state = newState("Heim", "Gast");
        fx(() -> {
            stateProperty.set(state);
            startShootout(state);
            layout(window.scene());
        });
        List<Double> columns = fx(() -> numberXs(window));
        assertEquals(5, columns.size());
        // Tor, Fehlwurf (Hand ist breiter/schmaler als der Ball), Tor, Fehlwurf: die Spalten bleiben stehen
        for (boolean goal : new boolean[] {true, false, true, false, false, true}) {
            fx(() -> {
                state.recordShootoutAttempt(goal);
                layout(window.scene());
            });
            List<Double> now = fx(() -> numberXs(window));
            assertEquals(columns.size(), now.size());
            for (int i = 0; i < columns.size(); i++) {
                assertEquals(columns.get(i), now.get(i), 0.01, "Spalte " + (i + 1) + " verschoben");
            }
        }
    }

    private static List<Double> numberXs(DisplayWindow window) {
        return labels(window, "shootout-number").stream()
                .map(l -> sceneBounds(l).getMinX()).sorted().toList();
    }

    @Test
    void shootoutListDropsOldestRoundsWithEllipsisAfterTheVisibleRounds() {
        DisplayWindow window = window(1280, 720);
        GameState state = newState("Heim", "Gast");
        fx(() -> {
            stateProperty.set(state);
            startShootout(state);
            // 18 unentschiedene Wurf-Paare (Sudden Death): beide treffen immer
            for (int i = 0; i < 36; i++) {
                state.recordShootoutAttempt(true);
            }
            layout(window.scene());
        });
        List<String> numbers = texts(window, "shootout-number");
        int visible = ShootoutTable.VISIBLE_ROUNDS;
        assertEquals(visible, numbers.size());
        assertEquals(String.valueOf(18 - visible + 1), numbers.get(0));
        assertEquals("18", numbers.get(visible - 1));
        assertEquals(2, texts(window, "shootout-symbol").stream().filter("…"::equals).count(),
                "je Team eine Auslassung für die verdrängten Runden");
        assertEquals(visible, symbols(window, TeamSide.HOME).stream().filter("⚽"::equals).count());
    }

    @ParameterizedTest(name = "{0}×{1}")
    @CsvSource({"1280,720", "1920,1080", "1024,768", "640,480"})
    void everyLabelStaysInsideTheWindowDuringALongShootout(double width, double height) {
        DisplayWindow window = window(width, height);
        GameState state = newState("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III");
        fx(() -> {
            window.headerBannerProperty().set(text("Herzlich Willkommen bei der HSG Tarp-Wanderup!"));
            window.footerBannerProperty().set(text("www.hsg-tarp-wanderup.de"));
            stateProperty.set(state);
            startShootout(state);
            for (int i = 0; i < 36; i++) {
                state.recordShootoutAttempt(true);
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
            Bounds list = union(listLabels(window));
            double inner = width - 30;
            assertTrue(list.getMinX() >= 15 + 0.25 * inner - 1 && list.getMaxX() <= 15 + 0.75 * inner + 1,
                    "auch die längste Liste bleibt in der Mittelspalte: " + list);
        });
    }
}
