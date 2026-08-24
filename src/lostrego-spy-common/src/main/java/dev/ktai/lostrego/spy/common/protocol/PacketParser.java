package dev.ktai.lostrego.spy.common.protocol;

import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.Packet;

/**
 * Entry point of packet dissection: turns a raw {@link Packet} into a tree of
 * {@link Layer}s. The link type selects the first protocol; each protocol's
 * parser then recurses into its own payload.
 */
public final class PacketParser {

    private PacketParser() {
    }

    /**
     * Dissects a captured packet into its protocol layer tree.
     *
     * @param packet the packet to dissect
     * @return the root layer of the tree
     */
    public static Layer parse(Packet packet) {
        byte[] data = packet.payload();
        if (packet.linkType() == LinkType.ETHERNET) {
            return EthernetLayer.parse(data, 0);
        }
        return new UnknownLayer();
    }
}
