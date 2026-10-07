package com.eden.particles.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Queue;

/** Lets spawning stay under vanilla's per-render-type eviction limit. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    @Accessor("particles")
    Map<ParticleRenderType, Queue<Particle>> eden_particles$getParticles();

    /** Creates a particle without adding it, unlike {@code createParticle}. */
    @Invoker("makeParticle")
    <T extends ParticleOptions> Particle eden_particles$makeParticle(T options, double x, double y, double z,
                                                                    double xSpeed, double ySpeed, double zSpeed);
}
