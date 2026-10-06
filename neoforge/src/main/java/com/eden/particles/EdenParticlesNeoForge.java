package com.eden.particles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class EdenParticlesNeoForge {

    public EdenParticlesNeoForge(IEventBus modEventBus) {
        EdenParticlesCommon.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EdenParticlesNeoForgeClient.init(modEventBus);
        }
    }
}
