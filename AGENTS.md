# AGENTS.md — Contexto del proyecto para agentes de IA

> Este fichero es la fuente de verdad del contexto de trabajo. Si trabajas como agente en este
> repositorio, lee esto primero.

## Objetivo del proyecto

Crear una librería de captura de paquetes de red en Java 25, usando la API FFM (Foreign Function & Memory) para interactuar directamente con las librerías nativas del Sistema Operativo. PDPK es un framework de altísimas prestaciones que se salta el kernel y accede directamente a la NIC, usando DMA y otras técnicas para minimizar la latencia. La librería podrá usar distintos backends según la plataforma (libpcap en Linux/Mac, PDPK en Linux, npcap en Windows).

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 25 |
| Build | Gradle |
| Interop. nativa | FFM API (Foreign Function & Memory, incubada → estándar en Java 25) |
| Backends Linux/Mac | libpcap |  
| Backend Linux | PDPK |
| Backend Windows | npcap |
| Testing | JUnit 5 (JUnitPlatform) |
| CI | GitHub Actions |

## Estructura del proyecto

La librería se publica como **un único JAR** con el siguiente árbol de paquetes dentro de un solo módulo Gradle:

```
src/
├── lostrego/                        → Módulo único de la librería
│   └── src/main/java/com/angazo/lostrego/
│       ├── core/                    → API pública, modelo de paquetes y abstracciones comunes
│       ├── backend/libpcap/         → Backend libpcap (Linux/Mac)
│       ├── backend/pdpk/            → Backend PDPK (Linux)
│       └── backend/npcap/           → Backend npcap (Windows)
│
└── lostrego-app/                    → (futuro) App de ejemplo que consume la librería

Paquete base: dev.ktai.lostrego
```

**Organización de paquetes dentro de la librería:**

| Paquete (bajo `dev.ktai.lostrego`) | Responsabilidad |
|---|---|
| `core` | Interfaces y tipos comunes (`PacketCapture`, `PacketListener`, `Packet`), factoría de backends, modelo de paquete independiente de plataforma |
| `backend.libpcap` | Adaptador FFM sobre libpcap, implementación del SPI de core |
| `backend.pdpk` | Adaptador FFM sobre PDPK, implementación del SPI de core |
| `backend.npcap` | Adaptador FFM sobre npcap, implementación del SPI de core |

## Comandos de uso frecuente

```bash
./gradlew build        # Compilar y ejecutar tests
./gradlew test         # Solo tests
./gradlew jar          # Generar JAR de la librería
```

## Estado actual

- **Fase actual:** Fase 1 — implementación de backends nativos (libpcap → pdpk → npcap) y primer consumidor (`lostrego-spy`)
- **Último hito:** Implementado y mergeado el issue #7: captura offline (`openOffline`) y escritura de savefiles (`PacketDumper`/`openDumper`) sobre el backend libpcap, ampliando el SPI de `core` con capacidades opcionales (`supportsOffline`/`supportsWriting`). Añadido el manual de usuario `docs/user-guide.md`. Change archivado como `2026-08-24-is7-offline-capture`.
- **Próximo hito:** Backend pdpk (Linux) mediante FFM. Le seguirá npcap (Windows).


## Convenciones de código

- **Java 25**: se aprovechan características modernas (`var`, text blocks, pattern matching, sealed types, etc.)
- **FFM API**: toda interacción con código nativo se canaliza a través de `java.lang.foreign.*` (Linker, SymbolLookup, Arena, MemorySegment, MethodHandle). No se usa JNI ni JNA.
- **Arenas**: usar `Arena.ofConfined()` o `Arena.ofShared()` según el ciclo de vida. Documentar quién cierra cada arena. Preferir try-with-resources sobre `Arena.ofAuto()` cuando el ciclo de vida es claro.
- **Gestión de memoria nativa**: cada `MemorySegment` devuelto por una función nativa debe tener un dueño claro (arena o scope). No exponer segments crudos fuera del paquete de backend; copiar a tipos Java antes de cruzar la frontera del paquete.
- **Carga de librerías nativas**: cada backend carga su librería nativa (`System.loadLibrary` o `SymbolLookup.libraryLookup`) en un inicializador estático. Si la librería no está disponible, el backend debe lanzar una excepción informativa y no impedir que otros backends funcionen.
- **Abstracción de plataforma**: `core` define un SPI (Service Provider Interface) que cada backend implementa. La selección del backend se hace en runtime mediante `ServiceLoader` o factoría explícita.
- **Modelo de paquetes**: el modelo de paquete en core es inmutable (records o clases con campos `final`) y no depende de ningún backend concreto.
- **Idioma**: código fuente en inglés (nombres de clases, métodos, variables, comentarios y logs). Documentación del proyecto (AGENTS.md, openspec/) en español. El manual de usuario (`docs/user-guide.md`) y el `README.md` en inglés (audiencia pública).
- **Manual de usuario**: `docs/user-guide.md` documenta el API pública con ejemplos. Todo change que toque la API pública de la librería (`core`) o su comportamiento observable debe actualizar este manual.
- **Documentación OpenSpec**: la prosa de `proposal.md`, `design.md`, `tasks.md` y las especificaciones debe estar en español. Se mantienen en inglés únicamente las palabras, encabezados, etiquetas o marcadores que OpenSpec exija, además de nombres de módulos, clases, interfaces, rutas, códigos y otros identificadores técnicos.
- **Commits**: mensajes de commit en inglés, siguiendo conventional commits (feat:, fix:, docs:, etc.)
- **Issues y milestones de GitHub**: redactados en inglés (títulos y descripciones).
- **Paquete base**: `dev.ktai.lostrego`
- **Layout de proyecto**: los ficheros de build de Gradle y los módulos viven bajo el directorio `src/` (mismo patrón que el proyecto arume). Los comandos Gradle se ejecutan desde `src/`.
- **Catálogo de versiones**: las dependencias se centralizan en `src/gradle/libs.versions.toml`. Usar `version.ref` en las dependencias de submódulos, no versiones inline.

## Pruebas de la aplicación

- **Pruebas unitarias**: lógica de core aislada, sin dependencias nativas. Deben ejecutarse en cualquier plataforma.
- **Pruebas de integración**: cada backend debe tener tests que verifiquen la carga de la librería nativa, la llamada a funciones FFM y la captura de paquetes real (o simulada con loopback/pcap files).
- **Pruebas condicionales**: los tests que requieren librerías nativas deben anotarse con `@EnabledOnOs` o condiciones JUnit que verifiquen la presencia de la librería antes de ejecutarse. Un test de backend no debe fallar en una plataforma donde ese backend no aplica.
- **CI**: GitHub Actions debe ejecutar los tests en matriz Linux + Windows. En Linux, instalar `libpcap-dev`. En Windows, disponer de npcap instalado en el runner.
- **Capturas de prueba**: usar archivos `.pcap` de ejemplo en `src/test/resources/` para tests reproducibles que no dependan de tráfico de red real.

## Ficheros clave

| Fichero | Contenido |
|---|---|
| `docs/user-guide.md` | Manual de usuario de la librería (API pública y ejemplos) |
| `openspec/specs/` | Baseline de specs (spec-driven) |
| `openspec/changes/` | Changes activos y archivados |
| `openspec/config.yaml` | Configuración de OpenSpec |
| `.github/workflows/` | Workflows de CI (GitHub Actions) |

## Hoja de ruta (fases)

> Haciendo uso de GitHub y sus issues y milestones, iremos definiendo de forma incremental
> las funcionalidades del proyecto. Nos ayudaremos de agentes de IA y de OpenSpec para
> ir definiendo cada "change" e implementándolo.

- **Fase 0 — arranque y API pública** (completada): esqueleto Gradle + CI (issue #1) y API pública de `core` + SPI (issue #5).
- **Fase 1 — backends nativos**: libpcap (Linux/Mac) → pdpk (Linux) → npcap (Windows), cada uno implementando `CaptureProvider` mediante FFM.
- **Futuro** (sin milestone asignado aún): capa de parseo de protocolos, enumeración de dispositivos, publicación a Maven Central, macOS en CI. Ver issues en el milestone Backlog.

## Preferencias de trabajo del usuario

- **Comunicación en español.**
- El usuario prefiere contexto durable en ficheros del repo (este AGENTS.md) antes que en
  memoria interna del agente, para que sobreviva a clones/moves del repositorio.
- **`openspec/` es público a propósito**: el usuario lo publica como
  registro didáctico de cómo se desarrolla el proyecto asistido por agentes de IA. Al escribir
  proposals/designs/specs/tasks: audiencia pública — autocontenidos, manteniendo el tono didáctico.

## Flujo de trabajo (OpenSpec) — regla importante

El flujo de trabajo usa **OpenSpec** (`openspec/`): cada cambio se propone, se
implementa y se archiva. El baseline de specs vive en `openspec/specs/`
y los cambios archivados en `openspec/changes/archive/`.

Para cada change: crear primero el **`proposal.md`** y el **`design.md`** (y
`tasks.md` + `specs/`) y **DETENERSE**. **No pasar a ejecutar/implementar las
tareas hasta que el usuario haya revisado y aprobado el proposal y el design.**
Tras implementar **archivar solo cuando el usuario lo confirme**.

Tras cada archivado, revisar este **AGENTS.md** y actualizarlo si procede: estado actual
(fase, último hito, próximo hito), nuevas convenciones surgidas durante el change, o cualquier
apunte relevante que ayude a futuros agentes a entender el contexto del proyecto sin tener que
rastrearlo.

Tras cada archivado, extraer los items pendientes (Non-Goals, placeholders, "próximamente",
"futuro", riesgos pospuestos) y crear un **issue de GitHub** en el milestone **Backlog**
para cada uno, con descripción, origen del change y tareas previstas en el body.
Esto asegura que nada se pierda al cerrar el change y mantiene el backlog como fuente única en GitHub Issues.

### Nomenclatura de changes

Cada change de OpenSpec se nombra con el prefijo del issue de GitHub que lo origina:
`is<nº-issue>-<slug>`. Ejemplo: `is1-core-api` para el issue #1.
El nombre de la carpeta del change es `openspec/changes/is<nº-issue>-<slug>/`.

## Flujo de trabajo con GitHub (issues, ramas, PRs)

Cada change de OpenSpec se rastrea en GitHub con este ciclo:

1. Propuesta OpenSpec aprobada por el usuario.
2. **Issue en GitHub** para el change, con enlace a su carpeta `openspec/changes/<nombre>/`
   y **milestone de su fase** (Fase 0, Fase 1…). Los milestones dan la vista de progreso
   por fase.
3. **Rama creada desde el issue** (panel *Development* → "Create a branch"), partiendo de `main`.
   Convención de nombre: `change/is<nº-issue>_<slug>`. Ejemplo: `change/is12_libpcap-backend`
   para el issue #12.
4. Implementación en la rama + push (los push los hace el usuario; el agente no tiene
   SSH hacia `origin` desde su shell).
5. **PR hacia `main`** con `Closes #<nº>` en la descripción → la CI proyecto
   (`project-ci.yml`) valida la PR → revisión del diff por el usuario.
6. **Squash merge** como norma (un change = un commit limpio en `main`).
   Excepción: PRs cuyos commits intermedios tengan valor propio.
7. Una vez el usuario mergea el PR y borra la rama de trabajo, nos propondrá el **archivado del change**
   , esto se trasformará en un nuevo commit que el usuario hará push sobre la rama `main`.
   El CI de GitHub se ha configurado para que solo los PR lancen el compilado y testing
   pero no lo hará un push directo.
