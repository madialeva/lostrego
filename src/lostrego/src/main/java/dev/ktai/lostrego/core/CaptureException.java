package dev.ktai.lostrego.core;

/**
 * The unified exception type of the library, thrown for capture failures,
 * unavailable backends and configuration errors.
 *
 * <p>It is an unchecked exception: capture failures happen on an internal
 * thread and are rethrown from {@link PacketCapture#stop()} or
 * {@link PacketCapture#close()}; a checked exception would force try/catch into
 * every listener and pollute the functional style of the API.
 */
public class CaptureException extends RuntimeException {

    public CaptureException(String message) {
        super(message);
    }

    public CaptureException(String message, Throwable cause) {
        super(message, cause);
    }

    public CaptureException(Throwable cause) {
        super(cause);
    }
}
