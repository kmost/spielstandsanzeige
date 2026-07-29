package de.kmost.scoreboard;

import javafx.application.Application;

/** Launcher, damit die App auch ohne javafx-maven-plugin startbar ist. */
public class Main {

    public static void main(String[] args) {
        Application.launch(ScoreboardApp.class, args);
    }
}
