package de.kmost.scoreboard;

import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
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
                new ThemeRepository());
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                GameState state = control.gameState();
                if (state != null) {
                    state.tick();
                }
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
