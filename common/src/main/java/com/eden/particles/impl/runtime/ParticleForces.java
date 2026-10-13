package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.Force;
import com.eden.particles.impl.math.CurlNoise;
import org.joml.Quaternionf;

/** One particle's forces: call {@link #accelerate} each tick and add {@code ax, ay, az} to its velocity. */
public final class ParticleForces {

    /** Inverse-square forces are capped at this distance, in effect blocks, so they never blow up. */
    static final double MIN_DISTANCE = 0.5;
    /** Turbulence drifts along this direction in noise space. */
    private static final double DRIFT_X = 0.48, DRIFT_Y = 0.6, DRIFT_Z = 0.64;

    private final BakedForces forces;
    private final float[] values;
    private final EffectFrame frame;
    private final float scale;
    private final int born;
    public double ax, ay, az;
    private double px, py, pz;

    public ParticleForces(BakedForces forces, float[] values, EffectFrame frame, float scale) {
        this.forces = forces;
        this.values = values;
        this.frame = frame;
        this.scale = scale;
        this.born = frame.ticks;
    }

    /**
     * Sets this tick's acceleration at (x, y, z) for a particle {@code age} ticks old, {@code life} of the way through.
     *
     * @return false if an attractor absorbed the particle
     */
    public boolean accelerate(double x, double y, double z, int age, float life) {
        ax = ay = az = 0;
        float[] v = values;
        for (int i = 0; i < forces.forces.length; i++) {
            int o = forces.offset[i];
            double strength = forces.strength[i].sample(life) * v[o] * scale;
            switch (forces.kinds[i]) {
                case BakedForces.TURBULENCE -> {
                    if (strength == 0) continue;
                    Force.Turbulence t = (Force.Turbulence) forces.forces[i];
                    double size = v[o + 1] * scale;
                    double drift = v[o + 2] * (born + age) / size;
                    double[] flow = CurlNoise.sample(x / size + frame.noiseX + drift * DRIFT_X,
                            y / size + frame.noiseY + drift * DRIFT_Y, z / size + frame.noiseZ + drift * DRIFT_Z, t.octaves());
                    ax += flow[0] * strength;
                    ay += flow[1] * strength;
                    az += flow[2] * strength;
                }
                case BakedForces.ATTRACTOR -> {
                    Force.Attractor a = (Force.Attractor) forces.forces[i];
                    point(a.point(), v, o + 3);
                    double dx = px - x, dy = py - y, dz = pz - z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < v[o + 2] * scale) return false;
                    if (d < 1e-6) continue;
                    double f = strength * falloff(a.falloff(), d, v[o + 1]) / d;
                    ax += dx * f;
                    ay += dy * f;
                    az += dz * f;
                }
                default -> {
                    Force.Vortex w = (Force.Vortex) forces.forces[i];
                    rotate(frame.facing, v[o + 7], v[o + 8], v[o + 9]);
                    double axisX = px, axisY = py, axisZ = pz;
                    point(w.point(), v, o + 4);
                    double rx = x - px, ry = y - py, rz = z - pz;
                    double along = rx * axisX + ry * axisY + rz * axisZ;
                    rx -= axisX * along;
                    ry -= axisY * along;
                    rz -= axisZ * along;
                    double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
                    double f = falloff(w.falloff(), d, v[o + 1]);
                    if (f == 0) continue;
                    double lift = v[o + 3] * scale * f;
                    ax += axisX * lift;
                    ay += axisY * lift;
                    az += axisZ * lift;
                    if (d < 1e-6) continue;
                    double spin = strength * f / d;
                    double pull = -v[o + 2] * scale * f / d;
                    ax += (axisY * rz - axisZ * ry) * spin + rx * pull;
                    ay += (axisZ * rx - axisX * rz) * spin + ry * pull;
                    az += (axisX * ry - axisY * rx) * spin + rz * pull;
                }
            }
        }
        return true;
    }

    /** 1 for none, fading to 0 at {@code radius} for linear, or 1/d² in effect blocks for inverse square; 0 past it. */
    private double falloff(Force.Falloff falloff, double d, float radius) {
        double r = radius * scale;
        if (r > 0 && d >= r) return 0;
        return switch (falloff) {
            case NONE -> 1;
            case LINEAR -> 1 - d / r;
            case INVERSE_SQUARE -> {
                double local = Math.max(MIN_DISTANCE, d / scale);
                yield 1 / (local * local);
            }
        };
    }

    /** Sets {@code p} to a point in world space: its offset in the effect's frame, from the origin or the target. */
    private void point(Force.Point point, float[] v, int at) {
        if (v[at] == 0f && v[at + 1] == 0f && v[at + 2] == 0f) {
            px = py = pz = 0;
        } else {
            rotate(frame.facing, v[at] * scale, v[at + 1] * scale, v[at + 2] * scale);
        }
        if (point.target()) {
            px += frame.target.x;
            py += frame.target.y;
            pz += frame.target.z;
        } else {
            px += frame.position.x;
            py += frame.position.y;
            pz += frame.position.z;
        }
    }

    /** Sets {@code p} to {@code q · (x, y, z)}. */
    private void rotate(Quaternionf q, double x, double y, double z) {
        double tx = 2 * (q.y * z - q.z * y);
        double ty = 2 * (q.z * x - q.x * z);
        double tz = 2 * (q.x * y - q.y * x);
        px = x + q.w * tx + (q.y * tz - q.z * ty);
        py = y + q.w * ty + (q.z * tx - q.x * tz);
        pz = z + q.w * tz + (q.x * ty - q.y * tx);
    }
}
