package de.kmost.scoreboard.ui.display;

import java.io.File;
import java.util.Locale;

import de.kmost.scoreboard.ui.BannerConfig;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;

/**
 * Header- oder Footer-Zeile der Publikumsanzeige: eine mittige Zeile aus Texten
 * und Bildern in Rasterreihenfolge (Text 1, Bild 1, Text 2, …), deren Höhe als
 * fester Anteil der Fensterhöhe reserviert ist ({@link #SHARE} mal
 * Theme-Größenfaktor) und deren Schrift direkt an dieser Zonenhöhe hängt — das
 * äußere Raster hängt dadurch nur an der Konfiguration, nicht am Inhalt. Ohne
 * anzeigbaren Inhalt ist der Banner ausgeblendet (unmanaged) und gibt seinen
 * Anteil an den Spielstand ab.
 */
final class Banner {

    /** Höhenanteil eines sichtbaren Banners bei Standardgröße (Raster 10:80:10). */
    static final double SHARE = 0.10;

    /** Schriftgröße als Anteil der Bannerhöhe (0.45 · 10 % = 4,5 % der Fensterhöhe). */
    private static final double FONT_SHARE = 0.45;

    private final Scene scene;
    private final HBox node = new HBox();
    // tatsächlicher Höhenanteil: SHARE mal Theme-Größenfaktor
    private final DoubleProperty share = new SimpleDoubleProperty(SHARE);
    private final ObjectProperty<BannerConfig> config =
            new SimpleObjectProperty<>(BannerConfig.empty());
    // verkleinert die ganze Zeile, wenn der Inhalt breiter ist als das Fenster
    private final Scale fitScale = new Scale(1, 1);
    private final InvalidationListener refit = obs -> updateFit();

    Banner(Scene scene, String styleClass) {
        this.scene = scene;
        node.setAlignment(Pos.CENTER);
        node.spacingProperty().bind(scene.widthProperty().multiply(0.015));
        node.setPadding(new Insets(2, 15, 2, 15));
        // feste, an die Fensterhöhe gebundene Höhe: die bevorzugte Höhe der Kinder
        // hinge sonst von Schriftart und em-Schriftgröße ab
        node.prefHeightProperty().bind(scene.heightProperty().multiply(share));
        node.setMinHeight(Region.USE_PREF_SIZE);
        node.setMaxHeight(Region.USE_PREF_SIZE);
        // Banner-Schrift direkt an die Zonenhöhe gekoppelt: wächst der Anteil,
        // wächst die Schrift im gleichen Verhältnis (unabhängig von der
        // Basis-em-Größe des Spielstands, die bei größeren Bannern ja kleiner wird)
        node.styleProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.US, "-fx-font-size: %.1fpx; ",
                        FONT_SHARE * scene.getHeight() * share.get()),
                scene.heightProperty(), share));
        // Passt der Inhalt nicht in die Fensterbreite, wird die komplette Zeile
        // (Texte und Bilder gemeinsam) proportional verkleinert — es wird nie
        // etwas mit „…“ abgeschnitten. Pivot links, weil die HBox überbreiten
        // Inhalt am linken Rand beginnen lässt: die verkleinerte Zeile füllt
        // dann genau die Fensterbreite
        fitScale.pivotYProperty().bind(node.heightProperty().divide(2));
        node.getTransforms().add(fitScale);
        scene.widthProperty().addListener(refit);
        scene.heightProperty().addListener(refit);
        share.addListener(refit);
        config.addListener((obs, oldConfig, newConfig) -> rebuild(newConfig, styleClass));
        rebuild(config.get(), styleClass);
    }

    /** Skalierungsfaktor neu berechnen: natürliche Inhaltsbreite gegen Fensterbreite. */
    private void updateFit() {
        double natural = node.prefWidth(-1);
        double available = scene.getWidth();
        double factor = natural > available && available > 0 ? available / natural : 1;
        fitScale.setX(factor);
        fitScale.setY(factor);
    }

    Node node() {
        return node;
    }

    /** Inhalt des Banners; ohne anzeigbaren Inhalt ausgeblendet. */
    ObjectProperty<BannerConfig> configProperty() {
        return config;
    }

    /** Theme-Größenfaktor: skaliert Zonenhöhe und Schrift gemeinsam. */
    void setScale(double factor) {
        share.set(SHARE * factor);
    }

    /** Tatsächlicher Höhenanteil an der Fensterhöhe (SHARE mal Faktor). */
    ReadOnlyDoubleProperty shareProperty() {
        return share;
    }

    /** true, solange der Banner sichtbar ist und seinen Höhenanteil belegt. */
    ObservableBooleanValue occupiesSpace() {
        return node.managedProperty();
    }

    /** Slots in Rasterreihenfolge (Text 1, Bild 1, Text 2, …); leere überspringen. */
    private void rebuild(BannerConfig config, String styleClass) {
        node.getChildren().clear();
        for (int i = 0; i < BannerConfig.TEXT_SLOTS; i++) {
            Label text = buildText(config.text(i), styleClass);
            if (text != null) {
                node.getChildren().add(text);
            }
            if (i < BannerConfig.IMAGE_SLOTS) {
                Node image = buildImage(config.image(i));
                if (image != null) {
                    node.getChildren().add(image);
                }
            }
        }
        boolean visible = !node.getChildren().isEmpty();
        node.setVisible(visible);
        node.setManaged(visible);
        // Kinder melden Größenänderungen (Schriftart, CSS-Pass, Bild geladen) an die
        // Einpassung; die Listener alter Kinder verschwinden mit den Kindern selbst
        for (Node child : node.getChildren()) {
            child.layoutBoundsProperty().addListener(refit);
        }
        updateFit();
    }

    private static Label buildText(String text, String styleClass) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setTextAlignment(TextAlignment.CENTER);
        // nie mit „…“ kürzen: die Zeile wird stattdessen als Ganzes eingepasst
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    /** Banner-Bild, auf die Bannerhöhe skaliert; null, wenn nicht ladbar. */
    private Node buildImage(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        Image image = new Image(file.toURI().toString(), 600, 600, true, true);
        if (image.isError()) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.fitHeightProperty().bind(node.heightProperty().subtract(6));
        return view;
    }
}
