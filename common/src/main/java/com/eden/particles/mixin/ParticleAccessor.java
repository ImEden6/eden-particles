package com.eden.particles.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets vanilla-mode templates apply their physics to vanilla particles. */
@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("gravity")
    void eden_particles$setGravity(float gravity);

    @Accessor("friction")
    void eden_particles$setFriction(float friction);

    @Accessor("hasPhysics")
    void eden_particles$setHasPhysics(boolean hasPhysics);
}
