package com.eden.particles;

import com.eden.particles.impl.client.EffectReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class EdenParticlesForgeClient {

    private EdenParticlesForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        modEventBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(new EffectReloadListener()));
    }
}
