package dev.ktai.lostrego.core;

/**
 * Capture counters for a capture session, mirroring the three values exposed by
 * {@code pcap_stats}.
 *
 * @param received         number of packets received by the capture
 * @param dropped          number of packets dropped by the capture (e.g. because
 *                         the listener or the kernel buffer could not keep up)
 * @param interfaceDropped number of packets dropped by the network interface
 */
public record CaptureStatistics(long received, long dropped, long interfaceDropped) {
}
