package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.SoundCue;
import com.eden.particles.impl.load.Linker;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SoundsTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "fx");

    private static ParticleEffect parse(String json) {
        DataResult<ParticleEffect> result = ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.result().orElseGet(() -> fail(result.error().orElseThrow().message()));
    }

    private static String parseError(String json) {
        return ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().orElseThrow().message();
    }

    private static EffectInstance play(String json, double x, long seed) {
        Linker.Result result = Linker.link(Map.of(), Map.of(ID, parse(json)), Set.of(), Map.of(), t -> true);
        if (!result.errors().isEmpty()) fail(result.errors().toString());
        return new EffectInstance(BakedEffect.bake(result.effects().get(ID)), Anchor.fixed(x, 0, 0),
                new PlayParams(1f, new Quaternionf(), 0xFFFFFF, seed));
    }

    /** Records plays; a sound is done once {@code length} ticks have passed, unless it loops. */
    private static final class Recorder implements SoundSink {
        final List<Played> played = new ArrayList<>();
        int tick;
        int length = 3;

        @Override
        public Sound play(SoundCue cue, float volume, float pitch, long seed, double x, double y, double z) {
            Played p = new Played(cue, tick, volume, pitch, x, y, z);
            played.add(p);
            return p;
        }

        final class Played implements Sound {
            final SoundCue cue;
            final int startTick;
            final float volume, pitch;
            double x, y, z;
            int fadedAt = -1;

            Played(SoundCue cue, int startTick, float volume, float pitch, double x, double y, double z) {
                this.cue = cue;
                this.startTick = startTick;
                this.volume = volume;
                this.pitch = pitch;
                this.x = x;
                this.y = y;
                this.z = z;
            }

            @Override
            public void moveTo(double x, double y, double z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }

            @Override
            public void fadeOut() {
                if (fadedAt < 0) fadedAt = tick;
            }

            @Override
            public boolean isDone() {
                return fadedAt >= 0 || (!cue.loop() && tick - startTick >= length);
            }
        }
    }

    private static Recorder run(EffectInstance instance, int ticks) {
        Recorder recorder = new Recorder();
        instance.setSounds(recorder);
        for (; recorder.tick < ticks; recorder.tick++) {
            if (instance.prepare()) instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values, frame) -> { }, 1f, 1f);
        }
        return recorder;
    }

    @Test
    void oneShotPlaysOnceAtItsStartTickAndOffset() {
        Recorder r = run(play("{\"duration\": 10, \"emitters\": [{\"sound\": \"test:boom\", \"start\": 4, \"offset\": [0, 2, 0]}]}", 5, 1), 10);
        assertEquals(1, r.played.size());
        Recorder.Played p = r.played.get(0);
        assertEquals(4, p.startTick);
        assertEquals(5, p.x, 1e-9);
        assertEquals(2, p.y, 1e-9);
        assertEquals(SoundSource.PLAYERS, p.cue.category());
    }

    @Test
    void oneShotReplaysOnEveryLoop() {
        Recorder r = run(play("{\"duration\": 5, \"loop\": true, \"emitters\": [{\"sound\": \"test:tick\"}]}", 0, 1), 15);
        assertEquals(List.of(0, 5, 10), r.played.stream().map(p -> p.startTick).toList());
    }

    @Test
    void loopingSoundFadesWhenItsWindowEnds() {
        Recorder r = run(play("{\"duration\": 20, \"emitters\": [{\"sound\": \"test:hum\", \"loop\": true, \"start\": 2, \"duration\": 6}]}", 0, 1), 20);
        assertEquals(1, r.played.size());
        assertEquals(2, r.played.get(0).startTick);
        assertEquals(8, r.played.get(0).fadedAt);
    }

    @Test
    void loopingSoundCoveringTheTimelineKeepsPlayingAcrossLoops() {
        Recorder r = run(play("{\"duration\": 5, \"loop\": true, \"emitters\": [{\"sound\": \"test:hum\", \"loop\": true}]}", 0, 1), 17);
        assertEquals(1, r.played.size());
        assertEquals(-1, r.played.get(0).fadedAt);
    }

    @Test
    void endingAndStoppingFadeLoopingSounds() {
        Recorder ended = run(play("{\"duration\": 6, \"emitters\": [{\"sound\": \"test:hum\", \"loop\": true}]}", 0, 1), 10);
        assertEquals(5, ended.played.get(0).fadedAt);

        EffectInstance instance = play("{\"duration\": 50, \"loop\": true, \"emitters\": [{\"sound\": \"test:hum\", \"loop\": true}]}", 0, 1);
        Recorder stopped = run(instance, 3);
        instance.stop();
        assertTrue(stopped.played.get(0).fadedAt >= 0);
    }

    @Test
    void lateJoinersHearLoopsButNotMissedOneShots() {
        EffectInstance instance = play("{\"duration\": 40, \"emitters\": [{\"sound\": \"test:boom\"}, {\"sound\": \"test:hum\", \"loop\": true}]}", 0, 1);
        instance.fastForward(10);
        Recorder r = run(instance, 5);
        assertEquals(List.of("hum"), r.played.stream().map(p -> p.cue.sound().getPath()).toList());
    }

    @Test
    void soundsFollowAMovingAnchor() {
        Linker.Result result = Linker.link(Map.of(), Map.of(ID, parse("{\"duration\": 10, \"emitters\": [{\"sound\": \"test:hum\", \"loop\": true}]}")),
                Set.of(), Map.of(), t -> true);
        double[] x = {0};
        EffectInstance instance = new EffectInstance(BakedEffect.bake(result.effects().get(ID)), out -> {
            out.position.set(x[0], 0, 0);
            out.facing.identity();
            return true;
        }, new PlayParams(1f, new Quaternionf(), 0xFFFFFF, 1));
        Recorder r = new Recorder();
        instance.setSounds(r);
        for (; r.tick < 4; r.tick++) {
            x[0] = r.tick;
            if (instance.prepare()) instance.tick((t, px, py, pz, vx, vy, vz, life, scale, tint, variant, values, frame) -> { }, 1f, 1f);
        }
        assertEquals(3, r.played.get(0).x, 1e-9);
    }

    @Test
    void pitchIsRolledFromTheEffectSeed() {
        String json = "{\"duration\": 10, \"emitters\": [{\"sound\": \"test:boom\", \"pitch\": [0.5, 2], \"volume\": 0.7}]}";
        float a = run(play(json, 0, 42), 1).played.get(0).pitch;
        assertEquals(a, run(play(json, 0, 42), 1).played.get(0).pitch);
        assertTrue(a >= 0.5f && a <= 2f);
        assertEquals(0.7f, run(play(json, 0, 42), 1).played.get(0).volume, 1e-6f);
        boolean differs = false;
        for (long seed = 0; seed < 8 && !differs; seed++) differs = run(play(json, 0, seed), 1).played.get(0).pitch != a;
        assertTrue(differs);
    }

    @Test
    void rejectsDurationOnOneShotsAndUnknownCategories() {
        assertTrue(parseError("{\"duration\": 10, \"emitters\": [{\"sound\": \"test:boom\", \"duration\": 3}]}")
                .contains("only looping sounds have a duration"));
        String category = parseError("{\"duration\": 10, \"emitters\": [{\"sound\": \"test:boom\", \"category\": \"players\"}]}");
        assertTrue(category.contains("did you mean 'player'"), category);
        assertFalse(parseError("{\"duration\": 10, \"emitters\": [{\"sound\": \"test:hum\", \"loop\": true, \"duration\": 11}]}").isEmpty());
    }
}
