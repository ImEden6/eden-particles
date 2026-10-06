package com.eden.particles;

import com.eden.particles.impl.client.EffectReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

final class EdenParticlesNeoForgeClient {

    private EdenParticlesNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(new EffectReloadListener()));
    }
}
