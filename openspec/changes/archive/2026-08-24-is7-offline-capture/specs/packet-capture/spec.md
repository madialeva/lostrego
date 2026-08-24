## ADDED Requirements

### Requirement: Apertura de una captura offline

La librería SHALL permitir abrir una sesión de lectura de paquetes desde un fichero de captura (`.pcap`/`.pcapng`) sin que el usuario conozca qué backend nativo se usa. Si ningún backend con soporte offline está disponible en la plataforma, la apertura SHALL fallar con una `CaptureException` informativa.

#### Scenario: Apertura de un fichero de captura

- **WHEN** el usuario invoca la factoría con la ruta a un fichero de captura válido y existe al menos un backend con soporte offline
- **THEN** se devuelve una instancia de `PacketCapture` lista para iniciar la lectura

#### Scenario: Fichero inexistente o ilegible

- **WHEN** el usuario invoca la factoría con la ruta a un fichero que no existe o no puede leerse
- **THEN** se lanza una `CaptureException` con un mensaje que incluye la causa nativa

#### Scenario: Ningún backend con soporte offline

- **WHEN** el usuario invoca la factoría y ningún backend soporta la lectura offline en la plataforma
- **THEN** se lanza una `CaptureException` indicando que no hay backend con soporte offline disponible

### Requirement: Selección explícita de backend para captura offline

La librería SHALL permitir solicitar un backend concreto por nombre para la captura offline. Si ese backend no está disponible o no soporta la lectura offline, la apertura SHALL fallar con una `CaptureException`.

#### Scenario: Selección de un backend con soporte offline

- **WHEN** el usuario invoca la factoría de captura offline indicando el nombre de un backend con soporte offline
- **THEN** se devuelve una sesión de lectura respaldada por ese backend

#### Scenario: Selección de un backend sin soporte offline

- **WHEN** el usuario invoca la factoría de captura offline indicando un backend que no soporta la lectura offline
- **THEN** se lanza una `CaptureException`

### Requirement: Finalización de la lectura por fin de fichero

Al leer un fichero de captura, la sesión SHALL terminar limpiamente al llegar al final del fichero: el hilo interno de captura finaliza, la sesión transiciona a estado detenido y las llamadas posteriores a `stop()` o `close()` SHALL retornar sin error.

#### Scenario: Lectura completa del fichero

- **WHEN** una sesión offline inicia la lectura y entrega todos los paquetes del fichero hasta alcanzar el final
- **THEN** la captura termina por sí misma y las llamadas posteriores a `stop()` y `close()` no lanzan excepción

### Requirement: Transparencia de backend en la captura offline

La captura offline SHALL entregar paquetes con el mismo modelo inmutable `Packet` de `core` que la captura en vivo, sin exponer tipos ni estructuras del backend.

#### Scenario: Paquetes offline con el modelo de core

- **WHEN** una sesión offline entrega paquetes al listener
- **THEN** cada paquete es un `Packet` autónomo con timestamp, longitudes, tipo de capa de enlace y payload, sin dependencia de memoria nativa
