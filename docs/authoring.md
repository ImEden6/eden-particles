# Writing effects

Effects are JSON files in a resource pack (your mod's `assets` count). Each effect is a timeline of **emitters**. Each emitter spawns particles described by a **template**.

```
assets/<namespace>/eden_particles/effects/<path>.json    → effect   <namespace>:<path>
assets/<namespace>/eden_particles/templates/<path>.json  → template <namespace>:<path>
assets/<namespace>/textures/particle/<name>.png          → sprite   <namespace>:<name>
```

Effects and templates can also come from a **datapack**, under `data/<namespace>/eden_particles/{effects,templates}/`. The server sends them to every player with the mod when they join and after `/reload`. A datapack definition overrides a resource-pack one with the same id, and can use resource-pack templates. Sprites still have to come from a resource pack, because textures can't be sent through a datapack. Leaving the server removes its datapack definitions.

Sprites go in the vanilla particle atlas, so any PNG under `textures/particle/` works with no extra configuration. Vanilla sprites work too (`"minecraft:bubble"`, `"minecraft:flame"`).

All times are in **ticks** (20 per second). Distances are in blocks. Speeds are in blocks per tick.

Reload with `F3+T`. `/eden_particles_client errors` lists any problems, and `/eden_particles_client play <effect>` plays one in front of you. The mod ships working examples under `eden_particles:examples/…`.

## Effect

```json
{
  "duration": 60,
  "loop": false,
  "range": 64,
  "emitters": [ … ]
}
```

| Field | Default | |
|---|---|---|
| `duration` | required | Length of the timeline in ticks. |
| `loop` | `false` | Restart the timeline when it ends. Looping effects play until stopped. |
| `range` | `64` | Particles thin out from 3/8 of this distance and are gone by 3/4 of it. |
| `emitters` | required | At least one. |

## Emitter

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

All shapes are centred on the emitter. Radii are curves over the emitter's life, so a ring can shrink.

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

**Render types:**
- `{"type": "sprite", "sprite": …, "blend": "translucent" | "additive", "emissive": bool}` is Eden's own particle and follows every curve. `additive` adds light: overlapping particles brighten, which suits glows, sparks and magic. `emissive` makes the particle full-bright at night.
- `{"type": "vanilla", "particle": "minecraft:campfire_cosy_smoke"}` spawns a vanilla particle type that takes no options. Emitter settings, lifetime and physics apply. Curves don't, and they're rejected if present.

> **Additive colour tip:** additive blending adds colours up and clips at white. Pale colours (like `#D8C4FF`) turn white wherever two particles overlap. Use saturated colours with `alpha` around 0.4–0.6, so dense areas build up to a bright core instead.

## Curves

A curve is a number that changes over time `t` from 0 to 1. Times must be strictly increasing and between 0 and 1.

```jsonc
0.5                                          // constant
[[0, 0], [0.2, 1], [1, 0]]                   // straight lines between [t, value] keys
{ "keys": [[0, 2], [1, 0.3]], "ease": "in_cubic" }               // eased segments
{ "keys": [{ "t": 0, "v": 0 }, { "t": 1, "v": 1, "ease": "out_back" }] }  // easing per segment
{ "keys": [[0, 0], [0.5, 1], [1, 0.2]], "mode": "monotone" }     // smooth through the keys
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
[0.05, 0.15]                                     // uniform between the two
{ "min": 0.05, "max": 0.15 }                     // the same
{ "mean": 0.1, "deviation": 0.02, "min": 0 }     // normal distribution, optionally clamped (decimals only)
```

## Quality and performance

- The vanilla *Particles* setting scales spawns: *All* keeps everything, *Decreased* keeps 2/3, *Minimal* keeps none (except `important` emitters, which keep 1/15). Lower settings show a subset of the same particles, so the effect thins out rather than changing shape.
- Every client that sees a server-started effect gets the same random pattern.
- Total spawning is capped per tick. Past the cap, every effect thins out proportionally.
- Vanilla holds at most 16,384 live particles per drawing style, and past that it deletes the oldest. Eden keeps below that limit by spawning fewer new particles as space runs out, so particles already on screen never vanish early.
- `/eden_particles_client bench <effect> [count]` plays an effect on a grid and reports Eden's own tick cost.

## Errors

Mistakes are reported with the file and the exact spot:

```
effect mymod:boom (pack 'mod/mymod'): at emitters[1].shape.radius: expected a curve (number or list or map), got "big"
at emitters[0]: unknown field 'ofset' (did you mean 'offset'?)
```

A broken file is skipped; everything else still loads.
