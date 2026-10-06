package com.eden.particles;

import com.eden.particles.impl.client.ClientEffects;
import com.eden.particles.impl.client.EffectReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public class EdenParticlesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricEffectReloadListener());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEffects.tick());
    }

    private static final class FabricEffectReloadListener extends EffectReloadListener implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return ID;
        }
    }
}
