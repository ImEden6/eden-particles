package com.eden.particles;

import com.eden.particles.impl.client.ClientCommands;
import com.eden.particles.impl.client.ClientEffects;
import com.eden.particles.impl.client.EffectReloadListener;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class EdenParticlesForgeClient {

    private EdenParticlesForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        ClientEffects.init();
        modEventBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(new EffectReloadListener()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> ClientEffects.tick());
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                ClientCommands.create((source, message) -> source.sendSuccess(() -> message, false))));
    }
}
