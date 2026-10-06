# Eden's Particles

Data-driven, choreographed particle effects for Minecraft 1.21.1, for mod developers. Effects are JSON timelines of emitters, with curves that animate size, colour, opacity, spawn rate and more over time.

Supports Fabric, NeoForge and Forge.

```java
EdenParticles.play(level, ResourceLocation.fromNamespaceAndPath("mymod", "boom"), EffectAnchor.entity(player));
```

- [Writing effects](docs/authoring.md): the JSON format.
- [Using the API](docs/api.md): playing effects from code, and the commands.

Examples ship with the mod: try `/eden_particles_client play eden_particles:examples/vortex`.

## Project layout

Built on [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (1.21.1 branch).

- `common/`: all library logic, compiled against vanilla (Mojang mappings + Parchment).
- `fabric/`, `neoforge/`, `forge/`: entrypoints, registration, reload listeners and networking glue only.

## Building

Requires Java 21.

```
./gradlew build
```

Run tasks: `:fabric:runClient` / `:fabric:runServer`, `:neoforge:runClient` / `:neoforge:runServer`, `:forge:Client` / `:forge:Server` (ForgeGradle names them differently).

Dev servers need `eula.txt` accepted in their run directory, except NeoForge, which skips the check in development.

Testing with Sodium or Iris (Fabric only): `./gradlew :fabric:runClient -PwithSodium` or `-PwithIris` (which includes Sodium).
