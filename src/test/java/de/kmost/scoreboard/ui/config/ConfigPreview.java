package de.kmost.scoreboard.ui.config;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.display.DisplayWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Kein Unit-Test: rendert das Konfigurationsfenster offscreen (ohne sichtbares Fenster)
 * in ein PNG, um das Layout zu prüfen. Aufruf:
 * mvn test-compile exec:java -Dexec.mainClass=de.kmost.scoreboard.ui.config.ConfigPreview \
 *   -Dexec.classpathScope=test -Dpreview.out=/tmp/config-preview.png
 */
public class ConfigPreview extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        var tempDir = Files.createTempDirectory("config-preview");
        DisplayWindow displayWindow = new DisplayWindow(new SimpleObjectProperty<GameState>());
        ConfigWindow configWindow = new ConfigWindow(primaryStage, displayWindow,
                new ThemeRepository(tempDir), new TeamRepository(tempDir),
                new de.kmost.scoreboard.sound.Horn(), theme -> { }, name -> { });

        String width = System.getProperty("preview.width");
        String height = System.getProperty("preview.height");
        if (width != null || height != null) {
            javafx.scene.layout.Region root =
                    (javafx.scene.layout.Region) configWindow.scene().getRoot();
            root.setPrefSize(width == null ? root.getPrefWidth() : Double.parseDouble(width),
                    height == null ? root.getPrefHeight() : Double.parseDouble(height));
        }
        for (int i = 0; i < 5 && configWindow.scene().getRoot().isNeedsLayout(); i++) {
            configWindow.scene().getRoot().applyCss();
            configWindow.scene().getRoot().layout();
        }
        WritableImage image = configWindow.scene().snapshot(null);
        BufferedImage buffered = new BufferedImage(
                (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        File out = new File(System.getProperty("preview.out", "config-preview.png"));
        ImageIO.write(buffered, "png", out);
        System.out.println("Vorschau gespeichert: " + out.getAbsolutePath());
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
