package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.Force;
import com.eden.particles.impl.math.BakedCurve;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A template's forces with strength curves baked. Input-dependent values are evaluated per spawn into a flat array
 * read by {@link ParticleForces}; the same array is reused when nothing depends on inputs.
 */
public final class BakedForces {

    public static final BakedForces NONE = bake(List.of());

    static final int TURBULENCE = 0, ATTRACTOR = 1, VORTEX = 2;

    final Force[] forces;
    /** {@link #TURBULENCE}, {@link #ATTRACTOR} or {@link #VORTEX} per force, for a cheap switch in the particle tick. */
    final int[] kinds;
    final BakedCurve[] strength;
    final int[] offset;
    private final int size;
    private final float[] constant;

    private BakedForces(List<Force> forces) {
        this.forces = forces.toArray(new Force[0]);
        this.strength = new BakedCurve[this.forces.length];
        this.offset = new int[this.forces.length];
        this.kinds = new int[this.forces.length];
        int at = 0;
        for (int i = 0; i < this.forces.length; i++) {
            offset[i] = at;
            Force force = this.forces[i];
            strength[i] = BakedCurve.bake(switch (force) {
                case Force.Turbulence t -> t.strength().curve();
                case Force.Attractor a -> a.strength().curve();
                case Force.Vortex v -> v.strength().curve();
            });
            kinds[i] = switch (force) {
                case Force.Turbulence t -> TURBULENCE;
                case Force.Attractor a -> ATTRACTOR;
                case Force.Vortex v -> VORTEX;
            };
            at += switch (kinds[i]) {
                case TURBULENCE -> 3;
                case ATTRACTOR -> 6;
                default -> 10;
            };
        }
        this.size = at;
        Set<String> names = new HashSet<>();
        forces.forEach(f -> f.names(names));
        this.constant = names.isEmpty() ? fill(new float[0]) : null;
    }

    public static BakedForces bake(List<Force> forces) {
        return new BakedForces(forces);
    }

    public boolean isEmpty() {
        return forces.length == 0;
    }

    /** Values for particles spawned now; particles keep the array. */
    public float[] evaluate(float[] inputs) {
        return constant != null ? constant : fill(inputs);
    }

    private float[] fill(float[] inputs) {
        float[] out = new float[size];
        Vector3f vec = new Vector3f();
        for (int i = 0; i < forces.length; i++) {
            int o = offset[i];
            switch (forces[i]) {
                case Force.Turbulence t -> {
                    out[o] = t.strength().scale().eval(inputs);
                    out[o + 1] = Math.max(0.01f, t.scale().eval(inputs));
                    out[o + 2] = t.flow().eval(inputs);
                }
                case Force.Attractor a -> {
                    out[o] = a.strength().scale().eval(inputs);
                    out[o + 1] = Math.max(0f, a.radius().eval(inputs));
                    out[o + 2] = Math.max(0f, a.absorb().eval(inputs));
                    put(a.point().offset().eval(inputs, vec), out, o + 3);
                }
                case Force.Vortex v -> {
                    out[o] = v.strength().scale().eval(inputs);
                    out[o + 1] = Math.max(0f, v.radius().eval(inputs));
                    out[o + 2] = v.pull().eval(inputs);
                    out[o + 3] = v.lift().eval(inputs);
                    put(v.point().offset().eval(inputs, vec), out, o + 4);
                    v.axis().eval(inputs, vec);
                    if (vec.lengthSquared() < 1e-12f) vec.set(0, 1, 0);
                    put(vec.normalize(), out, o + 7);
                }
            }
        }
        return out;
    }

    private static void put(Vector3f v, float[] out, int at) {
        out[at] = v.x;
        out[at + 1] = v.y;
        out[at + 2] = v.z;
    }
}
