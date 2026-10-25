package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.api.EdenParticles;
import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectParams;
import com.eden.particles.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Replays one effect in front of the player whenever nothing is playing. Set by
 * {@code /eden_particles_client preview}, or in dev with {@code -Pdemo=<effect id>}.
 */
final class Preview {

    private static ResourceLocation effect = parse();
    private static EffectParams params = EffectParams.DEFAULT;
    private static int cooldown;

    private Preview() {
    }

    /** Reads the flag at startup, so its log line appears before any world loads. */
    static void init() {
    }

    static void start(ResourceLocation id, EffectParams with) {
        effect = id;
        params = with;
        cooldown = 0;
    }

    static boolean stop() {
        boolean was = effect != null;
        effect = null;
        return was;
    }

    static void tick(Minecraft minecraft, EffectManager manager) {
        if (effect == null || minecraft.player == null || manager.activeCount() > 0 || --cooldown > 0) return;
        cooldown = 20;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        EdenParticles.play(minecraft.level, effect, EffectAnchor.at(eye.add(look.x * 4, -1.0, look.z * 4)), params);
    }

    private static ResourceLocation parse() {
        if (!Services.PLATFORM.isDevelopmentEnvironment()) return null;
        String value = System.getProperty("eden_particles.demo");
        if (value == null || value.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(value.trim());
        Constants.LOG.info("Demo mode: replaying {}", id != null ? id : "nothing ('" + value + "' is not a valid id)");
        return id;
    }
}
