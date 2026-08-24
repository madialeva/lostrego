package com.angazo.lostrego.backend.libpcap;

import com.angazo.lostrego.core.CaptureConfig;
import com.angazo.lostrego.core.LinkType;
import com.angazo.lostrego.core.PacketCapture;
import com.angazo.lostrego.core.PacketDumper;
import com.angazo.lostrego.core.spi.CaptureProvider;

import java.nio.file.Path;

/**
 * {@link CaptureProvider} backed by libpcap, available on Linux and macOS.
 */
public final class LibpcapProvider implements CaptureProvider {

    @Override
    public String name() {
        return "libpcap";
    }

    @Override
    public boolean isSupported() {
        return LibpcapNative.isAvailable();
    }

    @Override
    public PacketCapture openLive(CaptureConfig config) {
        return LibpcapCapture.open(config);
    }

    @Override
    public boolean supportsOffline() {
        return LibpcapNative.isAvailable();
    }

    @Override
    public boolean supportsWriting() {
        return LibpcapNative.isAvailable();
    }

    @Override
    public PacketCapture openOffline(Path file) {
        return LibpcapCapture.openOffline(file);
    }

    @Override
    public PacketDumper openDumper(Path file, LinkType linkType) {
        return LibpcapDumper.open(file, linkType);
    }
}
