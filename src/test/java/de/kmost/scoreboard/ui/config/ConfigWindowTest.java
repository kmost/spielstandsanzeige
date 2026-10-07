package de.kmost.scoreboard.ui.config;

import static de.kmost.scoreboard.ui.FxTestSupport.all;
import static de.kmost.scoreboard.ui.FxTestSupport.button;
import static de.kmost.scoreboard.ui.FxTestSupport.fx;
import static de.kmost.scoreboard.ui.FxTestSupport.layout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.FakeDialogs;
import de.kmost.scoreboard.ui.FxTestSupport;
import de.kmost.scoreboard.ui.display.DisplayWindow;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

/** Theme-Verwaltung im Konfigurationsfenster: Speichern, Konflikte, Laden, Löschen. */
@Tag("ui")
class ConfigWindowTest {

    @TempDir
    Path dir;

    private FakeDialogs dialogs;
    private ThemeRepository themes;
    private ConfigWindow config;
    private final AtomicInteger themeChanges = new AtomicInteger();

    @BeforeAll
    static void toolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void setUp() {
        dialogs = new FakeDialogs();
        themes = new ThemeRepository(dir);
        config = fx(() -> new ConfigWindow(new Stage(), new DisplayWindow(new SimpleObjectProperty<GameState>()),
                themes, new TeamRepository(dir), new Horn(new ProblemReporter(dir.resolve("horn.log"))), theme -> themeChanges.incrementAndGet(),
                name -> { }, dialogs));
    }

    private javafx.scene.Parent root() {
        layout(config.scene());
        return config.scene().getRoot();
    }

    private void typeThemeName(String name) {
        fx(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> box = all(root(), ComboBox.class).stream()
                    .filter(c -> "Theme-Name".equals(c.getPromptText())).findFirst().orElseThrow();
            box.getEditor().setText(name);
        });
    }

    private void click(String prefix) {
        fx(() -> button(root(), prefix).fire());
    }

    private List<String> listedThemes() {
        return fx(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> box = all(root(), ComboBox.class).stream()
                    .filter(c -> "Theme-Name".equals(c.getPromptText())).findFirst().orElseThrow();
            return List.copyOf(box.getItems());
        });
    }

    @Test
    void savingATheme_storesAndListsIt() {
        typeThemeName("Heimspiel");
        click("💾 Speichern");
        assertEquals(List.of("Heimspiel"), themes.themeNames());
        assertEquals(List.of("Heimspiel"), listedThemes());
        assertTrue(dialogs.warnings.isEmpty());
    }

    @Test
    void savingWithoutNameWarns() {
        typeThemeName("   ");
        click("💾 Speichern");
        assertEquals(1, dialogs.warnings.size());
        assertTrue(dialogs.warnings.get(0).contains("Theme-Namen"));
        assertTrue(themes.themeNames().isEmpty());
    }

    @Test
    void savingUnderAClashingFileNameIsRefusedWithAMessage() {
        typeThemeName("A/B");
        click("💾 Speichern");
        typeThemeName("A_B");
        click("💾 Speichern");

        assertEquals(1, dialogs.warnings.size());
        String warning = dialogs.warnings.get(0);
        assertTrue(warning.contains("A_B") && warning.contains("A/B")
                && warning.contains("kann nicht gespeichert werden"), warning);
        assertEquals(List.of("A/B"), themes.themeNames(), "das vorhandene Theme bleibt unangetastet");
    }

    @Test
    void savingTheSameNameAgainOverwrites() {
        typeThemeName("Heimspiel");
        click("💾 Speichern");
        click("💾 Speichern");
        assertTrue(dialogs.warnings.isEmpty());
        assertEquals(List.of("Heimspiel"), themes.themeNames());
    }

    @Test
    void loadingAnUnknownThemeWarns() {
        typeThemeName("Gibt es nicht");
        click("📂 Laden");
        assertEquals(1, dialogs.warnings.size());
        assertTrue(dialogs.warnings.get(0).contains("nicht gefunden"));
    }

    @Test
    void deletingAsksFirstAndOnlyDeletesOnConfirmation() {
        typeThemeName("Heimspiel");
        click("💾 Speichern");

        dialogs.confirmAnswer = false;
        click("🗑 Löschen");
        assertEquals(1, dialogs.confirms.size());
        assertEquals(List.of("Heimspiel"), themes.themeNames());

        dialogs.confirmAnswer = true;
        click("🗑 Löschen");
        assertTrue(themes.themeNames().isEmpty());
        assertFalse(listedThemes().contains("Heimspiel"));
    }

    @Test
    void resetToDefaultsAppliesTheThemeToTheDisplay() {
        int before = themeChanges.get();
        click("↺ Standard");
        assertTrue(themeChanges.get() > before, "das Standard-Theme wird an Anzeige und Konsole gemeldet");
    }
}
