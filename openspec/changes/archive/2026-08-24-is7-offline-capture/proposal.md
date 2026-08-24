## Why

La librería solo captura en vivo: no hay forma de leer paquetes desde un fichero `.pcap`/`.pcapng` ni de volcar una captura a fichero. `pcap_open_offline` ya está vinculado en el backend libpcap, pero únicamente de forma interna para los tests. La captura offline es la vía natural para tests reproducibles que no dependan de tráfico real, y la escritura de savefiles (`pcap_dump`) —pendiente del issue #14— solo tiene sentido una vez que existe la captura offline. Este change aborda ambas.

## What Changes

- Nuevo punto de entrada público `PacketCaptures.openOffline(Path)` (y variante con backend explícito) que abre una sesión de lectura desde un fichero `.pcap`/`.pcapng`, reutilizando el mismo ciclo de vida asíncrono `PacketCapture` (start/stop/close). Al llegar a EOF la captura termina limpiamente.
- Extensión del SPI `CaptureProvider` con soporte offline: `supportsOffline()` (default `false`), `openOffline(Path)` y `openDumper(Path, LinkType)` (defaults que lanzan `CaptureException`), de modo que un backend sin soporte no rompe a los demás.
- Nuevo tipo público `PacketDumper` (AutoCloseable) en `core` para escribir paquetes a un fichero savefile: `write(Packet)` y `flush()`, con `close()` idempotente.
- Nuevo punto de entrada público `PacketCaptures.openDumper(Path, LinkType)` (y variante con backend explícito).
- Backend libpcap: exponer la lectura offline existente y vincular la escritura (`pcap_open_dead`, `pcap_dump_open`, `pcap_dump`, `pcap_dump_close`, `pcap_dump_flush`) a través de un nuevo `LibpcapDumper`.
- Fixtures `.pcap` adicionales en `src/test/resources` y tests de lectura offline, escritura y *round-trip* (leer → escribir → releer).
- Manual de usuario `docs/user-guide.md` (en inglés) con ejemplos de cada funcionalidad, y una sección **Documentation** en el `README.md` que lo referencia.

## Capabilities

### New Capabilities

- `packet-writing`: contrato público de escritura de paquetes a fichero savefile (`PacketDumper`) — escritura, `flush`, cierre idempotente y liberación de recursos.

### Modified Capabilities

- `packet-capture`: añade la apertura de captura offline (`openOffline`) y el comportamiento de finalización por EOF dentro del ciclo de vida existente.
- `backend/libpcap`: añade la lectura offline desde fichero y la escritura de savefiles mediante libpcap.

## Impact

- `src/lostrego/src/main/java/com/angazo/lostrego/core/`: nuevos `PacketDumper` y métodos en `PacketCaptures`; `CaptureException` reutilizada para fallos.
- `src/lostrego/src/main/java/com/angazo/lostrego/core/spi/CaptureProvider.java`: nuevos métodos por defecto `supportsOffline()`, `openOffline(Path)`, `openDumper(Path, LinkType)`.
- `src/lostrego/src/main/java/com/angazo/lostrego/backend/libpcap/`: `LibpcapProvider` implementa el soporte offline; nuevos `LibpcapDumper` y bindings FFM (`LibpcapNative`).
- `module-info.java`: sin cambios (ningún tipo nuevo se exporta; `PacketDumper` vive en `core` ya exportado).
- Tests: nuevos tests de lectura offline, escritura y round-trip en el módulo `lostrego`, condicionales a la disponibilidad de libpcap.
- Documentación: nuevo `docs/user-guide.md` (manual de usuario) y enlace desde el `README.md`.
- Sin dependencias externas nuevas; sin cambios en CI.
