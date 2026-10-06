package com.eden.particles;

import com.eden.particles.impl.client.ClientEffects;
import com.eden.particles.impl.client.EffectReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

final class EdenParticlesNeoForgeClient {

    private EdenParticlesNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(new EffectReloadListener()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClientEffects.tick());
    }
}
