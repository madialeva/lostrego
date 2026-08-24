package dev.ktai.lostrego.spy.common.protocol;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Small byte-array helpers shared by the layer parsers. Package-private: not
 * part of the public surface of the protocol package.
 */
final class Bytes {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Bytes() {
    }

    static int u16(byte[] data, int off) {
        return ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
    }

    static String ip(byte[] data, int off, int length) {
        byte[] addr = new byte[length];
        System.arraycopy(data, off, addr, 0, length);
        try {
            return InetAddress.getByAddress(addr).getHostAddress();
        } catch (UnknownHostException e) {
            return "?";
        }
    }

    static String mac(byte[] data, int off) {
        char[] out = new char[17];
        for (int i = 0; i < 6; i++) {
            int b = data[off + i] & 0xFF;
            out[i * 3] = HEX[b >>> 4];
            out[i * 3 + 1] = HEX[b & 0x0F];
            if (i < 5) {
                out[i * 3 + 2] = ':';
            }
        }
        return new String(out);
    }

    static String tcpFlags(int flags) {
        StringBuilder sb = new StringBuilder(6);
        if ((flags & 0x02) != 0) {
            sb.append('S');
        }
        if ((flags & 0x10) != 0) {
            sb.append('A');
        }
        if ((flags & 0x08) != 0) {
            sb.append('P');
        }
        if ((flags & 0x01) != 0) {
            sb.append('F');
        }
        if ((flags & 0x04) != 0) {
            sb.append('R');
        }
        if ((flags & 0x20) != 0) {
            sb.append('U');
        }
        return sb.toString();
    }
}
