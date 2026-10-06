# Using Eden's Particles from code

Everything you need is in `com.eden.particles.api`. Anything under `impl` can change without notice.

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
EffectAnchor.entity(entity, new Vec3(0, 1, 0))  // follows with an offset
```

An effect following an entity stops when the entity is removed. The offset is in world space: it doesn't turn when the entity turns. To rotate the effect, use `EffectParams.rotation`. If a client hasn't loaded the entity yet, the effect plays where the entity was when it was sent.

## Parameters

```java
EffectParams params = EffectParams.builder()
        .scale(2f)              // distances, speeds, sizes and gravity
        .rotation(yaw, 0, 0)    // degrees; Minecraft convention (yaw 0 = south, +pitch = down)
        .tint(0xFF8080)         // multiplies every particle's colour (Eden sprites only)
        .seed(12345L)           // same seed → same pattern; random if not set
        .range(96f)             // server send distance
        .build();
```

## Handles

`play` returns an `EffectHandle`:

```java
EffectHandle handle = EdenParticles.play(level, id, EffectAnchor.entity(player), params);
handle.stop();        // stops new spawns; existing particles finish their lives
handle.isAlive();
```

On the client, `isAlive()` turns false when the effect finishes. A server doesn't know effect durations, so a server handle stays alive until you call `stop()` or its anchor entity is removed. Stop looping effects yourself.

If nothing could be played (unknown effect on the client, or no players in range on the server), you get `EffectHandle.NONE`.

## Client helpers

```java
EdenParticles.isLoaded(id);                 // is this effect loaded on this client?
EdenParticles.onReload(() -> rebuildMyCache());
```

Both are client-only and do nothing on a dedicated server.

## Commands

| Command | Side | |
|---|---|---|
| `/eden_particles play <effect> [pos] [targets]` | server, op 2 | Plays through the network, the same as `EdenParticles.play` on a server level. |
| `/eden_particles stop [targets]` | server, op 2 | Stops every server-started effect for those players. |
| `/eden_particles_client play <effect>` | client | Plays locally, in front of you. Tab completes loaded effects. |
| `/eden_particles_client errors` | client | Lists definition errors from the last reload. |
| `/eden_particles_client bench <effect> [count]` | client | Measures Eden's tick cost on a grid of effects. |

## Known limitations

- A player who comes into range after a server-started effect began doesn't see it. Re-send long or looping effects if that matters.
- Vanilla-type particles (`"render": {"type": "vanilla"}`) ignore `tint`.
