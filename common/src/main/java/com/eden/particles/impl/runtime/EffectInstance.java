package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.EmitterShape;
import com.eden.particles.impl.definition.SoundCue;
import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.definition.SpawnSpec;
import com.eden.particles.impl.definition.SubEffect;
import com.eden.particles.impl.definition.Speed;
import com.eden.particles.impl.definition.VelocityMode;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.expr.InputNames;
import com.eden.particles.impl.math.Kinematics;
import com.eden.particles.impl.math.ParticleRandom;
import com.eden.particles.impl.math.Rng;
import com.eden.particles.impl.math.ShapeSampler;
import com.eden.particles.impl.math.SpawnIntegrator;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * One playing effect. Each tick: gate emitters by the timeline, evaluate input-dependent values, count spawns, then
 * sample and transform each particle into world space. Particle randomness comes from per-particle seeds, so a lower
 * keep fraction shows a subset of the same particles, and changing inputs doesn't change which particles are rolled.
 */
public final class EffectInstance {

    private static final long BURST_SALT = 0x5851F42D4C957F2DL;
    private static final long JITTER_SALT = 0x14057B7EF767814FL;
    private static final long SUB_EFFECT_SALT = 0x94D049BB133111EBL;
    private static final long SOUND_SALT = 0xD6E8FEB86659FD93L;
    private static final float TWO_PI = (float) (2.0 * Math.PI);

    private final BakedEffect effect;
    private final Anchor anchor;
    private final long seed;
    private final EffectInstance[] children;
    private final int[] startedLoop;
    private EffectHost host;
    private SoundSink soundSink;
    private final SoundSink.Sound[] sounds;
    private final int[] soundLoop;
    private LightSink lightSink;
    private final LightSink.Light[] lights;
    private final long[] emitterSeeds;
    private final EmitterState[] states;
    private final float[] inputs;
    private float scale;
    private final Quaternionf rotation = new Quaternionf();
    private int tint;
    private final Anchor.Placement placement = new Anchor.Placement();
    private final EffectFrame effectFrame;
    private final Quaternionf base = new Quaternionf();
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
    private final Vector3f lineFrom = new Vector3f();
    private final Vector3f lineTo = new Vector3f();
    private final Vector3f cueOffset = new Vector3f();
    private final Quaternionf frame = new Quaternionf();

    public EffectInstance(BakedEffect effect, Anchor anchor, PlayParams params) {
        this.effect = effect;
        this.anchor = anchor;
        this.inputs = Arrays.copyOf(effect.defaults(), Math.max(effect.defaults().length, InputNames.count()));
        this.scale = params.scale();
        this.rotation.set(params.rotation());
        this.tint = params.tint();
        this.seed = params.seed();
        this.effectFrame = new EffectFrame(params.seed());
        this.children = new EffectInstance[effect.subEffects().size()];
        this.startedLoop = new int[children.length];
        Arrays.fill(startedLoop, -1);
        this.sounds = new SoundSink.Sound[effect.sounds().size()];
        this.soundLoop = new int[sounds.length];
        Arrays.fill(soundLoop, -1);
        this.lights = new LightSink.Light[effect.lights().size()];
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
        if (!anchor.resolve(placement)) {
            stop();
            return false;
        }
        current.set(placement.position);
        inputs[InputNames.DISTANCE_ID] = placement.distance;
        base.set(placement.facing).mul(rotation);
        if (!hasPrevious) {
            previous.set(current);
            hasPrevious = true;
        }
        effectFrame.position.set(current);
        effectFrame.facing.set(base);
        placement.facing.transform(effectFrame.target.set(0, 0, placement.distance)).add(current);
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

    /** Ends spawning, the sub-effects this effect started, its looping sounds and its lights. */
    public void stop() {
        finished = true;
        stopChildren();
        for (int s = 0; s < sounds.length; s++) {
            if (sounds[s] != null) sounds[s].fadeOut();
            sounds[s] = null;
        }
        for (int l = 0; l < lights.length; l++) {
            if (lights[l] != null) lights[l].end();
            lights[l] = null;
        }
    }

    /** Where lights go; without one they're skipped. */
    public void setLights(LightSink sink) {
        this.lightSink = sink;
    }

    /** Places lights in their window, following the anchor, and removes them after it. */
    private void tickLights() {
        List<BakedLight> baked = effect.lights();
        for (int l = 0; l < lights.length; l++) {
            BakedLight light = baked.get(l);
            int start = light.definition().start(), duration = light.definition().duration();
            if (age < start || age >= start + duration) {
                if (lights[l] != null) lights[l].end();
                lights[l] = null;
                continue;
            }
            float t = (age - start + 0.5f) / duration;
            float level = Math.max(0f, Math.min(15f, light.level().curve().sample(t) * light.level().scale(inputs)));
            base.transform(light.definition().offset().eval(inputs, cueOffset)).mul(scale);
            double x = current.x + cueOffset.x, y = current.y + cueOffset.y, z = current.z + cueOffset.z;
            if (lights[l] != null) lights[l].update(x, y, z, level);
            else if (lightSink != null) lights[l] = lightSink.start(x, y, z, level);
        }
    }

    /** Where sounds play; without one they're skipped. */
    public void setSounds(SoundSink sink) {
        this.soundSink = sink;
    }

    /**
     * Starts sounds whose time has come and moves playing ones with the anchor. Looping sounds fade out when their
     * window ends; one-shots play out, and a joiner who missed the start doesn't hear them.
     */
    private void tickSounds() {
        List<SoundCue> cues = effect.sounds();
        for (int s = 0; s < sounds.length; s++) {
            SoundCue cue = cues.get(s);
            boolean inWindow = age >= cue.start() && age < cue.end();
            SoundSink.Sound sound = sounds[s];
            if (sound != null && (sound.isDone() || (cue.loop() && !inWindow))) {
                sound.fadeOut();
                sounds[s] = sound = null;
            }
            base.transform(cue.offset().eval(inputs, cueOffset)).mul(scale);
            double x = current.x + cueOffset.x, y = current.y + cueOffset.y, z = current.z + cueOffset.z;
            boolean due = cue.loop() ? inWindow : age == cue.start();
            if (sound == null && due && soundSink != null && soundLoop[s] != loops) {
                soundLoop[s] = loops;
                long soundSeed = Rng.derive(seed ^ SOUND_SALT, (long) loops * sounds.length + s);
                random.reset(soundSeed);
                float volume = Math.max(0f, cue.volume().sample(random, inputs));
                float pitch = cue.pitch().sample(random, inputs);
                sounds[s] = soundSink.play(cue, volume, pitch, soundSeed, x, y, z);
            } else if (sound != null) {
                sound.moveTo(x, y, z);
            }
        }
    }

    /** Where sub-effects start; without one they're skipped. */
    public void setHost(EffectHost host) {
        this.host = host;
    }

    private void stopChildren() {
        for (int s = 0; s < children.length; s++) {
            if (children[s] != null) children[s].stop();
            children[s] = null;
        }
    }

    /** Starts sub-effects whose window has come, keeps live ones in step with this effect, and ends those past it. */
    private void tickSubEffects() {
        for (BakedSubEffect sub : effect.subEffects()) {
            int s = sub.index();
            SubEffect definition = sub.definition();
            boolean inWindow = age >= definition.start() && age < definition.end();
            EffectInstance child = children[s];
            if (child != null && (!inWindow || child.isFinished())) {
                child.stop();
                children[s] = child = null;
            }
            if (child == null && inWindow && host != null && startedLoop[s] != loops) {
                startedLoop[s] = loops;
                PlayParams params = new PlayParams(scale * definition.scale().eval(inputs), new Quaternionf(), tint,
                        Rng.derive(seed ^ SUB_EFFECT_SALT, (long) loops * children.length + s));
                child = host.start(definition.effect(), childAnchor(sub), params);
                if (child != null) child.fastForward(age - definition.start());
                children[s] = child;
            }
            if (child != null) {
                child.setScale(scale * definition.scale().eval(inputs));
                child.setTint(tint);
                for (int i = 0; i < sub.inputNames().length; i++) child.setInput(sub.inputNames()[i], sub.inputValues()[i].eval(inputs));
            }
        }
    }

    /** Follows this effect's anchor and facing, offset in its frame. */
    private Anchor childAnchor(BakedSubEffect sub) {
        Vector3f childOffset = new Vector3f();
        return out -> {
            if (finished) return false;
            base.transform(sub.definition().offset().eval(inputs, childOffset)).mul(scale);
            out.position.set(current).add(childOffset.x, childOffset.y, childOffset.z);
            out.facing.set(base).mul(sub.rotation());
            out.distance = inputs[InputNames.DISTANCE_ID];
            return true;
        };
    }

    /** @return false if the effect doesn't declare this input */
    public boolean setInput(String name, float value) {
        Integer id = effect.inputIds().get(name);
        if (id == null) return false;
        inputs[id] = value;
        return true;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void setRotation(Quaternionfc rotation) {
        this.rotation.set(rotation);
    }

    public void setTint(int tint) {
        this.tint = tint;
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
        effectFrame.ticks = ticks;
    }

    /**
     * Runs one tick after {@link #prepare}. {@code keep} and {@code keepImportant} are level-of-detail keep fractions.
     *
     * @return spawn attempts before level-of-detail filtering, for the global budget
     */
    public int tick(SpawnSink sink, float keep, float keepImportant) {
        int attempts = 0;
        if (children.length > 0) tickSubEffects();
        if (sounds.length > 0) tickSounds();
        if (lights.length > 0) tickLights();
        for (BakedEmitter emitter : effect.emitters()) {
            EmitterTrack track = emitter.track();
            if (age < track.start() || age >= track.end()) continue;
            EmitterState state = states[emitter.index()];
            long seed = emitterSeeds[emitter.index()];
            int local = age - track.start();
            emitter.template().evaluate(inputs, state.values);
            state.radiusScale = emitter.radius().scale(inputs);
            float spin = emitter.spin().curve().sample((local + 0.5f) / track.duration()) * emitter.spin().scale(inputs);
            state.spinDegrees = (state.spinDegrees + spin) % 360f;
            float fraction = track.important() ? keepImportant : keep;

            if (emitter.rate().isPresent()) {
                SpawnIntegrator rate = emitter.rate().get();
                double rateScale = Math.max(0f, emitter.rateScale().eval(inputs));
                int count = track.spawn().jitter()
                        ? SpawnIntegrator.poisson(rate.expectedInTick(local) * rateScale,
                                random.reset(Rng.derive(seed ^ JITTER_SALT, (long) loops * track.duration() + local)))
                        : state.accumulator.next(rate, local, rateScale);
                for (int j = 0; j < count; j++) {
                    spawn(emitter, state, sink, fraction, local, SpawnIntegrator.subTickOffset(j, count, 0.5f), -1, 0, 0f);
                }
                attempts += count;
            }

            List<SpawnSpec.Burst> bursts = track.spawn().bursts();
            for (int b = 0; b < bursts.size(); b++) {
                if (bursts.get(b).at() != local) continue;
                random.reset(Rng.derive(seed ^ BURST_SALT, (long) loops * bursts.size() + b));
                int count = Math.max(0, bursts.get(b).count().sample(random, inputs));
                float phase = random.nextFloat() * TWO_PI;
                for (int k = 0; k < count; k++) {
                    spawn(emitter, state, sink, fraction, local, 1f, k, count, phase);
                }
                attempts += count;
            }
        }

        previous.set(current);
        age++;
        effectFrame.ticks++;
        if (age >= effect.duration()) {
            stopChildren();
            if (effect.definition().loop()) {
                age = 0;
                loops++;
                for (EmitterState state : states) state.accumulator.reset();
            } else {
                stop();
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
        sampleShape(track.shape(), emitter.radius().curve().sample(u) * state.radiusScale, even, index, count, phase);
        int lifetime = Math.max(1, template.lifetime().sample(random, inputs));
        float speed = sampleVelocity(track.velocity(), lifetime, state.values.drag);
        float variant = random.nextFloat();

        frame.set(base).mul(emitter.axisRotation()).rotateY((float) Math.toRadians(state.spinDegrees));
        frame.transform(position).mul(scale);
        frame.transform(direction).mul(speed * scale);
        base.transform(track.offset().eval(inputs, offset)).mul(scale);

        double advance = 1.0 - tau;
        double x = previous.x + (current.x - previous.x) * tau + offset.x + position.x + direction.x * advance;
        double y = previous.y + (current.y - previous.y) * tau + offset.y + position.y + direction.y * advance;
        double z = previous.z + (current.z - previous.z) * tau + offset.z + position.z + direction.z * advance;
        sink.spawn(template, x, y, z, direction.x, direction.y, direction.z, lifetime, scale, tint, variant, state.values, effectFrame);
    }

    private void sampleShape(EmitterShape shape, float radius, boolean even, int index, int count, float phase) {
        switch (shape) {
            case EmitterShape.Point p -> position.zero();
            case EmitterShape.Sphere s -> {
                if (even) {
                    ShapeSampler.fibonacciSphere(index, count, position).rotateY(phase);
                    float r = s.surface() ? radius : shellRadius(nonNegative(s.innerRadius()), radius, (index + 0.5f) / count);
                    position.mul(r);
                } else if (s.surface()) {
                    ShapeSampler.sphereSurface(random.nextFloat(), random.nextFloat(), position).mul(radius);
                } else {
                    ShapeSampler.sphereVolume(nonNegative(s.innerRadius()), radius, random.nextFloat(), random.nextFloat(), random.nextFloat(), position);
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
                    float inner = nonNegative(d.innerRadius());
                    float r0 = inner * inner;
                    float r = (float) Math.sqrt((index + 0.5f) / count * (radius * radius - r0) + r0);
                    float theta = index * ShapeSampler.GOLDEN_ANGLE + phase;
                    position.set(r * (float) Math.cos(theta), 0f, r * (float) Math.sin(theta));
                } else {
                    ShapeSampler.disc(nonNegative(d.innerRadius()), radius, random.nextFloat(), random.nextFloat(), position);
                }
            }
            case EmitterShape.Ring r -> {
                if (even) ShapeSampler.evenRing(index, count, radius, phase, position);
                else ShapeSampler.ring(radius, random.nextFloat(), position);
            }
            case EmitterShape.Box b -> {
                float hx = b.size().x().eval(inputs) / 2f, hy = b.size().y().eval(inputs) / 2f, hz = b.size().z().eval(inputs) / 2f;
                if (b.surface()) {
                    ShapeSampler.boxSurface(hx, hy, hz, random.nextFloat(), random.nextFloat(), random.nextFloat(), position);
                } else {
                    ShapeSampler.boxVolume(hx, hy, hz, random.nextFloat(), random.nextFloat(), random.nextFloat(), position);
                }
            }
            case EmitterShape.Line l -> ShapeSampler.line(l.from().eval(inputs, lineFrom), l.to().eval(inputs, lineTo), random.nextFloat(), position);
        }
    }

    private float nonNegative(Expr expr) {
        return Math.max(0f, expr.eval(inputs));
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
                f.direction().eval(inputs, direction);
                if (direction.lengthSquared() < 1e-12f) direction.zero();
                else direction.normalize();
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
                float halfAngle = Math.max(0f, Math.min(180f, c.halfAngle().eval(inputs)));
                ShapeSampler.coneDirection((float) Math.toRadians(halfAngle), random.nextFloat(), random.nextFloat(), direction);
                speed = c.speed();
            }
        }
        float value = speed.value().sample(random, inputs);
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
        final TemplateValues values = new TemplateValues();
        float radiusScale;
        long counter;
        float spinDegrees;
    }
}
