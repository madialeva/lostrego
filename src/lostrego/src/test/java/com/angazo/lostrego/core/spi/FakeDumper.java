package com.angazo.lostrego.core.spi;

import com.angazo.lostrego.core.Packet;
import com.angazo.lostrego.core.PacketDumper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A fake {@link PacketDumper} used by the factory tests to exercise dumper
 * discovery and selection without any native backend.
 */
public final class FakeDumper implements PacketDumper {

    private final List<Packet> written = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void write(Packet packet) {
        written.add(packet);
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() {
        closed.set(true);
    }

    /** Returns the packets written so far. */
    public List<Packet> written() {
        return List.copyOf(written);
    }

    /** Returns whether {@link #close()} has been called. */
    public boolean isClosed() {
        return closed.get();
    }
}
