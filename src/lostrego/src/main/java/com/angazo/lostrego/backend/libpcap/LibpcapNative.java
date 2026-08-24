package com.angazo.lostrego.backend.libpcap;

import com.angazo.lostrego.core.CaptureException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.List;

/**
 * FFM bindings over libpcap. This is the single place in the backend that
 * touches {@code java.lang.foreign}; the rest of the library only sees plain
 * Java types.
 *
 * <p>The native library is loaded once in a static initializer. If it cannot
 * be loaded, or a required symbol is missing, the backend reports itself as
 * unavailable ({@link #isAvailable()} returns {@code false}) instead of
 * throwing, so it never prevents other backends from working.
 */
final class LibpcapNative {

    static final int PCAP_ERRBUF_SIZE = 256;
    static final int PCAP_NETMASK_UNKNOWN = 0xffffffff;

    /** Return codes of {@code pcap_next_ex}. */
    static final int NEXT_OK = 1;
    static final int NEXT_TIMEOUT = 0;
    static final int NEXT_ERROR = -1;
    static final int NEXT_BREAK = -2;

    private static final boolean MACOS =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    /**
     * {@code struct timeval}. On Linux x86_64 both fields are {@code long};
     * on macOS {@code tv_sec} is {@code long} and {@code tv_usec} is {@code int}.
     */
    static final StructLayout TIMEVAL = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("tv_sec"),
            MACOS ? ValueLayout.JAVA_INT.withName("tv_usec")
                  : ValueLayout.JAVA_LONG.withName("tv_usec"));

    /** {@code struct pcap_pkthdr}. */
    static final StructLayout PCAP_PKTHDR = MemoryLayout.structLayout(
            TIMEVAL.withName("ts"),
            ValueLayout.JAVA_INT.withName("caplen"),
            ValueLayout.JAVA_INT.withName("len"));

    /** {@code struct pcap_stat}. */
    static final StructLayout PCAP_STAT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("ps_recv"),
            ValueLayout.JAVA_INT.withName("ps_drop"),
            ValueLayout.JAVA_INT.withName("ps_ifdrop"));

    /** {@code struct bpf_program}. */
    static final StructLayout BPF_PROGRAM = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("bf_len"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("bf_insns"));

    static final VarHandle TS_SEC = PCAP_PKTHDR.varHandle(
            MemoryLayout.PathElement.groupElement("ts"),
            MemoryLayout.PathElement.groupElement("tv_sec"));
    static final VarHandle TS_USEC = PCAP_PKTHDR.varHandle(
            MemoryLayout.PathElement.groupElement("ts"),
            MemoryLayout.PathElement.groupElement("tv_usec"));
    static final VarHandle CAPLEN =
            PCAP_PKTHDR.varHandle(MemoryLayout.PathElement.groupElement("caplen"));
    static final VarHandle LEN =
            PCAP_PKTHDR.varHandle(MemoryLayout.PathElement.groupElement("len"));
    static final VarHandle PS_RECV =
            PCAP_STAT.varHandle(MemoryLayout.PathElement.groupElement("ps_recv"));
    static final VarHandle PS_DROP =
            PCAP_STAT.varHandle(MemoryLayout.PathElement.groupElement("ps_drop"));
    static final VarHandle PS_IFDROP =
            PCAP_STAT.varHandle(MemoryLayout.PathElement.groupElement("ps_ifdrop"));

    private static final Natives NATIVES = load();

    private LibpcapNative() {
    }

    /** Returns whether the libpcap library and all required symbols are available. */
    static boolean isAvailable() {
        return NATIVES != null;
    }

    static MemorySegment openLive(MemorySegment device, int snaplen, int promisc,
            int toMs, MemorySegment errbuf) {
        Natives n = natives();
        return (MemorySegment) call("pcap_open_live", n.openLive,
                device, snaplen, promisc, toMs, errbuf);
    }

    static MemorySegment openOffline(MemorySegment file, MemorySegment errbuf) {
        Natives n = natives();
        return (MemorySegment) call("pcap_open_offline", n.openOffline, file, errbuf);
    }

    static int nextEx(MemorySegment p, MemorySegment header, MemorySegment data) {
        Natives n = natives();
        return (Integer) call("pcap_next_ex", n.nextEx, p, header, data);
    }

    static void close(MemorySegment p) {
        call("pcap_close", natives().close, p);
    }

    static void breakloop(MemorySegment p) {
        call("pcap_breakloop", natives().breakloop, p);
    }

    static int stats(MemorySegment p, MemorySegment stat) {
        return (Integer) call("pcap_stats", natives().stats, p, stat);
    }

    static int dataLink(MemorySegment p) {
        return (Integer) call("pcap_datalink", natives().dataLink, p);
    }

    static int setFilter(MemorySegment p, MemorySegment program) {
        return (Integer) call("pcap_setfilter", natives().setFilter, p, program);
    }

    static int compile(MemorySegment p, MemorySegment program, MemorySegment expression) {
        Natives n = natives();
        return (Integer) call("pcap_compile", n.compile,
                p, program, expression, 1, PCAP_NETMASK_UNKNOWN);
    }

    static void freeCode(MemorySegment program) {
        call("pcap_freecode", natives().freeCode, program);
    }

    static String getErr(MemorySegment p) {
        MemorySegment err = (MemorySegment) call("pcap_geterr", natives().getErr, p);
        return readCString(err, 1024);
    }

    static String libVersion() {
        MemorySegment v = (MemorySegment) call("pcap_lib_version", natives().libVersion);
        return readCString(v, 256);
    }

    /** Enables immediate mode if the platform exposes the symbol; otherwise no-op. */
    static int setImmediateMode(MemorySegment p) {
        Natives n = natives();
        if (n.setImmediateMode == null) {
            return 0;
        }
        return (Integer) call("pcap_set_immediate_mode", n.setImmediateMode, p, 1);
    }

    /** Sets the buffer size if the platform exposes the symbol; otherwise no-op. */
    static int setBufferSize(MemorySegment p, int size) {
        Natives n = natives();
        if (n.setBufferSize == null) {
            return 0;
        }
        return (Integer) call("pcap_set_buffer_size", n.setBufferSize, p, size);
    }

    /** Opens a dead handle of the given link type, used to open a dumper. */
    static MemorySegment openDead(int linkType, int snaplen) {
        return (MemorySegment) call("pcap_open_dead", natives().openDead, linkType, snaplen);
    }

    static MemorySegment dumpOpen(MemorySegment p, MemorySegment path) {
        return (MemorySegment) call("pcap_dump_open", natives().dumpOpen, p, path);
    }

    static void dump(MemorySegment dumper, MemorySegment header, MemorySegment data) {
        call("pcap_dump", natives().dump, dumper, header, data);
    }

    static void dumpClose(MemorySegment dumper) {
        call("pcap_dump_close", natives().dumpClose, dumper);
    }

    static int dumpFlush(MemorySegment dumper) {
        return (Integer) call("pcap_dump_flush", natives().dumpFlush, dumper);
    }

    /**
     * Fills a {@code pcap_pkthdr} segment with the given values, handling the
     * platform-dependent {@code tv_usec} field width.
     */
    static void writePkthdr(MemorySegment header, long seconds, long micros, int caplen, int len) {
        TS_SEC.set(header, 0L, seconds);
        if (MACOS) {
            TS_USEC.set(header, 0L, (int) micros);
        } else {
            TS_USEC.set(header, 0L, micros);
        }
        CAPLEN.set(header, 0L, caplen);
        LEN.set(header, 0L, len);
    }

    private static Natives natives() {
        Natives n = NATIVES;
        if (n == null) {
            throw new CaptureException("libpcap is not available on this platform");
        }
        return n;
    }

    static String readCString(MemorySegment ptr, long maxLength) {
        if (ptr == null || ptr.address() == 0) {
            return "";
        }
        return ptr.reinterpret(maxLength).getString(0);
    }

    private static Object call(String name, MethodHandle handle, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new CaptureException("Native call " + name + " failed", t);
        }
    }

    private static Natives load() {
        try {
            SymbolLookup lookup = loadLibrary();
            if (lookup == null) {
                return null;
            }
            Linker linker = Linker.nativeLinker();
            return new Natives(
                    downcall(linker, lookup, "pcap_open_live", FunctionDescriptor.of(
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_open_offline", FunctionDescriptor.of(
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_next_ex", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_close", FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_breakloop", FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_stats", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_setfilter", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_compile", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)),
                    downcall(linker, lookup, "pcap_freecode", FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_datalink", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_geterr", FunctionDescriptor.of(
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_lib_version", FunctionDescriptor.of(
                            ValueLayout.ADDRESS)),
                    optional(linker, lookup, "pcap_set_immediate_mode", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
                    optional(linker, lookup, "pcap_set_buffer_size", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
                    downcall(linker, lookup, "pcap_open_dead", FunctionDescriptor.of(
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)),
                    downcall(linker, lookup, "pcap_dump_open", FunctionDescriptor.of(
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_dump", FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_dump_close", FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS)),
                    downcall(linker, lookup, "pcap_dump_flush", FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)));
        } catch (Throwable t) {
            return null;
        }
    }

    private static MethodHandle downcall(Linker linker, SymbolLookup lookup, String name,
            FunctionDescriptor descriptor) {
        return linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor);
    }

    private static MethodHandle optional(Linker linker, SymbolLookup lookup, String name,
            FunctionDescriptor descriptor) {
        return lookup.find(name)
                .map(symbol -> linker.downcallHandle(symbol, descriptor))
                .orElse(null);
    }

    private static SymbolLookup loadLibrary() {
        try {
            // System.loadLibrary resolves through the OS loader (dlopen), which
            // searches the standard library directories (e.g. /usr/lib64).
            System.loadLibrary("pcap");
            return SymbolLookup.loaderLookup();
        } catch (Throwable ignored) {
            // fall through to absolute-path candidates for runtime-only installs
            // that lack the "libpcap.so" development symlink
        }
        for (String candidate : List.of(
                "/usr/lib64/libpcap.so.1",
                "/lib64/libpcap.so.1",
                "/usr/lib/x86_64-linux-gnu/libpcap.so.1",
                "/lib/x86_64-linux-gnu/libpcap.so.1",
                "/usr/lib/x86_64-linux-gnu/libpcap.so.0.8",
                "/usr/lib/libpcap.so.1",
                "/usr/local/lib/libpcap.so.1",
                "/usr/lib/libpcap.dylib",
                "/opt/homebrew/lib/libpcap.dylib")) {
            try {
                return SymbolLookup.libraryLookup(Path.of(candidate), Arena.global());
            } catch (Throwable ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    private record Natives(
            MethodHandle openLive,
            MethodHandle openOffline,
            MethodHandle nextEx,
            MethodHandle close,
            MethodHandle breakloop,
            MethodHandle stats,
            MethodHandle setFilter,
            MethodHandle compile,
            MethodHandle freeCode,
            MethodHandle dataLink,
            MethodHandle getErr,
            MethodHandle libVersion,
            MethodHandle setImmediateMode,
            MethodHandle setBufferSize,
            MethodHandle openDead,
            MethodHandle dumpOpen,
            MethodHandle dump,
            MethodHandle dumpClose,
            MethodHandle dumpFlush) {
    }
}
