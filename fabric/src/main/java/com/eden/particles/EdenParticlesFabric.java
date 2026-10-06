package com.eden.particles;

import net.fabricmc.api.ModInitializer;

public class EdenParticlesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        EdenParticlesCommon.init();
    }
}
