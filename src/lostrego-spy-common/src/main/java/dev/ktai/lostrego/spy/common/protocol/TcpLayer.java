package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * A TCP segment header. Application-level dissection (by port) is not performed
 * for now.
 */
public record TcpLayer(int sourcePort, int destinationPort, String flags, List<Layer> payload)
        implements Layer {

    public TcpLayer {
        payload = List.copyOf(payload);
    }

    @Override
    public Protocol protocol() {
        return Protocol.TCP;
    }

    @Override
    public void accept(LayerVisitor visitor) {
        visitor.visit(this);
    }

    static Layer parse(byte[] data, int off) {
        if (data.length < off + 20) {
            return new UnknownLayer();
        }
        int sourcePort = Bytes.u16(data, off);
        int destinationPort = Bytes.u16(data, off + 2);
        String flags = Bytes.tcpFlags(data[off + 13]);
        return new TcpLayer(sourcePort, destinationPort, flags, List.of());
    }
}
