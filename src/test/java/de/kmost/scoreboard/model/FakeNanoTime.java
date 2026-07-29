package de.kmost.scoreboard.model;

import java.util.function.LongSupplier;

/** Kontrollierbare Zeitquelle für Tests. */
class FakeNanoTime implements LongSupplier {

    private long nanos;

    void advanceMillis(long millis) {
        nanos += millis * 1_000_000;
    }

    @Override
    public long getAsLong() {
        return nanos;
    }
}
