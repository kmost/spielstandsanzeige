package de.kmost.scoreboard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShootoutTest {

    /** Wirft ein komplettes Paar: erst das Team am Wurf, dann das andere. */
    private static void throwPair(Shootout shootout, boolean firstGoal, boolean secondGoal) {
        shootout.record(firstGoal);
        shootout.record(secondGoal);
    }

    @Test
    void alternatesStartingWithChosenTeam() {
        Shootout shootout = new Shootout(TeamSide.GUEST);
        assertEquals(TeamSide.GUEST, shootout.nextThrowerProperty().get());
        shootout.record(true);
        assertEquals(TeamSide.HOME, shootout.nextThrowerProperty().get());
        shootout.record(false);
        assertEquals(TeamSide.GUEST, shootout.nextThrowerProperty().get());
        assertEquals(1, shootout.goalCount(TeamSide.GUEST));
        assertEquals(0, shootout.goalCount(TeamSide.HOME));
    }

    @Test
    void endsEarlyWhenLeadIsUncatchable() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        throwPair(shootout, true, false);
        throwPair(shootout, true, false);
        throwPair(shootout, true, false);
        // 3:0 nach drei Paaren — Gast kann mit zwei Restwürfen nicht mehr ausgleichen
        assertEquals(TeamSide.HOME, shootout.winnerProperty().get());
        assertNull(shootout.nextThrowerProperty().get());
        // weitere Würfe werden ignoriert
        shootout.record(true);
        assertEquals(3, shootout.attemptCount(TeamSide.HOME));
    }

    @Test
    void decisionAfterFifthPairWithoutSuddenDeath() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        for (int i = 0; i < 4; i++) {
            throwPair(shootout, true, true);
        }
        throwPair(shootout, true, false);
        assertEquals(TeamSide.HOME, shootout.winnerProperty().get());
        assertFalse(shootout.suddenDeath());
    }

    @Test
    void tieAfterFiveGoesToSuddenDeathWithOtherTeamStarting() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        for (int i = 0; i < 5; i++) {
            throwPair(shootout, true, true);
        }
        assertNull(shootout.winnerProperty().get());
        assertTrue(shootout.suddenDeath());
        // im Sudden Death beginnt die andere Mannschaft
        assertEquals(TeamSide.GUEST, shootout.nextThrowerProperty().get());
        // erstes Sudden-Death-Paar unentschieden → weiter, jetzt beginnt wieder Heim
        throwPair(shootout, false, false);
        assertNull(shootout.winnerProperty().get());
        assertEquals(TeamSide.HOME, shootout.nextThrowerProperty().get());
        // zweites Paar: Heim trifft, Gast nicht → Sieger Heim
        throwPair(shootout, true, false);
        assertEquals(TeamSide.HOME, shootout.winnerProperty().get());
    }

    @Test
    void suddenDeathNotDecidedBeforePairIsComplete() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        for (int i = 0; i < 5; i++) {
            throwPair(shootout, true, true);
        }
        shootout.record(true); // Gast (Sudden-Death-Starter) trifft
        // das Paar ist noch nicht komplett — Heim hat den Ausgleichswurf
        assertNull(shootout.winnerProperty().get());
        assertEquals(TeamSide.HOME, shootout.nextThrowerProperty().get());
        shootout.record(false);
        assertEquals(TeamSide.GUEST, shootout.winnerProperty().get());
    }

    @Test
    void undoRevokesAttemptAndWinner() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        throwPair(shootout, true, false);
        throwPair(shootout, true, false);
        throwPair(shootout, true, false);
        assertEquals(TeamSide.HOME, shootout.winnerProperty().get());
        Shootout.Attempt removed = shootout.undoLast();
        assertEquals(TeamSide.GUEST, removed.side());
        assertFalse(removed.goal());
        assertNull(shootout.winnerProperty().get());
        assertEquals(TeamSide.GUEST, shootout.nextThrowerProperty().get());
    }

    @Test
    void symbolsShowGoalsAndMisses() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        throwPair(shootout, true, false);
        throwPair(shootout, false, true);
        assertEquals("● ○", shootout.symbols(TeamSide.HOME));
        assertEquals("○ ●", shootout.symbols(TeamSide.GUEST));
    }

    @Test
    void cappedSymbolsDropOldestAttemptsFirst() {
        Shootout shootout = new Shootout(TeamSide.HOME);
        throwPair(shootout, true, true);
        throwPair(shootout, true, true);
        throwPair(shootout, false, true);
        assertEquals("● ● ○", shootout.symbols(TeamSide.HOME, 5));
        assertEquals("… ● ○", shootout.symbols(TeamSide.HOME, 2));
    }

    @Test
    void attemptListenersSurviveGarbageCollection() throws InterruptedException {
        Shootout shootout = new Shootout(TeamSide.HOME);
        java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
        shootout.attempts().addListener(
                (javafx.collections.ListChangeListener<Shootout.Attempt>) change -> changes.incrementAndGet());
        shootout.record(true);
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(20);
        }
        shootout.record(false);
        assertEquals(2, changes.get());
    }
}
