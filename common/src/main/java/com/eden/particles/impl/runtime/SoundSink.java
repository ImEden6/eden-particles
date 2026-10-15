package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.SoundCue;

/** Plays an effect's sounds. Positions are world-space; {@code seed} picks among a sound event's variants. */
@FunctionalInterface
public interface SoundSink {

    /** @return the playing sound, or null if it wasn't played */
    Sound play(SoundCue cue, float volume, float pitch, long seed, double x, double y, double z);

    interface Sound {

        void moveTo(double x, double y, double z);

        /** Fades a looping sound out. One-shots finish on their own. */
        void fadeOut();

        boolean isDone();
    }
}
