package com.eden.particles;

import com.eden.particles.impl.DatapackDefinitions;
import com.eden.particles.impl.ServerCommands;
import com.eden.particles.impl.ServerEffects;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public class EdenParticlesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        EdenParticlesCommon.init();
        PayloadTypeRegistry.playS2C().register(PlayEffectPayload.TYPE, PlayEffectPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StopEffectPayload.TYPE, StopEffectPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncDefinitionsPayload.TYPE, SyncDefinitionsPayload.STREAM_CODEC);
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new FabricDatapackDefinitions());
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> DatapackDefinitions.syncTo(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> ServerCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(ServerEffects::tick);
    }

    private static final class FabricDatapackDefinitions extends DatapackDefinitions implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return ID;
        }
    }
}
