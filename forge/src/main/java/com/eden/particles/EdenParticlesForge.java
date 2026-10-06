package com.eden.particles;

import com.eden.particles.impl.ServerCommands;
import com.eden.particles.platform.ForgeNetworking;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class EdenParticlesForge {

    public EdenParticlesForge(FMLJavaModLoadingContext context) {
        EdenParticlesCommon.init();
        ForgeNetworking.init();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ServerCommands.register(event.getDispatcher()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EdenParticlesForgeClient.init(context.getModEventBus());
        }
    }
}
