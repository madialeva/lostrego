package dev.ktai.lostrego.backend.libpcap;

import dev.ktai.lostrego.core.CaptureConfig;
import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.core.PacketCapture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live capture test: requires a loopback device and capture privileges
 * (root or CAP_NET_RAW). It is skipped silently where those are absent, which
 * is the expected behavior in CI.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class LibpcapLiveTest {

    private static final int UDP_PORT = 45_999;

    @Test
    void capturesUdpPacketOnLoopback() throws Exception {
        assumeTrue(LibpcapNative.isAvailable(), "libpcap not available");

        PacketCapture capture = openLoopback();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger received = new AtomicInteger();
            capture.start(packet -> {
                received.incrementAndGet();
                latch.countDown();
            });

            byte[] payload = "lostrego".getBytes();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(new DatagramPacket(payload, payload.length,
                        InetAddress.getByName("127.0.0.1"), UDP_PORT));
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "no UDP packet was captured");
            assertTrue(received.get() >= 1, "expected at least one captured packet");
        } finally {
            capture.close();
        }
    }

    private static PacketCapture openLoopback() {
        for (String device : List.of("lo", "lo0")) {
            try {
                return new LibpcapProvider().openLive(CaptureConfig.builder()
                        .device(device)
                        .timeoutMillis(500)
                        .filter("udp and port " + UDP_PORT)
                        .build());
            } catch (CaptureException ignored) {
                // try the next loopback device name
            }
        }
        assumeTrue(false, "cannot open a live capture on loopback (missing privileges?)");
        return null;
    }
}
