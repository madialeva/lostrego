package dev.ktai.lostrego.spy.common;

import dev.ktai.lostrego.core.CaptureTimestamp;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.Packet;
import dev.ktai.lostrego.spy.common.protocol.EthernetLayer;
import dev.ktai.lostrego.spy.common.protocol.Ipv4Layer;
import dev.ktai.lostrego.spy.common.protocol.Layer;
import dev.ktai.lostrego.spy.common.protocol.PacketParser;
import dev.ktai.lostrego.spy.common.protocol.TcpLayer;
import dev.ktai.lostrego.spy.common.protocol.UdpLayer;
import dev.ktai.lostrego.spy.common.protocol.UnknownLayer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketParserTest {

    private static final byte[] SRC_MAC = {0x66, 0x77, (byte) 0x88, 0x66, 0x77, (byte) 0x88};
    private static final byte[] DST_MAC = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55};

    @Test
    void dissectsIpv4Tcp() {
        Layer root = PacketParser.parse(packet(tcpPayload("192.168.1.2", "8.8.8.8", 52_314, 80, 0x12)));

        EthernetLayer ethernet = assertInstanceOf(EthernetLayer.class, root);
        assertEquals(0x0800, ethernet.etherType());
        assertEquals("00:11:22:33:44:55", ethernet.destination());
        assertEquals("66:77:88:66:77:88", ethernet.source());

        Ipv4Layer ip = assertInstanceOf(Ipv4Layer.class, ethernet.payload().get(0));
        assertEquals("192.168.1.2", ip.source());
        assertEquals("8.8.8.8", ip.destination());

        TcpLayer tcp = assertInstanceOf(TcpLayer.class, ip.payload().get(0));
        assertEquals(52_314, tcp.sourcePort());
        assertEquals(80, tcp.destinationPort());
        assertEquals("SA", tcp.flags());
    }

    @Test
    void dissectsIpv4Udp() {
        Layer root = PacketParser.parse(packet(udpPayload("10.0.0.1", "10.0.0.2", 12_345, 53)));

        EthernetLayer ethernet = assertInstanceOf(EthernetLayer.class, root);
        Ipv4Layer ip = assertInstanceOf(Ipv4Layer.class, ethernet.payload().get(0));
        UdpLayer udp = assertInstanceOf(UdpLayer.class, ip.payload().get(0));
        assertEquals(12_345, udp.sourcePort());
        assertEquals(53, udp.destinationPort());
    }

    @Test
    void truncatedEthernetFallsBackToUnknown() {
        byte[] data = {0x00, 0x11, 0x22};
        Packet packet = new Packet(new CaptureTimestamp(1, 0), 60, 3, LinkType.ETHERNET, data);
        assertInstanceOf(UnknownLayer.class, PacketParser.parse(packet));
    }

    @Test
    void unknownEtherTypeHasNoPayload() {
        byte[] eth = ethernetHeader(0x88B5);
        Packet packet = new Packet(new CaptureTimestamp(1, 0), eth.length, eth.length, LinkType.ETHERNET, eth);
        EthernetLayer ethernet = assertInstanceOf(EthernetLayer.class, PacketParser.parse(packet));
        assertTrue(ethernet.payload().isEmpty());
    }

    @Test
    void unsupportedLinkTypeFallsBackToUnknown() {
        byte[] data = {1, 2, 3, 4};
        Packet packet = new Packet(new CaptureTimestamp(1, 0), data.length, data.length, LinkType.IPV4, data);
        assertInstanceOf(UnknownLayer.class, PacketParser.parse(packet));
    }

    @Test
    void trafficRecordPreservesMetadata() {
        byte[] payload = tcpPayload("1.1.1.1", "2.2.2.2", 1, 2, 0x10);
        Packet packet = new Packet(new CaptureTimestamp(1, 0), 60, payload.length, LinkType.ETHERNET, payload);
        TrafficRecord record = TrafficRecord.of(packet, PacketParser.parse(packet), 7L);
        assertEquals(7L, record.sequenceNumber());
        assertEquals(60, record.originalLength());
        assertEquals(payload.length, record.capturedLength());
        assertEquals(LinkType.ETHERNET, record.linkType());
        assertArrayEquals(payload, record.payload());
        assertInstanceOf(EthernetLayer.class, record.layers());
    }

    private static Packet packet(byte[] payload) {
        return new Packet(new CaptureTimestamp(1_000, 250_000_000),
                payload.length, payload.length, LinkType.ETHERNET, payload);
    }

    private static byte[] tcpPayload(String srcIp, String dstIp, int srcPort, int dstPort, int flags) {
        return concat(ethernetHeader(0x0800), ipv4Header(6, srcIp, dstIp), tcpHeader(srcPort, dstPort, flags));
    }

    private static byte[] udpPayload(String srcIp, String dstIp, int srcPort, int dstPort) {
        return concat(ethernetHeader(0x0800), ipv4Header(17, srcIp, dstIp), udpHeader(srcPort, dstPort));
    }

    private static byte[] ethernetHeader(int etherType) {
        byte[] eth = new byte[14];
        System.arraycopy(DST_MAC, 0, eth, 0, 6);
        System.arraycopy(SRC_MAC, 0, eth, 6, 6);
        eth[12] = (byte) (etherType >> 8);
        eth[13] = (byte) etherType;
        return eth;
    }

    private static byte[] ipv4Header(int proto, String srcIp, String dstIp) {
        byte[] ip = new byte[20];
        ip[0] = 0x45; // IPv4, IHL = 5
        ip[8] = 64;   // TTL
        ip[9] = (byte) proto;
        System.arraycopy(inet(srcIp), 0, ip, 12, 4);
        System.arraycopy(inet(dstIp), 0, ip, 16, 4);
        return ip;
    }

    private static byte[] tcpHeader(int srcPort, int dstPort, int flags) {
        byte[] tcp = new byte[20];
        tcp[0] = (byte) (srcPort >> 8);
        tcp[1] = (byte) srcPort;
        tcp[2] = (byte) (dstPort >> 8);
        tcp[3] = (byte) dstPort;
        tcp[12] = 0x50; // data offset (5 * 4 = 20)
        tcp[13] = (byte) flags;
        return tcp;
    }

    private static byte[] udpHeader(int srcPort, int dstPort) {
        byte[] udp = new byte[8];
        udp[0] = (byte) (srcPort >> 8);
        udp[1] = (byte) srcPort;
        udp[2] = (byte) (dstPort >> 8);
        udp[3] = (byte) dstPort;
        return udp;
    }

    private static byte[] inet(String ip) {
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("invalid literal IP: " + ip, e);
        }
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] a : arrays) {
            length += a.length;
        }
        byte[] out = new byte[length];
        int off = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, off, a.length);
            off += a.length;
        }
        return out;
    }
}
