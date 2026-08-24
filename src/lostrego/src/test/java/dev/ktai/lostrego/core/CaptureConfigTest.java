package dev.ktai.lostrego.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureConfigTest {

    @Test
    void appliesDefaults() {
        var config = CaptureConfig.builder().device("eth0").build();

        assertEquals("eth0", config.device());
        assertFalse(config.promiscuous());
        assertEquals(CaptureConfig.DEFAULT_SNAPLEN, config.snaplen());
        assertEquals(CaptureConfig.DEFAULT_TIMEOUT_MILLIS, config.timeoutMillis());
        assertEquals(CaptureConfig.DEFAULT_BUFFER_SIZE, config.bufferSize());
        assertNull(config.filter());
        assertFalse(config.immediateMode());
    }

    @Test
    void setsAllFields() {
        var config = CaptureConfig.builder()
                .device("wlan0")
                .promiscuous(true)
                .snaplen(1500)
                .timeoutMillis(500)
                .bufferSize(1_048_576)
                .filter("tcp port 443")
                .immediateMode(true)
                .build();

        assertEquals("wlan0", config.device());
        assertTrue(config.promiscuous());
        assertEquals(1500, config.snaplen());
        assertEquals(500, config.timeoutMillis());
        assertEquals(1_048_576, config.bufferSize());
        assertEquals("tcp port 443", config.filter());
        assertTrue(config.immediateMode());
    }

    @Test
    void rejectsBlankDevice() {
        assertThrows(IllegalArgumentException.class, () -> CaptureConfig.builder().build());
        assertThrows(IllegalArgumentException.class, () -> CaptureConfig.builder().device("  ").build());
    }

    @Test
    void rejectsNonPositiveSnaplen() {
        assertThrows(IllegalArgumentException.class,
                () -> CaptureConfig.builder().device("eth0").snaplen(0).build());
    }

    @Test
    void rejectsNegativeTimeoutAndBuffer() {
        assertThrows(IllegalArgumentException.class,
                () -> CaptureConfig.builder().device("eth0").timeoutMillis(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> CaptureConfig.builder().device("eth0").bufferSize(-1).build());
    }
}
