## ADDED Requirements

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
