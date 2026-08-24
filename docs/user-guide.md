# Lostrego — User Guide

Lostrego is a cross-platform **packet capture library for Java**. It talks directly to native
operating-system libraries through the **FFM API** (`java.lang.foreign`, standard in Java 25) —
no JNI, no JNA — and exposes a single, backend-transparent API so your code never has to know
whether packets are coming from libpcap, PDPK, or npcap.

This guide shows what the library is for and, feature by feature, how to use it with small,
copy-pasteable examples. It does **not** cover the internal backend implementation; for that,
read the code or the `openspec/` specs.

---

## 1. Requirements

- **Java 25** (the FFM API is a core dependency of the design).
- A native backend library available at runtime:
  - **Linux / macOS**: [libpcap](https://www.tcpdump.org/) (on Debian/Ubuntu: `sudo apt install libpcap-dev`).
  - **Windows**: [npcap](https://npcap.com/) (planned backend).
  - **Linux**: PDPK (planned high-performance backend).
- To **capture live traffic**, your process needs the privileges required by the backend —
  typically `root` or `CAP_NET_RAW` on Linux.

## 2. Adding Lostrego to your project

Lostrego is a single JPMS module named `dev.ktai.lostrego`. The library is not yet published
to Maven Central (that is a planned milestone), so today you build it from source:

```bash
git clone https://github.com/madialeva/lostrego.git
cd lostrego/src
./gradlew build
```

In your own `module-info.java`, declare the dependency:

```java
module your.app {
    requires dev.ktai.lostrego;
}
```

Everything you need lives in a single package: `dev.ktai.lostrego.core`.

## 3. Core concepts

### 3.1 The capture session — `PacketCapture`

A capture session has a simple asynchronous lifecycle:

```
open ── start(listener) ──► RUNNING ── stop() ──► stopped
                              │
                              └─► close() (also stops and frees native resources)
```

- `start(PacketListener)` launches an internal capture thread and **returns immediately**.
- `stop()` stops the capture and blocks until the internal thread has finished.
- `close()` stops (if running) and releases native resources. It is idempotent and safe to use
  with try-with-resources.

The session delivers every captured packet to your listener on that internal thread, one call
per packet.

### 3.2 The packet model — `Packet`

Every packet is an **immutable, self-contained** value you can keep after the callback returns:

```java
Packet packet = ...;
CaptureTimestamp ts = packet.timestamp();   // seconds + nanosecond fraction
long originalLength  = packet.originalLength(); // length on the wire
long capturedLength  = packet.capturedLength(); // length actually captured
LinkType linkType    = packet.linkType();       // data link layer type
byte[] raw           = packet.payload();        // captured bytes
```

- `CaptureTimestamp` exposes `seconds()`, `nanosOfSecond()` and a convenience `toInstant()`.
- `originalLength` may be larger than `capturedLength` when a *snaplen* truncates packets.
- `LinkType` identifies the data link layer (`LinkType.ETHERNET`, `LinkType.LINUX_SLL`, …) and
  preserves the numeric DLT code even for types Lostrego does not know by name.

### 3.3 The entry point — `PacketCaptures`

`PacketCaptures` is the factory that discovers the available backend at runtime and opens
sessions. You never import backend classes — only `dev.ktai.lostrego.core`.

## 4. Live capture

```java
import dev.ktai.lostrego.core.*;

CaptureConfig config = CaptureConfig.builder()
        .device("eth0")
        .promiscuous(true)
        .build();

try (PacketCapture capture = PacketCaptures.openLive(config)) {
    capture.start(packet ->
            System.out.println("Captured " + packet.capturedLength() + " bytes"));

    Thread.sleep(10_000); // capture for a while

    capture.stop(); // block until the capture thread finishes
}
```

`CaptureConfig` options (all except `device` are optional and defaulted):

| Option | Default | Meaning |
|---|---|---|
| `device(String)` | required | Network device, e.g. `"eth0"`, `"lo"`. |
| `promiscuous(boolean)` | `false` | Promiscuous mode. |
| `snaplen(int)` | `65535` | Maximum bytes captured per packet. |
| `timeoutMillis(int)` | `1000` | Read timeout in milliseconds. |
| `bufferSize(int)` | `0` | Kernel buffer size (`0` = backend default). |
| `immediateMode(boolean)` | `false` | Unbuffered (immediate) delivery. |
| `filter(String)` | `null` | BPF filter expression. |

## 5. Filtering with BPF

Pass a [BPF](https://www.tcpdump.org/manpages/pcap-filter.7.html) expression to capture only
the traffic you care about:

```java
CaptureConfig config = CaptureConfig.builder()
        .device("eth0")
        .filter("tcp port 443")
        .build();

try (PacketCapture capture = PacketCaptures.openLive(config)) {
    capture.start(packet -> System.out.println("HTTPS packet"));
    Thread.sleep(5_000);
    capture.stop();
}
```

An invalid filter fails at open time with a `CaptureException`.

## 6. Reading from a capture file (offline capture)

Open a `.pcap` / `.pcapng` file the same way you would a live device. The capture ends
**cleanly by itself** when the file reaches end-of-file:

```java
Path file = Path.of("capture.pcap");

try (PacketCapture capture = PacketCaptures.openOffline(file)) {
    capture.start(packet ->
            System.out.println(packet.timestamp().toInstant() + " -> "
                    + packet.capturedLength() + " bytes"));

    // EOF stops the session on its own; stop()/close() afterwards are no-ops.
}
```

This is also the recommended way to build reproducible tests that do not depend on live traffic.

## 7. Writing packets to a savefile

Use a `PacketDumper` to persist packets to a `.pcap` file — for example, to record a filtered
subset of a live capture:

```java
Path out = Path.of("filtered.pcap");

try (PacketCapture capture = PacketCaptures.openLive(config)) {
    try (PacketDumper dumper = PacketCaptures.openDumper(out, LinkType.ETHERNET)) {
        capture.start(packet -> {
            dumper.write(packet);          // record it
            System.out.println("Got one"); // ...and process it
        });
        Thread.sleep(5_000);
        capture.stop();
    } // dumper.close() flushes and releases resources
}
```

`PacketDumper` is `AutoCloseable` and offers `write(Packet)` plus an explicit `flush()`. It is
**not thread-safe**; write from a single thread. Savefiles store timestamps with microsecond
precision, so a packet whose nanosecond fraction is not a whole number of microseconds loses
the remainder when written and read back.

## 8. Selecting a backend

Let Lostrego pick the first available backend, or ask for one by name:

```java
// Automatic: first backend that is supported on this platform.
PacketCapture auto = PacketCaptures.openLive(config);

// Explicit: ask for a specific backend.
PacketCapture libpcap = PacketCaptures.openLive(config, "libpcap");

// Offline capture and dumpers support the same explicit selection.
PacketCapture offline = PacketCaptures.openOffline(Path.of("capture.pcap"), "libpcap");
PacketDumper  dumper  = PacketCaptures.openDumper(Path.of("out.pcap"), LinkType.ETHERNET, "libpcap");
```

If the requested backend is not available, a `CaptureException` is thrown.

## 9. Statistics

Query the current counters of a running session:

```java
CaptureStatistics stats = capture.statistics();
System.out.println("received:         " + stats.received());
System.out.println("dropped:          " + stats.dropped());
System.out.println("dropped by iface: " + stats.interfaceDropped());
```

## 10. Error handling

`CaptureException` is the single (unchecked) exception of the library. Because capture runs on
an internal thread, a failure — either from the native layer or because your listener threw an
exception — is rethrown from `stop()` or `close()`:

```java
try (PacketCapture capture = PacketCaptures.openLive(config)) {
    capture.start(packet -> {
        if (isBad(packet)) {
            throw new IllegalStateException("bad packet");
        }
    });
    Thread.sleep(2_000);
    capture.stop(); // throws CaptureException wrapping the listener's exception
}
```

`close()` is safe in try-with-resources: it stops the capture, releases native resources, and
rethrows any failure that occurred.

## 11. Platform notes

- **Backend availability**: each backend reports itself as unavailable (without throwing) when
  its native library is missing, so one missing library never breaks the others.
- **Live capture privileges**: on Linux, capturing live traffic normally requires `root` or
  `CAP_NET_RAW`. Offline capture and savefile writing do not. See the next section to capture
  as a non-root user.
- **Backends**: `libpcap` is implemented on Linux/macOS. `pdpk` (Linux) and `npcap` (Windows)
  are planned.

## 12. Capturing without root (Linux)

To open a network interface in promiscuous mode — and listen to all the traffic on it — you
normally need to run as `root`. Instead of granting root, you can give the JVM itself the raw
network capabilities once, so that any user can capture without elevated privileges:

```bash
sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))
```

- `cap_net_raw` lets the process open raw sockets (packet capture).
- `cap_net_admin` allows network administration operations.
- `eip` grants the capabilities as effective, inheritable and permitted.

> **Security note**: this capability applies to the `java` binary, so it is inherited by *any*
> JVM process launched with that binary. Re-apply it after upgrading the JDK, since the binary
> is replaced.

### 12.1 Red Hat and other hardened distributions

On security-hardened systems such as **Red Hat Enterprise Linux**, a binary that carries
capabilities runs in *secure execution* mode, which disables the runtime search path (`rpath`)
the JVM uses to locate `libjvm.so`. As a result, `java` fails to start with a missing-library
error. There are two ways to fix it.

**Option A — register the JDK in the system loader cache.** Add the JDK `lib` directory to the
dynamic linker configuration and rebuild the cache:

```bash
echo "/usr/lib/jvm/java-21-openjdk-21.0.7.0.6-1.0.1.el9.x86_64/lib/" \
  | sudo tee /etc/ld.so.conf.d/java.conf
sudo ldconfig
```

**Option B — embed the `rpath` with `patchelf` (cleaner).** Patch the `java` binary directly so
it carries the search path, without touching system-wide configuration. Do this **before**
granting the capabilities:

```bash
sudo patchelf --set-rpath '/usr/lib/jvm/java-21-openjdk-21.0.7.0.6-1.0.1.el9.x86_64/lib' \
  /usr/lib/jvm/java-21-openjdk-21.0.7.0.6-1.0.1.el9.x86_64/bin/java
```

Option B is more self-contained: it does not affect other applications and survives reboots
without extra files in `/etc/ld.so.conf.d/`.

> **Note**: the JDK paths above are examples — adjust them to your installed JDK. This extra
> step is only needed on hardened distributions; other Linux systems usually work with just the
> `setcap` command.

## 13. Further reading

- **Backend implementation & specs**: `openspec/specs/` and `openspec/changes/`.
- **Companion app**: Lostrego Spy (`src/lostrego-spy/`) is a console client built on top of
  this library, useful as a real-world usage reference.
- **Source of truth for the agent workflow**: `AGENTS.md`.
