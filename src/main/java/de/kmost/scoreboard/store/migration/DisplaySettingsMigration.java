package de.kmost.scoreboard.store.migration;

import java.util.Properties;

/**
 * Schemaversionen von display.properties und ihre Migrationen. Jede Stufe hebt die Datei um
 * eine Version an; {@link #upgrade} führt alle nötigen Stufen nacheinander aus.
 *
 * <ul>
 *   <li>Version 0 (kein {@code schema}-Eintrag): Zeit vor der Versionierung, Banner in einem
 *       der drei Formate (siehe {@link BannerFormatMigration}).</li>
 *   <li>Version 1: Banner im Raster-Format.</li>
 * </ul>
 *
 * Eine neue Version einführen: {@link #CURRENT_SCHEMA} erhöhen, eine Stufe in {@link #upgrade}
 * ergänzen, einen Test mit einer echten Datei der Vorversion schreiben.
 */
public final class DisplaySettingsMigration {

    public static final int CURRENT_SCHEMA = 1;

    private DisplaySettingsMigration() {
    }

    /**
     * Hebt die Einstellungen von {@code fromSchema} auf {@link #CURRENT_SCHEMA}. Die Eingabe
     * bleibt unverändert; das Ergebnis trägt noch keinen {@code schema}-Eintrag (den setzt der
     * Store beim Schreiben).
     */
    public static Properties upgrade(Properties props, int fromSchema) {
        if (fromSchema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Schema " + fromSchema + " ist neuer als " + CURRENT_SCHEMA);
        }
        Properties result = props;
        if (fromSchema < 1) {
            result = BannerFormatMigration.migrate(result);
        }
        return result;
    }
}
