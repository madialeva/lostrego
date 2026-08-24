package dev.ktai.lostrego.core;

import java.util.Map;
import java.util.Optional;

/**
 * The data link layer type of a captured packet, identified by the classic
 * {@code DLT_} code used by libpcap and friends.
 *
 * <p>This is an immutable value type rather than an enum so that a code not
 * covered by the predefined constants is never lost: {@link #of(int)} returns
 * the matching constant for a known code, or an instance that preserves the
 * original code otherwise. Two instances with the same code are equal.
 */
public final class LinkType {

    public static final LinkType NULL = new LinkType(0, "NULL");
    public static final LinkType ETHERNET = new LinkType(1, "ETHERNET");
    public static final LinkType PPP = new LinkType(9, "PPP");
    public static final LinkType IEEE802_11 = new LinkType(105, "IEEE802_11");
    public static final LinkType LOOP = new LinkType(108, "LOOP");
    public static final LinkType LINUX_SLL = new LinkType(113, "LINUX_SLL");
    public static final LinkType IEEE802_11_RADIO = new LinkType(127, "IEEE802_11_RADIO");
    public static final LinkType IPV4 = new LinkType(228, "IPV4");
    public static final LinkType IPV6 = new LinkType(229, "IPV6");
    public static final LinkType LINUX_SLL2 = new LinkType(276, "LINUX_SLL2");

    private static final Map<Integer, LinkType> KNOWN = Map.ofEntries(
            Map.entry(0, NULL),
            Map.entry(1, ETHERNET),
            Map.entry(9, PPP),
            Map.entry(105, IEEE802_11),
            Map.entry(108, LOOP),
            Map.entry(113, LINUX_SLL),
            Map.entry(127, IEEE802_11_RADIO),
            Map.entry(228, IPV4),
            Map.entry(229, IPV6),
            Map.entry(276, LINUX_SLL2));

    private final int code;
    private final String name;

    private LinkType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * Resolves a numeric DLT code to a {@link LinkType}.
     *
     * @param code the DLT code
     * @return the known constant for this code, or an instance carrying the
     *         original code if it is not recognized
     */
    public static LinkType of(int code) {
        LinkType known = KNOWN.get(code);
        return known != null ? known : new LinkType(code, null);
    }

    /**
     * Returns the numeric DLT code of this link type.
     *
     * @return the DLT code
     */
    public int code() {
        return code;
    }

    /**
     * Returns whether this link type is one of the predefined constants.
     *
     * @return {@code true} if the code is a known DLT
     */
    public boolean isKnown() {
        return name != null;
    }

    /**
     * Returns the DLT name, if this is a known link type.
     *
     * @return the name of a known link type, or {@link Optional#empty()} if unknown
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LinkType that)) {
            return false;
        }
        return code == that.code;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(code);
    }

    @Override
    public String toString() {
        return name != null
                ? "LinkType[" + name + " (" + code + ")]"
                : "LinkType[UNKNOWN (" + code + ")]";
    }
}
