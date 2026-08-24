package dev.ktai.lostrego.spy.common.protocol;

/**
 * A visitor over the concrete {@link Layer} types. Each frontend implements one
 * formatter (text, JSON, ...) as a visitor; adding a protocol adds a
 * {@code visit} method here, so every formatter is updated at compile time.
 */
public interface LayerVisitor {

    void visit(EthernetLayer layer);

    void visit(Ipv4Layer layer);

    void visit(Ipv6Layer layer);

    void visit(TcpLayer layer);

    void visit(UdpLayer layer);

    void visit(UnknownLayer layer);
}
