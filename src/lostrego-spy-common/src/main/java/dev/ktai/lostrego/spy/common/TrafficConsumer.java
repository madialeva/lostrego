package dev.ktai.lostrego.spy.common;

/**
 * Receives analyzed packets from a running capture. Implemented by each
 * frontend (console today, a graphical UI tomorrow); the analysis layer never
 * performs I/O itself.
 */
@FunctionalInterface
public interface TrafficConsumer {

    /**
     * Called for each analyzed packet, on the capture thread.
     *
     * @param record the analyzed packet; immutable and safe to retain
     */
    void accept(TrafficRecord record);
}
