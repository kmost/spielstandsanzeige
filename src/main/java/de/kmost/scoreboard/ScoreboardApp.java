package de.kmost.scoreboard;

import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.GameSnapshotStore;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.SettingsStores;
import de.kmost.scoreboard.ui.control.ControlWindow;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.stage.Stage;

public class ScoreboardApp extends Application {

    private AnimationTimer timer;

    @Override
    public void start(Stage stage) {
        Horn horn = new Horn();
        ControlWindow control = new ControlWindow(stage, horn, new TeamRepository(),
                SettingsStores.standard(), new GameSnapshotStore());
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                control.tick();
            }
        };
        timer.start();
        control.show();
    }

    @Override
    public void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
