# Eden's Particles

Data-driven, choreographed particle effects for Minecraft 1.21.1, for mod developers. Effects are JSON timelines of emitters, with curves that animate size, colour, opacity, spawn rate and more over time.

Supports Fabric, NeoForge and Forge.

```java
EdenParticles.play(level, ResourceLocation.fromNamespaceAndPath("mymod", "boom"), EffectAnchor.entity(player));
```

- [Writing effects](docs/authoring.md): the JSON format.
- [Using the API](docs/api.md): playing effects from code, and the commands.

Examples ship with the mod: try `/eden_particles_client play eden_particles:examples/vortex`.

## Depending on it

Releases are published to GitHub Packages. GitHub needs a token to read from it, even for public packages: create a personal access token with `read:packages` (and access to this repository while it's private), and put it in `~/.gradle/gradle.properties` as `gpr.user=<your GitHub name>` and `gpr.key=<token>`.

```groovy
repositories {
    maven {
        url = 'https://maven.pkg.github.com/ImEden6/eden-particles'
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    // Fabric (Loom)
    modImplementation 'com.eden.particles:eden_particles-fabric-1.21.1:0.1.0'
    // NeoForge (ModDevGradle)
    implementation 'com.eden.particles:eden_particles-neoforge-1.21.1:0.1.0'
    // Forge (ForgeGradle)
    implementation fg.deobf('com.eden.particles:eden_particles-forge-1.21.1:0.1.0')
    // A multiloader common module (vanilla mappings)
    compileOnly 'com.eden.particles:eden_particles-common-1.21.1:0.1.0'
}
```

Players need the mod installed too.

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

Demo mode: add `-Pdemo=<effect id>` to a client run (for example `./gradlew :fabric:runClient -Pdemo=eden_particles:examples/vortex`) to replay that effect in front of you whenever nothing else is playing.

## Releasing

Set `version` in `gradle.properties`, commit, then tag and push it:

```
git tag v0.1.0
git push origin v0.1.0
```

CI builds and tests as usual, and only then publishes all four artifacts. It refuses a tag that doesn't match `version`.

## Testing

- `./gradlew :common:test`: unit tests (a few seconds), plus a coverage report. `./gradlew build` also fails if line or branch coverage drops below the floor in `common/build.gradle`.
- Snapshot baselines: `EffectSnapshotTest` plays every bundled example with fixed seeds and compares a fingerprint of what it spawns against `common/src/test/resources/baselines/effects.txt`. After an intended change, run `./gradlew :common:test -PupdateBaselines` and commit the new baseline with the change.
- `./gradlew :common:bench`: times the runtime against `baselines/bench.txt` (recorded on one machine, so only compare on that machine). `-PupdateBaselines` re-records it; `-PbenchMaxRegression=25` fails if any case is more than 25% slower.
- `ACCEPT_EULA=true scripts/smoke-server.sh fabric|neoforge|forge`: starts that loader's dedicated server in `<loader>/runs/smoke` with a test datapack and checks the server side end to end. Setting `ACCEPT_EULA=true` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA). CI runs it for all three loaders.
- In-game numbers from `/eden_particles_client bench` are recorded by hand in `baselines/in-game.txt`.

Testing with Sodium or Iris (Fabric only): `./gradlew :fabric:runClient -PwithSodium` or `-PwithIris` (which includes Sodium).
