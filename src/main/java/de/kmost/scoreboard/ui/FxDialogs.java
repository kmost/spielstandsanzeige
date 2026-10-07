package de.kmost.scoreboard.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

/** Die echten JavaFX-Dialoge zu {@link Dialogs}. */
public final class FxDialogs implements Dialogs {

    @Override
    public boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @Override
    public void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @Override
    public Optional<String> askText(String title, String header, String label, String initialText) {
        TextInputDialog dialog = new TextInputDialog(initialText);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(label);
        return dialog.showAndWait();
    }

    @Override
    public Optional<Integer> choose(String title, String message, List<String> options) {
        List<ButtonType> buttons = new ArrayList<>();
        for (String option : options) {
            buttons.add(new ButtonType(option));
        }
        buttons.add(ButtonType.CANCEL);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message,
                buttons.toArray(ButtonType[]::new));
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        return dialog.showAndWait().map(buttons::indexOf).filter(index -> index < options.size());
    }

    @Override
    public boolean askResume(String title, String header, String message) {
        ButtonType resume = new ButtonType("Fortsetzen", ButtonBar.ButtonData.OK_DONE);
        ButtonType discard = new ButtonType("Verwerfen", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, message, resume, discard);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        return dialog.showAndWait().orElse(discard) == resume;
    }
}
