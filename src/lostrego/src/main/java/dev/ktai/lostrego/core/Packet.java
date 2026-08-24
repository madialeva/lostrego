package dev.ktai.lostrego.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * A captured network packet: raw bytes plus capture metadata.
 *
 * <p>The packet is immutable and self-contained: the payload is copied into a
 * plain {@code byte[]} before it crosses the backend boundary, so a {@code Packet}
 * never references native memory and may be retained safely after the listener
 * callback returns. No protocol parsing is performed; that is a concern of a
 * future upper layer built on top of this model.
 *
 * @param timestamp      capture timestamp
 * @param originalLength length of the packet on the wire, in bytes
 * @param capturedLength length actually captured, in bytes (may be less than
 *                       {@code originalLength} when a snaplen truncates packets)
 * @param linkType       the data link layer type of the packet
 * @param payload        the captured bytes; owned by this packet and must not be mutated
 */
public record Packet(
        CaptureTimestamp timestamp,
        long originalLength,
        long capturedLength,
        LinkType linkType,
        byte[] payload) {

    public Packet {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(linkType, "linkType");
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    /**
     * Returns a defensive copy of the payload.
     *
     * @return a copy of the captured bytes
     */
    public byte[] copyPayload() {
        return payload.clone();
    }

    @Override
    public String toString() {
        return "Packet[timestamp=" + timestamp
                + ", originalLength=" + originalLength
                + ", capturedLength=" + capturedLength
                + ", linkType=" + linkType
                + ", payload=" + payload.length + " bytes]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Packet that)) {
            return false;
        }
        return originalLength == that.originalLength
                && capturedLength == that.capturedLength
                && timestamp.equals(that.timestamp)
                && linkType.equals(that.linkType)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = timestamp.hashCode();
        result = 31 * result + Long.hashCode(originalLength);
        result = 31 * result + Long.hashCode(capturedLength);
        result = 31 * result + linkType.hashCode();
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
