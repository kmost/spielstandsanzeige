package de.kmost.scoreboard.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inhalt eines Anzeige-Banners (Header oder Footer): ein festes Raster aus abwechselnd
 * Text- und Bild-Slots — Text 1, Bild 1, Text 2, …, Bild 5, Text 6. Leere Slots werden
 * beim Rendern übersprungen, die Nachbarn rücken zusammen; ein Banner ganz ohne Inhalt
 * wird ausgeblendet.
 */
public record BannerConfig(List<String> texts, List<File> images) {

    public static final int IMAGE_SLOTS = 5;
    public static final int TEXT_SLOTS = IMAGE_SLOTS + 1;

    public BannerConfig {
        texts = fixedSize(texts, TEXT_SLOTS, "");
        images = fixedSize(images, IMAGE_SLOTS, null);
    }

    /** Auf feste Slot-Anzahl bringen; fehlende oder null-Einträge werden aufgefüllt. */
    private static <T> List<T> fixedSize(List<T> source, int size, T filler) {
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            T value = source != null && i < source.size() ? source.get(i) : null;
            list.add(value == null ? filler : value);
        }
        return Collections.unmodifiableList(list);
    }

    public static BannerConfig empty() {
        return new BannerConfig(List.of(), List.of());
    }

    public String text(int index) {
        return texts.get(index);
    }

    /** Bild des Slots; null, wenn keines gesetzt ist oder die Datei fehlt. */
    public File image(int index) {
        File file = images.get(index);
        return file != null && file.isFile() ? file : null;
    }

    /** true, wenn kein Slot anzeigbaren Inhalt hat. */
    public boolean isBlank() {
        return texts.stream().allMatch(String::isBlank) && !hasImages();
    }

    /** true, wenn mindestens ein vorhandenes Bild gesetzt ist. */
    public boolean hasImages() {
        return images.stream().anyMatch(file -> file != null && file.isFile());
    }
}
