package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * An IPv6 header. The encapsulated protocol is chosen from the next-header
 * field; IPv6 extension headers are not followed for now.
 */
public record Ipv6Layer(String source, String destination, int nextProtocol, List<Layer> payload)
        implements Layer {

    public Ipv6Layer {
        payload = List.copyOf(payload);
    }

    @Override
    public Protocol protocol() {
        return Protocol.IPV6;
    }

    @Override
    public void accept(LayerVisitor visitor) {
        visitor.visit(this);
    }

    static Layer parse(byte[] data, int off) {
        if (data.length < off + 40) {
            return new UnknownLayer();
        }
        String source = Bytes.ip(data, off + 8, 16);
        String destination = Bytes.ip(data, off + 24, 16);
        int nextProtocol = data[off + 6] & 0xFF;
        List<Layer> payload = switch (nextProtocol) {
            case 6 -> List.of(TcpLayer.parse(data, off + 40));
            case 17 -> List.of(UdpLayer.parse(data, off + 40));
            default -> List.of();
        };
        return new Ipv6Layer(source, destination, nextProtocol, payload);
    }
}
