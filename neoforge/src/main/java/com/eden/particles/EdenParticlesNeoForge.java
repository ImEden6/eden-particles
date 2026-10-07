package com.eden.particles;

import com.eden.particles.impl.ServerCommands;
import com.eden.particles.impl.ServerEffects;
import com.eden.particles.impl.Sides;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class EdenParticlesNeoForge {

    public EdenParticlesNeoForge(IEventBus modEventBus) {
        EdenParticlesCommon.init();
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar("1").optional();
            registrar.playToClient(PlayEffectPayload.TYPE, PlayEffectPayload.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
            registrar.playToClient(StopEffectPayload.TYPE, StopEffectPayload.STREAM_CODEC,
                    (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)));
        });
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> ServerCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> ServerEffects.tick(event.getServer()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EdenParticlesNeoForgeClient.init(modEventBus);
        }
    }
}
