package com.eden.particles.impl.runtime;

import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Where an effect plays and which way it faces. Returning false ends the effect (e.g. its entity was removed). */
@FunctionalInterface
public interface Anchor {

    /** Sets every field of {@code out}. */
    boolean resolve(Placement out);

    static Anchor fixed(double x, double y, double z) {
        return out -> {
            out.position.set(x, y, z);
            out.facing.identity();
            out.distance = 0f;
            return true;
        };
    }

    /** {@code distance} is the length of a two-point anchor, read by expressions as the {@code distance} input. */
    final class Placement {
        public final Vector3d position = new Vector3d();
        public final Quaternionf facing = new Quaternionf();
        public float distance;
    }
}
