package de.kmost.scoreboard.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Dialoge mit vorgegebenen Antworten; merkt sich, was gefragt wurde. */
public final class FakeDialogs implements Dialogs {

    public boolean confirmAnswer;
    public Optional<String> textAnswer = Optional.empty();
    public Optional<Integer> choiceAnswer = Optional.empty();
    public boolean resumeAnswer;

    public final List<String> confirms = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> textPrompts = new ArrayList<>();
    public final List<List<String>> choices = new ArrayList<>();
    public final List<String> resumes = new ArrayList<>();

    @Override
    public boolean confirm(String message) {
        confirms.add(message);
        return confirmAnswer;
    }

    @Override
    public void warn(String message) {
        warnings.add(message);
    }

    @Override
    public Optional<String> askText(String title, String header, String label, String initialText) {
        textPrompts.add(initialText);
        return textAnswer;
    }

    @Override
    public Optional<Integer> choose(String title, String message, List<String> options) {
        choices.add(options);
        return choiceAnswer;
    }

    @Override
    public boolean askResume(String title, String header, String message) {
        resumes.add(message);
        return resumeAnswer;
    }
}
