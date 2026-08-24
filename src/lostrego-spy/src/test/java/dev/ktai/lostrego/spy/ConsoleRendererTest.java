package dev.ktai.lostrego.spy;

import dev.ktai.lostrego.core.CaptureTimestamp;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.spy.common.TrafficRecord;
import dev.ktai.lostrego.spy.common.protocol.EthernetLayer;
import dev.ktai.lostrego.spy.common.protocol.Ipv4Layer;
import dev.ktai.lostrego.spy.common.protocol.Layer;
import dev.ktai.lostrego.spy.common.protocol.TcpLayer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleRendererTest {

    @Test
    void rendersTcpLine() {
        Layer tcp = new TcpLayer(52_314, 80, "SA", List.of());
        Layer ip = new Ipv4Layer("192.168.1.2", "8.8.8.8", 6, List.of(tcp));
        Layer ethernet = new EthernetLayer("66:77:88:66:77:88", "00:11:22:33:44:55", 0x0800, List.of(ip));
        TrafficRecord record = new TrafficRecord(1, new CaptureTimestamp(1_700_000_000, 123_456_000),
                100, 100, LinkType.ETHERNET, ethernet, new byte[0]);

        String line = renderer().line(record);
        assertTrue(line.startsWith("1 "));
        assertTrue(line.contains("TCP"));
        assertTrue(line.contains("192.168.1.2:52314"));
        assertTrue(line.contains("8.8.8.8:80"));
        assertTrue(line.contains("len=100/100"));
        assertTrue(line.contains("[SA]"));
    }

    @Test
    void rendersUnknownEndpointAsQuestionMark() {
        Layer ethernet = new EthernetLayer("", "", 0x88B5, List.of());
        TrafficRecord record = new TrafficRecord(1, new CaptureTimestamp(1, 0), 10, 10,
                LinkType.ETHERNET, ethernet, new byte[0]);
        assertTrue(renderer().line(record).contains("? -> ?"));
    }

    @Test
    void verboseIncludesLinkTypeName() {
        Layer ethernet = new EthernetLayer("", "", 0x0800, List.of());
        TrafficRecord record = new TrafficRecord(1, new CaptureTimestamp(1, 0), 10, 10,
                LinkType.ETHERNET, ethernet, new byte[0]);
        ConsoleRenderer verbose = new ConsoleRenderer(new PrintStream(new ByteArrayOutputStream()), true, false);
        assertTrue(verbose.line(record).contains("ETHERNET"));
    }

    @Test
    void hexDumpContainsOffsetAndAscii() {
        String dump = ConsoleRenderer.hexDump(new byte[]{0x00, 0x11, (byte) 0xff, 0x41, 0x42});
        assertTrue(dump.contains("0000"));
        assertTrue(dump.contains("ff"));
        assertTrue(dump.contains("AB"));
    }

    private static ConsoleRenderer renderer() {
        return new ConsoleRenderer(new PrintStream(new ByteArrayOutputStream()), false, false);
    }
}
