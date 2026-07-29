package de.kmost.scoreboard.ui;

import java.util.Objects;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * App-Logo (LED-Anzeigetafel mit gelbem Spielstand „7:6“ auf blauer Kachel)
 * als Fenster-Icon; für kleine Darstellungen (Titelleiste) liegt eine
 * vereinfachte 32-px-Fassung bei. Quelle und Erzeugung: packaging/app-icon-build.sh
 */
public final class AppIcon {

    private static final Image LARGE = load("app-icon.png");
    private static final Image SMALL = load("app-icon-small.png");

    private AppIcon() {
    }

    public static void apply(Stage stage) {
        stage.getIcons().addAll(SMALL, LARGE);
    }

    private static Image load(String name) {
        return new Image(Objects.requireNonNull(
                AppIcon.class.getResourceAsStream("/de/kmost/scoreboard/" + name)));
    }
}
