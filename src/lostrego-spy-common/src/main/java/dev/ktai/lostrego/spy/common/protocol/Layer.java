package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * One protocol layer of a dissected packet. A packet is seen as a tree of
 * layers: each layer's payload encapsulates zero or more further layers, down
 * to the highest (application) protocol.
 *
 * <p>Every layer exposes its own typed fields and the encapsulated layers, and
 * accepts a {@link LayerVisitor} so a frontend can render the tree (text, JSON,
 * ...) without touching the parsing logic.
 */
public interface Layer {

    /**
     * @return the protocol of this layer
     */
    Protocol protocol();

    /**
     * @return the layers encapsulated in this layer's payload (usually 0 or 1)
     */
    List<Layer> payload();

    /**
     * Dispatches this layer to the visitor's matching method.
     *
     * @param visitor the visitor to dispatch to
     */
    void accept(LayerVisitor visitor);

    /**
     * Visits this layer and, depth-first, every layer encapsulated in it.
     *
     * @param visitor the visitor to walk the tree with
     */
    default void walk(LayerVisitor visitor) {
        accept(visitor);
        for (Layer child : payload()) {
            child.walk(visitor);
        }
    }
}
