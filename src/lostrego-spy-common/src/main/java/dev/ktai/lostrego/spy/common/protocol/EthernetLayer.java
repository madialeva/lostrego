package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * An Ethernet II frame header. The encapsulated protocol is chosen from the
 * ethertype.
 */
public record EthernetLayer(String source, String destination, int etherType, List<Layer> payload)
        implements Layer {

    public EthernetLayer {
        payload = List.copyOf(payload);
    }

    @Override
    public Protocol protocol() {
        return Protocol.ETHERNET;
    }

    @Override
    public void accept(LayerVisitor visitor) {
        visitor.visit(this);
    }

    static Layer parse(byte[] data, int off) {
        if (data.length < off + 14) {
            return new UnknownLayer();
        }
        String source = Bytes.mac(data, off + 6);
        String destination = Bytes.mac(data, off);
        int etherType = Bytes.u16(data, off + 12);
        List<Layer> payload = switch (etherType) {
            case 0x0800 -> List.of(Ipv4Layer.parse(data, off + 14));
            case 0x86DD -> List.of(Ipv6Layer.parse(data, off + 14));
            default -> List.of();
        };
        return new EthernetLayer(source, destination, etherType, payload);
    }
}
