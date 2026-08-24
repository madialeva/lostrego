## 1. API pública en core

- [x] 1.1 Crear `PacketDumper` en `core` (`extends AutoCloseable`) con `write(Packet)`, `flush()` y `close()` idempotente, con Javadoc del contrato (no thread-safe, precisión de microsegundo)
- [x] 1.2 Añadir a `PacketCaptures` los puntos de entrada `openOffline(Path)`, `openOffline(Path, String)`, `openDumper(Path, LinkType)` y `openDumper(Path, LinkType, String)` con mensajes de error claros cuando no hay backend disponible

## 2. SPI

- [x] 2.1 Añadir a `CaptureProvider` los métodos por defecto `supportsOffline()`, `supportsWriting()`, `openOffline(Path)` y `openDumper(Path, LinkType)` (defaults `false` / lanzar `CaptureException`)

## 3. Backend libpcap

- [x] 3.1 Vincular en `LibpcapNative` los símbolos `pcap_open_dead`, `pcap_dump_open`, `pcap_dump`, `pcap_dump_close` y `pcap_dump_flush` con sus `FunctionDescriptor`s
- [x] 3.2 Crear `LibpcapDumper` (package-private) que implementa `PacketDumper`: abre con `pcap_open_dead` + `pcap_dump_open`, reconstruye `pcap_pkthdr`/`timeval` en `write` (nanos→micros), `flush` con `pcap_dump_flush` y `close` con `pcap_dump_close` (idempotente), con arena confinada de sesión y arena transitorio por escritura
- [x] 3.3 Implementar en `LibpcapProvider` `supportsOffline()`/`supportsWriting()` (delegando en `LibpcapNative.isAvailable()`), `openOffline(Path)` (→ `LibpcapCapture.openOffline`) y `openDumper(Path, LinkType)` (→ `LibpcapDumper`)

## 4. Tests

- [x] 4.1 Test de lectura offline pública: `PacketCaptures.openOffline(fixture)` entrega los paquetes esperados (timestamp/longitudes/linkType/payload) y termina limpiamente en EOF
- [x] 4.2 Test de escritura y round-trip: escribir los paquetes del fixture a un fichero temporal con `PacketDumper` y releerlos con `openOffline`, comparando los campos
- [x] 4.3 Test de error: abrir un fichero inexistente lanza `CaptureException`; abrir un dumper sobre ruta no escribible lanza `CaptureException`
- [x] 4.4 Test de cierre idempotente del `PacketDumper` y de que `supportsOffline()`/`supportsWriting()` son coherentes con la disponibilidad de la librería
- [x] 4.5 `./gradlew build` en verde desde `src/` (tests nativos se saltan donde no aplican)

## 5. Documentación

- [x] 5.1 Crear `docs/user-guide.md` (en inglés) con qué es la librería, requisitos, y ejemplos de captura en vivo, filtro BPF, captura offline, escritura de savefiles, selección de backend, estadísticas y manejo de errores
- [x] 5.2 Añadir la sección **Documentation** al `README.md` referenciando el manual (y futuros documentos)
- [x] 5.3 Actualizar `AGENTS.md`: idioma del manual en inglés, convención de actualizar el manual cuando se toque la API pública, y fila en "Ficheros clave"
