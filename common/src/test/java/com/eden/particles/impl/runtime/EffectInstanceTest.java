package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.load.Linker;
import com.eden.particles.impl.math.Kinematics;
import com.eden.particles.impl.math.Rotations;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectInstanceTest {

    private static final String TEMPLATE = """
            {"render": {"type": "sprite", "sprite": "test:dot"}, "lifetime": 20}""";

    record Spawn(double x, double y, double z, double vx, double vy, double vz, int lifetime) {
    }

    private static BakedEffect effect(String duration, String emitter) {
        String json = "{\"duration\": " + duration + ", \"emitters\": [" + emitter.replace("$T", TEMPLATE) + "]}";
        ParticleEffect definition = ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "fx");
        var linked = Linker.link(Map.of(), Map.of(id, definition), Set.of(), Map.of(), t -> true).effects().get(id);
        return BakedEffect.bake(linked);
    }

    private static List<List<Spawn>> run(BakedEffect effect, Anchor anchor, PlayParams params, int ticks, float keep) {
        EffectInstance instance = new EffectInstance(effect, anchor, params);
        List<List<Spawn>> perTick = new ArrayList<>();
        for (int t = 0; t < ticks && instance.prepare(); t++) {
            List<Spawn> spawns = new ArrayList<>();
            instance.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> spawns.add(new Spawn(x, y, z, vx, vy, vz, life)), keep, keep);
            perTick.add(spawns);
        }
        return perTick;
    }

    private static List<Spawn> all(List<List<Spawn>> perTick) {
        return perTick.stream().flatMap(List::stream).toList();
    }

    private static final Anchor ORIGIN = Anchor.fixed(0, 0, 0);

    @Test
    void rateSpawnsItsIntegralAndThenFinishes() {
        BakedEffect fx = effect("20", "{\"duration\": 20, \"template\": $T, \"spawn\": {\"rate\": [[0, 0], [1, 10]]}}");
        EffectInstance instance = new EffectInstance(fx, ORIGIN, PlayParams.of(1));
        int total = 0;
        while (instance.prepare()) {
            total += instance.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> { }, 1f, 1f);
        }
        assertEquals(100, total); // 20 ticks × average rate 5
        assertTrue(instance.isFinished());
    }

    @Test
    void sameSeedReplaysExactlyAndOtherSeedsDiffer() {
        BakedEffect fx = effect("10", "{\"duration\": 10, \"template\": $T, \"shape\": {\"type\": \"sphere\", \"radius\": 2},"
                + " \"spawn\": {\"rate\": 3}, \"velocity\": {\"type\": \"radial\", \"speed\": [0.1, 0.2]}}");
        List<Spawn> a = all(run(fx, ORIGIN, PlayParams.of(42), 10, 1f));
        List<Spawn> b = all(run(fx, ORIGIN, PlayParams.of(42), 10, 1f));
        List<Spawn> c = all(run(fx, ORIGIN, PlayParams.of(43), 10, 1f));
        assertEquals(30, a.size());
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void lowerQualityShowsASubsetOfTheSameParticles() {
        BakedEffect fx = effect("40", "{\"duration\": 40, \"template\": $T, \"shape\": {\"type\": \"disc\", \"radius\": 3}, \"spawn\": {\"rate\": 25}}");
        Set<Spawn> full = new HashSet<>(all(run(fx, ORIGIN, PlayParams.of(5), 40, 1f)));
        List<Spawn> reduced = all(run(fx, ORIGIN, PlayParams.of(5), 40, 2f / 3f));
        assertEquals(1000, full.size());
        assertEquals(2.0 / 3.0, reduced.size() / 1000.0, 0.05);
        assertTrue(full.containsAll(reduced));
    }

    @Test
    void ringParticlesSitOnTheRadiusAroundTheOffset() {
        BakedEffect fx = effect("5", "{\"duration\": 5, \"template\": $T, \"shape\": {\"type\": \"ring\", \"radius\": 2},"
                + " \"offset\": [0, 1, 0], \"spawn\": {\"rate\": 10}}");
        for (Spawn s : all(run(fx, Anchor.fixed(10, 64, -3), PlayParams.of(1), 5, 1f))) {
            assertEquals(65.0, s.y, 1e-6);
            assertEquals(2.0, Math.hypot(s.x - 10, s.z + 3), 1e-5);
        }
    }

    @Test
    void burstsFireOnTheirTickOnly() {
        BakedEffect fx = effect("10", "{\"duration\": 10, \"template\": $T, \"spawn\": {\"bursts\": [{\"at\": 3, \"count\": 12}]}}");
        List<List<Spawn>> perTick = run(fx, ORIGIN, PlayParams.of(1), 10, 1f);
        for (int t = 0; t < perTick.size(); t++) {
            assertEquals(t == 3 ? 12 : 0, perTick.get(t).size(), "tick " + t);
        }
    }

    @Test
    void evenBurstsSpreadOverTheSphere() {
        BakedEffect fx = effect("1", "{\"duration\": 1, \"template\": $T, \"shape\": {\"type\": \"sphere\", \"radius\": 1.5, \"surface\": true},"
                + " \"distribution\": \"even\", \"spawn\": {\"bursts\": [{\"count\": 200}]}}");
        List<Spawn> spawns = all(run(fx, ORIGIN, PlayParams.of(9), 1, 1f));
        double min = Double.MAX_VALUE, max = 0;
        for (Spawn a : spawns) {
            assertEquals(1.5, Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z), 1e-5);
            double nearest = Double.MAX_VALUE;
            for (Spawn b : spawns) {
                if (a != b) nearest = Math.min(nearest, Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2) + Math.pow(a.z - b.z, 2)));
            }
            min = Math.min(min, nearest);
            max = Math.max(max, nearest);
        }
        assertTrue(max / min < 2.0, "uneven: " + max / min);
    }

    @Test
    void rotationAndScaleApplyToOffsets() {
        BakedEffect fx = effect("1", "{\"duration\": 1, \"template\": $T, \"offset\": [1, 0, 0], \"spawn\": {\"bursts\": [{\"count\": 1}]}}");
        Quaternionf yaw90 = Rotations.fromYawPitchRoll(90, 0, 0);
        Spawn s = all(run(fx, ORIGIN, new PlayParams(2f, yaw90, 0xFFFFFF, 1), 1, 1f)).get(0);
        // Yaw 90 turns +X to +Z (the same turn that takes +Z to −X); scale doubles the distance.
        assertEquals(0.0, s.x, 1e-6);
        assertEquals(2.0, s.z, 1e-6);
    }

    @Test
    void axisTiltsTheShape() {
        BakedEffect fx = effect("3", "{\"duration\": 3, \"template\": $T, \"shape\": {\"type\": \"ring\", \"radius\": 1}, \"axis\": \"x\", \"spawn\": {\"rate\": 5}}");
        for (Spawn s : all(run(fx, ORIGIN, PlayParams.of(1), 3, 1f))) {
            assertEquals(0.0, s.x, 1e-6); // a ring around +X lies in the YZ plane
        }
    }

    @Test
    void distanceSpeedCoversTheDistanceOverEachLifetime() {
        BakedEffect fx = effect("1", "{\"duration\": 1, \"template\": "
                + "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": [10, 30], \"physics\": {\"drag\": 0.9}},"
                + " \"spawn\": {\"bursts\": [{\"count\": 50}]}, \"velocity\": {\"type\": \"cone\", \"half_angle\": 30, \"speed\": {\"distance\": 2}}}");
        for (Spawn s : all(run(fx, ORIGIN, PlayParams.of(3), 1, 1f))) {
            double speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
            assertEquals(Kinematics.speedForDistance(2, 0.9, s.lifetime), speed, 1e-5);
            assertTrue(s.vy >= Math.cos(Math.toRadians(30)) * speed - 1e-6);
        }
    }

    @Test
    void rateParticlesSpreadAlongAMovingAnchor() {
        Vector3d anchorPos = new Vector3d();
        BakedEffect fx = effect("5", "{\"duration\": 5, \"template\": $T, \"spawn\": {\"rate\": 4}}");
        EffectInstance instance = new EffectInstance(fx, out -> { out.set(anchorPos); return true; }, PlayParams.of(1));
        instance.prepare();
        instance.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> { }, 1f, 1f);
        anchorPos.set(10, 0, 0);
        List<Double> xs = new ArrayList<>();
        instance.prepare();
        instance.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> xs.add(x), 1f, 1f);
        assertEquals(List.of(1.25, 3.75, 6.25, 8.75), xs);
    }

    @Test
    void loopingEffectsRepeatWithFreshParticles() {
        BakedEffect fx = effect("10, \"loop\": true", "{\"duration\": 10, \"template\": $T, \"shape\": {\"type\": \"sphere\", \"radius\": 1},"
                + " \"spawn\": {\"bursts\": [{\"count\": 4}]}}");
        List<List<Spawn>> perTick = run(fx, ORIGIN, PlayParams.of(1), 30, 1f);
        assertEquals(30, perTick.size());
        assertEquals(4, perTick.get(0).size());
        assertEquals(4, perTick.get(10).size());
        assertEquals(4, perTick.get(20).size());
        assertNotEquals(perTick.get(0), perTick.get(10));
    }

    @Test
    void lateJoinersLandMidLoopWithoutTheBacklog() {
        BakedEffect fx = effect("10, \"loop\": true", "{\"duration\": 10, \"template\": $T, \"spawn\": {\"bursts\": [{\"at\": 3, \"count\": 4}]}}");
        EffectInstance joiner = new EffectInstance(fx, ORIGIN, PlayParams.of(1));
        joiner.fastForward(23); // third loop, tick 3: the burst fires on the joiner's first tick, in step with everyone
        assertTrue(joiner.prepare());
        assertEquals(4, joiner.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> { }, 1f, 1f));
        for (int tick = 24; tick < 33; tick++) {
            joiner.prepare();
            assertEquals(0, joiner.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> { }, 1f, 1f), "tick " + tick);
        }
        joiner.prepare();
        assertEquals(4, joiner.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> { }, 1f, 1f)); // tick 33: next loop's burst
    }

    @Test
    void lateJoinersOfAFinishedOneShotSeeNothing() {
        BakedEffect fx = effect("10", "{\"duration\": 10, \"template\": $T, \"spawn\": {\"rate\": 1}}");
        EffectInstance joiner = new EffectInstance(fx, ORIGIN, PlayParams.of(1));
        joiner.fastForward(10);
        assertFalse(joiner.prepare());

        EffectInstance midway = new EffectInstance(fx, ORIGIN, PlayParams.of(1));
        midway.fastForward(6);
        int ticks = 0;
        while (midway.prepare()) {
            midway.tick((tpl, x, y, z, vx, vy, vz, life, scale, tint) -> { }, 1f, 1f);
            ticks++;
        }
        assertEquals(4, ticks);
    }

    @Test
    void stoppingOrLosingTheAnchorEndsTheEffect() {
        BakedEffect fx = effect("100", "{\"duration\": 100, \"template\": $T, \"spawn\": {\"rate\": 1}}");
        EffectInstance stopped = new EffectInstance(fx, ORIGIN, PlayParams.of(1));
        stopped.stop();
        assertFalse(stopped.prepare());

        boolean[] alive = {true};
        EffectInstance orphan = new EffectInstance(fx, out -> alive[0], PlayParams.of(1));
        assertTrue(orphan.prepare());
        alive[0] = false;
        assertFalse(orphan.prepare());
        assertTrue(orphan.isFinished());
    }
}
