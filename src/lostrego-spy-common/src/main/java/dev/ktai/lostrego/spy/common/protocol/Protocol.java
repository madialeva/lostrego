package dev.ktai.lostrego.spy.common.protocol;

/**
 * The protocol identified for a {@link Layer}. A closed enumeration with a
 * stable id, so a layer's protocol is a type-safe value rather than a free-form
 * string.
 */
public enum Protocol {

    UNKNOWN(0, "Unknown"),
    ETHERNET(1, "Ethernet"),
    IPV4(2, "IPv4"),
    IPV6(3, "IPv6"),
    TCP(4, "TCP"),
    UDP(5, "UDP");

    private final int id;
    private final String label;

    Protocol(int id, String label) {
        this.id = id;
        this.label = label;
    }

    /** @return the stable numeric id of this protocol */
    public int id() {
        return id;
    }

    /** @return the human-readable label of this protocol */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
