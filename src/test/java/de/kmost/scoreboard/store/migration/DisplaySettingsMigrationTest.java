package de.kmost.scoreboard.store.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class DisplaySettingsMigrationTest {

    @Test
    void version0IsUpgradedToGridBanners() {
        Properties legacy = new Properties();
        legacy.setProperty("headerText", "Willkommen");

        Properties upgraded = DisplaySettingsMigration.upgrade(legacy, 0);

        assertEquals("Willkommen", upgraded.getProperty("header.text.0"));
    }

    @Test
    void currentVersionPassesThroughUntouched() {
        Properties current = new Properties();
        current.setProperty("header.text.0", "Hallo");

        assertSame(current, DisplaySettingsMigration.upgrade(current, DisplaySettingsMigration.CURRENT_SCHEMA));
    }

    @Test
    void newerVersionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DisplaySettingsMigration.upgrade(new Properties(), DisplaySettingsMigration.CURRENT_SCHEMA + 1));
    }
}
