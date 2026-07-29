package de.kmost.scoreboard.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReloadsTeamNames() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveTeam("TSV Tarp");
        repository.saveTeam("TSV Wanderup");

        TeamRepository reloaded = new TeamRepository(tempDir);
        assertEquals(List.of("TSV Tarp", "TSV Wanderup"), reloaded.teamNames());
    }

    @Test
    void sortsNamesCaseInsensitively() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveTeam("Alster SV");
        repository.saveTeam("Berliner TSV");
        repository.saveTeam("Ahrensburg");
        assertEquals(List.of("Ahrensburg", "Alster SV", "Berliner TSV"), repository.teamNames());
    }

    @Test
    void ignoresBlankAndNullNames() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveTeam("   ");
        repository.saveTeam(null);
        assertTrue(repository.teamNames().isEmpty());
    }

    @Test
    void stripsWhitespaceAndIgnoresDuplicates() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveTeam("  TSV Tarp  ");
        repository.saveTeam("TSV Tarp");
        assertEquals(List.of("TSV Tarp"), repository.teamNames());
    }

    @Test
    void missingFileYieldsEmptyList() {
        TeamRepository repository = new TeamRepository(tempDir.resolve("gibtsnicht"));
        assertTrue(repository.teamNames().isEmpty());
    }

    @Test
    void savesAndReloadsDefaultHomeTeam() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveDefaultHomeTeam("  HSG Tarp-Wanderup  ");

        TeamRepository reloaded = new TeamRepository(tempDir);
        assertEquals("HSG Tarp-Wanderup", reloaded.defaultHomeTeam());
    }

    @Test
    void defaultHomeTeamIsNotListedAsTeam() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveDefaultHomeTeam("HSG Tarp-Wanderup");
        assertTrue(repository.teamNames().isEmpty());
    }

    @Test
    void missingDefaultHomeTeamIsEmpty() {
        assertEquals("", new TeamRepository(tempDir).defaultHomeTeam());
    }

    @Test
    void clearsDefaultHomeTeam() {
        TeamRepository repository = new TeamRepository(tempDir);
        repository.saveDefaultHomeTeam("HSG Tarp-Wanderup");
        repository.saveDefaultHomeTeam(null);
        assertEquals("", new TeamRepository(tempDir).defaultHomeTeam());
    }
}
