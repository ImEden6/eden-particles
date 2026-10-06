package com.eden.particles;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class EdenParticlesForge {

    public EdenParticlesForge(FMLJavaModLoadingContext context) {
        EdenParticlesCommon.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EdenParticlesForgeClient.init(context.getModEventBus());
        }
    }
}
