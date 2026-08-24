package com.angazo.lostrego.core.spi;

import com.angazo.lostrego.core.CaptureConfig;
import com.angazo.lostrego.core.CaptureException;
import com.angazo.lostrego.core.LinkType;
import com.angazo.lostrego.core.PacketCapture;
import com.angazo.lostrego.core.PacketDumper;

import java.nio.file.Path;

/**
 * The service provider contract implemented by each capture backend (libpcap,
 * pdpk, npcap).
 *
 * <p>This package is an internal contract between {@code core} and the backends;
 * it is not exported and is not part of the public API.
 */
public interface CaptureProvider {

    /**
     * Returns the stable name of this backend, e.g. {@code "libpcap"}.
     *
     * @return the backend name
     */
    String name();

    /**
     * Returns whether this backend is usable on the current platform, i.e. its
     * native library is available.
     *
     * <p>An unavailable backend must return {@code false} rather than throwing,
     * so it never prevents other backends from working.
     *
     * @return {@code true} if this backend can be used here
     */
    boolean isSupported();

    /**
     * Opens a live capture session using this backend.
     *
     * @param config the capture configuration
     * @return a new capture session
     * @throws CaptureException if the session cannot be opened
     */
    PacketCapture openLive(CaptureConfig config) throws CaptureException;

    /**
     * Returns whether this backend can read packets from a capture file
     * (offline capture).
     *
     * @return {@code true} if this backend supports offline capture
     */
    default boolean supportsOffline() {
        return false;
    }

    /**
     * Returns whether this backend can write packets to a savefile.
     *
     * @return {@code true} if this backend supports savefile writing
     */
    default boolean supportsWriting() {
        return false;
    }

    /**
     * Opens an offline capture session reading from a capture file.
     *
     * @param file the capture file to read from
     * @return a new capture session
     * @throws CaptureException if this backend does not support offline capture
     */
    default PacketCapture openOffline(Path file) throws CaptureException {
        throw new CaptureException("Offline capture is not supported by this backend");
    }

    /**
     * Opens a savefile writer for packets with the given link type.
     *
     * @param file     the savefile to write to
     * @param linkType the link type of the packets that will be written
     * @return a new dumper
     * @throws CaptureException if this backend does not support savefile writing
     */
    default PacketDumper openDumper(Path file, LinkType linkType) throws CaptureException {
        throw new CaptureException("Savefile writing is not supported by this backend");
    }
}
