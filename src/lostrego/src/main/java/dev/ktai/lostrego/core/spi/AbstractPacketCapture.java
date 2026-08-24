package dev.ktai.lostrego.core.spi;

import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.core.CaptureStatistics;
import dev.ktai.lostrego.core.Packet;
import dev.ktai.lostrego.core.PacketCapture;
import dev.ktai.lostrego.core.PacketListener;

import java.time.Duration;
import java.util.Objects;

/**
 * Base implementation of {@link PacketCapture} that owns the session lifecycle
 * (state machine, internal capture thread and failure handling) so each backend
 * only has to implement the native interaction.
 *
 * <p>Subclasses implement the following hooks:
 * <ul>
 *   <li>{@link #doStart()} — blocking call that pumps packets until the capture
 *       is stopped; delivers each packet through {@link #deliver(Packet)}.</li>
 *   <li>{@link #doStop()} — called (from the stopping thread) to signal the
 *       running {@code doStart()} loop to break.</li>
 *   <li>{@link #doClose()} — releases native resources.</li>
 *   <li>{@link #doStatistics()} — returns the current counters.</li>
 * </ul>
 *
 * <p>If {@link #deliver(Packet)} propagates an exception thrown by the listener,
 * the run loop records it as a failure and the session transitions to
 * {@code FAILED}; the cause is rethrown from {@link #stop()} or {@link #close()}.
 */
public abstract class AbstractPacketCapture implements PacketCapture {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

    private enum State {
        READY, RUNNING, STOPPED, FAILED, CLOSED
    }

    private final Object monitor = new Object();
    private State state = State.READY;
    private volatile boolean stopRequested;
    private volatile PacketListener listener;
    private volatile Throwable failure;
    private Thread captureThread;

    @Override
    public final void start(PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (monitor) {
            if (state == State.CLOSED) {
                throw new IllegalStateException("Capture is closed");
            }
            if (state == State.RUNNING) {
                throw new IllegalStateException("Capture is already running");
            }
            this.listener = listener;
            this.stopRequested = false;
            this.failure = null;
            this.state = State.RUNNING;
            captureThread = new Thread(this::runLoop, "lostrego-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        }
    }

    @Override
    public final void stop() {
        Thread toJoin = null;
        synchronized (monitor) {
            if (state == State.RUNNING) {
                stopRequested = true;
                toJoin = captureThread;
            }
        }
        if (toJoin != null) {
            try {
                doStop();
            } catch (Throwable ignored) {
                // The run loop records its own failure; a failing doStop() must
                // not mask the root cause.
            }
            join(toJoin);
        }
        rethrowFailure();
    }

    @Override
    public final void close() {
        CaptureException failureToRethrow = null;
        try {
            stop();
        } catch (CaptureException e) {
            failureToRethrow = e;
        }
        synchronized (monitor) {
            if (state != State.CLOSED) {
                state = State.CLOSED;
                try {
                    doClose();
                } catch (Throwable ignored) {
                    // Closing must not throw.
                }
            }
        }
        if (failureToRethrow != null) {
            throw failureToRethrow;
        }
    }

    @Override
    public final boolean isRunning() {
        synchronized (monitor) {
            return state == State.RUNNING;
        }
    }

    @Override
    public final CaptureStatistics statistics() {
        return doStatistics();
    }

    private void runLoop() {
        Throwable error = null;
        try {
            doStart();
        } catch (Throwable t) {
            error = t;
        } finally {
            synchronized (monitor) {
                if (error != null) {
                    failure = error;
                    state = State.FAILED;
                } else {
                    state = State.STOPPED;
                }
                stopRequested = false;
                captureThread = null;
                monitor.notifyAll();
            }
        }
    }

    /**
     * Delivers a packet to the registered listener. Called by the backend from
     * {@link #doStart()}. If the listener throws, the exception propagates out
     * of {@code doStart()} and fails the session.
     *
     * @param packet the captured packet
     */
    protected final void deliver(Packet packet) {
        Objects.requireNonNull(packet, "packet");
        listener.onPacket(packet);
    }

    /**
     * Returns whether a stop has been requested. Backend loops should exit when
     * this becomes {@code true}.
     *
     * @return {@code true} once {@code stop()} has been requested
     */
    protected final boolean isStopRequested() {
        return stopRequested;
    }

    /**
     * Runs the capture loop until the session is stopped or an error occurs.
     * Implementations must block and deliver packets through
     * {@link #deliver(Packet)}, and must return (or throw) once
     * {@link #isStopRequested()} becomes {@code true} or the native capture
     * fails.
     *
     * @throws Exception on capture failure
     */
    protected void doStart() throws Exception {
    }

    /**
     * Signals the running {@link #doStart()} loop to break, invoked from the
     * thread that calls {@code stop()}. Implementations must be safe if the
     * loop has already stopped.
     *
     * @throws Exception if signaling fails
     */
    protected void doStop() throws Exception {
    }

    /**
     * Releases native resources. Invoked once, after the capture thread has
     * stopped.
     *
     * @throws Exception if releasing fails
     */
    protected void doClose() throws Exception {
    }

    /**
     * Returns the current capture statistics.
     *
     * @return the capture counters
     */
    protected abstract CaptureStatistics doStatistics();

    private void join(Thread thread) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    thread.join(STOP_TIMEOUT.toMillis());
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (thread.isAlive()) {
            throw new IllegalStateException("Capture thread did not stop within " + STOP_TIMEOUT);
        }
    }

    private void rethrowFailure() {
        Throwable f = failure;
        if (f != null) {
            // One-shot: once reported, the failure is cleared so a subsequent
            // stop()/close() does not throw the same cause again.
            failure = null;
            throw new CaptureException("Capture failed: " + f.getMessage(), f);
        }
    }
}
