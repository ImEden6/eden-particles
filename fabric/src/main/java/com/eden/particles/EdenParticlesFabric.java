package com.eden.particles;

import com.eden.particles.impl.ServerCommands;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class EdenParticlesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        EdenParticlesCommon.init();
        PayloadTypeRegistry.playS2C().register(PlayEffectPayload.TYPE, PlayEffectPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StopEffectPayload.TYPE, StopEffectPayload.STREAM_CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> ServerCommands.register(dispatcher));
    }
}
