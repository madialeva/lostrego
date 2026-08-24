package dev.ktai.lostrego.backend.libpcap;

import dev.ktai.lostrego.core.CaptureConfig;
import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.core.CaptureStatistics;
import dev.ktai.lostrego.core.CaptureTimestamp;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.Packet;
import dev.ktai.lostrego.core.spi.AbstractPacketCapture;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

/**
 * libpcap-backed {@link dev.ktai.lostrego.core.PacketCapture}: translates the
 * native {@code struct pcap_pkthdr} into the immutable {@link Packet} model and
 * delegates the lifecycle state machine to {@link AbstractPacketCapture}.
 *
 * <p>Instances are created through {@link #open(CaptureConfig)} (live capture)
 * or {@link #openOffline(Path)} (test-only offline capture).
 */
final class LibpcapCapture extends AbstractPacketCapture {

    private final Arena arena;
    private final LinkType linkType;
    private final MemorySegment headerSlot;
    private final MemorySegment dataSlot;
    private volatile MemorySegment handle;

    private LibpcapCapture(Arena arena, MemorySegment handle, LinkType linkType) {
        this.arena = arena;
        this.handle = handle;
        this.linkType = linkType;
        this.headerSlot = arena.allocate(ValueLayout.ADDRESS);
        this.dataSlot = arena.allocate(ValueLayout.ADDRESS);
    }

    static LibpcapCapture open(CaptureConfig config) {
        Arena arena = Arena.ofShared();
        MemorySegment handle = null;
        try {
            MemorySegment errbuf = arena.allocate(LibpcapNative.PCAP_ERRBUF_SIZE);
            MemorySegment device = arena.allocateFrom(config.device());
            handle = LibpcapNative.openLive(device, config.snaplen(),
                    config.promiscuous() ? 1 : 0, config.timeoutMillis(), errbuf);
            if (handle.address() == 0) {
                throw new CaptureException("pcap_open_live on '" + config.device()
                        + "' failed: " + LibpcapNative.readCString(errbuf, LibpcapNative.PCAP_ERRBUF_SIZE));
            }
            if (config.bufferSize() > 0) {
                check(LibpcapNative.setBufferSize(handle, config.bufferSize()), "pcap_set_buffer_size");
            }
            if (config.immediateMode()) {
                check(LibpcapNative.setImmediateMode(handle), "pcap_set_immediate_mode");
            }
            if (config.filter() != null && !config.filter().isBlank()) {
                applyFilter(handle, config.filter());
            }
            LinkType linkType = LinkType.of(LibpcapNative.dataLink(handle));
            return new LibpcapCapture(arena, handle, linkType);
        } catch (Throwable t) {
            closeQuietly(handle);
            arena.close();
            if (t instanceof CaptureException ce) {
                throw ce;
            }
            throw new CaptureException("Failed to open libpcap capture on '" + config.device() + "'", t);
        }
    }

    static LibpcapCapture openOffline(Path file) {
        Arena arena = Arena.ofShared();
        MemorySegment handle = null;
        try {
            MemorySegment errbuf = arena.allocate(LibpcapNative.PCAP_ERRBUF_SIZE);
            MemorySegment path = arena.allocateFrom(file.toString());
            handle = LibpcapNative.openOffline(path, errbuf);
            if (handle.address() == 0) {
                throw new CaptureException("pcap_open_offline failed: "
                        + LibpcapNative.readCString(errbuf, LibpcapNative.PCAP_ERRBUF_SIZE));
            }
            LinkType linkType = LinkType.of(LibpcapNative.dataLink(handle));
            return new LibpcapCapture(arena, handle, linkType);
        } catch (Throwable t) {
            closeQuietly(handle);
            arena.close();
            if (t instanceof CaptureException ce) {
                throw ce;
            }
            throw new CaptureException("Failed to open offline capture '" + file + "'", t);
        }
    }

    @Override
    protected void doStart() {
        MemorySegment h = handle;
        while (!isStopRequested()) {
            int result = LibpcapNative.nextEx(h, headerSlot, dataSlot);
            switch (result) {
                case LibpcapNative.NEXT_OK -> deliver(readPacket());
                case LibpcapNative.NEXT_BREAK -> {
                    return;
                }
                case LibpcapNative.NEXT_ERROR -> throw new CaptureException(
                        "pcap_next_ex failed: " + LibpcapNative.getErr(h));
                case LibpcapNative.NEXT_TIMEOUT -> {
                    // no packet within the read timeout: loop and re-check the stop flag
                }
                default -> throw new CaptureException("unexpected pcap_next_ex result: " + result);
            }
        }
    }

    @Override
    protected void doStop() {
        MemorySegment h = handle;
        if (h != null && h.address() != 0) {
            LibpcapNative.breakloop(h);
        }
    }

    @Override
    protected void doClose() {
        MemorySegment h = handle;
        handle = null;
        closeQuietly(h);
        arena.close();
    }

    @Override
    protected CaptureStatistics doStatistics() {
        MemorySegment h = handle;
        if (h == null || h.address() == 0) {
            return new CaptureStatistics(0, 0, 0);
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment stat = a.allocate(LibpcapNative.PCAP_STAT);
            if (LibpcapNative.stats(h, stat) != 0) {
                return new CaptureStatistics(0, 0, 0);
            }
            long received = Integer.toUnsignedLong((int) LibpcapNative.PS_RECV.get(stat, 0L));
            long dropped = Integer.toUnsignedLong((int) LibpcapNative.PS_DROP.get(stat, 0L));
            long interfaceDropped = Integer.toUnsignedLong((int) LibpcapNative.PS_IFDROP.get(stat, 0L));
            return new CaptureStatistics(received, dropped, interfaceDropped);
        }
    }

    private Packet readPacket() {
        MemorySegment header = headerSlot.get(ValueLayout.ADDRESS, 0)
                .reinterpret(LibpcapNative.PCAP_PKTHDR.byteSize());
        MemorySegment data = dataSlot.get(ValueLayout.ADDRESS, 0);
        long seconds = (long) LibpcapNative.TS_SEC.get(header, 0L);
        long microseconds = (long) LibpcapNative.TS_USEC.get(header, 0L);
        int captured = (int) LibpcapNative.CAPLEN.get(header, 0L);
        int original = (int) LibpcapNative.LEN.get(header, 0L);
        byte[] payload = data.reinterpret(Integer.toUnsignedLong(captured)).toArray(ValueLayout.JAVA_BYTE);
        CaptureTimestamp timestamp = new CaptureTimestamp(seconds, (int) (microseconds * 1000));
        return new Packet(timestamp, Integer.toUnsignedLong(original),
                Integer.toUnsignedLong(captured), linkType, payload);
    }

    private static void applyFilter(MemorySegment handle, String expression) {
        try (Arena filterArena = Arena.ofConfined()) {
            MemorySegment program = filterArena.allocate(LibpcapNative.BPF_PROGRAM);
            MemorySegment expr = filterArena.allocateFrom(expression);
            if (LibpcapNative.compile(handle, program, expr) != 0) {
                throw new CaptureException("Invalid BPF filter '" + expression
                        + "': " + LibpcapNative.getErr(handle));
            }
            if (LibpcapNative.setFilter(handle, program) != 0) {
                throw new CaptureException("pcap_setfilter failed: " + LibpcapNative.getErr(handle));
            }
            LibpcapNative.freeCode(program);
        }
    }

    private static void check(int result, String function) {
        if (result < 0) {
            throw new CaptureException(function + " failed (code " + result + ")");
        }
    }

    private static void closeQuietly(MemorySegment handle) {
        if (handle != null && handle.address() != 0) {
            try {
                LibpcapNative.close(handle);
            } catch (Throwable ignored) {
                // closing must not throw
            }
        }
    }
}
