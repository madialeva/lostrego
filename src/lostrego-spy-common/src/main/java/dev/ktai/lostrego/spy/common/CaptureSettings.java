package dev.ktai.lostrego.spy.common;

import dev.ktai.lostrego.core.CaptureConfig;

/**
 * The capture configuration chosen by the user, independent of any frontend.
 * Both the console app and a future graphical UI build one of these and hand it
 * to the analysis layer; no frontend-specific values (rendering options, etc.)
 * belong here.
 *
 * <p>Instances are immutable and created through {@link #builder()}. All fields
 * have sensible defaults except {@code device}, which is required.
 */
public final class CaptureSettings {

    public static final int DEFAULT_SNAPLEN = CaptureConfig.DEFAULT_SNAPLEN;
    public static final int DEFAULT_TIMEOUT_MILLIS = CaptureConfig.DEFAULT_TIMEOUT_MILLIS;
    public static final int DEFAULT_BUFFER_SIZE = CaptureConfig.DEFAULT_BUFFER_SIZE;

    private final String device;
    private final boolean promiscuous;
    private final String filter;
    private final int snaplen;
    private final int timeoutMillis;
    private final int bufferSize;
    private final boolean immediateMode;
    private final long packetCount;

    private CaptureSettings(Builder builder) {
        this.device = builder.device;
        this.promiscuous = builder.promiscuous;
        this.filter = builder.filter;
        this.snaplen = builder.snaplen;
        this.timeoutMillis = builder.timeoutMillis;
        this.bufferSize = builder.bufferSize;
        this.immediateMode = builder.immediateMode;
        this.packetCount = builder.packetCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @return the network device to capture from */
    public String device() {
        return device;
    }

    /** @return whether promiscuous mode is enabled */
    public boolean promiscuous() {
        return promiscuous;
    }

    /** @return the BPF filter expression, or {@code null} for no filter */
    public String filter() {
        return filter;
    }

    /** @return the maximum number of bytes captured per packet */
    public int snaplen() {
        return snaplen;
    }

    /** @return the read timeout in milliseconds */
    public int timeoutMillis() {
        return timeoutMillis;
    }

    /** @return the kernel buffer size in bytes, or {@code 0} for the backend default */
    public int bufferSize() {
        return bufferSize;
    }

    /** @return whether immediate (unbuffered) delivery mode is enabled */
    public boolean immediateMode() {
        return immediateMode;
    }

    /** @return the packet limit, or {@code 0} for unlimited */
    public long packetCount() {
        return packetCount;
    }

    /**
     * Builds the equivalent library {@link CaptureConfig}.
     *
     * @return the library capture configuration
     */
    public CaptureConfig toCaptureConfig() {
        return CaptureConfig.builder()
                .device(device)
                .promiscuous(promiscuous)
                .filter(filter)
                .snaplen(snaplen)
                .timeoutMillis(timeoutMillis)
                .bufferSize(bufferSize)
                .immediateMode(immediateMode)
                .build();
    }

    /**
     * Builder for {@link CaptureSettings}.
     */
    public static final class Builder {

        private String device;
        private boolean promiscuous;
        private String filter;
        private int snaplen = DEFAULT_SNAPLEN;
        private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private boolean immediateMode;
        private long packetCount;

        private Builder() {
        }

        public Builder device(String device) {
            this.device = device;
            return this;
        }

        public Builder promiscuous(boolean promiscuous) {
            this.promiscuous = promiscuous;
            return this;
        }

        public Builder filter(String filter) {
            this.filter = filter;
            return this;
        }

        public Builder snaplen(int snaplen) {
            this.snaplen = snaplen;
            return this;
        }

        public Builder timeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder immediateMode(boolean immediateMode) {
            this.immediateMode = immediateMode;
            return this;
        }

        public Builder packetCount(long packetCount) {
            this.packetCount = packetCount;
            return this;
        }

        /**
         * Builds the settings after validating them.
         *
         * @return a new immutable {@link CaptureSettings}
         * @throws IllegalArgumentException if {@code device} is blank, {@code snaplen <= 0},
         *                                  {@code timeoutMillis < 0}, {@code bufferSize < 0}
         *                                  or {@code packetCount < 0}
         */
        public CaptureSettings build() {
            if (device == null || device.isBlank()) {
                throw new IllegalArgumentException("device must not be blank");
            }
            if (snaplen <= 0) {
                throw new IllegalArgumentException("snaplen must be positive");
            }
            if (timeoutMillis < 0) {
                throw new IllegalArgumentException("timeoutMillis must be >= 0");
            }
            if (bufferSize < 0) {
                throw new IllegalArgumentException("bufferSize must be >= 0");
            }
            if (packetCount < 0) {
                throw new IllegalArgumentException("packetCount must be >= 0");
            }
            return new CaptureSettings(this);
        }
    }
}
