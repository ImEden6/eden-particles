package com.eden.particles.impl.runtime;

import net.minecraft.resources.ResourceLocation;

/** Starts effects that a playing effect plays itself. */
@FunctionalInterface
public interface EffectHost {

    /** @return the started effect, or null if it couldn't start (unknown, or too many playing) */
    EffectInstance start(ResourceLocation effect, Anchor anchor, PlayParams params);
}
