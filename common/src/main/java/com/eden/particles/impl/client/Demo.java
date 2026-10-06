package com.eden.particles.impl.client;

import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.PlayParams;
import com.eden.particles.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only: with {@code -Deden_particles.demo=<effect id>} (or {@code EDEN_PARTICLES_DEMO}), replays that effect
 * in front of the player whenever nothing is playing.
 */
final class Demo {

    private static final ResourceLocation EFFECT = parse();
    private static int cooldown;

    private Demo() {
    }

    static void tick(Minecraft minecraft, EffectManager manager) {
        if (EFFECT == null || minecraft.player == null || manager.activeCount() > 0 || --cooldown > 0) return;
        cooldown = 20;
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 at = eye.add(look.x * 4, -1.0, look.z * 4);
        manager.play(EFFECT, Anchor.fixed(at.x, at.y, at.z), PlayParams.of(minecraft.level.getGameTime()));
    }

    private static ResourceLocation parse() {
        if (!Services.PLATFORM.isDevelopmentEnvironment()) return null;
        String value = System.getProperty("eden_particles.demo", System.getenv("EDEN_PARTICLES_DEMO"));
        return value == null || value.isBlank() ? null : ResourceLocation.tryParse(value.trim());
    }
}
