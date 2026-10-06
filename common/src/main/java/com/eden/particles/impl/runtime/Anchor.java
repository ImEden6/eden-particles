package com.eden.particles.impl.runtime;

import org.joml.Vector3d;

/** Where an effect plays. Returning false ends the effect (e.g. its entity was removed). */
@FunctionalInterface
public interface Anchor {

    boolean resolve(Vector3d out);

    static Anchor fixed(double x, double y, double z) {
        return out -> {
            out.set(x, y, z);
            return true;
        };
    }
}
