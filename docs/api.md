# Using Eden's Particles from code

Everything you need is in `com.eden.particles.api`. Anything under `impl` can change without notice.

## Stability

From 1.0.0 on, these follow semantic versioning. They only break in a new major version:

- the `com.eden.particles.api` package
- the effect and template JSON format. Files can say `"format": 1`, and a file with a newer format than the installed mod reads fails with a message saying so.
- the network protocol (currently 2). Clients and servers on different protocols ignore each other's effects. They don't disconnect.

New optional JSON fields and new API methods can arrive in minor versions. A file that uses a new field won't load on an older 1.x, so put the minimum version you need in your mod's dependency.

## Playing an effect

```java
EdenParticles.play(level, ResourceLocation.fromNamespaceAndPath("mymod", "boom"), EffectAnchor.at(pos));
```

This works from either side:
- **Client level:** the effect plays on this client only.
- **Server level:** the effect is sent to every player within `range` blocks of the anchor (default 64) who has Eden's Particles installed. Players without it are skipped and can still join. Every client gets the same random seed, so everyone sees the same pattern.

To send to one player regardless of range:

```java
EdenParticles.playFor(serverPlayer, id, anchor, EffectParams.DEFAULT);
```

The server never simulates particles and doesn't read effect files. Effects live in client resource packs, so ship your JSON in your mod's `assets`.

## Anchors

```java
EffectAnchor.at(vec3)                           // a fixed point
EffectAnchor.at(blockPos)                       // bottom centre of a block
EffectAnchor.entity(entity)                     // follows the entity
EffectAnchor.entity(entity, new Vec3(0, 1, 0))  // follows with a world-space offset
EffectAnchor.entity(player).eyes()              // from eye height
EffectAnchor.entity(mob).turning(Turning.BODY)  // turns with the body
EffectAnchor.entity(player).eyes().turning(Turning.LOOK).offset(0, 0, 0.5)  // half a block in front of the eyes
EffectAnchor.between(EffectAnchor.entity(caster).eyes(), EffectAnchor.entity(target))  // a beam
```

An effect following an entity stops when the entity is removed. If a client hasn't loaded the entity yet, the effect plays where the entity was when it was sent.

**Turning.** By default the effect keeps world axes and the offset is in world space. With `turning`, the effect turns with the entity and the offset is in the entity's frame: x is left, y is up, z is forward. `BODY` follows the body's yaw, so a mob's aura faces where it walks. `LOOK` follows the head's yaw and pitch, which is where a player is looking. `EffectParams.rotation` is applied on top.

**Between two points.** `between(from, to)` plays at `from`, turned so the effect's +Z axis points at `to`, and sets the effect's built-in `distance` input to the length between them every tick. Either end can follow an entity, with its own eyes, turning and offset; the effect ends when either entity is removed. A server sends it to players within range of either end. See [Inputs](authoring.md#inputs) for writing the effect.

## Parameters

```java
EffectParams params = EffectParams.builder()
        .scale(2f)              // distances, speeds, sizes and gravity
        .rotation(yaw, 0, 0)    // degrees; Minecraft convention (yaw 0 = south, +pitch = down)
        .tint(0xFF8080)         // multiplies every particle's colour
        .seed(12345L)           // same seed → same pattern; random if not set
        .range(96f)             // server send distance
        .persistent()           // server: keep sending to players who arrive later (for looping effects)
        .duration(60)           // server: send to players who arrive in the next 60 ticks
        .input("power", 0.7f)   // an input the effect declares
        .build();
```

### Inputs

Effects can declare named numbers, such as a charge level, that change how they look (see [Inputs](authoring.md#inputs)). Set them with `input(name, value)`. Inputs you don't set keep the effect's defaults. On the client, a name the effect doesn't declare is ignored with a warning in the log; the server can't check, because it doesn't read effect files.

### Players who arrive late

A server-started effect reaches players who come into range while it's still playing, caught up to where everyone else is. The server needs to know how long it plays:

- Datapack effects that don't loop: the server reads their `duration`, so this just works.
- Effects that only live in client resource packs: pass `duration(ticks)`. Without it, they only reach players who are in range when they start.
- Looping effects: use `persistent()`, below.

### Persistent effects

With `persistent()`, the server remembers the effect and sends it to anyone who comes into range later, or who comes back after changing dimension. A looping effect joins at the same point in its loop as everyone else's, so a late joiner's aura pulses in step with what other players see. The particles themselves are rolled fresh, so they won't match exactly.

The server can't tell which effects loop, so it's up to you to mark them. A persistent effect lives until you call `stop()` on its handle or its anchor entity is removed. Use it for looping effects, and keep the handle.

## Handles

`play` returns an `EffectHandle`:

```java
EffectHandle handle = EdenParticles.play(level, id, EffectAnchor.entity(player), params);
handle.stop();        // stops new spawns; existing particles finish their lives
handle.isAlive();
```

On the client, `isAlive()` turns false when the effect finishes. A server doesn't know effect durations, so a server handle stays alive until you call `stop()` or its anchor entity is removed. Stop looping effects yourself.

If nothing could be played (unknown effect on the client, or no players in range on the server), you get `EffectHandle.NONE`.

### Changing a playing effect

```java
handle.setInput("power", charge);
handle.setTint(0xFF4040);
handle.setScale(1.5f);
handle.setRotation(yaw, 0, 0);
```

New particles use the new values; particles already spawned keep theirs. On the client the change applies on the next tick. On the server, changes are collected and sent to everyone who has the effect at the end of the tick, so calling a setter every tick for a charge-up is fine. Players who join a persistent effect late get its current values. Setters on `EffectHandle.NONE` do nothing.

## Client helpers

```java
EdenParticles.isLoaded(id);                 // is this effect loaded on this client?
EdenParticles.onReload(() -> rebuildMyCache());
```

Both are client-only and do nothing on a dedicated server.

## Effects from code

Mods can register templates and effects from JSON in code instead of a resource pack. The definitions are parsed and linked like pack files. They override a pack file with the same id, and a server datapack still overrides them. They survive resource reloads. Each call relinks every effect, so register in batches at startup rather than every tick. Registrations are client-only and do nothing on a dedicated server.

```java
List<String> templateErrors = EdenParticles.registerTemplate(id("dot"), JsonParser.parseString(dotJson));
List<String> effectErrors = EdenParticles.registerEffect(id("burst"), JsonParser.parseString(burstJson));
if (!effectErrors.isEmpty()) LOG.warn("Bad effect: {}", effectErrors);
```

The returned list holds the errors for the id that was registered. It is empty when that definition loaded. `EdenParticles.unregister(id)` removes it again.

## Commands

| Command | Side | |
|---|---|---|
| `/eden_particles play <effect> [pos] [targets]` | server, op 2 | Plays through the network, the same as `EdenParticles.play` on a server level. |
| `/eden_particles play <effect> <pos> persistent` | server, op 2 | The same, but players who arrive later get it too. |
| `/eden_particles play <effect> <pos> with <inputs>` | server, op 2 | Plays with inputs, written `power=2 spread=0.5`. |
| `/eden_particles beam <effect> <from> <to> [inputs]` | server, op 2 | Plays between two entities' eyes, following both. |
| `/eden_particles attach <effect> <entity> <none\|body\|look> <feet\|eyes> [x y z] [inputs]` | server, op 2 | Plays on an entity with a turning anchor; the offset is in the entity's frame when turning. |
| `/eden_particles set <input> <value>` | server, op 2 | Changes an input on every effect these commands started that is still playing, like `EffectHandle.setInput`. |
| `/eden_particles stop [targets]` | server, op 2 | Stops every server-started effect for those players. Without targets, it also forgets the dimension's tracked effects, so nobody arriving later gets them. |
| `/eden_particles_client play <effect> [inputs]` | client | Plays locally, in front of you. Tab completes loaded effects. |
| `/eden_particles_client errors` | client | Lists definition errors from the last reload. |
| `/eden_particles_client reload` | client | Reloads effect and template files without a full `F3+T`, then prints how many loaded and how many failed. Images aren't reloaded. |
| `/eden_particles_client preview <effect> [inputs]` | client | Replays the effect in front of you whenever nothing else is playing. `preview stop` ends it. |
| `/eden_particles_client bench <effect> [count]` | client | Measures Eden's tick cost on a grid of effects. |
