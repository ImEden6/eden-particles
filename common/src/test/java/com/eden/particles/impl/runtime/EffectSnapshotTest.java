package com.eden.particles.impl.runtime;

import com.eden.particles.impl.TestDefinitions;
import com.eden.particles.testing.Baselines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Plays every bundled example and test fixture with fixed seeds and compares a fingerprint of everything spawned
 * against {@code baselines/effects.txt}, so any change to what an effect produces is caught and reviewed.
 */
class EffectSnapshotTest {

    private static final int MAX_TICKS = 400;
    private static final PlayParams PLAIN = PlayParams.of(0x5EEDL);
    private static final PlayParams TRANSFORMED = new PlayParams(1.5f,
            new Quaternionf().rotationXYZ(0.4f, 1.1f, -0.3f), 0xFF8040, 0xBEEFL);

    @Test
    void effectsMatchTheirBaselines() {
        Map<String, String> actual = new TreeMap<>();
        record(actual, "", TestDefinitions.load(TestDefinitions.BUNDLED).baked());
        record(actual, "fixture/", TestDefinitions.load(TestDefinitions.FIXTURES).baked());
        Baselines.check("effects.txt", """
                Spawn fingerprints of each effect, seeded and played for up to %d ticks.
                plain: default params; half: level-of-detail keep 0.5 (a subset of plain); transformed: scale, rotation, tint.
                """.formatted(MAX_TICKS), actual);
    }

    private static void record(Map<String, String> out, String prefix, Map<ResourceLocation, BakedEffect> effects) {
        effects.forEach((id, effect) -> {
            out.put(prefix + id + " plain", fingerprint(effect, PLAIN, 1f));
            out.put(prefix + id + " half", fingerprint(effect, PLAIN, 0.5f));
            out.put(prefix + id + " transformed", fingerprint(effect, TRANSFORMED, 1f));
        });
    }

    private static String fingerprint(BakedEffect effect, PlayParams params, float keep) {
        Fingerprint print = new Fingerprint();
        EffectInstance instance = new EffectInstance(effect, Anchor.fixed(0.5, 64, 0.5), params);
        int ticks = 0;
        int attempts = 0;
        while (ticks < MAX_TICKS && instance.prepare()) {
            attempts += instance.tick(print, keep, keep);
            ticks++;
        }
        return String.format(Locale.ROOT, "ticks=%d attempts=%d %s", ticks, attempts, print);
    }

    /** Hashes every spawn quantized to 1e-4, which absorbs last-bit differences between JVM maths intrinsics. */
    private static final class Fingerprint implements SpawnSink {
        private final Map<BakedTemplate, Integer> templates = new IdentityHashMap<>();
        private final double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        private final double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        private long hash = 0xCBF29CE484222325L;
        private int spawns;
        private double speed;
        private long life;

        @Override
        public void spawn(BakedTemplate template, double x, double y, double z, double vx, double vy, double vz,
                          int lifetime, float scale, int tint, float variant, TemplateValues values) {
            mix(templates.computeIfAbsent(template, t -> templates.size()));
            for (double v : new double[]{x, y, z, vx, vy, vz, scale, variant}) mix(Math.round(v * 1e4));
            mix(lifetime);
            mix(tint);
            double[] p = {x, y, z};
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], p[i]);
                max[i] = Math.max(max[i], p[i]);
            }
            speed += Math.sqrt(vx * vx + vy * vy + vz * vz);
            life += lifetime;
            spawns++;
        }

        /** FNV-1a over the value's bytes. */
        private void mix(long value) {
            for (int i = 0; i < 8; i++) {
                hash ^= (value >>> (8 * i)) & 0xFF;
                hash *= 0x100000001B3L;
            }
        }

        @Override
        public String toString() {
            if (spawns == 0) return "spawns=0";
            return String.format(Locale.ROOT, "spawns=%d templates=%d hash=%016x box=[%.2f %.2f %.2f | %.2f %.2f %.2f] speed=%.4f life=%.2f",
                    spawns, templates.size(), hash, min[0], min[1], min[2], max[0], max[1], max[2], speed / spawns, life / (double) spawns);
        }
    }
}
