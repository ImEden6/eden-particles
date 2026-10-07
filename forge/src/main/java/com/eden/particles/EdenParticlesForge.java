package com.eden.particles;

import com.eden.particles.impl.DatapackDefinitions;
import com.eden.particles.impl.ServerCommands;
import com.eden.particles.impl.ServerEffects;
import com.eden.particles.platform.ForgeNetworking;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class EdenParticlesForge {

    public EdenParticlesForge(FMLJavaModLoadingContext context) {
        EdenParticlesCommon.init();
        ForgeNetworking.init();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ServerCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> ServerEffects.tick(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new DatapackDefinitions()));
        // Covers /reload. On join, Forge 1.21.1 posts this with no player before adding the new one to the player list,
        // so the joining player is synced on login instead.
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.getPlayers().forEach(DatapackDefinitions::syncTo));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) DatapackDefinitions.syncTo(player);
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EdenParticlesForgeClient.init(context.getModEventBus());
        }
    }
}
