package com.eden.particles.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets vanilla-mode templates apply their physics and tint to vanilla particles. */
@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("gravity")
    void eden_particles$setGravity(float gravity);

    @Accessor("friction")
    void eden_particles$setFriction(float friction);

    @Accessor("hasPhysics")
    void eden_particles$setHasPhysics(boolean hasPhysics);

    @Accessor("rCol")
    float eden_particles$getRed();

    @Accessor("gCol")
    float eden_particles$getGreen();

    @Accessor("bCol")
    float eden_particles$getBlue();

    @Accessor("rCol")
    void eden_particles$setRed(float red);

    @Accessor("gCol")
    void eden_particles$setGreen(float green);

    @Accessor("bCol")
    void eden_particles$setBlue(float blue);
}
