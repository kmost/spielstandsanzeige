package de.kmost.scoreboard.store;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import de.kmost.scoreboard.model.ClockDirection;
import de.kmost.scoreboard.model.GameClock;
import de.kmost.scoreboard.model.GameMode;
import de.kmost.scoreboard.model.GameSnapshot;
import de.kmost.scoreboard.model.OvertimeFormat;
import de.kmost.scoreboard.model.Shootout;
import de.kmost.scoreboard.model.TeamSide;

/**
 * Sicherung des laufenden Spiels unter ~/.spielstandsanzeige/game.properties, damit es nach
 * einem Absturz oder Neustart fortgesetzt werden kann. Geschrieben wird atomar (Temp-Datei,
 * dann Umbenennen), eine halb geschriebene Datei kann also nie die gute ersetzen. Eine
 * unlesbare oder unstimmige Datei blockiert den Start nie: sie wird beiseitegelegt
 * (game.properties.defekt) und {@link #load()} liefert leer.
 */
public class GameSnapshotStore {

    private static final String FILE = "game.properties";
    private static final String BROKEN_FILE = "game.properties.defekt";
    private static final int SCHEMA = 1;

    private final Path baseDir;
    private final ProblemReporter reporter;

    public GameSnapshotStore() {
        this(Path.of(System.getProperty("user.home"), ".spielstandsanzeige"), ProblemReporter.shared());
    }

    public GameSnapshotStore(Path baseDir) {
        this(baseDir, new ProblemReporter(baseDir.resolve("spielstandsanzeige.log")));
    }

    public GameSnapshotStore(Path baseDir, ProblemReporter reporter) {
        this.baseDir = baseDir;
        this.reporter = reporter;
    }

    /** Sichert das Abbild; Fehler werden gemeldet, blockieren aber nie den Spielbetrieb. */
    public void save(GameSnapshot snapshot) {
        try {
            PropertiesFiles.store(baseDir.resolve(FILE), toProperties(snapshot),
                    "Gesichertes Spiel der Spielstandsanzeige");
        } catch (IOException e) {
            reporter.report("Spielstand konnte nicht gesichert werden", e);
        }
    }

    /** Gesichertes Spiel; leer, wenn keines existiert oder die Datei unbrauchbar ist. */
    public Optional<GameSnapshot> load() {
        Path file = baseDir.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(reader);
            return Optional.of(fromProperties(props));
        } catch (IOException | RuntimeException e) {
            reporter.report("Gesichertes Spiel nicht lesbar", e);
            quarantine();
            return Optional.empty();
        }
    }

    /** Löscht die Sicherung (Spiel beendet, verworfen oder durch ein neues ersetzt). */
    public void delete() {
        try {
            Files.deleteIfExists(baseDir.resolve(FILE));
            Files.deleteIfExists(PropertiesFiles.tempFor(baseDir.resolve(FILE)));
        } catch (IOException e) {
            reporter.report("Gesichertes Spiel konnte nicht gelöscht werden", e);
        }
    }

    /** Legt eine unbrauchbare Sicherung beiseite, damit sie nicht bei jedem Start wieder auftaucht. */
    public void quarantine() {
        if (!Files.exists(baseDir.resolve(FILE))) {
            return;
        }
        try {
            Files.move(baseDir.resolve(FILE), baseDir.resolve(BROKEN_FILE),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            reporter.log("Defektes Spiel konnte nicht beiseitegelegt werden", e);
        }
    }

    private static Properties toProperties(GameSnapshot s) {
        Properties props = new Properties();
        props.setProperty("schema", String.valueOf(SCHEMA));
        props.setProperty("home", s.homeName());
        props.setProperty("guest", s.guestName());
        props.setProperty("mode", s.mode().name());
        props.setProperty("periodMillis", String.valueOf(s.periodMillis()));
        props.setProperty("direction", s.direction().name());
        props.setProperty("overtimeFormat", s.overtimeFormat().name());
        props.setProperty("overtimeMillis", String.valueOf(s.overtimeMillis()));
        props.setProperty("sport", s.sport());
        props.setProperty("phase", s.phase().name());
        props.setProperty("period", String.valueOf(s.period()));
        props.setProperty("overtimes", String.valueOf(s.overtimes()));
        props.setProperty("elapsed", String.valueOf(s.elapsedMillis()));
        props.setProperty("score.home", String.valueOf(s.homeScore()));
        props.setProperty("score.guest", String.valueOf(s.guestScore()));
        props.setProperty("timeouts.home", String.valueOf(s.homeTimeoutsUsed()));
        props.setProperty("timeouts.guest", String.valueOf(s.guestTimeoutsUsed()));
        props.setProperty("penalty.count", String.valueOf(s.penalties().size()));
        for (int i = 0; i < s.penalties().size(); i++) {
            GameSnapshot.PenaltySnapshot p = s.penalties().get(i);
            String prefix = "penalty." + i + ".";
            props.setProperty(prefix + "side", p.side().name());
            if (p.playerNumber() != null) {
                props.setProperty(prefix + "number", p.playerNumber());
            }
            props.setProperty(prefix + "start", String.valueOf(p.startElapsedMillis()));
            props.setProperty(prefix + "duration", String.valueOf(p.durationMillis()));
            props.setProperty(prefix + "extended", String.valueOf(p.extended()));
        }
        if (s.shootoutStart() != null) {
            props.setProperty("shootout.start", s.shootoutStart().name());
            props.setProperty("shootout.count", String.valueOf(s.shootoutAttempts().size()));
            for (int i = 0; i < s.shootoutAttempts().size(); i++) {
                Shootout.Attempt a = s.shootoutAttempts().get(i);
                props.setProperty("shootout." + i + ".side", a.side().name());
                props.setProperty("shootout." + i + ".goal", String.valueOf(a.goal()));
            }
        }
        if (s.ended()) {
            props.setProperty("ended", "true");
        }
        return props;
    }

    private static GameSnapshot fromProperties(Properties props) {
        if (number(props, "schema") != SCHEMA) {
            throw new IllegalArgumentException("Unbekanntes Schema " + props.getProperty("schema"));
        }
        List<GameSnapshot.PenaltySnapshot> penalties = new ArrayList<>();
        int penaltyCount = (int) number(props, "penalty.count");
        for (int i = 0; i < penaltyCount; i++) {
            String prefix = "penalty." + i + ".";
            penalties.add(new GameSnapshot.PenaltySnapshot(
                    TeamSide.valueOf(text(props, prefix + "side")),
                    props.getProperty(prefix + "number"),
                    number(props, prefix + "start"),
                    number(props, prefix + "duration"),
                    Boolean.parseBoolean(text(props, prefix + "extended"))));
        }
        TeamSide shootoutStart = null;
        List<Shootout.Attempt> attempts = new ArrayList<>();
        if (props.getProperty("shootout.start") != null) {
            shootoutStart = TeamSide.valueOf(text(props, "shootout.start"));
            int attemptCount = (int) number(props, "shootout.count");
            for (int i = 0; i < attemptCount; i++) {
                attempts.add(new Shootout.Attempt(
                        TeamSide.valueOf(text(props, "shootout." + i + ".side")),
                        Boolean.parseBoolean(text(props, "shootout." + i + ".goal"))));
            }
        }
        return new GameSnapshot(
                text(props, "home"), text(props, "guest"),
                GameMode.valueOf(text(props, "mode")),
                number(props, "periodMillis"),
                ClockDirection.valueOf(text(props, "direction")),
                OvertimeFormat.valueOf(text(props, "overtimeFormat")),
                number(props, "overtimeMillis"),
                text(props, "sport"),
                GameClock.Phase.valueOf(text(props, "phase")),
                (int) number(props, "period"), (int) number(props, "overtimes"),
                number(props, "elapsed"),
                (int) number(props, "score.home"), (int) number(props, "score.guest"),
                (int) number(props, "timeouts.home"), (int) number(props, "timeouts.guest"),
                List.copyOf(penalties), shootoutStart, List.copyOf(attempts),
                Boolean.parseBoolean(props.getProperty("ended", "false")));
    }

    private static String text(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Eintrag fehlt: " + key);
        }
        return value;
    }

    private static long number(Properties props, String key) {
        try {
            return Long.parseLong(text(props, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültige Zahl für " + key);
        }
    }
}
