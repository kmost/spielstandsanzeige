package de.kmost.scoreboard.ui;

import java.util.List;
import java.util.Optional;

/**
 * Modale Rückfragen und Hinweise der Oberfläche. Die Fenster fragen über diese Schnittstelle,
 * statt selbst {@code Alert}s zu öffnen — dadurch lässt sich die Entscheidungslogik hinter den
 * Dialogen in Tests mit vorgegebenen Antworten prüfen ({@link FxDialogs} ist die echte Umsetzung).
 */
public interface Dialogs {

    /** Ja/Nein-Rückfrage; {@code true} bei Bestätigung. */
    boolean confirm(String message);

    /** Hinweis mit „OK“. */
    void warn(String message);

    /** Texteingabe; leer, wenn abgebrochen. */
    Optional<String> askText(String title, String header, String label, String initialText);

    /** Auswahl aus Optionen mit Abbrechen; liefert den Index der gewählten Option, sonst leer. */
    Optional<Integer> choose(String title, String message, List<String> options);

    /** Rückfrage zum Fortsetzen eines gesicherten Spiels; {@code true} = fortsetzen, {@code false} = verwerfen. */
    boolean askResume(String title, String header, String message);
}
