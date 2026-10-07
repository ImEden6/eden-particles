package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.EmitterShape;
import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.definition.SpawnSpec;
import com.eden.particles.impl.definition.Speed;
import com.eden.particles.impl.definition.VelocityMode;
import com.eden.particles.impl.math.Kinematics;
import com.eden.particles.impl.math.ParticleRandom;
import com.eden.particles.impl.math.Rng;
import com.eden.particles.impl.math.ShapeSampler;
import com.eden.particles.impl.math.SpawnIntegrator;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.List;

/**
 * One playing effect. Each tick: gate emitters by the timeline, count spawns, then sample and transform each
 * particle into world space. Particle randomness comes from per-particle seeds, so a lower keep fraction shows a
 * subset of the same particles.
 */
public final class EffectInstance {

    private static final long BURST_SALT = 0x5851F42D4C957F2DL;
    private static final long JITTER_SALT = 0x14057B7EF767814FL;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    private final BakedEffect effect;
    private final Anchor anchor;
    private final PlayParams params;
    private final long[] emitterSeeds;
    private final EmitterState[] states;
    private final Vector3d current = new Vector3d();
    private final Vector3d previous = new Vector3d();
    private boolean hasPrevious;
    private boolean finished;
    private int age;
    private int loops;

    private final ParticleRandom random = new ParticleRandom();
    private final Vector3f position = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f offset = new Vector3f();
    private final Quaternionf frame = new Quaternionf();

    public EffectInstance(BakedEffect effect, Anchor anchor, PlayParams params) {
        this.effect = effect;
        this.anchor = anchor;
        this.params = params;
        int count = effect.emitters().size();
        this.emitterSeeds = new long[count];
        this.states = new EmitterState[count];
        for (int i = 0; i < count; i++) {
            emitterSeeds[i] = Rng.derive(params.seed(), i);
            states[i] = new EmitterState();
        }
    }

    /** Resolves the anchor for this tick; false once the effect is over. */
    public boolean prepare() {
        if (finished) return false;
        if (!anchor.resolve(current)) {
            finished = true;
            return false;
        }
        if (!hasPrevious) {
            previous.set(current);
            hasPrevious = true;
        }
        return true;
    }

    public Vector3dc position() {
        return current;
    }

    public BakedEffect effect() {
        return effect;
    }

    public boolean isFinished() {
        return finished;
    }

    public void stop() {
        finished = true;
    }

    /**
     * Joins an effect already {@code ticks} old without spawning the backlog: a looping effect lands at the same
     * point in its loop as everyone else's; a finished one-shot ends.
     */
    public void fastForward(int ticks) {
        if (ticks <= 0) return;
        int duration = effect.duration();
        if (effect.definition().loop()) {
            loops = ticks / duration;
            age = ticks % duration;
        } else if (ticks >= duration) {
            finished = true;
        } else {
            age = ticks;
        }
    }

    /**
     * Runs one tick after {@link #prepare}. {@code keep} and {@code keepImportant} are level-of-detail keep fractions.
     *
     * @return spawn attempts before level-of-detail filtering, for the global budget
     */
    public int tick(SpawnSink sink, float keep, float keepImportant) {
        int attempts = 0;
        for (BakedEmitter emitter : effect.emitters()) {
            EmitterTrack track = emitter.track();
            if (age < track.start() || age >= track.end()) continue;
            EmitterState state = states[emitter.index()];
            long seed = emitterSeeds[emitter.index()];
            int local = age - track.start();
            state.spinDegrees += emitter.spin().sample((local + 0.5f) / track.duration());
            float fraction = track.important() ? keepImportant : keep;

            if (emitter.rate().isPresent()) {
                SpawnIntegrator rate = emitter.rate().get();
                int count = track.spawn().jitter()
                        ? SpawnIntegrator.poisson(rate.expectedInTick(local),
                                random.reset(Rng.derive(seed ^ JITTER_SALT, (long) loops * track.duration() + local)))
                        : state.accumulator.next(rate, local);
                for (int j = 0; j < count; j++) {
                    spawn(emitter, state, sink, fraction, local, SpawnIntegrator.subTickOffset(j, count, 0.5f), -1, 0, 0f);
                }
                attempts += count;
            }

            List<SpawnSpec.Burst> bursts = track.spawn().bursts();
            for (int b = 0; b < bursts.size(); b++) {
                if (bursts.get(b).at() != local) continue;
                random.reset(Rng.derive(seed ^ BURST_SALT, (long) loops * bursts.size() + b));
                int count = bursts.get(b).count().sample(random);
                float phase = random.nextFloat() * TWO_PI;
                for (int k = 0; k < count; k++) {
                    spawn(emitter, state, sink, fraction, local, 1f, k, count, phase);
                }
                attempts += count;
            }
        }

        previous.set(current);
        age++;
        if (age >= effect.duration()) {
            if (effect.definition().loop()) {
                age = 0;
                loops++;
                for (EmitterState state : states) state.accumulator.reset();
            } else {
                finished = true;
            }
        }
        return attempts;
    }

    /**
     * @param tau   sub-tick offset in (0,1]; the particle is pre-advanced by {@code 1 − tau} ticks
     * @param index position in an even burst, or -1 for random placement
     */
    private void spawn(BakedEmitter emitter, EmitterState state, SpawnSink sink, float keep,
                       int local, float tau, int index, int count, float phase) {
        long seed = Rng.derive(emitterSeeds[emitter.index()], state.counter++);
        if (!Rng.keep(seed, keep)) return;
        random.reset(seed);

        EmitterTrack track = emitter.track();
        BakedTemplate template = emitter.template();
        float u = Math.min(1f, (local + tau) / track.duration());
        boolean even = index >= 0 && track.distribution() == EmitterTrack.Distribution.EVEN;
        sampleShape(track.shape(), emitter.radius().sample(u), even, index, count, phase);
        int lifetime = template.lifetime().sample(random);
        float speed = sampleVelocity(track.velocity(), lifetime, template.physics().drag());

        float scale = params.scale();
        frame.set(params.rotation()).mul(emitter.axisRotation()).rotateY((float) Math.toRadians(state.spinDegrees));
        frame.transform(position).mul(scale);
        frame.transform(direction).mul(speed * scale);
        params.rotation().transform(offset.set(track.offset())).mul(scale);

        double advance = 1.0 - tau;
        double x = previous.x + (current.x - previous.x) * tau + offset.x + position.x + direction.x * advance;
        double y = previous.y + (current.y - previous.y) * tau + offset.y + position.y + direction.y * advance;
        double z = previous.z + (current.z - previous.z) * tau + offset.z + position.z + direction.z * advance;
        sink.spawn(template, x, y, z, direction.x, direction.y, direction.z, lifetime, scale, params.tint());
    }

    private void sampleShape(EmitterShape shape, float radius, boolean even, int index, int count, float phase) {
        switch (shape) {
            case EmitterShape.Point p -> position.zero();
            case EmitterShape.Sphere s -> {
                if (even) {
                    ShapeSampler.fibonacciSphere(index, count, position).rotateY(phase);
                    float r = s.surface() ? radius : shellRadius(s.innerRadius(), radius, (index + 0.5f) / count);
                    position.mul(r);
                } else if (s.surface()) {
                    ShapeSampler.sphereSurface(random.nextFloat(), random.nextFloat(), position).mul(radius);
                } else {
                    ShapeSampler.sphereVolume(s.innerRadius(), radius, random.nextFloat(), random.nextFloat(), random.nextFloat(), position);
                }
            }
            case EmitterShape.Hemisphere h -> {
                if (h.surface()) {
                    ShapeSampler.hemisphereSurface(random.nextFloat(), random.nextFloat(), position).mul(radius);
                } else {
                    ShapeSampler.hemisphereSurface(random.nextFloat(), random.nextFloat(), position)
                            .mul(radius * (float) Math.cbrt(random.nextFloat()));
                }
            }
            case EmitterShape.Disc d -> {
                if (even) {
                    float r0 = d.innerRadius() * d.innerRadius();
                    float r = (float) Math.sqrt((index + 0.5f) / count * (radius * radius - r0) + r0);
                    float theta = index * ShapeSampler.GOLDEN_ANGLE + phase;
                    position.set(r * (float) Math.cos(theta), 0f, r * (float) Math.sin(theta));
                } else {
                    ShapeSampler.disc(d.innerRadius(), radius, random.nextFloat(), random.nextFloat(), position);
                }
            }
            case EmitterShape.Ring r -> {
                if (even) ShapeSampler.evenRing(index, count, radius, phase, position);
                else ShapeSampler.ring(radius, random.nextFloat(), position);
            }
            case EmitterShape.Box b -> {
                float hx = b.size().x() / 2f, hy = b.size().y() / 2f, hz = b.size().z() / 2f;
                if (b.surface()) {
                    ShapeSampler.boxSurface(hx, hy, hz, random.nextFloat(), random.nextFloat(), random.nextFloat(), position);
                } else {
                    ShapeSampler.boxVolume(hx, hy, hz, random.nextFloat(), random.nextFloat(), random.nextFloat(), position);
                }
            }
            case EmitterShape.Line l -> ShapeSampler.line(l.from(), l.to(), random.nextFloat(), position);
        }
    }

    private static float shellRadius(float inner, float outer, float w) {
        float r0 = inner * inner * inner;
        return (float) Math.cbrt(w * (outer * outer * outer - r0) + r0);
    }

    /** Sets {@link #direction} to a unit vector (or zero) in the local frame and returns the speed. */
    private float sampleVelocity(VelocityMode mode, int lifetime, float drag) {
        Speed speed;
        switch (mode) {
            case VelocityMode.None n -> {
                direction.zero();
                return 0f;
            }
            case VelocityMode.Fixed f -> {
                direction.set(f.direction());
                speed = f.speed();
            }
            case VelocityMode.Radial r -> {
                radial();
                speed = r.speed();
            }
            case VelocityMode.TowardCenter t -> {
                radial().negate();
                speed = t.speed();
            }
            case VelocityMode.Tangent t -> {
                direction.set(position.z, 0f, -position.x);
                if (direction.lengthSquared() < 1e-12f) direction.zero();
                else direction.normalize();
                speed = t.speed();
            }
            case VelocityMode.Cone c -> {
                ShapeSampler.coneDirection((float) Math.toRadians(c.halfAngle()), random.nextFloat(), random.nextFloat(), direction);
                speed = c.speed();
            }
        }
        float value = speed.value().sample(random);
        return speed.byDistance() ? (float) Kinematics.speedForDistance(value, drag, lifetime) : value;
    }

    /** Away from the centre; a particle at the centre gets a random direction. */
    private Vector3f radial() {
        if (position.lengthSquared() < 1e-12f) {
            return ShapeSampler.sphereSurface(random.nextFloat(), random.nextFloat(), direction);
        }
        return direction.set(position).normalize();
    }

    private static final class EmitterState {
        final SpawnIntegrator.Accumulator accumulator = new SpawnIntegrator.Accumulator();
        long counter;
        float spinDegrees;
    }
}
