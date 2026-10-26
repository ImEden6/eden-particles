# Changelog

## [1.0.0]

- The effect format, the network protocol and the `api` package are now stable. The API docs say what that covers.
- Effect and template files can say `"format": 1`. A file made for a newer version fails with one clear message.
- Server effects reach players who come into range while they're still playing, not just persistent ones. The server reads durations from datapack effects. For resource-pack effects, pass `EffectParams.duration`.
- `vanilla` templates now take the effect's tint.
- An effect anchored to an entity the client hasn't loaded yet starts following it once it loads, instead of staying where it was sent.
- A JSON schema for effects and templates, in `docs/schema/`, for autocomplete and typo checks in your editor.
- `/eden_particles_client reload` reloads just the effect files. `/eden_particles_client preview <effect>` keeps replaying an effect while you edit it.
- Network channel ids changed, so 1.0.0 doesn't talk to 0.x. Mixed versions ignore each other's effects and don't disconnect.
- Only claims Minecraft 1.21.1 now, which is the version it's built and tested on.

## [0.2.0]

- Particle forces: curl-noise turbulence that swirls without clumping, attractors that can absorb particles, and vortices with pull and lift.
- Ribbon trails behind particles, and a `ribbon` render type that draws only the trail.
- Effects can play sounds and light up the blocks around them on their timeline. Lights need LambDynamicLights (Fabric or NeoForge).
- The server's `/eden_particles` commands suggest effect ids.
- Server particle images are cached on the client, so rejoining only downloads new or changed ones. This changes the image protocol: 0.2.0 clients and servers only work with each other.
- New examples: smoke_column, absorb, drain, tornado, magic_missile, slash_arc and spark_ribbon. Several examples now have sounds and lights.

## [0.1.0]

- First release.
