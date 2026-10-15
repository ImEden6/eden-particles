package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.load.Linker;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LightsTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "fx");

    private static EffectInstance play(String json) {
        DataResult<ParticleEffect> parsed = ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        ParticleEffect effect = parsed.result().orElseGet(() -> fail(parsed.error().orElseThrow().message()));
        Linker.Result result = Linker.link(Map.of(), Map.of(ID, effect), Set.of(), Map.of(), t -> true);
        if (!result.errors().isEmpty()) fail(result.errors().toString());
        return new EffectInstance(BakedEffect.bake(result.effects().get(ID)), Anchor.fixed(2, 0, 0),
                new PlayParams(1f, new Quaternionf(), 0xFFFFFF, 1));
    }

    /** Records each light's level per tick, and the tick it ended on. */
    private static final class Recorder implements LightSink {
        final List<Lit> lit = new ArrayList<>();
        int tick;

        @Override
        public Light start(double x, double y, double z, float level) {
            Lit l = new Lit(tick, y);
            l.levels.add(level);
            lit.add(l);
            return l;
        }

        final class Lit implements Light {
            final int startTick;
            final List<Float> levels = new ArrayList<>();
            double y;
            int endedAt = -1;

            Lit(int startTick, double y) {
                this.startTick = startTick;
                this.y = y;
            }

            @Override
            public void update(double x, double y, double z, float level) {
                this.y = y;
                levels.add(level);
            }

            @Override
            public void end() {
                endedAt = tick;
            }
        }
    }

    private static Recorder run(EffectInstance instance, int ticks) {
        Recorder recorder = new Recorder();
        instance.setLights(recorder);
        for (; recorder.tick < ticks; recorder.tick++) {
            if (instance.prepare()) instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values, frame) -> { }, 1f, 1f);
        }
        return recorder;
    }

    @Test
    void lightFollowsItsCurveOverItsWindowThenEnds() {
        Recorder r = run(play("{\"duration\": 20, \"emitters\": [{\"light\": [[0, 15], [1, 0]], \"start\": 2, \"duration\": 10, \"offset\": [0, 1, 0]}]}"), 20);
        assertEquals(1, r.lit.size());
        Recorder.Lit light = r.lit.get(0);
        assertEquals(2, light.startTick);
        assertEquals(12, light.endedAt);
        assertEquals(10, light.levels.size());
        assertEquals(15f * 0.95f, light.levels.get(0), 1e-3f);
        assertTrue(light.levels.get(9) < 1f);
        assertEquals(1, light.y, 1e-9);
    }

    @Test
    void levelIsClampedToBlockLightRange() {
        Recorder r = run(play("{\"duration\": 4, \"inputs\": {\"power\": 3}, \"emitters\": [{\"light\": \"power * 10\"}]}"), 2);
        assertEquals(15f, r.lit.get(0).levels.get(0));
    }

    @Test
    void lightCoveringALoopingTimelineStaysLitAndStopEndsIt() {
        EffectInstance instance = play("{\"duration\": 5, \"loop\": true, \"emitters\": [{\"light\": 12}]}");
        Recorder r = run(instance, 17);
        assertEquals(1, r.lit.size());
        assertEquals(-1, r.lit.get(0).endedAt);
        instance.stop();
        assertEquals(17, r.lit.get(0).endedAt);
    }

    @Test
    void oneShotEffectEndsItsLightWhenItFinishes() {
        Recorder r = run(play("{\"duration\": 6, \"emitters\": [{\"light\": 9}]}"), 10);
        assertEquals(5, r.lit.get(0).endedAt);
    }

    @Test
    void lateJoinersSeeLightsStillInTheirWindow() {
        EffectInstance instance = play("{\"duration\": 40, \"emitters\": [{\"light\": 9, \"duration\": 5}, {\"light\": 6, \"start\": 5}]}");
        instance.fastForward(10);
        Recorder r = run(instance, 3);
        assertEquals(1, r.lit.size());
        assertEquals(6f, r.lit.get(0).levels.get(0));
    }
}
