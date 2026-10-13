package com.eden.particles.impl.runtime;

import com.eden.particles.impl.TestDefinitions;
import com.eden.particles.testing.Baselines;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Moves the particles of every bundled example with forces the way the client does (without block collision) and
 * compares where they end up against {@code baselines/motion.txt}.
 */
class ForceSnapshotTest {

    private static final int SPAWN_TICKS = 40;
    private static final int MAX_TICKS = 200;

    @Test
    void trajectoriesMatchTheirBaselines() {
        Map<String, String> actual = new TreeMap<>();
        TestDefinitions.load(TestDefinitions.BUNDLED).baked().forEach((id, effect) -> {
            if (effect.emitters().stream().anyMatch(e -> !e.template().forces().isEmpty())) {
                actual.put(id.toString(), simulate(effect));
            }
        });
        Baselines.check("motion.txt", """
                Where particles of examples with forces end up: spawned for %d ticks from a beam anchor 8 blocks long,
                moved without collision for up to %d ticks. absorbed: removed by an attractor.
                """.formatted(SPAWN_TICKS, MAX_TICKS), actual);
    }

    private static String simulate(BakedEffect effect) {
        List<Moving> live = new ArrayList<>();
        List<Moving> done = new ArrayList<>();
        int[] absorbed = {0};
        Anchor beam = out -> {
            out.position.set(0.5, 64, 0.5);
            out.facing.identity();
            out.distance = 8f;
            return true;
        };
        EffectInstance instance = new EffectInstance(effect, beam, PlayParams.of(0xF0CE));
        SpawnSink sink = (template, x, y, z, vx, vy, vz, lifetime, scale, tint, variant, values, frame) ->
                live.add(new Moving(template, values.gravity * scale, values.drag, values.speed, lifetime,
                        template.forces().isEmpty() ? null : new ParticleForces(template.forces(), values.forces, frame, scale),
                        new double[]{x, y, z, vx, vy, vz}));
        for (int tick = 0; tick < MAX_TICKS && !(live.isEmpty() && tick >= SPAWN_TICKS); tick++) {
            if (tick < SPAWN_TICKS && instance.prepare()) instance.tick(sink, 1f, 1f);
            live.removeIf(p -> {
                int result = p.step();
                if (result == Moving.ABSORBED) absorbed[0]++;
                if (result != Moving.ALIVE) done.add(p);
                return result != Moving.ALIVE;
            });
        }
        done.addAll(live);
        long hash = 1;
        double[] mean = new double[3];
        for (Moving p : done) {
            for (int k = 0; k < 3; k++) {
                hash = hash * 31 + Math.round(p.state[k] * 1000);
                mean[k] += p.state[k] / done.size();
            }
        }
        return String.format(Locale.ROOT, "particles=%d absorbed=%d mean=[%.2f %.2f %.2f] hash=%016x",
                done.size(), absorbed[0], mean[0], mean[1], mean[2], hash);
    }

    /** EdenParticle's tick without collision. */
    private static final class Moving {
        static final int ALIVE = 0, EXPIRED = 1, ABSORBED = 2;

        final BakedTemplate template;
        final float gravity, drag, speed;
        final int lifetime;
        final ParticleForces forces;
        final double[] state;
        int age;

        Moving(BakedTemplate template, float gravity, float drag, float speed, int lifetime, ParticleForces forces, double[] state) {
            this.template = template;
            this.gravity = gravity;
            this.drag = drag;
            this.speed = speed;
            this.lifetime = lifetime;
            this.forces = forces;
            this.state = state;
        }

        int step() {
            if (age++ >= lifetime) return EXPIRED;
            state[4] -= 0.04 * gravity;
            if (forces != null) {
                if (!forces.accelerate(state[0], state[1], state[2], age, age / (float) lifetime)) return ABSORBED;
                state[3] += forces.ax;
                state[4] += forces.ay;
                state[5] += forces.az;
            }
            float s = template.speed().curve().sample(age / (float) lifetime) * speed;
            for (int k = 0; k < 3; k++) {
                state[k] += state[k + 3] * s;
                state[k + 3] *= drag;
            }
            return ALIVE;
        }
    }
}
