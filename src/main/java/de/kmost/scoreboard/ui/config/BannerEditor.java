package de.kmost.scoreboard.ui.config;

import java.io.File;
import java.util.Arrays;

import de.kmost.scoreboard.store.LogoDownloader;
import de.kmost.scoreboard.store.ThemeRepository;
import de.kmost.scoreboard.ui.BannerConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Editor für einen Banner (Header oder Footer): das feste Slot-Raster aus sechs
 * Texten und fünf Bildern. Eine Zeile „Text n | Bild n“ entspricht der
 * Anzeige-Reihenfolge von links nach rechts; leere Slots rücken zusammen.
 * Jede Änderung meldet sich über den onChange-Callback (anwenden + speichern).
 */
final class BannerEditor {

    private final String slotPrefix;
    private final ThemeRepository themeRepository;
    private final Window owner;
    private final Runnable onChange;
    private final TextField[] textFields = new TextField[BannerConfig.TEXT_SLOTS];
    private final File[] images = new File[BannerConfig.IMAGE_SLOTS];
    private final Label[] imageLabels = new Label[BannerConfig.IMAGE_SLOTS];

    BannerEditor(String slotPrefix, BannerConfig initial, ThemeRepository themeRepository,
            Window owner, Runnable onChange) {
        this.slotPrefix = slotPrefix;
        this.themeRepository = themeRepository;
        this.owner = owner;
        this.onChange = onChange;
        for (int i = 0; i < BannerConfig.TEXT_SLOTS; i++) {
            textFields[i] = new TextField(initial.text(i));
            textFields[i].setPromptText("Text " + (i + 1));
            textFields[i].setPrefColumnCount(12);
        }
        for (int i = 0; i < BannerConfig.IMAGE_SLOTS; i++) {
            images[i] = initial.image(i);
            imageLabels[i] = new Label();
            imageLabels[i].setMaxWidth(140);
        }
        refreshImageLabels();

        // Listener erst nach der Initialisierung, damit der Aufbau nichts speichert
        for (TextField field : textFields) {
            field.textProperty().addListener((obs, oldText, text) -> onChange.run());
        }
    }

    Node build() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        for (int i = 0; i < BannerConfig.IMAGE_SLOTS; i++) {
            grid.addRow(i, new Label("Text " + (i + 1) + ":"), textFields[i],
                    new Label("Bild " + (i + 1) + ":"), buildImageChooser(i));
        }
        grid.addRow(BannerConfig.IMAGE_SLOTS,
                new Label("Text " + BannerConfig.TEXT_SLOTS + ":"),
                textFields[BannerConfig.TEXT_SLOTS - 1]);
        for (TextField field : textFields) {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
        return grid;
    }

    BannerConfig config() {
        return new BannerConfig(
                Arrays.stream(textFields).map(TextField::getText).toList(),
                Arrays.asList(images));
    }

    private Node buildImageChooser(int index) {
        Button fileButton = new Button("📁 Datei…");
        fileButton.setOnAction(e -> chooseFile(index));
        Button urlButton = new Button("🌐 URL…");
        urlButton.setOnAction(e -> chooseUrl(index));
        Button clearButton = new Button("✕");
        clearButton.setOnAction(e -> setImage(index, null));
        HBox box = new HBox(6, fileButton, urlButton, clearButton, imageLabels[index]);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void chooseFile(int index) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Bild " + (index + 1) + " wählen");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Bilder", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(owner);
        if (file != null) {
            storeImage(index, file);
        }
    }

    private void chooseUrl(int index) {
        TextInputDialog dialog = new TextInputDialog("https://");
        dialog.setTitle("Bild aus dem Internet");
        dialog.setHeaderText("Bild-URL für " + ("header".equals(slotPrefix) ? "Header" : "Footer"));
        dialog.setContentText("URL:");
        dialog.showAndWait().ifPresent(url -> {
            if (url.isBlank() || url.strip().equals("https://")) {
                return;
            }
            try {
                storeImage(index, LogoDownloader.download(url));
            } catch (Exception ex) {
                warn("Bild konnte nicht geladen werden: " + ex.getMessage());
            }
        });
    }

    /** Kopiert das Bild in die Datenbank und übernimmt es in den Slot. */
    private void storeImage(int index, File source) {
        File stored = themeRepository.storeBannerImage(slotPrefix + "-" + (index + 1), source);
        if (stored == null) {
            warn("Bild konnte nicht gespeichert werden.");
            return;
        }
        setImage(index, stored);
    }

    private void setImage(int index, File file) {
        images[index] = file;
        refreshImageLabels();
        onChange.run();
    }

    private void refreshImageLabels() {
        for (int i = 0; i < BannerConfig.IMAGE_SLOTS; i++) {
            imageLabels[i].setText(images[i] == null ? "kein Bild" : images[i].getName());
        }
    }

    private void warn(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
