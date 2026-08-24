package dev.ktai.lostrego.core.spi;

import dev.ktai.lostrego.core.CaptureStatistics;
import dev.ktai.lostrego.core.CaptureTimestamp;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.Packet;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A fake capture backend used by tests: it emits synthetic packets in a loop
 * until stopped, with no native dependency.
 */
public final class FakeCapture extends AbstractPacketCapture {

    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long emitted;
    private volatile long maxPackets = Long.MAX_VALUE;
    private volatile long delayMillis;

    @Override
    protected void doStart() {
        while (!isStopRequested() && emitted < maxPackets) {
            deliver(packet(emitted));
            emitted++;
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    protected void doClose() {
        closed.set(true);
    }

    @Override
    protected CaptureStatistics doStatistics() {
        return new CaptureStatistics(emitted, 0, 0);
    }

    /** Limits how many packets this capture emits before stopping on its own. */
    public void maxPackets(long maxPackets) {
        this.maxPackets = maxPackets;
    }

    /** Adds a sleep between packets, to make the loop observable/controllable. */
    public void delayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public long emitted() {
        return emitted;
    }

    public boolean isClosed() {
        return closed.get();
    }

    private static Packet packet(long n) {
        return new Packet(new CaptureTimestamp(n, 0), 100, 100, LinkType.ETHERNET, new byte[]{1, 2, 3, 4});
    }
}
