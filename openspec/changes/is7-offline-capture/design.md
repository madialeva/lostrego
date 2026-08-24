## Context

`core` expone el contrato público (`PacketCapture`, `PacketCaptures`, modelo `Packet`) y un SPI interno `CaptureProvider` (no exportado) con un único método de apertura `openLive(CaptureConfig)`. La máquina de estados asíncrona vive en `AbstractPacketCapture` (hooks `doStart`/`doStop`/`doClose`/`doStatistics`). El backend libpcap ya vincula `pcap_open_offline` de forma **interna** para sus tests (`LibpcapCapture.openOffline(Path)`, package-private) y su bucle `pcap_next_ex` ya trata `-2` (breakloop/EOF) como terminación limpia. Ver `proposal.md` para la motivación y las deltas en `specs/` para el contrato.

## Goals / Non-Goals

**Goals:**
- Exponer la captura offline y la escritura de savefiles como parte del API pública de `core`, reutilizando el ciclo de vida asíncrono y el modelo `Packet` existentes, sin filtrar por backend.
- Mantener el SPI mínimo y retrocompatible: los backends actuales y futuros no se ven obligados a implementar nada nuevo.

**Non-Goals:**
- Modo burst (`pcap_dispatch`): relegado a Backlog (issue aparte).
- Filtro BPF aplicado a la lectura offline: futuro.
- Escritura en formato `pcapng` (libpcap escribe `pcap` clásico con `pcap_dump`): no cubierto.
- Dump simultáneo adjunto a una sesión en marcha: el escritor es independiente; el usuario puede escribir desde su listener si lo desea.
- Enumeración de dispositivos, parseo de protocolos, publicación a Maven: fuera de alcance.

## Decisions

### D1: La captura offline reutiliza `PacketCapture` y su ciclo de vida asíncrono

`openOffline(Path)` devuelve el mismo `PacketCapture` que la captura en vivo: `start(listener)` lanza el hilo interno, y `doStart()` lee del fichero hasta EOF. Al alcanzar el final, `pcap_next_ex` devuelve `-2` y el bucle retorna, transitando a `STOPPED`; `stop()`/`close()` retornan sin error. No se introduce un tipo nuevo de sesión ni un modo síncrono.

**Alternativas consideradas:**
- API pull síncrono (`nextPacket()`) para offline: más cómodo para leer ficheros, pero introduce un segundo modelo de consumo y duplica el contrato. Rechazado; el modelo pull se reserva para el issue #9 si procede.

### D2: Extensión del SPI con métodos por defecto

`CaptureProvider` gana tres miembros con implementación por defecto que no rompe a los backends existentes:
- `default boolean supportsOffline()` → `false`
- `default boolean supportsWriting()` → `false`
- `default PacketCapture openOffline(Path file)` → lanza `CaptureException` informativa
- `default PacketDumper openDumper(Path file, LinkType linkType)` → lanza `CaptureException` informativa

Se separan `supportsOffline()` (lectura) y `supportsWriting()` (escritura) para no acoplar ambas capacidades: un backend futuro podría leer sin escribir (o viceversa). libpcap devuelve `LibpcapNative.isAvailable()` en ambos.

**Alternativas consideradas:**
- Un único flag `supportsOffline()` para lectura y escritura: más corto, pero mezcla semánticamente dos capacidades distintas y obligaría a romper el SPI si un backend soporta solo una dirección. Rechazado.
- Un SPI separado (`PacketDumperProvider`) para la escritura: más desacople pero añade una interfaz de proveedor y un segundo `ServiceLoader` sin beneficio hoy. Rechazado; se puede extraer si crece.

### D3: Puntos de entrada en `PacketCaptures`

Se añaden a la factoría (espejo de `openLive`):
- `PacketCapture openOffline(Path file)` — primer provider con `isSupported() && supportsOffline()`.
- `PacketCapture openOffline(Path file, String backend)` — selección explícita.
- `PacketDumper openDumper(Path file, LinkType linkType)` — primer provider con `isSupported() && supportsWriting()`.
- `PacketDumper openDumper(Path file, LinkType linkType, String backend)` — selección explícita.

Sin backend disponible, se lanza `CaptureException` con mensaje claro, igual que `openLive`.

### D4: `PacketDumper` como tipo público en `core`

`PacketDumper` (interface, `extends AutoCloseable`) con `void write(Packet packet)` y `void flush()`. Es autónomo: se abre con `Path` + `LinkType` y no depende de una sesión de captura. Se elige el nombre `PacketDumper` por fidelidad al concepto nativo (`pcap_dumper_t`); el módulo `core` ya exportado lo cubre sin cambios en `module-info.java`.

**Alternativas consideradas:**
- `PacketWriter`: más idiomático, pero "writer" sugiere un `Writer`/stream y no el concepto de volcado a savefile. Rechazado.
- `CaptureWriter`/`PcapWriter`: acoplan el nombre a "captura" o al formato. Rechazado.

### D5: Escritura con `pcap_open_dead` + `pcap_dump`

`pcap_dump_open` exige un `pcap_t*` para leer el `LinkType` y generar la cabecera global del savefile. Como el dumper es independiente de una sesión, se obtiene un handle "muerto" con `pcap_open_dead(linkTypeCode, snaplen)` —con `snaplen = DEFAULT_SNAPLEN`—, se llama a `pcap_dump_open(deadHandle, path)`, se cierra el handle muerto con `pcap_close` y se conserva únicamente el `pcap_dumper_t*`. Escribir un paquete reconstruye `struct pcap_pkthdr` desde el `Packet` y llama a `pcap_dump(dumper, header, data)`; `flush()` invoca `pcap_dump_flush` y `close()` invoca `pcap_dump_close`.

**Alternativas consideradas:**
- Escribir la cabecera global y los registros a mano (sin `pcap_dump`): reinventa libpcap y es propenso a errores de endianness/formato. Rechazado.
- Adjuntar el dumper a un `pcap_t` vivo: obligaría a que el dumper dependa de una sesión. Rechazado.

### D6: Conversión de timestamp (nanosegundos → microsegundos)

El savefile `pcap` almacena microsegundos. Al escribir se reconstruye `timeval` con `tv_sec = seconds` y `tv_usec = nanosOfSecond / 1000`. Al releer, libpcap devuelve microsegundos que se re-multiplican por 1000. El *round-trip* preserva la precisión de microsegundo; los `Packet` con nanos no múltiplos de 1000 pierden el resto al escribirse. Esto es aceptable (los backends vivos entregan timestamps con precisión ≤ microsegundo) y se documenta en el Javadoc de `write`.

### D7: Arenas y ownership del dumper

`LibpcapDumper` (package-private, en `backend.libpcap`) mantiene un `Arena.ofConfined()` de sesión que aloja el `pcap_dumper_t*` opaco. Cada `write` usa un arena confinado transitorio (`try (Arena a = Arena.ofConfined())`) para el `pcap_pkthdr` y la copia del payload, ya que `pcap_dump` copia los bytes de forma síncrona al buffer del `FILE*`; el arena se libera al terminar la llamada. El dumper **no es thread-safe** (confinado), coherente con el uso típico (el usuario escribe desde un único hilo). Nada de memoria nativa cruza la frontera del paquete.

**Alternativas consideradas:**
- `Arena.ofShared()` + sincronización: añade complejidad sin caso de uso. Rechazado.
- Reusar un slot de header en el arena de sesión: ahorra una asignación por escritura pero no la del payload, que domina el coste; se prefiere la simplicidad del arena transitorio. Rechazado.

### D8: Traducción de errores nativos

Fallos de `openOffline` se leen del `errbuf` (ya implementado). Fallos de `openDumper`/`pcap_dump_open` (retorno NULL) se leen con `pcap_geterr` sobre el handle muerto antes de cerrarlo. Todo se envuelve en `CaptureException`.

### D9: Bindings FFM adicionales

`LibpcapNative` añade `MethodHandle`s para `pcap_open_dead`, `pcap_dump_open`, `pcap_dump`, `pcap_dump_close` y `pcap_dump_flush`, con sus `FunctionDescriptor`s. `pcap_dumper_t*` se trata como valor opaco (`ValueLayout.ADDRESS`).

### D10: Estrategia de tests

- **Lectura offline pública:** test en `core` que usa `PacketCaptures.openOffline(...)` (vía `ServiceLoader`) sobre el fixture `ethernet-truncated.pcap` y verifica el modelo `Packet`; protegido con `assumeTrue` de disponibilidad de libpcap.
- **Escritura y round-trip:** test en `backend/libpcap` que escribe los paquetes del fixture a un fichero temporal con `PacketDumper` y los relee con `openOffline`, comparando timestamp/longitudes/linkType/payload. Los timestamps del fixture son múltiplos de microsegundo, por lo que el round-trip es exacto.
- **Errores:** apertura de un fichero inexistente lanza `CaptureException`.
- El fixture existente (2 paquetes, el primero truncado por snaplen) basta; no se añaden fixtures nuevos salvo que un test concreto lo requiera.

## Risks / Trade-offs

- **[Riesgo] `pcap_dump` escribe `pcap` clásico, no `pcapng`** → Mitigación: documentado como Non-Goal; el `openOffline` sí lee ambos formatos.
- **[Riesgo] Pérdida de precisión sub-microsegundo al escribir** → Mitigación: aceptado y documentado (D6); los backends actuales no producen nanos no alineados a microsegundo.
- **[Riesgo] `pcap_open_dead`/`pcap_dump_*` son símbolos antiguos** → Presentes en todas las libpcap modernas; si faltaran, `supportsWriting()` seguiría el patrón de `isAvailable()` (búsqueda de símbolo opcional) sin romper el backend.
- **[Riesgo] El dumper no es thread-safe** → Mitigación: documentado; arena confinada lanza `WrongThreadException` ante uso indebido en lugar de corromper memoria.
- **[Trade-off] El dumper es independiente y no adjunto a la sesión** → El usuario que quiera dump simultáneo escribe desde su `PacketListener`; añadir un dump adjunto queda como posible extensión sin romper el contrato.

## Migration Plan

No aplica migración de código existente: `openOffline` interno de `LibpcapCapture` pasa a exponerse a través de `LibpcapProvider`, y el resto son tipos y métodos nuevos. Sin cambios en `module-info.java` ni en CI.

## Open Questions

Ninguna que afecte al contrato, al enfoque o al desglose de tareas.
