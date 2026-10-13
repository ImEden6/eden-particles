package com.eden.particles.impl.runtime;

/**
 * Receives each particle the runtime decides to spawn. Positions are world-space; velocities are blocks/tick;
 * {@code variant} is a per-particle uniform in [0,1) for choices like a random sprite frame. {@code values} is reused
 * between calls, so copy what you keep. {@code frame} follows the spawning effect, for forces.
 */
@FunctionalInterface
public interface SpawnSink {

    void spawn(BakedTemplate template, double x, double y, double z, double vx, double vy, double vz,
               int lifetime, float scale, int tint, float variant, TemplateValues values, EffectFrame frame);
}
