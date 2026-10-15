package com.eden.particles.bench;

import com.eden.particles.impl.TestDefinitions;
import com.eden.particles.impl.definition.Force;
import com.eden.particles.impl.expr.InputNames;
import com.eden.particles.impl.math.RibbonMath;
import com.eden.particles.impl.math.TrailHistory;
import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.BakedEffect;
import com.eden.particles.impl.runtime.BakedForces;
import com.eden.particles.impl.runtime.EffectFrame;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.ParticleForces;
import com.eden.particles.impl.runtime.PlayParams;
import com.eden.particles.impl.runtime.SpawnSink;
import com.eden.particles.testing.Baselines;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Times the pure runtime ({@code ./gradlew :common:bench}) and compares against {@code baselines/bench.txt}. Each case
 * warms up, then reports the median of several timed samples. Numbers only compare on the same machine.
 */
public final class RuntimeBench {

    private static final int INSTANCES = 32;
    private static final int PARTICLES = 15_000;
    private static final Map<String, String> FORCE_MIXES = new LinkedHashMap<>();

    static {
        String turbulence = "{\"type\": \"turbulence\", \"strength\": 0.02, \"scale\": 2, \"flow\": 0.02}";
        String attractor = "{\"type\": \"attractor\", \"point\": [0, 1, 0], \"strength\": 0.02, \"falloff\": \"inverse_square\"}";
        String vortex = "{\"type\": \"vortex\", \"strength\": 0.03, \"pull\": 0.01, \"lift\": 0.01, \"falloff\": \"linear\", \"radius\": 12}";
        FORCE_MIXES.put("none", "[]");
        FORCE_MIXES.put("turbulence", "[" + turbulence + "]");
        FORCE_MIXES.put("turbulence_2_octaves", "[" + turbulence.replace("}", ", \"octaves\": 2}") + "]");
        FORCE_MIXES.put("attractor", "[" + attractor + "]");
        FORCE_MIXES.put("vortex", "[" + vortex + "]");
        FORCE_MIXES.put("all", "[" + turbulence + ", " + attractor + ", " + vortex + "]");
    }
    private static final long WARMUP_NANOS = 1_500_000_000L;
    private static final long SAMPLE_NANOS = 300_000_000L;
    private static final int SAMPLES = 7;
    private static final String HEADER = """
            Runtime benchmark medians from ./gradlew :common:bench, on the machine that last ran -PupdateBaselines.
            tick: %d instances of the effect ticking together (restarted when they end); ns per effect tick and per spawn.
            load: parsing, linking and baking every bundled example.
            motion: %d particles moved under each force mix; ns per particle tick (forces, gravity, drag and the move).
            """.formatted(INSTANCES, PARTICLES);

    private static double blackhole;

    private RuntimeBench() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, Map<String, Double>> results = new LinkedHashMap<>();
        results.put("load", Map.of("ms", measure(RuntimeBench::loadOnce) / 1e6));
        for (Map.Entry<ResourceLocation, BakedEffect> entry : TestDefinitions.load(TestDefinitions.BUNDLED).baked().entrySet()) {
            results.put("tick " + entry.getKey().getPath(), tickCase(entry.getValue()));
        }
        FORCE_MIXES.forEach((name, json) -> results.put("motion " + name, Map.of("ns_per_particle", motionCase(json))));
        results.put("trail ribbon", Map.of("ns_per_particle", trailCase()));
        if (blackhole == 42) System.out.println();

        Map<String, String> lines = new LinkedHashMap<>();
        results.forEach((name, metrics) -> lines.put(name, format(metrics)));
        Map<String, String> baseline = Baselines.read("bench.txt");
        String report = report(results, baseline);
        System.out.println(report);
        String out = System.getProperty("eden.benchOut");
        if (out != null) {
            Files.createDirectories(Path.of(out).getParent());
            Files.writeString(Path.of(out), report);
        }
        if (Baselines.updating()) {
            Baselines.write("bench.txt", HEADER, lines);
            System.out.println("Updated baselines/bench.txt");
            return;
        }
        String limit = System.getProperty("eden.benchMaxRegression");
        if (limit != null && worstRegression(results, baseline) > Double.parseDouble(limit)) {
            System.out.println("A case is more than " + limit + "% slower than its baseline");
            System.exit(1);
        }
    }

    private static long loadOnce() {
        long start = System.nanoTime();
        blackhole += TestDefinitions.load(TestDefinitions.BUNDLED).baked().size();
        return System.nanoTime() - start;
    }

    private static Map<String, Double> tickCase(BakedEffect effect) {
        long[] spawns = new long[1];
        SpawnSink sink = (t, x, y, z, vx, vy, vz, life, scale, tint, variant, values, frame) -> {
            blackhole += x + vy;
            spawns[0]++;
        };
        EffectInstance[] instances = new EffectInstance[INSTANCES];
        long[] seed = {0};
        for (int i = 0; i < INSTANCES; i++) instances[i] = start(effect, seed[0]++);
        long[] ticks = new long[1];
        LongSupplier round = () -> {
            long begin = System.nanoTime();
            for (int i = 0; i < INSTANCES; i++) {
                EffectInstance instance = instances[i];
                if (!instance.prepare()) {
                    instance = instances[i] = start(effect, seed[0]++);
                    instance.prepare();
                }
                instance.tick(sink, 1f, 1f);
            }
            ticks[0] += INSTANCES;
            return System.nanoTime() - begin;
        };

        warm(round);
        double[] perTick = new double[SAMPLES];
        double[] perSpawn = new double[SAMPLES];
        for (int s = 0; s < SAMPLES; s++) {
            ticks[0] = 0;
            spawns[0] = 0;
            long elapsed = 0;
            while (elapsed < SAMPLE_NANOS) elapsed += round.getAsLong();
            perTick[s] = elapsed / (double) ticks[0];
            perSpawn[s] = spawns[0] == 0 ? 0 : elapsed / (double) spawns[0];
        }
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("ns_per_tick", median(perTick));
        metrics.put("ns_per_spawn", median(perSpawn));
        return metrics;
    }

    /** Moves particles the way EdenParticle does, without collision, and times it per particle tick. */
    private static double motionCase(String json) {
        BakedForces forces = BakedForces.bake(Force.LIST.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow());
        float[] values = forces.evaluate(new float[InputNames.count()]);
        EffectFrame frame = new EffectFrame(1);
        double[] state = new double[PARTICLES * 6];
        ParticleForces[] particles = new ParticleForces[PARTICLES];
        java.util.SplittableRandom random = new java.util.SplittableRandom(1);
        for (int i = 0; i < PARTICLES; i++) {
            for (int k = 0; k < 3; k++) state[i * 6 + k] = random.nextDouble(-8, 8);
            particles[i] = forces.isEmpty() ? null : new ParticleForces(forces, values, frame, 1f);
        }
        int[] age = {0};
        LongSupplier round = () -> {
            long begin = System.nanoTime();
            int a = age[0]++ % 40;
            for (int i = 0; i < PARTICLES; i++) {
                int o = i * 6;
                ParticleForces p = particles[i];
                state[o + 4] -= 0.04 * 0.1;
                if (p != null && p.accelerate(state[o], state[o + 1], state[o + 2], a, a / 40f)) {
                    state[o + 3] += p.ax;
                    state[o + 4] += p.ay;
                    state[o + 5] += p.az;
                }
                for (int k = 0; k < 3; k++) {
                    state[o + k] += state[o + 3 + k];
                    state[o + 3 + k] *= 0.96;
                }
            }
            frame.ticks++;
            return System.nanoTime() - begin;
        };
        double perRound = measure(round);
        blackhole += state[0];
        return perRound / PARTICLES;
    }

    private static double trailCase() {
        int particles = 15_000;
        TrailHistory[] histories = new TrailHistory[particles];
        for (int i = 0; i < particles; i++) {
            histories[i] = new TrailHistory(24, 0.05f);
            for (int k = 0; k < 20; k++) {
                histories[i].record(k * 0.1f, k * 0.05f, 0f, k);
            }
        }

        int[] step = {0};
        LongSupplier round = () -> {
            long begin = System.nanoTime();
            int s = step[0]++;
            for (int i = 0; i < particles; i++) {
                TrailHistory th = histories[i];
                th.record(s * 0.1f + i * 0.01f, (s + i) * 0.05f, 0f, s);
                th.pruneOlderThan(s, 20);
                blackhole += th.size();
            }
            return System.nanoTime() - begin;
        };
        double perRound = measure(round);
        return perRound / particles;
    }

    private static EffectInstance start(BakedEffect effect, long seed) {
        return new EffectInstance(effect, Anchor.fixed(0, 64, 0), PlayParams.of(seed));
    }

    /** Median of timed samples of {@code once} (which returns its own duration), after warming up. */
    private static double measure(LongSupplier once) {
        warm(once);
        double[] samples = new double[SAMPLES];
        for (int s = 0; s < SAMPLES; s++) {
            long elapsed = 0;
            int runs = 0;
            while (elapsed < SAMPLE_NANOS) {
                elapsed += once.getAsLong();
                runs++;
            }
            samples[s] = elapsed / (double) runs;
        }
        return median(samples);
    }

    private static void warm(LongSupplier once) {
        long elapsed = 0;
        while (elapsed < WARMUP_NANOS) elapsed += once.getAsLong();
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String format(Map<String, Double> metrics) {
        List<String> parts = new ArrayList<>();
        metrics.forEach((key, value) -> parts.add(key + "=" + String.format(Locale.ROOT, "%.1f", value)));
        return String.join(" ", parts);
    }

    private static Map<String, Double> parse(String line) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        if (line == null) return metrics;
        for (String part : line.split(" ")) {
            int eq = part.indexOf('=');
            metrics.put(part.substring(0, eq), Double.parseDouble(part.substring(eq + 1)));
        }
        return metrics;
    }

    /** Percent slower than the baseline for the worst metric (negative means faster). */
    private static double worstRegression(Map<String, Map<String, Double>> results, Map<String, String> baseline) {
        double worst = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Map<String, Double>> result : results.entrySet()) {
            Map<String, Double> before = parse(baseline.get(result.getKey()));
            for (Map.Entry<String, Double> metric : result.getValue().entrySet()) {
                Double was = before.get(metric.getKey());
                if (was != null && was > 0) worst = Math.max(worst, (metric.getValue() / was - 1) * 100);
            }
        }
        return worst;
    }

    /** A Markdown table, readable in a terminal and in a CI job summary. */
    private static String report(Map<String, Map<String, Double>> results, Map<String, String> baseline) {
        StringBuilder out = new StringBuilder("| case | metric | baseline | now | change |\n|---|---|---:|---:|---:|\n");
        results.forEach((name, metrics) -> {
            Map<String, Double> before = parse(baseline.get(name));
            metrics.forEach((metric, value) -> {
                Double was = before.get(metric);
                String change = was == null || was == 0 ? "new" : String.format(Locale.ROOT, "%+.1f%%", (value / was - 1) * 100);
                out.append(String.format(Locale.ROOT, "| %s | %s | %s | %.1f | %s |%n", name, metric,
                        was == null ? "-" : String.format(Locale.ROOT, "%.1f", was), value, change));
            });
        });
        return out.toString();
    }
}
