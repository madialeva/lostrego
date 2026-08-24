package dev.ktai.lostrego.core;

/**
 * Writes captured packets to a savefile in the classic pcap format, readable by
 * offline tools and by {@link PacketCaptures#openOffline(java.nio.file.Path)}.
 *
 * <p>Instances are created through {@link PacketCaptures#openDumper} and are not
 * thread-safe: a single caller must perform all writes. {@link #write(Packet)}
 * persists the packet's timestamp, lengths, link type and payload.
 *
 * <p>Savefiles store timestamps with microsecond precision, so a packet whose
 * nanosecond fraction is not a whole number of microseconds loses the remainder
 * when written and read back.
 *
 * <p>{@link #close()} flushes any buffered packets and releases the underlying
 * resources. It is idempotent.
 */
public interface PacketDumper extends AutoCloseable {

    /**
     * Writes a packet to the savefile.
     *
     * @param packet the packet to persist
     * @throws IllegalStateException if this dumper has been closed
     * @throws NullPointerException  if {@code packet} is {@code null}
     */
    void write(Packet packet);

    /**
     * Flushes any buffered packets to the underlying file without closing the
     * dumper.
     */
    void flush();

    /**
     * Flushes and releases the underlying resources. Idempotent: closing an
     * already-closed dumper is a no-op.
     */
    @Override
    void close();
}
