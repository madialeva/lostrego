package dev.ktai.lostrego.core.spi;

import dev.ktai.lostrego.core.CaptureConfig;
import dev.ktai.lostrego.core.CaptureException;
import dev.ktai.lostrego.core.LinkType;
import dev.ktai.lostrego.core.PacketCapture;
import dev.ktai.lostrego.core.PacketCaptures;
import dev.ktai.lostrego.core.PacketDumper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCapturesTest {

    @Test
    void opensLiveWithFirstSupportedBackend() {
        FakeCaptureProvider.supported = true;
        var config = CaptureConfig.builder().device("eth0").build();

        try (PacketCapture capture = PacketCaptures.openLive(config)) {
            assertInstanceOf(FakeCapture.class, capture);
        }
    }

    @Test
    void opensLiveWithNamedBackend() {
        FakeCaptureProvider.supported = true;
        var config = CaptureConfig.builder().device("eth0").build();

        try (PacketCapture capture = PacketCaptures.openLive(config, "fake")) {
            assertInstanceOf(FakeCapture.class, capture);
        }
    }

    @Test
    void unknownBackendThrows() {
        FakeCaptureProvider.supported = true;
        var config = CaptureConfig.builder().device("eth0").build();

        assertThrows(CaptureException.class, () -> PacketCaptures.openLive(config, "nope"));
    }

    @Test
    void unavailableBackendThrows() {
        FakeCaptureProvider.supported = false;
        try {
            var config = CaptureConfig.builder().device("eth0").build();
            assertThrows(CaptureException.class, () -> PacketCaptures.openLive(config, "fake"));
        } finally {
            FakeCaptureProvider.supported = true;
        }
    }

    @Test
    void noSupportedBackendThrows() {
        FakeCaptureProvider.supported = false;
        try {
            var config = CaptureConfig.builder().device("eth0").build();
            assertThrows(CaptureException.class, () -> PacketCaptures.openLive(config));
        } finally {
            FakeCaptureProvider.supported = true;
        }
    }

    @Test
    void opensOfflineWithFirstSupportedBackend() {
        FakeCaptureProvider.supported = true;
        try (PacketCapture capture = PacketCaptures.openOffline(Path.of("capture.pcap"))) {
            assertInstanceOf(FakeCapture.class, capture);
        }
    }

    @Test
    void opensOfflineWithNamedBackend() {
        FakeCaptureProvider.supported = true;
        try (PacketCapture capture = PacketCaptures.openOffline(Path.of("capture.pcap"), "fake")) {
            assertInstanceOf(FakeCapture.class, capture);
        }
    }

    @Test
    void noOfflineBackendThrows() {
        FakeCaptureProvider.supported = false;
        try {
            assertThrows(CaptureException.class, () -> PacketCaptures.openOffline(Path.of("capture.pcap")));
        } finally {
            FakeCaptureProvider.supported = true;
        }
    }

    @Test
    void opensDumperWithFirstSupportedBackend() {
        FakeCaptureProvider.supported = true;
        try (PacketDumper dumper = PacketCaptures.openDumper(Path.of("out.pcap"), LinkType.ETHERNET)) {
            assertInstanceOf(FakeDumper.class, dumper);
        }
    }

    @Test
    void opensDumperWithNamedBackend() {
        FakeCaptureProvider.supported = true;
        try (PacketDumper dumper = PacketCaptures.openDumper(Path.of("out.pcap"), LinkType.ETHERNET, "fake")) {
            assertInstanceOf(FakeDumper.class, dumper);
        }
    }

    @Test
    void noWritingBackendThrows() {
        FakeCaptureProvider.supported = false;
        try {
            assertThrows(CaptureException.class,
                    () -> PacketCaptures.openDumper(Path.of("out.pcap"), LinkType.ETHERNET));
        } finally {
            FakeCaptureProvider.supported = true;
        }
    }
}
