package de.kmost.scoreboard.ui.control;

import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.transform.Scale;

/** Gemeinsame Layout-Hilfen der Spielsteuerung: Prozent-Raster und Einpassen in Zellen. */
final class ControlLayout {

    private ControlLayout() {
    }

    static RowConstraints percentRow(double percent) {
        RowConstraints row = new RowConstraints();
        row.setPercentHeight(percent);
        row.setVgrow(Priority.ALWAYS);
        return row;
    }

    static ColumnConstraints percentColumn(double percent) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(percent);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    /**
     * Passt den Inhalt einer Raster-Zelle per Skalierung ein, sobald er breiter
     * ist als der ihm zugeteilte Platz — analog zur Einpassung auf der
     * Publikumsanzeige: nichts wird mit „…“ gekürzt, nichts ragt in
     * Nachbarzellen. Damit das funktioniert, müssen die Kinder ihre bevorzugte
     * Breite als Mindestbreite behalten (USE_PREF_SIZE) statt gestaucht zu
     * werden. Der Pivot bestimmt, welche Kante beim Einpassen stehen bleibt.
     */
    static <T extends Region> T fitToCellWidth(T content, HPos anchor) {
        Scale fit = new Scale(1, 1);
        if (anchor == HPos.RIGHT) {
            fit.pivotXProperty().bind(content.widthProperty());
        }
        // LEFT/CENTER: Pivot 0 — überbreiter Inhalt beginnt links und füllt
        // eingepasst genau die Zellbreite
        fit.pivotYProperty().bind(content.heightProperty().divide(2));
        content.getTransforms().add(fit);
        InvalidationListener refit = obs -> {
            double natural = content.prefWidth(-1);
            double available = content.getWidth();
            double factor = natural > available && available > 0 ? available / natural : 1;
            fit.setX(factor);
            fit.setY(factor);
        };
        content.widthProperty().addListener(refit);
        for (Node child : content.getChildrenUnmodifiable()) {
            child.layoutBoundsProperty().addListener(refit);
        }
        // dynamisch neu aufgebaute Kinder (Strafen, Timeout-Zeile) mitverfolgen
        content.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
            while (change.next()) {
                for (Node added : change.getAddedSubList()) {
                    added.layoutBoundsProperty().addListener(refit);
                }
            }
            refit.invalidated(null);
        });
        return content;
    }
}
