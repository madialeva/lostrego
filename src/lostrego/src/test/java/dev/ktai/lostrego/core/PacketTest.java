package dev.ktai.lostrego.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketTest {

    @Test
    void clonesInputPayloadDefensively() {
        byte[] input = {1, 2, 3, 4};
        var packet = new Packet(new CaptureTimestamp(0, 0), 4, 4, LinkType.ETHERNET, input);

        input[0] = 99;

        assertEquals(1, packet.payload()[0]);
    }

    @Test
    void copyPayloadReturnsIndependentCopy() {
        var packet = new Packet(new CaptureTimestamp(0, 0), 3, 3, LinkType.ETHERNET, new byte[]{1, 2, 3});

        byte[] copy = packet.copyPayload();
        assertNotSame(packet.payload(), copy);
        copy[0] = 42;

        assertEquals(1, packet.payload()[0]);
    }

    @Test
    void distinguishesOriginalAndCapturedLengths() {
        var packet = new Packet(new CaptureTimestamp(0, 0), 1500, 64, LinkType.ETHERNET, new byte[64]);

        assertEquals(1500, packet.originalLength());
        assertEquals(64, packet.capturedLength());
    }

    @Test
    void equalsAndHashCodeUsePayloadContents() {
        var a = new Packet(new CaptureTimestamp(1, 0), 10, 10, LinkType.ETHERNET, new byte[]{1, 2});
        var b = new Packet(new CaptureTimestamp(1, 0), 10, 10, LinkType.ETHERNET, new byte[]{1, 2});
        var c = new Packet(new CaptureTimestamp(1, 0), 10, 10, LinkType.ETHERNET, new byte[]{9, 9});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotSame(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void rejectsNullComponents() {
        var ts = new CaptureTimestamp(0, 0);
        assertThrows(NullPointerException.class,
                () -> new Packet(null, 1, 1, LinkType.ETHERNET, new byte[1]));
        assertThrows(NullPointerException.class,
                () -> new Packet(ts, 1, 1, null, new byte[1]));
        assertThrows(NullPointerException.class,
                () -> new Packet(ts, 1, 1, LinkType.ETHERNET, null));
    }

    @Test
    void retainsPayloadIntegrity() {
        byte[] payload = new byte[16];
        Arrays.fill(payload, (byte) 0xAB);
        var packet = new Packet(new CaptureTimestamp(7, 5), 16, 16, LinkType.ETHERNET, payload);

        assertArrayEquals(payload, packet.payload());
    }
}
