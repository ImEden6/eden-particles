package com.eden.particles.impl.runtime;

/** Receives each particle the runtime decides to spawn. Positions are world-space; velocities are blocks/tick. */
@FunctionalInterface
public interface SpawnSink {

    void spawn(BakedTemplate template, double x, double y, double z, double vx, double vy, double vz,
               int lifetime, float scale, int tint);
}
