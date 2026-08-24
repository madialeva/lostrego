package dev.ktai.lostrego.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkTypeTest {

    @Test
    void resolvesKnownCodeToConstant() {
        assertSame(LinkType.ETHERNET, LinkType.of(1));
        assertSame(LinkType.LINUX_SLL, LinkType.of(113));
        assertSame(LinkType.IPV4, LinkType.of(228));
    }

    @Test
    void unknownCodePreservesOriginalValue() {
        var linkType = LinkType.of(9_999);
        assertFalse(linkType.isKnown());
        assertEquals(9_999, linkType.code());
        assertTrue(linkType.name().isEmpty());
    }

    @Test
    void unknownInstancesWithSameCodeAreEqual() {
        var a = LinkType.of(7_777);
        var b = LinkType.of(7_777);
        assertNotSame(a, b);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void exposesDltCodeAndName() {
        assertEquals(1, LinkType.ETHERNET.code());
        assertTrue(LinkType.ETHERNET.isKnown());
        assertEquals("ETHERNET", LinkType.ETHERNET.name().orElseThrow());
    }
}
