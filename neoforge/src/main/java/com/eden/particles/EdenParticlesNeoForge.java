package com.eden.particles;

import com.eden.particles.impl.DatapackDefinitions;
import com.eden.particles.impl.ServerCommands;
import com.eden.particles.impl.ServerEffects;
import com.eden.particles.impl.Sides;
import com.eden.particles.impl.images.ServerImages;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.impl.net.UpdateEffectPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;

@Mod(Constants.MOD_ID)
public class EdenParticlesNeoForge {

    public EdenParticlesNeoForge(IEventBus modEventBus) {
        EdenParticlesCommon.init();
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar(String.valueOf(Constants.PROTOCOL)).optional();
            registrar.playToClient(PlayEffectPayload.TYPE, PlayEffectPayload.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToClient(StopEffectPayload.TYPE, StopEffectPayload.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToClient(UpdateEffectPayload.TYPE, UpdateEffectPayload.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToClient(SyncDefinitionsPayload.TYPE, SyncDefinitionsPayload.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToClient(ImagePayloads.Offer.TYPE, ImagePayloads.Offer.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToClient(ImagePayloads.Images.TYPE, ImagePayloads.Images.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToServer(ImagePayloads.Consent.TYPE, ImagePayloads.Consent.STREAM_CODEC,
                    (payload, context) -> ServerImages.onConsent((ServerPlayer) context.player(), payload));
        });
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> ServerCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> ServerEffects.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, event -> event.addListener(new DatapackDefinitions()));
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class, event -> event.getRelevantPlayers().forEach(DatapackDefinitions::syncTo));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EdenParticlesNeoForgeClient.init(modEventBus);
        }
    }
}
