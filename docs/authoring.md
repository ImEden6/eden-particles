# Writing effects

Effects are JSON files in a resource pack (your mod's `assets` count). Each effect is a timeline of **emitters**. Each emitter spawns particles described by a **template**.

```
assets/<namespace>/eden_particles/effects/<path>.json    → effect   <namespace>:<path>
assets/<namespace>/eden_particles/templates/<path>.json  → template <namespace>:<path>
assets/<namespace>/textures/particle/<name>.png          → sprite   <namespace>:<name>
```

Effects, templates and images can also come from a **datapack**:

```
data/<namespace>/eden_particles/effects/<path>.json      → effect   <namespace>:<path>
data/<namespace>/eden_particles/templates/<path>.json    → template <namespace>:<path>
data/<namespace>/eden_particles/textures/<path>.png      → sprite   <namespace>:<path>
```

The server sends definitions to every player with the mod when they join and after `/reload`. A datapack definition overrides a resource-pack one with the same id, and can use resource-pack templates and sprites. Images need the player's consent first: see [Images in a datapack](#images-in-a-datapack). Leaving the server removes everything it sent.

Sprites go in the vanilla particle atlas, so any PNG under `textures/particle/` works with no extra configuration. Vanilla sprites work too (`"minecraft:bubble"`, `"minecraft:flame"`).

All times are in **ticks** (20 per second). Distances are in blocks. Speeds are in blocks per tick.

For autocomplete and typo checks in your editor, point the file at the schema. Copy `docs/schema/` from this repo next to your files:

```json
{ "$schema": "../schema/effect.schema.json", "format": 1, "duration": 40, "emitters": [ … ] }
```

Templates use `template.schema.json`, which needs `effect.schema.json` beside it. `format` is optional and only marks which format the file is written for.

Reload with `F3+T`. `/eden_particles_client errors` lists any problems, and `/eden_particles_client play <effect> [inputs]` plays one in front of you (inputs as `power=2 spread=0.5`). The mod ships working examples under `eden_particles:examples/…`.

## Images in a datapack

Put PNGs in `data/<namespace>/eden_particles/textures/` and use them like any sprite: `data/mypack/eden_particles/textures/ember.png` is `"sprite": "mypack:ember"`. If a datapack image and a resource-pack sprite share an id, the datapack image wins.

**Players are asked first.** When they join, they see "Particle images from this server" with **Allow**, **Always allow** (remembered for that server in `config/eden_particles-client.json`), or **Don't allow**. Until they allow, particles that use a server image simply don't spawn; the rest of each effect still plays. Your own singleplayer worlds never ask.

**Limits** (larger images are rejected with an error naming the file):

| | |
|---|---|
| Size | up to 256 × 256 pixels |
| File | up to 256 KiB, PNG only |
| Count | up to 512 images |
| Total | up to 8 MiB |

Players keep the images they receive in `eden_particles/image_cache/` in their game folder (up to 64 MiB, oldest first out), so when they rejoin, or after a `/reload`, only new or changed images are downloaded. A saved image is only reused for the server that sent it. Players are still asked before a server's images are used, whether or not they're saved.

### Alternative: a server resource pack

If you'd rather players download images through vanilla's own mechanism, ship them in a **server resource pack** instead. Players are prompted by Minecraft itself.

1. **Make a resource pack** with your PNGs:
   ```
   my_particles.zip
   ├── pack.mcmeta
   └── assets/mypack/textures/particle/
       ├── ember.png
       └── smoke_puff.png
   ```
   `pack.mcmeta` goes at the root of the zip, not inside a folder. For 1.21.1 it looks like this:
   ```json
   { "pack": { "pack_format": 34, "description": "Particle images" } }
   ```
2. **Use the images** from your datapack templates as `"sprite": "mypack:ember"`. The id is the namespace folder, then the file name without `.png`.
3. **Host the zip** at a URL that downloads the file directly, such as a GitHub release asset or your own web server. A share page that shows a "Download" button won't work.
4. **Point the server at it** in `server.properties`:
   ```properties
   resource-pack=https://example.com/my_particles.zip
   resource-pack-sha1=<the zip's SHA-1>
   require-resource-pack=true
   ```
   Get the SHA-1 on Windows with `certutil -hashfile my_particles.zip SHA1`, or `sha1sum my_particles.zip` elsewhere. Clients use it to notice when the pack changes. Update it whenever you change the zip. `require-resource-pack=true` disconnects players who decline. Leave it `false` to let them join anyway: they'll still see the effects, but with a missing-texture checkerboard where your images should be, and a warning naming the missing sprite in their log.

In singleplayer, put the zip in `.minecraft/resourcepacks` and turn it on under Options → Resource Packs.

## Effect

```json
{
  "duration": 60,
  "loop": false,
  "range": 64,
  "inputs": { "power": 1 },
  "emitters": [ … ]
}
```

| Field | Default | |
|---|---|---|
| `duration` | required | Length of the timeline in ticks. |
| `loop` | `false` | Restart the timeline when it ends. Looping effects play until stopped. |
| `range` | `64` | Particles thin out from 3/8 of this distance and are gone by 3/4 of it. |
| `inputs` | none | Numbers the code playing the effect can set, with their defaults. See [Inputs](#inputs). |
| `emitters` | required | At least one. Each entry is an [emitter](#emitter), a [sub-effect](#sub-effects), a [sound](#sounds) or a [light](#lights). |

## Inputs

Inputs let one effect cover many cases: a charge level, a size, a spread. Declare them on the effect with a default:

```json
"inputs": { "power": 1, "spread": 0.5 }
```

Then write an **expression** in place of a number:

```jsonc
"spawn": { "rate": "power * 10" },
"shape": { "type": "sphere", "radius": "0.5 + power * 2" },
"velocity": { "type": "cone", "half_angle": "spread * 60", "speed": ["power * 0.1", "power * 0.2"] }
```

A curve that changes over time can be multiplied by an expression with `scale`:

```json
"radius": { "keys": [[0, 0], [1, 3]], "ease": "out_cubic", "scale": "power" }
```

- Expressions use numbers, input names, `+ - * / %`, parentheses and the functions `min`, `max`, `clamp(x, lo, hi)`, `lerp(a, b, t)`, `abs`, `floor`, `ceil`, `round`, `sqrt` and `pow`. Dividing by zero gives 0.
- **`distance`** is built in: the length of a two-point anchor (a beam from one entity to another), and 0 otherwise. Such effects are turned so their +Z axis points at the far end, so a beam is `"shape": {"type": "line", "from": [0, 0, 0], "to": [0, 0, "distance"]}`. See the `eden_particles:examples/beam` example.
- A template can use inputs too. Every effect using that template must declare them.
- Using an input the effect doesn't declare is an error when it loads, with a suggestion if it looks like a typo.

Where expressions work:

| | |
|---|---|
| Emitters | `spawn.rate`, burst `count`, shape radii, `inner_radius`, box `size`, line `from` / `to`, `offset`, `spin`, velocity `speed`, cone `half_angle`, fixed `direction` |
| Templates | `lifetime`, `size`, `alpha`, `speed`, `physics.gravity`, `physics.drag` |

Not in timeline values (`duration`, `start`, burst `at`) or colours; use the code's tint for colour.

Code can change inputs while an effect plays. Emitter values follow the change on the next tick. Template values are worked out when each particle spawns, so particles already alive keep theirs.

## Effects that play effects

An effect can play other effects in two ways: a **sub-effect** on the timeline, and a **trigger** on a template that fires when its particles die or land.

### Sub-effects

An entry in `emitters` with `"effect"` instead of `"template"` plays another effect as part of this one:

```json
{ "effect": "eden_particles:flash", "start": 10, "duration": 20, "offset": [0, 1, 0], "rotation": [0, 90, 0],
  "scale": "power", "inputs": { "power": "power * 2" } }
```

- `effect` is the effect to play. Required.
- `start` is the tick it starts on. Default `0`.
- `duration` is how long it runs. Default is the rest of the parent's timeline.
- `offset` moves it, in the parent's frame.
- `rotation` is yaw, pitch and roll in degrees, added to the parent's.
- `scale` is an expression that multiplies its size.
- `inputs` sets the child's inputs. Each value is an expression over the parent's inputs, re-evaluated every tick, so live changes reach the child.

The child follows the parent's anchor and inherits its facing, tint and `distance`. A looping parent plays it again on every loop. When the child's window ends, it stops spawning and its particles finish their lives.

See `eden_particles:examples/charge_release` for a timeline with two sub-effects.

### On death and on landing

A template can play an effect when its particles die or land:

```json
"on_death": { "effect": "eden_particles:pop", "chance": 0.5, "aim": "velocity", "inputs": { "size": "power" } }
```

- `effect` is the effect to play. Required.
- `chance` is the odds, from 0 to 1, as an expression. Default `1`.
- `aim` is `none` (world axes, the default) or `velocity`, which turns the played effect's +Z along the particle's velocity at that moment.
- `inputs` sets the played effect's inputs, as expressions.

`chance` and `inputs` are read from the spawning effect's inputs when the particle spawns. The played effect starts at the particle's position, with its scale and tint.

Triggers work on sprite templates only. `on_land` also needs `"physics": {"collide": true}`, and it fires once, the first time a particle touches a block.

Whether a particle's trigger fires is decided the same way for every player, so they see the same pops. At most 64 triggered effects start per tick; past that, extra ones are skipped.

See `eden_particles:examples/firework` (a rocket that bursts on death) and `eden_particles:examples/rain` (drops that splash on landing).

### Load-time checks

- The effect you play must exist.
- The inputs you pass must be declared by the effect you play.
- An effect can't play itself, directly or through a chain of sub-effects and triggers. The error names the loop.

If the played effect fails to load, the effect that plays it fails too, and the error points to the child's error.

## Sounds

An entry in `emitters` with `"sound"` plays a sound as part of the effect:

```json
{ "sound": "minecraft:entity.firework_rocket.blast", "start": 0, "volume": 0.8, "pitch": [0.9, 1.1] }
{ "sound": "minecraft:block.beacon.ambient", "loop": true, "start": 10, "duration": 30, "pitch": "0.8 + power * 0.6" }
```

| Field | Default | |
|---|---|---|
| `sound` | required | A sound event id, from vanilla or any resource pack's `sounds.json`. |
| `start` | `0` | Tick it starts on. |
| `loop` | `false` | Repeat the sound until `duration` ends or the effect stops, then fade it out. |
| `duration` | rest of the timeline | Looping sounds only. |
| `volume` | `1` | A [random value](#random-values). Above 1 makes it heard from further away, like vanilla. |
| `pitch` | `1` | A random value. Vanilla clamps it to 0.5–2. |
| `offset` | `[0, 0, 0]` | Moves the sound in the effect's frame. |
| `category` | `"player"` | Which volume slider controls it: `master`, `music`, `record`, `weather`, `block`, `hostile`, `neutral`, `player`, `ambient` or `voice`. |
| `name` | none | Used in error messages. |

- The sound follows the effect's anchor while it plays, so a charge-up stays on a moving player.
- `volume` and `pitch` are rolled from the effect's seed when the sound starts, so every player hears the same pitch. Expressions use the inputs at that moment.
- A looping effect plays one-shot sounds again on every loop. A looping sound that covers the whole timeline keeps playing across loops without restarting.
- Someone who arrives after a one-shot has played doesn't hear it. Looping sounds still start for them.
- Sounds aren't thinned by the *Particles* setting or by distance. Players hear them up to the sound's own range.
- At most 4 copies of the same sound start per tick, so a burst of particles that all play a sound on death doesn't stack into a wall of noise.
- An unknown sound id logs one warning and is skipped. It can't be checked at load time, because resource packs' sounds load separately.

To play a sound when a particle dies or lands, make a small effect with just the sound and play it with [`on_death` or `on_land`](#on-death-and-on-landing).

See `eden_particles:examples/charge` (a looping hum whose pitch rises with `power`), `firework_burst` (a blast, then a twinkle) and `rain` (a drip on each splash).

## Lights

An entry in `emitters` with `"light"` lights up the blocks around the effect, like a torch that moves with it:

```json
{ "light": [[0, 15], [1, 0]], "start": 40, "duration": 12 }
{ "light": "4 + power * 10", "offset": [0, 1, 0] }
```

| Field | Default | |
|---|---|---|
| `light` | required | A [curve](#curves) of block light, 0 to 15, over the entry's window. |
| `start` | `0` | Tick it turns on. |
| `duration` | rest of the timeline | Ticks it stays on. |
| `offset` | `[0, 0, 0]` | Moves the light in the effect's frame. |
| `name` | none | Used in error messages. |

- Lights need [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) (Fabric or NeoForge) on the client. Without it, lights are skipped and the rest of the effect plays as normal. Forge has no supported dynamic-lights mod, so lights never show there.
- The light follows the effect's anchor and fades with distance like block light: level 15 reaches about 15 blocks.
- Lights are per effect, not per particle. Relighting blocks is expensive, so at most 32 effect lights are lit at once; past that, new ones are skipped.
- A light that covers a looping effect's whole timeline stays on across loops. A late joiner sees lights still in their window.

See `eden_particles:examples/charge` (a glow that grows with `power`) and `charge_release` (a flash when it fires).

## Emitter

An entry with `"effect"` instead of `"template"` is a sub-effect. See [Effects that play effects](#effects-that-play-effects).

| Field | Default | |
|---|---|---|
| `template` | required | A template id, or a template object inline. |
| `duration` | required | Ticks this emitter runs. `start + duration` must fit in the effect. |
| `start` | `0` | Tick it starts on. |
| `spawn` | required | See [Spawning](#spawning). |
| `shape` | point | Where particles appear. See [Shapes](#shapes). |
| `velocity` | none | How they start moving. See [Velocity](#velocity). |
| `offset` | `[0, 0, 0]` | Moves the emitter relative to the effect's anchor. |
| `axis` | `"y"` | The shape's up direction: `"x"`, `"-y"`… or a vector like `[1, 1, 0]`. |
| `spin` | `0` | A curve, in degrees per tick, rotating the shape around its axis. |
| `distribution` | `"random"` | `"even"` spaces burst particles evenly (sphere, disc or ring shapes only). |
| `important` | `false` | Keeps a few particles on the *Minimal* particle setting, like vanilla's important particles. |
| `name` | none | Used in error messages. Must be unique within the effect. |

### Spawning

```json
"spawn": { "rate": [[0, 2], [1, 8]], "bursts": [{ "at": 0, "count": [20, 30] }], "jitter": false }
```

- `rate`: a curve of particles per tick over the emitter's life. Fractions accumulate, so `0.3` spawns roughly every third tick. The total spawned matches the area under the curve exactly.
- `bursts`: `count` particles all at once, `at` ticks after the emitter starts.
- `jitter`: spawns at random intervals (Poisson) instead of evenly. The average stays the same.

### Shapes

All shapes are centred on the emitter. Radii are curves over the emitter's life, so a ring can shrink. Other sizes are numbers or [expressions](#inputs).

| `type` | Fields |
|---|---|
| `point` | none |
| `sphere` | `radius`, `inner_radius` (makes a shell), `surface` |
| `hemisphere` | `radius`, `surface` (the upper half, along `axis`) |
| `disc` | `radius`, `inner_radius` (makes a ring band) |
| `ring` | `radius` |
| `box` | `size: [x, y, z]` (full size), `surface` |
| `line` | `from`, `to` |

Points are spread uniformly by area or volume, so they don't bunch up at the centre.

### Velocity

| `type` | Fields | Direction |
|---|---|---|
| `none` | none | Particles start still. |
| `fixed` | `direction`, `speed` | One direction, in the emitter's frame. |
| `radial` | `speed` | Away from the shape's centre. |
| `toward_center` | `speed` | Toward the centre. |
| `tangent` | `speed` | Around the axis, the same way positive `spin` turns. |
| `cone` | `half_angle` (degrees), `speed` | Uniformly within a cone around the axis. |

`speed` is a [random value](#random-values) in blocks per tick. You can also give `{"distance": d}`: each particle then gets the speed that carries it `d` blocks over its lifetime, given its drag. That way you can change drag or lifetime without retuning speeds.

## Template

```json
{
  "render": { "type": "sprite", "sprite": "eden_particles:glow", "blend": "additive", "emissive": true },
  "lifetime": [15, 25],
  "size": [[0, 0.0], [0.15, 0.25], [1, 0.0]],
  "alpha": [[0, 0.55], [0.7, 0.55], [1, 0.0]],
  "color": [{ "t": 0, "color": "#A66BFF" }, { "t": 1, "color": "#4A1FD6" }],
  "speed": 1,
  "physics": { "gravity": 0, "drag": 0.92, "collide": false }
}
```

Curves on a template run over the particle's own life, from `t = 0` (spawn) to `t = 1` (death).

| Field | Default | |
|---|---|---|
| `render` | required | See below. |
| `lifetime` | required | Ticks, as a whole number or a range like `[15, 25]`. |
| `size` | `0.1` | Curve of the quad's size in blocks. |
| `alpha` | `1` | Curve of opacity. |
| `color` | white | Colour or gradient. |
| `speed` | `1` | Curve multiplying how far the particle moves each tick. |
| `physics.gravity` | `0` | `1` is vanilla's normal pull. Negative values float upward. |
| `physics.drag` | `0.98` | Speed kept each tick (vanilla's friction). `0.9` stops quickly, `1` never slows down. |
| `physics.collide` | `false` | Bounces off blocks. Costs more, so leave it off unless you need it. |
| `physics.forces` | none | Up to 8 pushes applied each tick. Not on vanilla templates. See [Forces](#forces). |
| `on_death` | none | Plays an effect when a particle dies. Not on vanilla templates. See [On death and on landing](#on-death-and-on-landing). |
| `on_land` | none | Plays an effect the first time a particle touches a block. Not on vanilla templates, and needs `physics.collide`. See [On death and on landing](#on-death-and-on-landing). |
| `trail` | none | A ribbon left behind each particle. Not on vanilla templates. See [Trails](#trails). |

**Render types:**
- `{"type": "sprite", "sprite": …, "blend": "translucent" | "additive", "emissive": bool}` is Eden's own particle and follows every curve. See [Sprites](#sprites) for animated forms. `additive` adds light: overlapping particles brighten, which suits glows, sparks and magic. `emissive` makes the particle full-bright at night.
- `{"type": "ribbon", "sprite": …, "blend": "translucent" | "additive", "emissive": bool}` draws only the particle's [trail](#trails), with no quad at its head. It needs a `trail` block. Use it for slashes and streaks, where the shape of the path is the effect.
- `{"type": "vanilla", "particle": "minecraft:campfire_cosy_smoke"}` spawns a vanilla particle type that takes no options. Emitter settings, lifetime and physics apply. Curves don't, and they're rejected if present.

> **Additive colour tip:** additive blending adds colours up and clips at white. Pale colours (like `#D8C4FF`) turn white wherever two particles overlap. Use saturated colours with `alpha` around 0.4–0.6, so dense areas build up to a bright core instead.

### Sprites

```jsonc
"sprite": "mypack:ember"                                                       // one image
"sprite": { "frames": ["mypack:f0", "mypack:f1", "mypack:f2"] }                 // frames spread over the particle's life
"sprite": { "frames": [ … ], "mode": "loop", "ticks_per_frame": 2 }             // cycle at a fixed speed
"sprite": { "frames": [ … ], "mode": "random" }                                 // each particle shows one frame for its whole life
"sprite": { "sheet": "mypack:smoke", "columns": 4, "rows": 2, "count": 7 }      // one image sliced into frames
```

- `mode` is `over_life` by default. `ticks_per_frame` only applies to `loop`.
- A **sheet** is read left to right, top to bottom. `count` defaults to `columns × rows`; use it when the last row isn't full. Up to 64 frames in a list, 256 in a sheet.
- Frames can be resource-pack sprites (including vanilla ones such as `minecraft:generic_0` … `minecraft:generic_7`) or datapack images, but not a mix of both in one sprite.

### Trails

A trail is a ribbon that follows the particle, turned to face the camera. It's made of points the particle drops as it moves, and each point fades out over the trail's `lifetime`.

```json
"trail": {
  "lifetime": 14,
  "max_points": 32,
  "width": [[0, 0.22], [1, 0]],
  "alpha": [[0, 0.85], [1, 0]],
  "color": [{ "t": 0, "color": "#E040FB" }, { "t": 1, "color": "#651FFF" }]
}
```

| Field | Default | |
|---|---|---|
| `lifetime` | required | Ticks each point lasts. |
| `max_points` | `16` | 3 to 64. When full, the oldest point is dropped, which shortens the trail. |
| `min_distance` | `0.02` | Blocks the particle must move before it drops another point. |
| `width` | the template's `size` | Curve of the width in blocks. |
| `alpha` | the template's `alpha` | Curve of opacity. |
| `color` | the template's `color` | Colour or gradient. The effect's tint applies on top. |
| `sprite` | the render sprite | Texture of the ribbon. See [Sprites](#sprites); animated sprites follow the particle's life. |
| `mode` | `"stretch"` | `stretch` spreads the texture once along the whole trail; `tile` repeats it every `tile_length` blocks. |
| `tile_length` | `1` | Blocks per texture repeat in `tile` mode. At least 0.01. |
| `blend` | the render blend | Only for `ribbon` templates. A `sprite` template draws its trail with the head's blend and ignores this, with a warning. |
| `emissive` | the render `emissive` | Full-bright at night. |

- `width`, `alpha` and `color` run over each point's age, from `t = 0` (just dropped) to `t = 1` (`lifetime` ticks old), not over the particle's life. Width grows with the effect's `scale`.
- When the particle dies, its trail stays until the last point fades, including when an attractor absorbs it. `on_death` fires when the particle dies, not when the trail ends.
- The trail sprite must come from the same place as the head sprite: both from resource packs or both from a datapack. If they don't, a `sprite` template uses its head sprite for the trail and logs a warning.
- Each point costs about as much as a small quad. Long trails on hundreds of particles add up, so keep `max_points` low on dense effects.

The bundled examples show each kind:

- `eden_particles:examples/magic_missile`: sprite heads with trails, swirled by turbulence.
- `eden_particles:examples/spark_ribbon`: a burst of falling sparks with short trails.
- `eden_particles:examples/slash_arc`: three stacked `ribbon` swipes a tick apart, each held on a curved path by a vortex's `pull`, like a sword swing.

## Forces

A force pushes particles every tick. Forces go in a template's `physics` as a list of up to 8:

```json
"physics": {
  "drag": 0.96,
  "forces": [
    { "type": "turbulence", "strength": 0.01, "scale": 2, "flow": 0.02 },
    { "type": "attractor", "point": [0, 1, 0], "strength": 0.05, "falloff": "inverse_square", "absorb": 0.2 }
  ]
}
```

These rules apply to every force:

- Each tick a force adds an acceleration to the particle's velocity, after gravity and before drag. Units are blocks per tick², scaled by the effect's `scale` like gravity. With `drag` below 1, a steady force settles to a top speed of roughly `strength / (1 − drag)`.
- `vanilla` templates can't have forces. One with forces fails to load, the same as size and alpha curves and `on_death`.
- `strength` is a curve over the particle's life, like `size` and `alpha`: a number, an expression, a key list, or an object with `keys` and `scale`.
- Expressions in a force are evaluated when the particle spawns, using the effect's inputs at that moment. Changing an input later affects new particles only.
- Points and lengths (`point`, `radius`, `absorb`, turbulence `scale`) are in the effect's space. They follow the anchor as it moves, turn with its facing and grow with its scale. When the effect ends, its leftover particles keep the last position.

### Turbulence

Turbulence swirls particles with smooth, curl-style noise. The field has no sources or sinks, so particles swirl like smoke without bunching into clumps.

| Field | Default | |
|---|---|---|
| `strength` | required | Curve. Roughly the average push. |
| `scale` | `1` | Size of the swirls, in blocks. Must be above 0. |
| `flow` | `0` | How fast the pattern drifts, in blocks per tick. |
| `octaves` | `1` | `1` or `2`. `2` adds finer detail at about twice the cost. |

- The pattern is fixed in the world. It doesn't turn with the effect, but the effect's seed offsets it on each play, so two plays swirl differently.
- Turbulence is the most expensive force, about five times an attractor. Prefer it on effects with moderate particle counts.

### Attractor

An attractor pulls particles toward `point`. A negative `strength` pushes them away.

| Field | Default | |
|---|---|---|
| `point` | `"origin"` | Where the pull is aimed. See [Points](#points). |
| `strength` | required | Curve. Negative pushes away. |
| `falloff` | `"none"` | How the force weakens with distance, see below. |
| `radius` | `0` | Past this distance there's no push. `0` means no limit. |
| `absorb` | `0` | Distance at which the particle dies. Its `on_death` fires, which suits "absorbed" sparkles. |

Falloff:

- `none`: the same push at any distance.
- `linear`: fades to 0 at `radius`. Needs a `radius`.
- `inverse_square`: `strength` is the push at 1 block. Anything closer counts as 0.5 blocks, so it never blows up.

### Vortex

A vortex spins particles around an axis that runs through `point`.

| Field | Default | |
|---|---|---|
| `point` | `"origin"` | A point on the axis. See [Points](#points). |
| `axis` | `[0, 1, 0]` | Direction of the axis, in effect space. Can't be all zeros. |
| `strength` | required | Curve. Spin around the axis. Positive is counter-clockwise seen from the axis tip, which is from above for the default axis. |
| `pull` | `0` | Acceleration toward the axis. Negative pushes out. |
| `lift` | `0` | Acceleration along the axis. |
| `falloff` | `"none"` | As for attractors, measured from the axis. |
| `radius` | `0` | As for attractors, measured from the axis. |

Spin alone makes particles spiral outward. A little `pull` keeps them circling.

### Points

`point` is one of:

- `[x, y, z]`: an offset from the effect's origin, in effect space.
- `"origin"`: the effect's origin. The default.
- `"target"`: the far end of a `between` anchor. See [Anchors](api.md#anchors) in the API docs.

The bundled examples show each type:

- `eden_particles:examples/smoke_column`: turbulence.
- `eden_particles:examples/absorb`: an attractor with `absorb` and `on_death`.
- `eden_particles:examples/drain`: a beam whose particles spawn at the far end and are pulled back to `"origin"`. Play it with `/eden_particles beam`.
- `eden_particles:examples/tornado`: a vortex with `pull` and `lift`.

## Curves

A curve is a number that changes over time `t` from 0 to 1. Times must be strictly increasing and between 0 and 1.

```jsonc
0.5                                          // constant
[[0, 0], [0.2, 1], [1, 0]]                   // straight lines between [t, value] keys
{ "keys": [[0, 2], [1, 0.3]], "ease": "in_cubic" }               // eased segments
{ "keys": [{ "t": 0, "v": 0 }, { "t": 1, "v": 1, "ease": "out_back" }] }  // easing per segment
{ "keys": [[0, 0], [0.5, 1], [1, 0.2]], "mode": "monotone" }     // smooth through the keys
"power * 2"                                  // an expression over inputs, constant over time
{ "keys": [[0, 0], [1, 1]], "scale": "power" }                   // a curve multiplied by an expression
```

- An `ease` on a key shapes the segment *arriving* at that key.
- Easings: `linear`, `step` (hold, then jump), `smooth`, and `in_` / `out_` / `in_out_` versions of `sine`, `quad`, `cubic`, `quart`, `quint`, `expo`, `circ`, `back`, `elastic` and `bounce`. You can also use `{"cubic_bezier": [x1, y1, x2, y2]}`, the same as CSS.
- `mode: "monotone"` is smooth and never overshoots its keys, so a size curve that never goes below 0 between keys won't dip negative. `mode: "catmull_rom"` is smooth but can overshoot.

## Gradients

```jsonc
"#FFAA00"                                                         // one colour
[{ "t": 0, "color": "#FFF4C2" }, { "t": 1, "color": "#FF5A1F" }]  // blend over life
{ "keys": [ … ], "space": "oklab", "ease": "out_cubic" }
```

`space` controls how colours blend:
- `oklab` (default) keeps blends even in perceived brightness, so red→green doesn't go through muddy brown.
- `srgb` matches most other tools.
- `linear` is physically mixed light.

## Random values

Values rolled once per particle (speeds, lifetimes, burst counts):

```jsonc
0.1                                              // always 0.1
"power * 0.1"                                    // an expression over inputs
[0.05, 0.15]                                     // uniform between the two (either end can be an expression)
{ "min": 0.05, "max": 0.15 }                     // the same
{ "mean": 0.1, "deviation": 0.02, "min": 0 }     // normal distribution, optionally clamped (decimals only)
```

## Quality and performance

- The vanilla *Particles* setting scales spawns: *All* keeps everything, *Decreased* keeps 2/3, *Minimal* keeps none (except `important` emitters, which keep 1/15). Lower settings show a subset of the same particles, so the effect thins out rather than changing shape.
- Every client that sees a server-started effect gets the same random pattern.
- Sounds play in full whatever the *Particles* setting. See [Sounds](#sounds).
- At most 32 effect lights are lit at once. See [Lights](#lights).
- Total spawning is capped per tick. Past the cap, every effect thins out proportionally.
- Attractors and vortices are cheap. Turbulence costs several times the rest of a particle's tick, and `octaves: 2` doubles that.
- Vanilla holds at most 16,384 live particles per drawing style, and past that it deletes the oldest. Eden keeps below that limit by spawning fewer new particles as space runs out, so particles already on screen never vanish early.
- `/eden_particles_client bench <effect> [count]` plays an effect on a grid and reports Eden's own tick cost.

## Errors

Mistakes are reported with the file and the exact spot:

```
effect mymod:boom (pack 'mod/mymod'): at emitters[1].shape.radius: in 'power *' at column 8: expected a number, input or '('
at emitters[0]: unknown field 'ofset' (did you mean 'offset'?)
at emitters[0]: uses input 'powr', which the effect doesn't declare (did you mean 'power'?); add it to "inputs" with a default value
```

A broken file is skipped; everything else still loads.
