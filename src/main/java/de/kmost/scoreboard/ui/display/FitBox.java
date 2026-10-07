package de.kmost.scoreboard.ui.display;

import javafx.scene.layout.Region;

/**
 * Setzt einen Inhalt mittig in die eigene Fläche und verkleinert ihn proportional, sobald er
 * breiter oder höher ist als die Fläche — nichts wird gekürzt oder ragt in Nachbarzellen.
 * Die Mindestgröße ist 0, damit die Fläche nicht vom Inhalt aufgeweitet wird (wie die
 * Strafen-Spalten und die Phase-Zeile, die per Skalierung eingepasst werden).
 */
final class FitBox extends Region {

    private final Region content;

    FitBox(Region content) {
        this.content = content;
        setMinSize(0, 0);
        getChildren().add(content);
    }

    @Override
    protected double computePrefWidth(double height) {
        return content.prefWidth(-1);
    }

    @Override
    protected double computePrefHeight(double width) {
        return content.prefHeight(-1);
    }

    @Override
    protected void layoutChildren() {
        double width = content.prefWidth(-1);
        double height = content.prefHeight(width);
        content.resize(width, height);
        double factor = 1;
        if (width > 0 && height > 0 && getWidth() > 0 && getHeight() > 0) {
            factor = Math.min(1, Math.min(getWidth() / width, getHeight() / height));
        }
        // Skalierung um die Mitte des Inhalts: mittig platziert bleibt er mittig
        content.setScaleX(factor);
        content.setScaleY(factor);
        content.relocate((getWidth() - width) / 2, (getHeight() - height) / 2);
    }
}
