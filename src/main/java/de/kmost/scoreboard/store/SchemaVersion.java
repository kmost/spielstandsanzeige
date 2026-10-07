package de.kmost.scoreboard.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import de.kmost.scoreboard.diagnostics.ProblemReporter;

/**
 * Schemaversion der Properties-Dateien unter ~/.spielstandsanzeige/. Jede Datei trägt
 * {@code schema=<n>}; fehlt der Eintrag, gilt sie als {@link #LEGACY} (vor Einführung der
 * Version geschrieben). Beim Schreiben steht immer die aktuelle Version drin.
 *
 * <p>Umgang mit Dateien einer <em>neueren</em> Version (z. B. nach einem Downgrade): Die App
 * kennt deren Format nicht und überschreibt sie nie. Aktive Einstellungen werden mit
 * {@link #setAsideNewer} unter {@code <datei>.schema<n>} beiseitegelegt, die Meldestelle
 * informiert, die App startet mit Standardwerten. Einzelne Themes werden ignoriert und
 * bleiben liegen.
 */
final class SchemaVersion {

    static final String KEY = "schema";

    /** Datei ohne Versionseintrag. */
    static final int LEGACY = 0;

    private SchemaVersion() {
    }

    static int of(Properties props) {
        return of(props, KEY);
    }

    /** Version unter dem Schlüssel; fehlend oder unlesbar gilt als {@link #LEGACY}. */
    static int of(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null) {
            return LEGACY;
        }
        try {
            return Math.max(LEGACY, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return LEGACY;
        }
    }

    static void stamp(Properties props, int version) {
        stamp(props, KEY, version);
    }

    static void stamp(Properties props, String key, int version) {
        props.setProperty(key, String.valueOf(version));
    }

    /**
     * Legt eine Datei einer neueren Schemaversion beiseite ({@code <datei>.schema<n>}),
     * damit sie nicht überschrieben wird, und meldet es dem Nutzer.
     */
    static void setAsideNewer(Path file, int schema, ProblemReporter reporter) {
        String name = file.getFileName().toString();
        Path target = file.resolveSibling(name + ".schema" + schema);
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            reporter.report("Datei „" + name + "“ stammt von einer neueren Version der "
                    + "Spielstandsanzeige (Schema " + schema + ") und wurde als „"
                    + target.getFileName() + "“ beiseitegelegt; es gelten Standardwerte", null);
        } catch (IOException e) {
            reporter.report("Datei „" + name + "“ stammt von einer neueren Version der "
                    + "Spielstandsanzeige (Schema " + schema + ") und konnte nicht beiseitegelegt "
                    + "werden; es gelten Standardwerte", e);
        }
    }
}
