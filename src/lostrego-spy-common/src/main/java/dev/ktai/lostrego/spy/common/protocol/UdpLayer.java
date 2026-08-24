package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * A UDP datagram header. Application-level dissection (by port) is not
 * performed for now.
 */
public record UdpLayer(int sourcePort, int destinationPort, List<Layer> payload)
        implements Layer {

    public UdpLayer {
        payload = List.copyOf(payload);
    }

    @Override
    public Protocol protocol() {
        return Protocol.UDP;
    }

    @Override
    public void accept(LayerVisitor visitor) {
        visitor.visit(this);
    }

    static Layer parse(byte[] data, int off) {
        if (data.length < off + 8) {
            return new UnknownLayer();
        }
        int sourcePort = Bytes.u16(data, off);
        int destinationPort = Bytes.u16(data, off + 2);
        return new UdpLayer(sourcePort, destinationPort, List.of());
    }
}
