# Changelog

## [0.2.0]

- Particle forces: curl-noise turbulence that swirls without clumping, attractors that can absorb particles, and vortices with pull and lift.
- Ribbon trails behind particles, and a `ribbon` render type that draws only the trail.
- Effects can play sounds and light up the blocks around them on their timeline. Lights need LambDynamicLights (Fabric or NeoForge).
- The server's `/eden_particles` commands suggest effect ids.
- Server particle images are cached on the client, so rejoining only downloads new or changed ones. This changes the image protocol: 0.2.0 clients and servers only work with each other.
- New examples: smoke_column, absorb, drain, tornado, magic_missile, slash_arc and spark_ribbon. Several examples now have sounds and lights.

## [0.1.0]

- First release.
