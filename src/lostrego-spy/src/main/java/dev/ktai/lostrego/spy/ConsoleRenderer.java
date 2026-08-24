package dev.ktai.lostrego.spy;

import dev.ktai.lostrego.spy.common.TrafficConsumer;
import dev.ktai.lostrego.spy.common.TrafficRecord;
import dev.ktai.lostrego.spy.common.protocol.EthernetLayer;
import dev.ktai.lostrego.spy.common.protocol.Ipv4Layer;
import dev.ktai.lostrego.spy.common.protocol.Ipv6Layer;
import dev.ktai.lostrego.spy.common.protocol.LayerVisitor;
import dev.ktai.lostrego.spy.common.protocol.Protocol;
import dev.ktai.lostrego.spy.common.protocol.TcpLayer;
import dev.ktai.lostrego.spy.common.protocol.UdpLayer;
import dev.ktai.lostrego.spy.common.protocol.UnknownLayer;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Console frontend: renders each {@link TrafficRecord} as a line (and,
 * optionally, a hex/ascii dump). The line is built by walking the protocol
 * tree with a {@link Summary} visitor, keeping the presentation logic here and
 * out of the analysis layer.
 */
public final class ConsoleRenderer implements TrafficConsumer {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS").withZone(ZoneId.systemDefault());

    private final PrintStream out;
    private final boolean verbose;
    private final boolean hex;

    public ConsoleRenderer(PrintStream out, boolean verbose, boolean hex) {
        this.out = out;
        this.verbose = verbose;
        this.hex = hex;
    }

    @Override
    public void accept(TrafficRecord record) {
        out.println(line(record));
        if (hex) {
            out.println(hexDump(record.payload()));
        }
    }

    String line(TrafficRecord record) {
        Summary summary = new Summary();
        record.layers().walk(summary);

        StringBuilder sb = new StringBuilder();
        sb.append(record.sequenceNumber()).append(' ');
        sb.append(TIME.format(record.timestamp().toInstant())).append(' ');
        if (verbose) {
            sb.append(record.linkType().name().orElse("DLT-" + record.linkType().code())).append(' ');
        }
        sb.append(summary.top).append(' ')
                .append(endpoint(summary.source, summary.sourcePort))
                .append(" -> ")
                .append(endpoint(summary.destination, summary.destinationPort))
                .append(" len=").append(record.originalLength()).append('/').append(record.capturedLength());
        if (!summary.flags.isEmpty()) {
            sb.append(" [").append(summary.flags).append(']');
        }
        return sb.toString();
    }

    private static String endpoint(String address, int port) {
        if (address.isEmpty()) {
            return "?";
        }
        return port >= 0 ? address + ":" + port : address;
    }

    static String hexDump(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 16) {
            int row = Math.min(16, data.length - i);
            sb.append(String.format("%04x  ", i));
            StringBuilder hex = new StringBuilder();
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < row; j++) {
                int b = data[i + j] & 0xFF;
                hex.append(String.format("%02x ", b));
                ascii.append(b >= 32 && b < 127 ? (char) b : '.');
            }
            sb.append(hex);
            if (row < 16) {
                sb.append(" ".repeat((16 - row) * 3));
            }
            sb.append(' ').append(ascii).append('\n');
        }
        return sb.toString();
    }

    /**
     * A {@link LayerVisitor} that reduces the protocol tree to the single-line
     * summary: the most specific protocol plus the deepest addressing info.
     */
    private static final class Summary implements LayerVisitor {

        String source = "?";
        String destination = "?";
        int sourcePort = -1;
        int destinationPort = -1;
        String flags = "";
        Protocol top = Protocol.UNKNOWN;

        @Override
        public void visit(EthernetLayer layer) {
            source = layer.source();
            destination = layer.destination();
        }

        @Override
        public void visit(Ipv4Layer layer) {
            source = layer.source();
            destination = layer.destination();
            top = Protocol.IPV4;
        }

        @Override
        public void visit(Ipv6Layer layer) {
            source = layer.source();
            destination = layer.destination();
            top = Protocol.IPV6;
        }

        @Override
        public void visit(TcpLayer layer) {
            sourcePort = layer.sourcePort();
            destinationPort = layer.destinationPort();
            flags = layer.flags();
            top = Protocol.TCP;
        }

        @Override
        public void visit(UdpLayer layer) {
            sourcePort = layer.sourcePort();
            destinationPort = layer.destinationPort();
            top = Protocol.UDP;
        }

        @Override
        public void visit(UnknownLayer layer) {
            // nothing further to extract
        }
    }
}
