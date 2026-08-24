# packet-writing Specification

## Purpose
Permitir volcar paquetes capturados a un fichero savefile estándar (`.pcap`), reutilizable por otras herramientas y por la propia lectura offline de la librería.
## Requirements
### Requirement: Apertura de un escritor de savefile

La librería SHALL permitir abrir un escritor de fichero savefile a partir de una ruta y un tipo de capa de enlace, a través de un backend con soporte de escritura. Si ningún backend soporta la escritura, la apertura SHALL fallar con una `CaptureException`.

#### Scenario: Apertura exitosa

- **WHEN** el usuario invoca la factoría con una ruta válida y un `LinkType`, y existe un backend con soporte de escritura
- **THEN** se devuelve un `PacketDumper` listo para escribir paquetes

#### Scenario: Ningún backend con soporte de escritura

- **WHEN** el usuario invoca la factoría y ningún backend soporta la escritura de savefiles en la plataforma
- **THEN** se lanza una `CaptureException`

#### Scenario: Ruta no escribible

- **WHEN** el usuario invoca la factoría con una ruta que no puede crearse ni escribirse
- **THEN** se lanza una `CaptureException` con un mensaje que incluye la causa nativa

### Requirement: Escritura de paquetes

El escritor SHALL escribir cada paquete entregado a `write(Packet)` en el fichero savefile preservando su timestamp, longitudes y payload, de modo que pueda releerse con una captura offline.

#### Scenario: Escritura de un paquete

- **WHEN** el usuario llama a `write(Packet)` con un paquete capturado
- **THEN** el paquete queda registrado en el fichero y es recuperable al leer el fichero con una captura offline

### Requirement: Vaciado del buffer

El escritor SHALL ofrecer un método de vaciado explícito que fuerce la escritura de los paquetes pendientes al fichero.

#### Scenario: Vaciado explícito

- **WHEN** el usuario llama al método de vaciado tras escribir paquetes
- **THEN** los paquetes escritos quedan disponibles en el fichero sin necesidad de cerrar el escritor

### Requirement: Cierre y liberación de recursos

El escritor SHALL liberar los recursos nativos al cerrarse, y `close()` SHALL ser idempotente y vaciar el buffer pendiente al fichero.

#### Scenario: Cierre del escritor

- **WHEN** el usuario cierra el escritor
- **THEN** los paquetes escritos quedan persistidos y los recursos nativos liberados

#### Scenario: Cierre repetido

- **WHEN** el usuario cierra el mismo escritor más de una vez
- **THEN** la segunda llamada no produce error

