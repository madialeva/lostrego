package dev.ktai.lostrego.core;

/**
 * A capture session, opened by a backend through {@link PacketCaptures}.
 *
 * <p>The session follows an asynchronous callback lifecycle:
 *
 * <pre>
 * READY --start(listener)--&gt; RUNNING --stop()--&gt; STOPPED
 *                            |                       |
 *                            +-- (failure) --&gt; FAILED
 *                                                      |
 *                                        close() (also STOPPED) --&gt; CLOSED
 * </pre>
 *
 * <ul>
 *   <li>{@link #start(PacketListener)} launches an internal capture thread and
 *       returns immediately.</li>
 *   <li>{@link #stop()} stops the capture and blocks until the internal thread
 *       has finished; no more callbacks are delivered afterwards.</li>
 *   <li>{@link #close()} stops the capture (if running) and releases the
 *       underlying native resources. It is idempotent.</li>
 * </ul>
 *
 * A session may be started again after {@code stop()} or a failure, until it is
 * closed.
 */
public interface PacketCapture extends AutoCloseable {

    /**
     * Starts capturing packets and delivering them to the given listener on an
     * internal thread. Returns immediately.
     *
     * @param listener the listener to receive captured packets
     * @throws IllegalStateException if the session is already running or closed
     * @throws NullPointerException  if {@code listener} is {@code null}
     */
    void start(PacketListener listener);

    /**
     * Stops the capture, blocking until the internal capture thread has
     * terminated.
     *
     * <p>If the capture stopped because of a failure (an exception thrown from
     * the listener or a native error), this method rethrows the cause wrapped in
     * a {@link CaptureException}.
     *
     * @throws CaptureException if the capture failed
     */
    void stop();

    /**
     * Returns whether the capture is currently running.
     *
     * @return {@code true} if a capture thread is active
     */
    boolean isRunning();

    /**
     * Returns the current capture statistics.
     *
     * @return the capture counters
     */
    CaptureStatistics statistics();

    /**
     * Stops the capture (if running) and releases the underlying resources.
     * Idempotent: closing an already-closed session is a no-op.
     */
    @Override
    void close();
}
