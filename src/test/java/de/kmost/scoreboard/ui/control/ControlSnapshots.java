package de.kmost.scoreboard.ui.control;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameConfig;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameState;
import de.kmost.scoreboard.model.SportProfile;
import de.kmost.scoreboard.model.TeamSide;
import de.kmost.scoreboard.sound.Horn;
import de.kmost.scoreboard.store.GameSnapshotStore;
import de.kmost.scoreboard.store.TeamRepository;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.FxTestSupport;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

/**
 * Kein Unit-Test: rendert die Kampfgericht-Konsole in mehreren typischen Zuständen und
 * zwei Breiten offscreen als PNGs in ein Verzeichnis. Zum Vergleichen vor/nach einem
 * Umbau der Oberfläche (identische Dateien = identische Optik):
 * <pre>
 * mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *   -Dexec.mainClass=de.kmost.scoreboard.ui.control.ControlSnapshots \
 *   -Dexec.classpathScope=test -Dsnapshots.dir=/tmp/control-vorher
 * </pre>
 */
public final class ControlSnapshots {

    private ControlSnapshots() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(System.getProperty("snapshots.dir", "control-snapshots"));
        Files.createDirectories(out);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);

        Map<String, Consumer<ControlWindow>> scenarios = new LinkedHashMap<>();
        scenarios.put("01-setup", control -> { });
        scenarios.put("02-bereit", control -> control.gameStateProperty().set(newState()));
        scenarios.put("03-laeuft-strafen-timeout", control -> {
            GameState state = newState();
            control.gameStateProperty().set(state);
            state.clock().start();
            state.addGoal(TeamSide.HOME);
            state.addPenalty(TeamSide.HOME, "7");
            state.addPenalty(TeamSide.HOME, null);
            state.addPenalty(TeamSide.GUEST, "13");
            state.extendPenalty(state.penalties(TeamSide.GUEST).get(0));
            state.startTeamTimeout(TeamSide.GUEST);
            state.tick();
        });
        scenarios.put("04-halbzeitpause", control -> {
            GameState state = newState();
            control.gameStateProperty().set(state);
            state.clock().start();
            state.clock().setElapsed(state.config().periodMillis());
            state.clock().tick();
        });
        scenarios.put("05-unentschieden", control -> {
            GameState state = newState();
            control.gameStateProperty().set(state);
            finishRegulation(state);
        });
        scenarios.put("06-verlaengerung", control -> {
            GameState state = newState();
            control.gameStateProperty().set(state);
            finishRegulation(state);
            state.clock().startOvertime();
            state.addPenalty(TeamSide.HOME, "5");
        });
        scenarios.put("07-siebenmeter", control -> {
            GameState state = newState();
            control.gameStateProperty().set(state);
            finishRegulation(state);
            state.startShootout(TeamSide.HOME);
            for (boolean goal : new boolean[] {true, false, true, true, false}) {
                state.recordShootoutAttempt(goal);
            }
        });
        scenarios.put("08-siebenmeter-sieger-meldung", control -> {
            GameState state = newState();
            control.gameStateProperty().set(state);
            finishRegulation(state);
            state.startShootout(TeamSide.GUEST);
            for (int i = 0; i < 20 && state.shootoutProperty().get().winnerProperty().get() == null; i++) {
                state.recordShootoutAttempt(i % 2 == 1);
            }
            control.showProblem("Teams konnten nicht gespeichert werden");
        });

        for (double width : new double[] {940, 760}) {
            for (var scenario : scenarios.entrySet()) {
                Path file = out.resolve(scenario.getKey() + "-" + (int) width + ".png");
                FxTestSupport.fx(() -> {
                    try {
                        Path temp = Files.createTempDirectory("control-snapshots");
                        ControlWindow control = new ControlWindow(new Stage(), new Horn(new ProblemReporter(temp.resolve("horn.log"))),
                                new TeamRepository(temp), new ThemeRepository(temp),
                                new GameSnapshotStore(temp), width, 700);
                        scenario.getValue().accept(control);
                        render(control, file.toFile());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
        }
        System.out.println("Schnappschüsse gespeichert in " + out.toAbsolutePath());
        Platform.exit();
    }

    private static GameState newState() {
        return new GameState(new GameConfig("HSG Tarp-Wanderup II", "HSG Tarp-Wanderup III",
                GameMode.TWO_HALVES, Duration.ofMinutes(30), ClockDirection.UP,
                SportProfile.HANDBALL));
    }

    private static void finishRegulation(GameState state) {
        GameClock clock = state.clock();
        clock.start();
        for (int period = 1; period <= state.config().mode().periodCount(); period++) {
            clock.setElapsed((long) period * state.config().periodMillis());
            clock.tick();
            if (clock.phaseProperty().get() == GameClock.Phase.HALF_TIME) {
                clock.startNextPeriod();
            }
        }
    }

    private static void render(ControlWindow control, File file) throws Exception {
        FxTestSupport.layout(control.scene());
        WritableImage image = control.scene().snapshot(null);
        BufferedImage buffered = new BufferedImage(
                (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", file);
    }
}
