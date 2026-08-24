package dev.ktai.lostrego.spy.common;

import dev.ktai.lostrego.core.CaptureStatistics;
import dev.ktai.lostrego.core.PacketCapture;
import dev.ktai.lostrego.core.PacketCaptures;
import dev.ktai.lostrego.spy.common.protocol.PacketParser;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates a live capture over the lostrego library and hands each analyzed
 * packet to a {@link TrafficConsumer}. It performs no I/O and knows nothing of
 * the frontend: the caller supplies the consumer and the capture settings.
 */
public final class CaptureRunner implements AutoCloseable {

    private static final long POLL_INTERVAL_MILLIS = 50L;

    private final PacketCapture capture;
    private final long packetCount;
    private final AtomicLong sequence = new AtomicLong();

    private CaptureRunner(PacketCapture capture, long packetCount) {
        this.capture = capture;
        this.packetCount = packetCount;
    }

    /**
     * Opens a live capture session with the given settings.
     *
     * @param settings the capture settings
     * @return a new runner
     */
    public static CaptureRunner open(CaptureSettings settings) {
        return new CaptureRunner(PacketCaptures.openLive(settings.toCaptureConfig()), settings.packetCount());
    }

    /**
     * Starts capturing and delivering analyzed records to the consumer on the
     * capture thread. Returns immediately. Each record carries a monotonically
     * increasing read sequence number (1-based).
     *
     * @param consumer the consumer of analyzed records
     */
    public void start(TrafficConsumer consumer) {
        capture.start(packet -> consumer.accept(
                TrafficRecord.of(packet, PacketParser.parse(packet), sequence.incrementAndGet())));
    }

    /**
     * Stops the capture, blocking until the capture thread has finished.
     */
    public void stop() {
        capture.stop();
    }

    /**
     * Returns whether the capture is currently running.
     */
    public boolean isRunning() {
        return capture.isRunning();
    }

    /**
     * Returns the current capture statistics.
     */
    public CaptureStatistics statistics() {
        return capture.statistics();
    }

    /**
     * Runs the capture until the configured packet limit is reached (or, when
     * there is no limit, until {@link #stop()} is called from another thread,
     * e.g. a shutdown hook). Blocks the calling thread, then stops the capture
     * and returns the final statistics.
     *
     * @param consumer the consumer of analyzed records
     * @return the final capture statistics
     */
    public CaptureStatistics run(TrafficConsumer consumer) {
        start(consumer);
        try {
            while (isRunning() && (packetCount <= 0 || sequence.get() < packetCount)) {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stop();
        }
        return statistics();
    }

    @Override
    public void close() {
        capture.close();
    }
}
