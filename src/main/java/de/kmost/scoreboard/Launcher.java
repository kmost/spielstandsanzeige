package de.kmost.scoreboard;

/**
 * Einstiegspunkt für den Start per {@code java -jar}: Der Java-Launcher
 * verweigert den Start einer Application-Subklasse, wenn JavaFX nur auf dem
 * Classpath liegt — diese Klasse erbt deshalb nicht von Application.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ScoreboardApp.main(args);
    }
}
