package com.eden.particles.mixin;

import com.eden.particles.impl.client.EdenRenderTypes;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Vanilla renders only the types in RENDER_ORDER; NeoForge and Forge patch this, Fabric doesn't. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    @Final
    @Mutable
    private static List<ParticleRenderType> RENDER_ORDER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void eden_particles$addRenderTypes(CallbackInfo ci) {
        List<ParticleRenderType> order = new ArrayList<>(RENDER_ORDER);
        order.addAll(EdenRenderTypes.CUSTOM);
        RENDER_ORDER = List.copyOf(order);
    }
}
