# Eden's Particles

Data-driven, choreographed particle effects for Minecraft 1.21.1, for mod developers. Effects are defined in JSON: multi-emitter timelines with curves that animate properties over time.

Supports Fabric, NeoForge and Forge.

## Project layout

Built on [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (1.21.1 branch).

- `common/`: all library logic, compiled against vanilla (Mojang mappings + Parchment).
- `fabric/`, `neoforge/`, `forge/`: entrypoints, registration, reload listeners and networking glue only.

Loader-specific behavior goes behind interfaces in `com.eden.particles.platform.services`, with one implementation per loader registered under `META-INF/services`.

## Building

Requires Java 21.

```
./gradlew build
```

Run tasks: `:fabric:runClient` / `:fabric:runServer`, `:neoforge:runClient` / `:neoforge:runServer`, `:forge:Client` / `:forge:Server` (ForgeGradle names them differently).

Dev servers need `eula.txt` accepted in their run directory, except NeoForge, which skips the check in development.
