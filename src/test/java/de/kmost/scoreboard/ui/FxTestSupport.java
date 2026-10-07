package de.kmost.scoreboard.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assumptions;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;

/**
 * Hilfen für UI-Tests: startet das JavaFX-Toolkit einmal je JVM (ohne sichtbare Fenster) und
 * führt Test-Code auf dem FX-Thread aus. Ist kein Toolkit startbar (z. B. Linux ohne Display
 * und ohne xvfb), werden UI-Tests über {@link #assumeToolkit()} übersprungen statt zu scheitern.
 */
public final class FxTestSupport {

    private static final long TIMEOUT_SECONDS = 20;
    private static Boolean toolkitAvailable;

    private FxTestSupport() {
    }

    /** Überspringt den Test, wenn kein JavaFX-Toolkit gestartet werden kann. */
    public static void assumeToolkit() {
        Assumptions.assumeTrue(startToolkit(),
                "JavaFX-Toolkit nicht verfügbar (kein Display? → xvfb-run verwenden)");
    }

    private static synchronized boolean startToolkit() {
        if (toolkitAvailable != null) {
            return toolkitAvailable;
        }
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.setImplicitExit(false);
            Platform.startup(started::countDown);
            toolkitAvailable = started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            toolkitAvailable = true;
        } catch (Throwable e) {
            toolkitAvailable = false;
        }
        return toolkitAvailable;
    }

    /** Führt den Code auf dem FX-Thread aus und liefert sein Ergebnis (Fehler werden weitergereicht). */
    public static <T> T fx(Callable<T> action) {
        if (Platform.isFxApplicationThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        } catch (InterruptedException | TimeoutException e) {
            throw new AssertionError("FX-Thread antwortet nicht", e);
        }
    }

    public static void fx(Runnable action) {
        fx(() -> {
            action.run();
            return null;
        });
    }

    /** Mehrere CSS-/Layout-Durchläufe wie im echten Betrieb (em-Größen greifen erst im zweiten). */
    public static void layout(Scene scene) {
        for (int i = 0; i < 5; i++) {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }
    }

    /** Alle Knöpfe unter der Wurzel in Baumreihenfolge (Heim vor Gast, oben vor unten). */
    public static List<Button> buttons(Parent root) {
        List<Button> found = new ArrayList<>();
        collect(root, Button.class, found);
        return found;
    }

    /** Alle Knoten eines Typs unter der Wurzel in Baumreihenfolge. */
    public static <T extends Node> List<T> all(Parent root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T extends Node> void collect(Node node, Class<T> type, List<T> found) {
        if (type.isInstance(node)) {
            found.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, type, found);
            }
        }
    }

    /** Knöpfe, deren Text mit dem Präfix beginnt. */
    public static List<Button> buttonsStartingWith(Parent root, String prefix) {
        return buttons(root).stream().filter(b -> b.getText() != null && b.getText().startsWith(prefix)).toList();
    }

    /** Der einzige Knopf mit diesem Textanfang; scheitert bei keinem oder mehreren. */
    public static Button button(Parent root, String prefix) {
        List<Button> matches = buttonsStartingWith(root, prefix);
        if (matches.size() != 1) {
            throw new AssertionError("Erwartet genau einen Knopf „" + prefix + "…“, gefunden: "
                    + matches.size() + " in " + buttons(root).stream().map(Labeled::getText).toList());
        }
        return matches.get(0);
    }

    /** Ob der Knoten selbst und alle Vorfahren sichtbar und verwaltet sind. */
    public static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible() || !n.isManaged()) {
                return false;
            }
        }
        return true;
    }

    /** Grenzen des Knotens in Szenen-Koordinaten (inklusive Skalierung und Verschiebung). */
    public static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }
}
