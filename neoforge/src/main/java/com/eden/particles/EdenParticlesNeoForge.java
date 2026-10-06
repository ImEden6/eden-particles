package com.eden.particles;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class EdenParticlesNeoForge {

    public EdenParticlesNeoForge(IEventBus modEventBus) {
        EdenParticlesCommon.init();
    }
}
