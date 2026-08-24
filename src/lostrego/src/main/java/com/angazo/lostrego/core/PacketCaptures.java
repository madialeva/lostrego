package com.angazo.lostrego.core;

import com.angazo.lostrego.core.spi.CaptureProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Entry point of the library: resolves a capture backend in runtime and opens
 * capture sessions.
 *
 * <p>Backends are discovered with {@link ServiceLoader}; {@code core} has no
 * compile-time knowledge of the concrete backends. The returned
 * {@link PacketCapture} is fully backend-transparent: callers only ever deal
 * with the types in {@code com.angazo.lostrego.core}.
 */
public final class PacketCaptures {

    private static final List<CaptureProvider> PROVIDERS = loadProviders();

    private PacketCaptures() {
    }

    private static List<CaptureProvider> loadProviders() {
        return ServiceLoader.load(CaptureProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    /**
     * Opens a live capture session using the first available backend.
     *
     * @param config the capture configuration
     * @return a capture session backed by an available backend
     * @throws CaptureException     if no backend is supported on this platform
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public static PacketCapture openLive(CaptureConfig config) {
        Objects.requireNonNull(config, "config");
        return PROVIDERS.stream()
                .filter(CaptureProvider::isSupported)
                .findFirst()
                .orElseThrow(() -> new CaptureException(
                        "No capture backend is supported on this platform"))
                .openLive(config);
    }

    /**
     * Opens a live capture session using the named backend.
     *
     * @param config  the capture configuration
     * @param backend the backend name, e.g. {@code "libpcap"}
     * @return a capture session backed by the named backend
     * @throws CaptureException     if the backend is not registered or not supported here
     * @throws NullPointerException if {@code config} or {@code backend} is {@code null}
     */
    public static PacketCapture openLive(CaptureConfig config, String backend) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(backend, "backend");
        return PROVIDERS.stream()
                .filter(provider -> provider.name().equals(backend))
                .findFirst()
                .filter(CaptureProvider::isSupported)
                .orElseThrow(() -> new CaptureException(
                        "Capture backend '" + backend + "' is not available on this platform"))
                .openLive(config);
    }

    /**
     * Opens an offline capture session reading packets from a capture file,
     * using the first available backend that supports offline capture.
     *
     * @param file the capture file to read from
     * @return a capture session backed by an available backend
     * @throws CaptureException     if no backend supports offline capture on this platform
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static PacketCapture openOffline(Path file) {
        Objects.requireNonNull(file, "file");
        return PROVIDERS.stream()
                .filter(CaptureProvider::isSupported)
                .filter(CaptureProvider::supportsOffline)
                .findFirst()
                .orElseThrow(() -> new CaptureException(
                        "No capture backend supports offline capture on this platform"))
                .openOffline(file);
    }

    /**
     * Opens an offline capture session using the named backend.
     *
     * @param file    the capture file to read from
     * @param backend the backend name, e.g. {@code "libpcap"}
     * @return a capture session backed by the named backend
     * @throws CaptureException     if the backend is not registered, not supported here,
     *                              or does not support offline capture
     * @throws NullPointerException if {@code file} or {@code backend} is {@code null}
     */
    public static PacketCapture openOffline(Path file, String backend) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(backend, "backend");
        return PROVIDERS.stream()
                .filter(provider -> provider.name().equals(backend))
                .findFirst()
                .filter(CaptureProvider::isSupported)
                .filter(CaptureProvider::supportsOffline)
                .orElseThrow(() -> new CaptureException(
                        "Capture backend '" + backend
                                + "' is not available for offline capture on this platform"))
                .openOffline(file);
    }

    /**
     * Opens a savefile writer for packets with the given link type, using the
     * first available backend that supports savefile writing.
     *
     * @param file     the savefile to write to
     * @param linkType the link type of the packets that will be written
     * @return a dumper backed by an available backend
     * @throws CaptureException     if no backend supports savefile writing on this platform
     * @throws NullPointerException if {@code file} or {@code linkType} is {@code null}
     */
    public static PacketDumper openDumper(Path file, LinkType linkType) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(linkType, "linkType");
        return PROVIDERS.stream()
                .filter(CaptureProvider::isSupported)
                .filter(CaptureProvider::supportsWriting)
                .findFirst()
                .orElseThrow(() -> new CaptureException(
                        "No capture backend supports savefile writing on this platform"))
                .openDumper(file, linkType);
    }

    /**
     * Opens a savefile writer using the named backend.
     *
     * @param file     the savefile to write to
     * @param linkType the link type of the packets that will be written
     * @param backend  the backend name, e.g. {@code "libpcap"}
     * @return a dumper backed by the named backend
     * @throws CaptureException     if the backend is not registered, not supported here,
     *                              or does not support savefile writing
     * @throws NullPointerException if {@code file}, {@code linkType} or {@code backend} is {@code null}
     */
    public static PacketDumper openDumper(Path file, LinkType linkType, String backend) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(linkType, "linkType");
        Objects.requireNonNull(backend, "backend");
        return PROVIDERS.stream()
                .filter(provider -> provider.name().equals(backend))
                .findFirst()
                .filter(CaptureProvider::isSupported)
                .filter(CaptureProvider::supportsWriting)
                .orElseThrow(() -> new CaptureException(
                        "Capture backend '" + backend
                                + "' is not available for savefile writing on this platform"))
                .openDumper(file, linkType);
    }
}
