# packet-capture Specification

## Purpose

Define el contrato público de captura de paquetes de la librería lostrego: un API transparente a los backends nativos que permite abrir una captura en vivo, recibir paquetes de forma asíncrona mediante callback y liberar los recursos de forma segura.
## Requirements
### Requirement: Apertura de una captura en vivo

La librería SHALL permitir abrir una sesión de captura en vivo a partir de una configuración, sin que el usuario conozca qué backend nativo se usa. Si ningún backend está disponible en la plataforma, la apertura SHALL fallar con una `CaptureException` informativa.

#### Scenario: Apertura con backend disponible

- **WHEN** el usuario invoca la factoría con una `CaptureConfig` válida y existe al menos un backend soportado en la plataforma
- **THEN** se devuelve una instancia de `PacketCapture` lista para iniciar la captura

#### Scenario: Apertura sin backend disponible

- **WHEN** el usuario invoca la factoría y ningún backend está soportado en la plataforma
- **THEN** se lanza una `CaptureException` con un mensaje que indica que no hay backend disponible

### Requirement: Selección explícita de backend

La librería SHALL permitir al usuario solicitar un backend concreto por nombre. Si ese backend no está disponible o no está soportado, la apertura SHALL fallar con una `CaptureException`.

#### Scenario: Selección de un backend concreto

- **WHEN** el usuario invoca la factoría indicando el nombre de un backend soportado
- **THEN** se devuelve una sesión de captura respaldada por ese backend

#### Scenario: Selección de un backend no disponible

- **WHEN** el usuario invoca la factoría indicando un backend que no está disponible en la plataforma
- **THEN** se lanza una `CaptureException`

### Requirement: Entrega asíncrona de paquetes por callback

La sesión de captura SHALL entregar los paquetes mediante un listener invocado en un hilo interno, de forma que `start(listener)` retorne inmediatamente y la captura continúe en segundo plano.

#### Scenario: Inicio de captura no bloqueante

- **WHEN** el usuario llama a `start(listener)` sobre una sesión abierta
- **THEN** la llamada retorna sin esperar a que lleguen paquetes

#### Scenario: Recepción de paquetes en el listener

- **WHEN** un paquete es capturado mientras la sesión está en ejecución
- **THEN** el método del listener es invocado una vez por paquete con el `Packet` correspondiente

### Requirement: Detención de la captura

La sesión SHALL permitir detener la captura de forma bloqueante: tras `stop()`, el hilo interno de captura habrá terminado y no se invocarán más callbacks.

#### Scenario: Detención bloqueante

- **WHEN** el usuario llama a `stop()` sobre una sesión en ejecución
- **THEN** la llamada no retorna hasta que el hilo interno ha terminado y no se entregan más paquetes

### Requirement: Cierre y liberación de recursos

La sesión SHALL liberar los recursos subyacentes al cerrarse, y `close()` SHALL ser idempotente y forzar la detención si la captura sigue en ejecución.

#### Scenario: Cierre con captura en ejecución

- **WHEN** el usuario llama a `close()` sobre una sesión en ejecución
- **THEN** la captura se detiene y los recursos nativos quedan liberados

#### Scenario: Cierre repetido

- **WHEN** el usuario llama a `close()` más de una vez sobre la misma sesión
- **THEN** la segunda llamada no produce error

### Requirement: Modelo de paquete inmutable y crudo

Cada paquete entregado SHALL ser un objeto inmutable y autónomo, con metadatos (timestamp, longitudes, tipo de capa de enlace) y los bytes capturados. El paquete SHALL poder conservarse tras el retorno del listener sin que dependa del backend ni de memoria nativa.

#### Scenario: Conservación del paquete tras el callback

- **WHEN** el listener almacena una referencia al `Packet` y el hilo de captura sigue entregando más paquetes
- **THEN** el `Packet` almacenado conserva sus datos intactos y no referencia memoria nativa invalidada

#### Scenario: Longitudes del paquete

- **WHEN** un paquete es capturado con un snaplen menor que su tamaño real
- **THEN** el `Packet` distingue la longitud original (en el cable) de la longitud capturada

### Requirement: Timestamp con resolución de nanosegundos

El timestamp de cada paquete SHALL representarse como un par `(segundos, nanosegundos)` y SHALL ofrecer una conversión de conveniencia a `Instant`.

#### Scenario: Conversión a Instant

- **WHEN** un `Packet` tiene un timestamp de captura
- **THEN** es posible obtener un `Instant` equivalente desde los campos de segundos y nanosegundos

### Requirement: Estadísticas de captura

La sesión SHALL exponer estadísticas de captura con los contadores de paquetes recibidos, descartados y descartados por la interfaz.

#### Scenario: Consulta de estadísticas

- **WHEN** el usuario consulta las estadísticas de una sesión
- **THEN** obtiene los valores de recibidos, descartados y descartados-por-interfaz

### Requirement: Fallo de captura por excepción en el listener

Si el listener lanza una excepción durante la entrega de un paquete, la sesión SHALL detener la captura y relanzar la causa envuelta en una `CaptureException` al llamar a `stop()` o `close()`.

#### Scenario: Listener que lanza excepción

- **WHEN** el listener lanza una excepción al procesar un paquete
- **THEN** la captura se detiene y la llamada posterior a `stop()` (o `close()`) lanza una `CaptureException` que envuelve la causa original

### Requirement: Transparencia de backend en el API pública

El API pública SHALL exponer únicamente tipos del paquete `core`; ningún tipo ni estructura de memoria del backend concreto SHALL aparecer en el contrato público.

#### Scenario: Sin tipos de backend en el contrato público

- **WHEN** un usuario consume el API pública para capturar paquetes
- **THEN** solo necesita importar tipos de `com.angazo.lostrego.core`, sin referencia a `libpcap`, `pdpk` o `npcap`

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

