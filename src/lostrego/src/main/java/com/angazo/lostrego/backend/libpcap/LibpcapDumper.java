package com.angazo.lostrego.backend.libpcap;

import com.angazo.lostrego.core.CaptureConfig;
import com.angazo.lostrego.core.CaptureException;
import com.angazo.lostrego.core.LinkType;
import com.angazo.lostrego.core.Packet;
import com.angazo.lostrego.core.PacketDumper;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

/**
 * libpcap-backed {@link PacketDumper}: writes packets to a savefile through
 * {@code pcap_dump}. A dead handle obtained with {@code pcap_open_dead} provides
 * the link type for the savefile header and is closed as soon as the dumper is
 * open.
 *
 * <p>Instances are confined to a single thread and are created through
 * {@link #open(Path, LinkType)}.
 */
final class LibpcapDumper implements PacketDumper {

    private final Arena arena;
    private volatile MemorySegment dumper;
    private boolean closed;

    private LibpcapDumper(Arena arena, MemorySegment dumper) {
        this.arena = arena;
        this.dumper = dumper;
    }

    static LibpcapDumper open(Path file, LinkType linkType) {
        Arena arena = Arena.ofConfined();
        MemorySegment deadHandle = null;
        MemorySegment dumper = null;
        try {
            deadHandle = LibpcapNative.openDead(linkType.code(), CaptureConfig.DEFAULT_SNAPLEN);
            if (deadHandle.address() == 0) {
                throw new CaptureException("pcap_open_dead failed for link type " + linkType.code());
            }
            MemorySegment path = arena.allocateFrom(file.toString());
            dumper = LibpcapNative.dumpOpen(deadHandle, path);
            if (dumper.address() == 0) {
                throw new CaptureException("pcap_dump_open on '" + file
                        + "' failed: " + LibpcapNative.getErr(deadHandle));
            }
            return new LibpcapDumper(arena, dumper);
        } catch (Throwable t) {
            closeDumperQuietly(dumper);
            arena.close();
            if (t instanceof CaptureException ce) {
                throw ce;
            }
            throw new CaptureException("Failed to open savefile '" + file + "'", t);
        } finally {
            closeHandleQuietly(deadHandle);
        }
    }

    @Override
    public void write(Packet packet) {
        Objects.requireNonNull(packet, "packet");
        MemorySegment d = dumper;
        if (d == null || d.address() == 0) {
            throw new IllegalStateException("dumper is closed");
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment header = a.allocate(LibpcapNative.PCAP_PKTHDR);
            LibpcapNative.writePkthdr(header,
                    packet.timestamp().seconds(),
                    packet.timestamp().nanosOfSecond() / 1000,
                    (int) packet.capturedLength(),
                    (int) packet.originalLength());
            MemorySegment data = a.allocateFrom(ValueLayout.JAVA_BYTE, packet.payload());
            LibpcapNative.dump(d, header, data);
        }
    }

    @Override
    public void flush() {
        MemorySegment d = dumper;
        if (d != null && d.address() != 0) {
            LibpcapNative.dumpFlush(d);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        MemorySegment d = dumper;
        dumper = null;
        closeDumperQuietly(d);
        arena.close();
    }

    private static void closeDumperQuietly(MemorySegment dumper) {
        if (dumper != null && dumper.address() != 0) {
            try {
                LibpcapNative.dumpClose(dumper);
            } catch (Throwable ignored) {
                // closing must not throw
            }
        }
    }

    private static void closeHandleQuietly(MemorySegment handle) {
        if (handle != null && handle.address() != 0) {
            try {
                LibpcapNative.close(handle);
            } catch (Throwable ignored) {
                // closing must not throw
            }
        }
    }
}
