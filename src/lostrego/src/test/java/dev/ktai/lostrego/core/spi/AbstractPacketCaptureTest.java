package dev.ktai.lostrego.core.spi;

import dev.ktai.lostrego.core.CaptureException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AbstractPacketCaptureTest {

    @Test
    void startReturnsImmediatelyAndMarksRunning() {
        try (var capture = new FakeCapture()) {
            capture.start(packet -> { });
            assertTrue(capture.isRunning());
        }
    }

    @Test
    void stopIsBlockingAndStopsDelivery() throws InterruptedException {
        var capture = new FakeCapture();
        capture.delayMillis(1);
        var received = new AtomicInteger();
        capture.start(packet -> received.incrementAndGet());

        awaitUntil(() -> received.get() >= 10);

        capture.stop();
        assertFalse(capture.isRunning());

        long afterStop = received.get();
        Thread.sleep(30);
        assertEquals(afterStop, received.get(), "no packets should be delivered after stop()");

        capture.close();
        assertTrue(capture.isClosed());
    }

    @Test
    void closeIsIdempotent() {
        var capture = new FakeCapture();
        capture.close();
        capture.close();
        assertTrue(capture.isClosed());
    }

    @Test
    void canRestartAfterStop() throws InterruptedException {
        var capture = new FakeCapture();
        capture.delayMillis(1);

        capture.start(packet -> { });
        capture.stop();
        assertFalse(capture.isRunning());

        var received = new AtomicInteger();
        capture.start(packet -> received.incrementAndGet());
        awaitUntil(() -> received.get() >= 1);
        capture.stop();
        assertFalse(capture.isRunning());

        capture.close();
    }

    @Test
    void listenerExceptionFailsCaptureAndRethrowsOnStop() throws InterruptedException {
        var capture = new FakeCapture();
        var boom = new IllegalStateException("boom");
        var invoked = new CountDownLatch(1);

        capture.start(packet -> {
            invoked.countDown();
            throw boom;
        });

        assertTrue(invoked.await(5, TimeUnit.SECONDS), "listener should have been invoked");

        var ex = assertThrows(CaptureException.class, capture::stop);
        assertSame(boom, ex.getCause());
        assertFalse(capture.isRunning());

        capture.close();
    }

    @Test
    void startTwiceThrows() {
        var capture = new FakeCapture();
        capture.start(packet -> { });
        try {
            assertThrows(IllegalStateException.class, () -> capture.start(packet -> { }));
        } finally {
            capture.close();
        }
    }

    @Test
    void startAfterCloseThrows() {
        var capture = new FakeCapture();
        capture.close();
        assertThrows(IllegalStateException.class, () -> capture.start(packet -> { }));
    }

    @Test
    void statisticsAreAvailable() {
        var capture = new FakeCapture();
        var stats = capture.statistics();
        assertEquals(0, stats.received());
        capture.close();
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("condition not met within timeout");
    }
}
