package dev.ktai.lostrego.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureTimestampTest {

    @Test
    void keepsInRangeNanosUnchanged() {
        var ts = new CaptureTimestamp(42, 123_456_789);
        assertEquals(42, ts.seconds());
        assertEquals(123_456_789, ts.nanosOfSecond());
    }

    @Test
    void normalizesNanosOverflowIntoSeconds() {
        var ts = new CaptureTimestamp(1, 1_500_000_000);
        assertEquals(2, ts.seconds());
        assertEquals(500_000_000, ts.nanosOfSecond());
    }

    @Test
    void normalizesNegativeNanosByBorrowingFromSeconds() {
        var ts = new CaptureTimestamp(10, -500_000_000);
        assertEquals(9, ts.seconds());
        assertEquals(500_000_000, ts.nanosOfSecond());
    }

    @Test
    void convertsToInstant() {
        var ts = new CaptureTimestamp(1_700_000_000, 123_456_789);
        assertEquals(Instant.ofEpochSecond(1_700_000_000, 123_456_789), ts.toInstant());
    }
}
