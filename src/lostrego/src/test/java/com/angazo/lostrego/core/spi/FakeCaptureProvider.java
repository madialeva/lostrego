package com.angazo.lostrego.core.spi;

import com.angazo.lostrego.core.CaptureConfig;
import com.angazo.lostrego.core.LinkType;
import com.angazo.lostrego.core.PacketCapture;
import com.angazo.lostrego.core.PacketDumper;

import java.nio.file.Path;

/**
 * A fake {@link CaptureProvider} registered through
 * {@code META-INF/services} so the factory tests can exercise backend
 * discovery and selection without any native backend.
 */
public final class FakeCaptureProvider implements CaptureProvider {

    /** Controls {@link #isSupported()} so tests can simulate an unavailable backend. */
    static volatile boolean supported = true;

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public boolean isSupported() {
        return supported;
    }

    @Override
    public PacketCapture openLive(CaptureConfig config) {
        return new FakeCapture();
    }

    @Override
    public boolean supportsOffline() {
        return supported;
    }

    @Override
    public boolean supportsWriting() {
        return supported;
    }

    @Override
    public PacketCapture openOffline(Path file) {
        return new FakeCapture();
    }

    @Override
    public PacketDumper openDumper(Path file, LinkType linkType) {
        return new FakeDumper();
    }
}
