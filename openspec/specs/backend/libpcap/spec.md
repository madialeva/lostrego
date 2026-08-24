# backend/libpcap Specification

## Purpose

Define el comportamiento del backend de captura libpcap de lostrego: la disponibilidad de la librería nativa, la apertura de capturas en vivo aplicando la configuración de `core`, la entrega de paquetes traducidos al modelo común y la gestión correcta de recursos y errores.
## Requirements
### Requirement: Disponibilidad del backend libpcap

El backend libpcap SHALL reportarse como soportado solo cuando su librería nativa pueda cargarse en la plataforma, y SHALL reportarse como no soportado (sin lanzar excepción) en caso contrario, de modo que no impida el funcionamiento de otros backends.

#### Scenario: Librería nativa presente

- **WHEN** la librería nativa libpcap está instalada en la plataforma
- **THEN** el backend se reporta como soportado

#### Scenario: Librería nativa ausente

- **WHEN** la librería nativa libpcap no puede cargarse
- **THEN** el backend se reporta como no soportado sin lanzar excepción

### Requirement: Apertura de una captura en vivo con libpcap

El backend libpcap SHALL abrir una sesión de captura en vivo aplicando el dispositivo, el modo promiscuo, el snaplen y el timeout de lectura de la configuración. Si la apertura falla, SHALL lanzar una `CaptureException` con el mensaje de error nativo.

#### Scenario: Apertura exitosa

- **WHEN** se abre una captura con un dispositivo válido y una `CaptureConfig` correcta
- **THEN** se devuelve una sesión de captura lista para iniciar

#### Scenario: Dispositivo inválido

- **WHEN** se abre una captura sobre un dispositivo inexistente o sin permisos
- **THEN** se lanza una `CaptureException` con un mensaje que incluye la causa nativa

### Requirement: Aplicación de opciones avanzadas de configuración

El backend libpcap SHALL aplicar el tamaño de buffer y el modo inmediato de la configuración cuando estén activados, sin alterar la captura cuando usen sus valores por defecto.

#### Scenario: Buffer e inmediato configurados

- **WHEN** la configuración establece un `bufferSize` mayor que cero y `immediateMode` activo
- **THEN** ambos se aplican a la captura abierta

#### Scenario: Valores por defecto

- **WHEN** la configuración usa el buffer por defecto y el modo inmediato desactivado
- **THEN** la captura se abre sin forzar ninguna de estas opciones

### Requirement: Aplicación de filtro BPF

El backend libpcap SHALL compilar y aplicar el filtro BPF de la configuración cuando esté presente, y SHALL lanzar una `CaptureException` si el filtro no puede compilarse.

#### Scenario: Filtro válido

- **WHEN** la configuración incluye una expresión de filtro BPF válida
- **THEN** la captura solo entrega paquetes que cumplen el filtro

#### Scenario: Filtro inválido

- **WHEN** la configuración incluye una expresión de filtro malformada
- **THEN** se lanza una `CaptureException` y no se entrega ningún paquete

### Requirement: Traducción de paquetes al modelo de core

Cada paquete capturado SHALL entregarse como un `Packet` inmutable con el timestamp (microsegundos nativos convertidos a nanosegundos), la longitud original y la capturada, el tipo de capa de enlace y los bytes capturados copiados, sin referencia a memoria nativa.

#### Scenario: Paquete completo

- **WHEN** se captura un paquete
- **THEN** se entrega un `Packet` con el timestamp, las longitudes, el `LinkType` y el payload correctos

#### Scenario: Paquete truncado por snaplen

- **WHEN** un paquete es truncado por el snaplen
- **THEN** el `Packet` distingue la longitud original de la capturada

### Requirement: Parada oportuna de la captura

La parada de una captura libpcap SHALL desbloquear la lectura en curso de forma que `stop()` no espere el timeout de lectura completo.

#### Scenario: Parada durante la espera de paquetes

- **WHEN** se solicita la parada mientras la captura espera paquetes con un timeout largo
- **THEN** la captura termina sin esperar el timeout de lectura completo

### Requirement: Estadísticas de captura libpcap

La sesión SHALL exponer estadísticas con los contadores de paquetes recibidos, descartados y descartados por la interfaz reportados por la librería nativa.

#### Scenario: Consulta de estadísticas

- **WHEN** el usuario consulta las estadísticas de una sesión libpcap abierta
- **THEN** obtiene los tres contadores tal como los reporta libpcap

### Requirement: Liberación de recursos nativos

La sesión SHALL liberar el handle nativo de libpcap al cerrarse, y el cierre SHALL ser idempotente.

#### Scenario: Cierre de la sesión

- **WHEN** el usuario cierra la sesión
- **THEN** el handle nativo de libpcap queda liberado

#### Scenario: Cierre repetido

- **WHEN** el usuario cierra la misma sesión más de una vez
- **THEN** la segunda llamada no produce error

### Requirement: Lectura offline con libpcap

El backend libpcap SHALL abrir una sesión de lectura desde un fichero `.pcap`/`.pcapng` y entregar sus paquetes traducidos al modelo de `core`. Si el fichero no puede abrirse, SHALL lanzar una `CaptureException` con el mensaje de error nativo.

#### Scenario: Apertura de un fichero válido

- **WHEN** se abre un fichero de captura válido
- **THEN** se devuelve una sesión de lectura lista para iniciar

#### Scenario: Fichero inválido

- **WHEN** se abre un fichero inexistente o corrupto
- **THEN** se lanza una `CaptureException` con un mensaje que incluye la causa nativa

#### Scenario: Fin de fichero

- **WHEN** la lectura alcanza el final del fichero
- **THEN** la captura termina limpiamente sin error

### Requirement: Soporte offline declarado

El backend libpcap SHALL reportar soporte offline solo cuando su librería nativa esté disponible.

#### Scenario: Librería disponible

- **WHEN** la librería nativa libpcap está disponible
- **THEN** el backend reporta soporte offline

### Requirement: Escritura de savefiles con libpcap

El backend libpcap SHALL escribir paquetes a un fichero savefile preservando timestamp, longitudes, tipo de capa de enlace y payload, de modo que el fichero resultante sea legible por una captura offline.

#### Scenario: Escritura y relectura

- **WHEN** se escribe un conjunto de paquetes a un fichero y se lee después con una captura offline
- **THEN** los paquetes releídos preservan el timestamp, las longitudes, el tipo de capa de enlace y el payload de los originales

