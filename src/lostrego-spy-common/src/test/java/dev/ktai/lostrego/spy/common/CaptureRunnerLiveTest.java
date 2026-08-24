package dev.ktai.lostrego.spy.common;

import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.spy.common.protocol.Layer;
import dev.ktai.lostrego.spy.common.protocol.UdpLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end live capture through the analysis layer: requires a loopback
 * device and capture privileges (root or CAP_NET_RAW). Skipped silently where
 * those are absent.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class CaptureRunnerLiveTest {

    private static final int UDP_PORT = 46_001;

    @Test
    void deliversAnalyzedUdpPacket() throws Exception {
        CaptureRunner runner = openLoopback();
        try {
            List<TrafficRecord> records = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);
            runner.start(record -> {
                records.add(record);
                latch.countDown();
            });

            byte[] payload = "lostrego-spy".getBytes();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(new DatagramPacket(payload, payload.length,
                        InetAddress.getByName("127.0.0.1"), UDP_PORT));
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "no packet was captured");
            TrafficRecord record = records.get(0);
            assertTrue(record.sequenceNumber() >= 1, "sequence number must start at 1");
            UdpLayer udp = findUdp(record.layers());
            assertNotNull(udp, "captured packet does not contain a UDP layer");
            assertEquals(UDP_PORT, udp.destinationPort());
        } finally {
            runner.close();
        }
    }

    private static UdpLayer findUdp(Layer layer) {
        if (layer instanceof UdpLayer udp) {
            return udp;
        }
        for (Layer child : layer.payload()) {
            UdpLayer udp = findUdp(child);
            if (udp != null) {
                return udp;
            }
        }
        return null;
    }

    private static CaptureRunner openLoopback() {
        for (String device : List.of("lo", "lo0")) {
            try {
                return CaptureRunner.open(CaptureSettings.builder()
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
