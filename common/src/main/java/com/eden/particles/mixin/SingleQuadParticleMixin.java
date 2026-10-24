package com.eden.particles.mixin;

import com.eden.particles.impl.client.TintedParticle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Many vanilla particles recolour themselves every tick, so the tint only goes on for the draw.
@Mixin(SingleQuadParticle.class)
abstract class SingleQuadParticleMixin implements TintedParticle {

    @Unique
    private int eden_particles$tint = 0xFFFFFF;
    @Unique
    private float eden_particles$red;
    @Unique
    private float eden_particles$green;
    @Unique
    private float eden_particles$blue;

    @Override
    public void eden_particles$setTint(int rgb) {
        eden_particles$tint = rgb;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void eden_particles$tint(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) {
        if (eden_particles$tint == 0xFFFFFF) return;
        ParticleAccessor particle = (ParticleAccessor) this;
        eden_particles$red = particle.eden_particles$getRed();
        eden_particles$green = particle.eden_particles$getGreen();
        eden_particles$blue = particle.eden_particles$getBlue();
        particle.eden_particles$setRed(eden_particles$red * ((eden_particles$tint >> 16 & 0xFF) / 255f));
        particle.eden_particles$setGreen(eden_particles$green * ((eden_particles$tint >> 8 & 0xFF) / 255f));
        particle.eden_particles$setBlue(eden_particles$blue * ((eden_particles$tint & 0xFF) / 255f));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void eden_particles$untint(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) {
        if (eden_particles$tint == 0xFFFFFF) return;
        ParticleAccessor particle = (ParticleAccessor) this;
        particle.eden_particles$setRed(eden_particles$red);
        particle.eden_particles$setGreen(eden_particles$green);
        particle.eden_particles$setBlue(eden_particles$blue);
    }
}
