package com.eden.particles.impl.client;

import com.eden.particles.impl.runtime.SoundSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A sound that its effect moves each tick, and that fades out rather than cutting off when a loop ends. */
final class EdenSound extends AbstractTickableSoundInstance implements SoundSink.Sound {

    private static final int FADE_TICKS = 5;

    private final float fullVolume;
    private int fade = -1;

    EdenSound(ResourceLocation sound, SoundSource category, boolean loop, float volume, float pitch, long seed,
              double x, double y, double z) {
        super(SoundEvent.createVariableRangeEvent(sound), category, RandomSource.create(seed));
        this.looping = loop;
        this.delay = 0;
        this.volume = volume;
        this.fullVolume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void tick() {
        if (fade < 0) return;
        if (--fade <= 0) stop();
        else volume = fullVolume * fade / FADE_TICKS;
    }

    @Override
    public void moveTo(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fadeOut() {
        if (looping && fade < 0) fade = FADE_TICKS;
    }

    @Override
    public boolean isDone() {
        return isStopped() || !Minecraft.getInstance().getSoundManager().isActive(this);
    }
}
