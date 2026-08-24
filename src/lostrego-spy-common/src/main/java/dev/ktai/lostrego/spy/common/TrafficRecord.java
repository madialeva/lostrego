package dev.ktai.lostrego.spy.common;

import dev.ktai.lostrego.core.CaptureTimestamp;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.Packet;
import dev.ktai.lostrego.spy.common.protocol.Layer;

import java.util.Objects;

/**
 * An immutable, self-contained analysis of a single captured packet: capture
 * metadata plus the dissected protocol layer tree. It carries no native memory
 * and no backend reference, so it can be consumed (and retained) by any
 * frontend. Rendering of the tree is the frontend's concern (text, JSON, ...).
 *
 * @param sequenceNumber the read order of this packet within the capture session (1-based)
 * @param layers         the root of the dissected protocol tree
 * @param payload        the captured bytes, owned by this record and not to be mutated
 */
public record TrafficRecord(
        long sequenceNumber,
        CaptureTimestamp timestamp,
        long originalLength,
        long capturedLength,
        LinkType linkType,
        Layer layers,
        byte[] payload) {

    public TrafficRecord {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(linkType, "linkType");
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(payload, "payload");
    }

    /**
     * Builds a record from a captured packet and its dissected tree.
     *
     * @param packet         the captured packet
     * @param layers         the root of the dissected tree
     * @param sequenceNumber the read order of this packet (1-based)
     * @return a new analysis record
     */
    public static TrafficRecord of(Packet packet, Layer layers, long sequenceNumber) {
        return new TrafficRecord(sequenceNumber, packet.timestamp(), packet.originalLength(),
                packet.capturedLength(), packet.linkType(), layers, packet.payload());
    }

    /**
     * Returns a defensive copy of the captured bytes.
     *
     * @return a copy of the payload
     */
    public byte[] copyPayload() {
        return payload.clone();
    }
}
