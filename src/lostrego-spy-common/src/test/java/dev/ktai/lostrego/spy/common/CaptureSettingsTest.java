package dev.ktai.lostrego.spy.common;

import dev.ktai.lostrego.core.CaptureConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSettingsTest {

    @Test
    void buildsEquivalentLibraryConfig() {
        CaptureSettings settings = CaptureSettings.builder()
                .device("eth0")
                .filter("tcp port 80")
                .promiscuous(true)
                .packetCount(100)
                .build();

        CaptureConfig config = settings.toCaptureConfig();
        assertEquals("eth0", config.device());
        assertEquals("tcp port 80", config.filter());
        assertTrue(config.promiscuous());
        assertEquals(100, settings.packetCount());
    }

    @Test
    void defaultsMatchLibrary() {
        CaptureSettings settings = CaptureSettings.builder().device("lo").build();
        CaptureConfig config = settings.toCaptureConfig();
        assertEquals(CaptureConfig.DEFAULT_SNAPLEN, config.snaplen());
        assertEquals(CaptureConfig.DEFAULT_TIMEOUT_MILLIS, config.timeoutMillis());
        assertEquals(CaptureConfig.DEFAULT_BUFFER_SIZE, config.bufferSize());
        assertFalse(config.immediateMode());
        assertNull(config.filter());
        assertEquals(0, settings.packetCount());
    }

    @Test
    void requiresDevice() {
        assertThrows(IllegalArgumentException.class,
                () -> CaptureSettings.builder().build());
    }

    @Test
    void rejectsNegativePacketCount() {
        assertThrows(IllegalArgumentException.class,
                () -> CaptureSettings.builder().device("lo").packetCount(-1).build());
    }
}
