package com.eden.particles.impl.definition;

import java.util.Optional;
import java.util.Set;

/** An entry on an effect's timeline: an emitter, another effect played as part of this one, a sound or a light. */
public sealed interface Track permits EmitterTrack, SubEffect, SoundCue, LightCue {

    Optional<String> name();

    int start();

    int duration();

    default int end() {
        return start() + duration();
    }

    /** Input names this entry's own expressions use. */
    void names(Set<String> out);
}
