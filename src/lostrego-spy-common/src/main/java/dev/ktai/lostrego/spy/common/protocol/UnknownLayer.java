package dev.ktai.lostrego.spy.common.protocol;

import java.util.List;

/**
 * A payload that could not be dissected (unsupported link type or truncated
 * header). Carries no fields and no encapsulated layers.
 */
public record UnknownLayer() implements Layer {

    @Override
    public Protocol protocol() {
        return Protocol.UNKNOWN;
    }

    @Override
    public List<Layer> payload() {
        return List.of();
    }

    @Override
    public void accept(LayerVisitor visitor) {
        visitor.visit(this);
    }
}
