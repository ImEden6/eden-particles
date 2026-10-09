package com.eden.particles;

import com.eden.particles.impl.client.ClientCommands;
import com.eden.particles.impl.client.ClientEffects;
import com.eden.particles.impl.client.EffectReloadListener;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.impl.net.UpdateEffectPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public class EdenParticlesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientEffects.init();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FabricEffectReloadListener());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEffects.tick());
        ClientPlayNetworking.registerGlobalReceiver(PlayEffectPayload.TYPE, (payload, context) -> ClientEffects.HOOKS.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(StopEffectPayload.TYPE, (payload, context) -> ClientEffects.HOOKS.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(UpdateEffectPayload.TYPE, (payload, context) -> ClientEffects.HOOKS.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(SyncDefinitionsPayload.TYPE, (payload, context) -> ClientEffects.HOOKS.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(ImagePayloads.Offer.TYPE, (payload, context) -> ClientEffects.HOOKS.handle(payload));
        ClientPlayNetworking.registerGlobalReceiver(ImagePayloads.Images.TYPE, (payload, context) -> ClientEffects.HOOKS.handle(payload));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommands.create((source, message) -> source.sendFeedback(message))));
    }

    private static final class FabricEffectReloadListener extends EffectReloadListener implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return ID;
        }
    }
}
