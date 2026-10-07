package de.kmost.scoreboard.ui.control;

import de.kmost.scoreboard.diagnostics.ProblemReporter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * Statuszeile am unteren Rand für Probleme, die den Spielbetrieb nicht stoppen
 * (z. B. Speichern fehlgeschlagen): blendet sich nach einigen Sekunden aus.
 */
final class StatusBar {

    private final Label statusLine = new Label();
    private final PauseTransition timeout = new PauseTransition(javafx.util.Duration.seconds(10));

    StatusBar(ProblemReporter reporter) {
        statusLine.getStyleClass().add("status-line");
        statusLine.setMaxWidth(Double.MAX_VALUE);
        statusLine.setWrapText(true);
        statusLine.setVisible(false);
        statusLine.setManaged(false);
        timeout.setOnFinished(e -> {
            statusLine.setVisible(false);
            statusLine.setManaged(false);
        });
        // Meldungen können aus jedem Thread kommen
        reporter.addListener(message -> {
            if (Platform.isFxApplicationThread()) {
                showProblem(message);
            } else {
                Platform.runLater(() -> showProblem(message));
            }
        });
    }

    Node node() {
        return statusLine;
    }

    /** Zeigt eine Problemmeldung; eine neue Meldung ersetzt die alte. */
    void showProblem(String message) {
        statusLine.setText("⚠ " + message + " – Details in ~/.spielstandsanzeige/spielstandsanzeige.log");
        statusLine.setVisible(true);
        statusLine.setManaged(true);
        timeout.playFromStart();
    }
}
