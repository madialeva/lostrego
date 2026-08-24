package dev.ktai.lostrego.backend.libpcap;

import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.core.CaptureStatistics;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.Packet;
import dev.ktai.lostrego.core.PacketCapture;
import dev.ktai.lostrego.core.PacketDumper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Backend tests that do not require live-capture privileges: they run anywhere
 * libpcap is installed and parse a bundled {@code .pcap} file deterministically.
 */
class LibpcapBackendTest {

    @Test
    void providerNameIsLibpcap() {
        assertEquals("libpcap", new LibpcapProvider().name());
    }

    @Test
    void isSupportedDoesNotThrow() {
        assertDoesNotThrow(LibpcapNative::isAvailable);
    }

    @Test
    void isSupportedCoherentWithLibrary() {
        assertEquals(LibpcapNative.isAvailable(), new LibpcapProvider().isSupported());
    }

    @Test
    void libVersionAvailableWhenLoaded() {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        String version = LibpcapNative.libVersion();
        assertNotNull(version);
        assertFalse(version.isBlank());
    }

    @Test
    void parsesOfflinePackets() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        List<Packet> packets = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        try (LibpcapCapture capture = LibpcapCapture.openOffline(pcapPath())) {
            capture.start(packet -> {
                synchronized (packets) {
                    packets.add(packet);
                }
                latch.countDown();
            });
            assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for packets");
            waitUntilStopped(capture);
            capture.stop();
        }

        assertEquals(2, packets.size());

        Packet first = packets.get(0);
        assertEquals(95, first.timestamp().seconds());
        assertEquals(200_000_000, first.timestamp().nanosOfSecond());
        assertEquals(60, first.originalLength());
        assertEquals(14, first.capturedLength());
        assertEquals(LinkType.ETHERNET, first.linkType());
        assertArrayEquals(hex("00112233445566778899aabb0800"), first.payload());

        Packet second = packets.get(1);
        assertEquals(96, second.timestamp().seconds());
        assertEquals(1_000_000, second.timestamp().nanosOfSecond());
        assertEquals(20, second.originalLength());
        assertEquals(20, second.capturedLength());
        assertEquals(LinkType.ETHERNET, second.linkType());
        assertArrayEquals(hex("00112233445566778899aabb0800aabbccddeeff"), second.payload());
    }

    @Test
    void closeIsIdempotent() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        LibpcapCapture capture = LibpcapCapture.openOffline(pcapPath());
        capture.close();
        capture.close();
    }

    @Test
    void statisticsReturnZeroesWhenClosed() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        LibpcapCapture capture = LibpcapCapture.openOffline(pcapPath());
        capture.close();
        CaptureStatistics statistics = capture.statistics();
        assertNotNull(statistics);
    }

    @Test
    void readsOfflineThroughProvider() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        List<Packet> packets = readAll(pcapPath());
        assertEquals(2, packets.size());
        assertEquals(LinkType.ETHERNET, packets.get(0).linkType());
    }

    @Test
    void writesAndReadsBackPackets() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        List<Packet> original = readAll(pcapPath());
        Path out = Files.createTempFile("lostrego", ".pcap");
        try {
            try (PacketDumper dumper = new LibpcapProvider().openDumper(out, LinkType.ETHERNET)) {
                for (Packet packet : original) {
                    dumper.write(packet);
                }
            }
            assertEquals(original, readAll(out));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void openOfflineMissingFileThrows() {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        Path missing = Path.of("/nonexistent/definitely-not-there.pcap");
        assertThrows(CaptureException.class, () -> new LibpcapProvider().openOffline(missing));
    }

    @Test
    void openDumperUnwritablePathThrows() {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        Path bad = Path.of("/nonexistent/dir/out.pcap");
        assertThrows(CaptureException.class, () -> new LibpcapProvider().openDumper(bad, LinkType.ETHERNET));
    }

    @Test
    void dumperCloseIsIdempotent() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");
        Path out = Files.createTempFile("lostrego", ".pcap");
        try {
            PacketDumper dumper = new LibpcapProvider().openDumper(out, LinkType.ETHERNET);
            dumper.close();
            dumper.close();
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void offlineAndWritingSupportCoherentWithLibrary() {
        assertEquals(LibpcapNative.isAvailable(), new LibpcapProvider().supportsOffline());
        assertEquals(LibpcapNative.isAvailable(), new LibpcapProvider().supportsWriting());
    }

    private static List<Packet> readAll(Path file) throws Exception {
        List<Packet> packets = new ArrayList<>();
        try (PacketCapture capture = new LibpcapProvider().openOffline(file)) {
            capture.start(packets::add);
            waitUntilStopped(capture);
            capture.stop();
        }
        return packets;
    }

    private static Path pcapPath() throws Exception {
        return Path.of(Objects.requireNonNull(
                LibpcapBackendTest.class.getResource("/ethernet-truncated.pcap")).toURI());
    }

    private static void waitUntilStopped(PacketCapture capture) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (capture.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(capture.isRunning(), "capture did not stop on its own");
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
