package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.definition.SoundCue;
import com.eden.particles.impl.runtime.SoundSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Plays effect sounds, at most a few copies of one sound per tick so bursts of triggers don't pile up. */
final class ClientSoundSink implements SoundSink {

    static final int MAX_SAME_SOUND_PER_TICK = 4;

    private final Map<ResourceLocation, Integer> startedThisTick = new HashMap<>();
    private final Set<ResourceLocation> warnedMissing = new HashSet<>();

    void beginTick() {
        startedThisTick.clear();
    }

    void clearCaches() {
        warnedMissing.clear();
    }

    @Override
    public Sound play(SoundCue cue, float volume, float pitch, long seed, double x, double y, double z) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        ResourceLocation id = cue.sound();
        if (sounds.getSoundEvent(id) == null) {
            if (warnedMissing.add(id)) Constants.LOG.warn("Unknown sound event '{}'; no resource pack's sounds.json defines it", id);
            return null;
        }
        if (startedThisTick.merge(id, 1, Integer::sum) > MAX_SAME_SOUND_PER_TICK) return null;
        EdenSound sound = new EdenSound(id, cue.category(), cue.loop(), volume, pitch, seed, x, y, z);
        sounds.play(sound);
        return sound;
    }
}
