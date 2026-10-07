package de.kmost.scoreboard.store.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Die drei Banner-Formate aus display.properties werden mit echten Altdateien ins Raster überführt. */
class BannerFormatMigrationTest {

    private static Properties props(String content) throws IOException {
        Properties props = new Properties();
        props.load(new StringReader(content));
        return props;
    }

    @Test
    void gridFormatStaysUnchanged() throws IOException {
        Properties grid = props("""
                header.text.0=Willkommen
                header.text.2=TSV
                header.image.1=header-2.png
                footer.text.0=Danke
                clock=#ffffff
                """);

        Properties result = BannerFormatMigration.migrate(grid);

        assertEquals(grid, result);
    }

    @Test
    void oldestFormatBecomesFirstTextSlot() throws IOException {
        Properties result = BannerFormatMigration.migrate(props("headerText=Willkommen\nfooterText=TSV Tarp\n"));

        assertEquals("Willkommen", result.getProperty("header.text.0"));
        assertEquals("TSV Tarp", result.getProperty("footer.text.0"));
        assertNull(result.getProperty("headerText"));
        assertNull(result.getProperty("footerText"));
    }

    @Test
    void fileWithoutAnyBannerKeysGetsEmptyFirstSlot() throws IOException {
        Properties result = BannerFormatMigration.migrate(props("clock=#ffffff\n"));

        assertEquals("", result.getProperty("header.text.0"));
        assertEquals("", result.getProperty("footer.text.0"));
        assertEquals("#ffffff", result.getProperty("clock"));
    }

    /** Variante → (Text 1, Bild 1, Text 2, Bild 2, Text 3) im Raster: Text 1, Bild 1, Text 2 … */
    @ParameterizedTest
    @CsvSource({
            // variante,  t0, i0, t1, i1, t2
            "BILD_TEXT,      '',  B1, A,  '', ''",
            "TEXT_BILD,      A,   B1, '', '', ''",
            "BILD_TEXT_BILD, '',  B1, A,  B2, ''",
            "TEXT_BILD_TEXT, A,   B1, C,  '', ''",
            "NUR_BILD,       '',  B1, '', '', ''",
            "BILDER,         '',  B1, '', B2, ''",
            "UNBEKANNT,      A,   '', '', '', ''",
    })
    void variantFormatKeepsDisplayOrder(String variant, String t0, String i0, String t1, String i1, String t2)
            throws IOException {
        Properties result = BannerFormatMigration.migrate(props(
                "header.variant=" + variant + "\n"
                        + "header.text1=A\nheader.text2=C\n"
                        + "header.image1=B1\nheader.image2=B2\n"));

        assertEquals(t0, result.getProperty("header.text.0"), variant + " Text 1");
        assertEquals(i0, result.getProperty("header.image.0"), variant + " Bild 1");
        assertEquals(t1, result.getProperty("header.text.1"), variant + " Text 2");
        assertEquals(i1, result.getProperty("header.image.1"), variant + " Bild 2");
        assertEquals(t2, result.getProperty("header.text.2"), variant + " Text 3");
    }

    @Test
    void variantKeysAreRemovedAndGridIsComplete() throws IOException {
        Properties result = BannerFormatMigration.migrate(props(
                "header.variant=BILD_TEXT\nheader.text1=TSV Tarp\nheader.image1=header-1.png\n"));

        for (String legacy : new String[] {"header.variant", "header.text1", "header.text2",
                "header.image1", "header.image2"}) {
            assertNull(result.getProperty(legacy), legacy);
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(true, result.containsKey("header.text." + i), "text " + i);
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(true, result.containsKey("header.image." + i), "image " + i);
        }
        assertEquals("TSV Tarp", result.getProperty("header.text.1"));
        assertEquals("header-1.png", result.getProperty("header.image.0"));
    }

    @Test
    void headerAndFooterCanBeInDifferentFormats() throws IOException {
        Properties result = BannerFormatMigration.migrate(props("""
                header.text.0=Neu
                header.text.1=Mitte
                footer.variant=TEXT_BILD
                footer.text1=Alt
                """));

        assertEquals("Neu", result.getProperty("header.text.0"));
        assertEquals("Mitte", result.getProperty("header.text.1"));
        assertEquals("Alt", result.getProperty("footer.text.0"));
    }

    @Test
    void migrationDoesNotModifyItsInputAndIsIdempotent() throws IOException {
        Properties legacy = props("headerText=Willkommen\nfooter.variant=BILD_TEXT\nfooter.text1=X\n");
        Properties copy = new Properties();
        copy.putAll(legacy);

        Properties once = BannerFormatMigration.migrate(legacy);
        Properties twice = BannerFormatMigration.migrate(once);

        assertEquals(copy, legacy);
        assertEquals(once, twice);
    }
}
