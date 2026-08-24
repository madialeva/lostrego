package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * An IPv4 header. The encapsulated protocol is chosen from the protocol number.
 */
public record Ipv4Layer(String source, String destination, int nextProtocol, List<Layer> payload)
        implements Layer {

    public Ipv4Layer {
        payload = List.copyOf(payload);
    }

    @Override
    public Protocol protocol() {
        return Protocol.IPV4;
    }

    @Override
    public void accept(LayerVisitor visitor) {
        visitor.visit(this);
    }

    static Layer parse(byte[] data, int off) {
        if (data.length < off + 20) {
            return new UnknownLayer();
        }
        int ihl = (data[off] & 0x0F) * 4;
        if (ihl < 20 || data.length < off + ihl) {
            return new UnknownLayer();
        }
        String source = Bytes.ip(data, off + 12, 4);
        String destination = Bytes.ip(data, off + 16, 4);
        int nextProtocol = data[off + 9] & 0xFF;
        List<Layer> payload = switch (nextProtocol) {
            case 6 -> List.of(TcpLayer.parse(data, off + ihl));
            case 17 -> List.of(UdpLayer.parse(data, off + ihl));
            default -> List.of();
        };
        return new Ipv4Layer(source, destination, nextProtocol, payload);
    }
}
