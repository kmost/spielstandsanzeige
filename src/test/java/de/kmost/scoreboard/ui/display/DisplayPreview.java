package de.kmost.scoreboard.ui.display;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;

import javax.imageio.ImageIO;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.ui.BannerConfig;
import de.kmost.scoreboard.ui.FontScale;
import de.kmost.scoreboard.ui.Theme;
import de.kmost.scoreboard.ui.ThemeColor;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Kein Unit-Test: rendert die Publikumsanzeige offscreen (ohne sichtbares Fenster)
 * in ein PNG, um das Layout zu prüfen. Aufruf:
 * mvn test-compile exec:java -Dexec.mainClass=de.kmost.scoreboard.ui.display.DisplayPreview \
 *   -Dexec.classpathScope=test -Dpreview.out=/tmp/preview.png
 */
public class DisplayPreview extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Worst Case: lange Teamnamen + mehrere Strafen gleichzeitig
        GameConfig config = new GameConfig("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III",
                GameMode.TWO_HALVES, Duration.ofMinutes(30), ClockDirection.UP,
                SportProfile.HANDBALL);
        GameState state = new GameState(config);
        ObjectProperty<GameState> prop = new SimpleObjectProperty<>();
        // Fenstergröße über -Dpreview.width/-Dpreview.height wählbar (z. B. schmale Formate)
        DisplayWindow window = new DisplayWindow(prop,
                Double.parseDouble(System.getProperty("preview.width", "1024")),
                Double.parseDouble(System.getProperty("preview.height", "576")));
        window.headerBannerProperty().set(new BannerConfig(
                java.util.List.of("Herzlich Willkommen bei der HSG Tarp-Wanderup!"),
                java.util.List.of()));
        // drei Bilder, Texte nur in Slot 2 und 4 — prüft auch das Zusammenrücken leerer Slots
        window.footerBannerProperty().set(new BannerConfig(
                java.util.Arrays.asList("", "HSG Tarp-Wanderup", "www.hsg-tarp-wanderup.de", "www.tsvtarp.de", "www.tsv-wanderup.de"),
                java.util.Arrays.asList(sampleLogo(0xffcc8800), sampleLogo(0xff7744cc),
                        sampleLogo(0xff2299aa))));
        // Schriftart und alle Größenfaktoren per -Dpreview.font bzw. -Dpreview.<key>
        // (z. B. -Dpreview.clockScale=1.5) wählbar
        Theme previewTheme = Theme.defaults().withFont(System.getProperty("preview.font", ""));
        for (FontScale scale : FontScale.values()) {
            previewTheme = previewTheme.with(scale,
                    Double.parseDouble(System.getProperty("preview." + scale.key(), "1.0")));
        }
        window.applyTheme(previewTheme);
        if (Boolean.getBoolean("preview.theme")) {
            window.applyTheme(Theme.defaults()
                    .with(ThemeColor.BACKGROUND_TOP, javafx.scene.paint.Color.web("#001a00"))
                    .with(ThemeColor.BACKGROUND_BOTTOM, javafx.scene.paint.Color.web("#003300"))
                    .with(ThemeColor.CLOCK, javafx.scene.paint.Color.web("#00e5ff"))
                    .with(ThemeColor.SCORE, javafx.scene.paint.Color.web("#ffcc00"))
                    .with(ThemeColor.PENALTY, javafx.scene.paint.Color.web("#ff00ff")));
        }
        prop.set(state);
        state.clock().start();
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.GUEST);
        // Strafen zeitlich gestaffelt: die älteste (kürzeste Restzeit) steht oben;
        // Spielzeit über -Dpreview.elapsedSeconds wählbar (z. B. für Ziffern-Vergleiche)
        state.addPenalty(TeamSide.HOME, "7");
        state.clock().setElapsed(Integer.getInteger("preview.elapsedSeconds", 40) * 1000L);
        state.addPenalty(TeamSide.HOME, null);
        state.addPenalty(TeamSide.GUEST, "13");
        // weitere Heim-Strafen über -Dpreview.homePenalties=N, um das Abschneiden
        // am unteren Rand des Strafen-Bereichs zu prüfen
        for (int i = 0; i < Integer.getInteger("preview.homePenalties", 0); i++) {
            state.addPenalty(TeamSide.HOME, String.valueOf(20 + i));
        }
        state.startTeamTimeout(TeamSide.GUEST); // Timeout-Chip + ausgeblendete Halbzeit-Angabe prüfen
        state.tick();

        // mehrere Layout-Durchläufe wie im echten Betrieb: die em-Schriftgrößen aus dem
        // CSS sind im ersten Durchlauf noch nicht angewandt
        for (int i = 0; i < 5 && window.scene().getRoot().isNeedsLayout(); i++) {
            window.scene().getRoot().applyCss();
            window.scene().getRoot().layout();
        }
        WritableImage image = window.scene().snapshot(null);
        BufferedImage buffered = new BufferedImage(
                (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        File out = new File(System.getProperty("preview.out", "display-preview.png"));
        ImageIO.write(buffered, "png", out);
        System.out.println("Vorschau gespeichert: " + out.getAbsolutePath());
        Platform.exit();
    }

    private static File sampleLogo(int argb) throws Exception {
        BufferedImage logo = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = logo.createGraphics();
        g.setColor(new java.awt.Color(argb, true));
        g.fillOval(0, 0, 200, 200);
        g.dispose();
        File file = File.createTempFile("preview-logo-", ".png");
        file.deleteOnExit();
        ImageIO.write(logo, "png", file);
        return file;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
