package de.kmost.scoreboard.ui.control;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;

import javax.imageio.ImageIO;

import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Kein Unit-Test: rendert die Kampfgericht-Konsole offscreen (ohne sichtbares Fenster)
 * in ein PNG, um das Layout zu prüfen. Aufruf:
 * mvn test-compile exec:java -Dexec.mainClass=de.kmost.scoreboard.ui.control.ControlPreview \
 *   -Dexec.classpathScope=test -Dpreview.out=/tmp/control-preview.png
 */
public class ControlPreview extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Repositories auf ein Temp-Verzeichnis zeigen lassen, damit die Vorschau
        // keine echten Nutzerdaten liest oder schreibt
        var tempDir = Files.createTempDirectory("control-preview");
        // Fenstergröße über -Dpreview.width/-Dpreview.height wählbar
        ControlWindow control = new ControlWindow(primaryStage, new Horn(),
                new TeamRepository(tempDir), new ThemeRepository(tempDir),
                Double.parseDouble(System.getProperty("preview.width", "940")),
                Double.parseDouble(System.getProperty("preview.height", "700")));

        // Worst Case wie in der DisplayPreview: lange Namen, Strafen, laufendes Timeout
        GameConfig config = new GameConfig("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III",
                GameMode.TWO_HALVES, Duration.ofMinutes(30), ClockDirection.UP,
                SportProfile.HANDBALL);
        GameState state = new GameState(config);
        control.gameStateProperty().set(state);
        state.clock().start();
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.HOME);
        state.addGoal(TeamSide.GUEST);
        state.addPenalty(TeamSide.HOME, "7");
        state.addPenalty(TeamSide.HOME, null);
        state.addPenalty(TeamSide.GUEST, "13");
        state.startTeamTimeout(TeamSide.GUEST);
        state.tick();

        // mehrere Layout-Durchläufe wie im echten Betrieb
        for (int i = 0; i < 5 && control.scene().getRoot().isNeedsLayout(); i++) {
            control.scene().getRoot().applyCss();
            control.scene().getRoot().layout();
        }
        WritableImage image = control.scene().snapshot(null);
        BufferedImage buffered = new BufferedImage(
                (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        File out = new File(System.getProperty("preview.out", "control-preview.png"));
        ImageIO.write(buffered, "png", out);
        System.out.println("Vorschau gespeichert: " + out.getAbsolutePath());
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
