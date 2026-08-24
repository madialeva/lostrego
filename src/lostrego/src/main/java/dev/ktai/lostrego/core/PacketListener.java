package dev.ktai.lostrego.core;

/**
 * Receives packets from a running {@link PacketCapture}.
 *
 * <p>The listener is invoked on the capture's internal thread, one call per
 * packet and sequentially. Implementations must be fast: because delivery is
 * direct (no queue), a slow listener causes packets to be dropped by the
 * kernel or NIC. If {@link #onPacket(Packet)} throws, the capture is stopped and
 * the failure is rethrown from {@link PacketCapture#stop()} or
 * {@link PacketCapture#close()}.
 */
@FunctionalInterface
public interface PacketListener {

    /**
     * Called for each captured packet.
     *
     * @param packet the captured packet; immutable and safe to retain
     */
    void onPacket(Packet packet);
}
