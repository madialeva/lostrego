package dev.ktai.lostrego.core;

import java.time.Instant;

/**
 * A packet capture timestamp represented as an epoch second plus a nanosecond
 * fraction of that second.
 *
 * <p>The two-field representation maps directly onto the timestamp sources of
 * the native backends: {@code struct timeval} (libpcap/npcap, microsecond
 * precision) and the nanosecond field of a PDPK/DPDK mbuf. It deliberately does
 * not imply wall-clock semantics: the clock a backend timestamps with may not be
 * aligned to the Unix epoch.
 */
public record CaptureTimestamp(long seconds, int nanosOfSecond) {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public CaptureTimestamp {
        if (nanosOfSecond < 0 || nanosOfSecond >= NANOS_PER_SECOND) {
            seconds += Math.floorDiv(nanosOfSecond, NANOS_PER_SECOND);
            nanosOfSecond = (int) Math.floorMod(nanosOfSecond, NANOS_PER_SECOND);
        }
    }

    /**
     * Converts this timestamp to an {@link Instant}, for callers that want
     * arithmetic or formatting with {@code java.time}. This conversion assumes
     * the timestamp is anchored to the Unix epoch.
     *
     * @return an {@code Instant} equivalent to {@code seconds} and {@code nanosOfSecond}
     */
    public Instant toInstant() {
        return Instant.ofEpochSecond(seconds, nanosOfSecond);
    }
}
