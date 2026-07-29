package de.kmost.scoreboard.ui.control;

import java.util.Locale;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Popup;

/**
 * Teamnamen-Eingabe: ein echtes Freitextfeld plus Vorschlags-Dropdown mit den
 * bekannten Teams. Tippen filtert nur die Vorschläge und bleibt sonst
 * unangetastet — ein Name wird ausschließlich auf ausdrückliche Auswahl
 * übernommen: per Klick ins Dropdown oder per Pfeil-runter/-hoch. Kein
 * Autocomplete, das beim Tippen dazwischenfunkt.
 */
final class TeamNamePicker {

    private final TextField field = new TextField();
    private final FilteredList<String> suggestions;
    private final ListView<String> listView;
    private final Popup popup = new Popup();
    private final HBox node;
    private boolean adoptingSelection; // Übernahme aus dem Dropdown filtert nicht neu

    TeamNamePicker(ObservableList<String> teams, String prompt) {
        field.setPromptText(prompt);
        suggestions = new FilteredList<>(teams, name -> true);
        listView = new ListView<>(suggestions);
        listView.setFocusTraversable(false);
        popup.getContent().add(listView);
        popup.setAutoHide(true);

        // Tippen filtert nur die Vorschläge — der eingegebene Text bleibt frei
        field.textProperty().addListener((obs, oldText, text) -> {
            if (adoptingSelection) {
                return;
            }
            String query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
            suggestions.setPredicate(name -> name.toLowerCase(Locale.ROOT).contains(query));
            listView.getSelectionModel().clearSelection();
            if (field.isFocused()) {
                if (suggestions.isEmpty()) {
                    popup.hide();
                } else {
                    showPopup();
                }
            }
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeys);
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                popup.hide();
            }
        });

        // Klick ins Dropdown übernimmt den Namen
        listView.setOnMouseClicked(e -> {
            String picked = listView.getSelectionModel().getSelectedItem();
            if (picked != null) {
                adopt(picked);
                popup.hide();
            }
        });

        // ▾ öffnet die volle Liste (ein Klick daneben bzw. erneut schließt sie)
        Button arrow = new Button("▾");
        arrow.setFocusTraversable(false);
        arrow.setOnAction(e -> {
            suggestions.setPredicate(name -> true);
            listView.getSelectionModel().clearSelection();
            field.requestFocus();
            showPopup();
        });

        HBox.setHgrow(field, Priority.ALWAYS);
        node = new HBox(4, field, arrow);
    }

    Node node() {
        return node;
    }

    /** Der eingetippte bzw. aus dem Dropdown übernommene Name. */
    String text() {
        return field.getText();
    }

    /** Vorbelegung (z. B. Standard-Heimteam); filtert die Vorschläge nicht. */
    void setText(String name) {
        adopt(name == null ? "" : name);
    }

    private void handleKeys(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN -> {
                moveSelection(1);
                event.consume();
            }
            case UP -> {
                moveSelection(-1);
                event.consume();
            }
            case ESCAPE -> popup.hide();
            case ENTER -> {
                if (popup.isShowing()) {
                    // Auswahl steht schon im Feld: nur schließen, nicht
                    // gleichzeitig den „Spiel anlegen“-Default-Button auslösen
                    popup.hide();
                    event.consume();
                }
            }
            default -> {
            }
        }
    }

    /** Pfeil-Auswahl: navigiert im Dropdown und übernimmt den gewählten Namen. */
    private void moveSelection(int delta) {
        if (suggestions.isEmpty()) {
            return;
        }
        showPopup();
        int size = suggestions.size();
        int index = listView.getSelectionModel().getSelectedIndex();
        int next = index < 0
                ? (delta > 0 ? 0 : size - 1)
                : Math.floorMod(index + delta, size);
        listView.getSelectionModel().select(next);
        listView.scrollTo(next);
        adopt(suggestions.get(next));
    }

    private void adopt(String name) {
        adoptingSelection = true;
        field.setText(name);
        field.positionCaret(name.length());
        adoptingSelection = false;
    }

    private void showPopup() {
        listView.setPrefWidth(Math.max(field.getWidth(), 220));
        listView.setPrefHeight(Math.min(suggestions.size(), 8) * 26 + 4);
        if (!popup.isShowing()) {
            Bounds bounds = field.localToScreen(field.getBoundsInLocal());
            if (bounds != null) {
                popup.show(field, bounds.getMinX(), bounds.getMaxY());
            }
        }
    }
}
