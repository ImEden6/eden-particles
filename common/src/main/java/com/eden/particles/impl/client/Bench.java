package com.eden.particles.impl.client;

import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.PlayParams;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Plays an effect on a grid and measures {@link EffectManager}'s own tick cost (spawning and effect bookkeeping;
 * vanilla's particle tick and render are not included) over {@value #TICKS} ticks.
 */
final class Bench {

    static final int TICKS = 100;
    private static final double SPACING = 4.0;

    private final Consumer<Component> report;
    private int ticks;
    private long totalNanos;
    private long maxNanos;
    private long attempts;
    private int peakParticles;

    private Bench(Consumer<Component> report) {
        this.report = report;
    }

    static boolean start(EffectManager manager, ResourceLocation effect, int count, Consumer<Component> report) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !manager.isLoaded(effect)) return false;
        int side = (int) Math.ceil(Math.sqrt(count));
        Vec3 look = minecraft.player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        Vec3 origin = minecraft.player.position().add(look.scale(SPACING * 2)).subtract(right.scale(SPACING * (side - 1) / 2.0));
        for (int i = 0; i < count; i++) {
            Vec3 at = origin.add(right.scale(SPACING * (i % side))).add(look.scale(SPACING * (i / side)));
            manager.play(effect, Anchor.fixed(at.x, at.y, at.z), PlayParams.of(minecraft.level.getGameTime() + i));
        }
        manager.bench = new Bench(report);
        return true;
    }

    /** @return false once finished */
    boolean record(long nanos, int spawnAttempts) {
        ticks++;
        totalNanos += nanos;
        maxNanos = Math.max(maxNanos, nanos);
        attempts += spawnAttempts;
        peakParticles = Math.max(peakParticles, Integer.parseInt(Minecraft.getInstance().particleEngine.countParticles()));
        if (ticks < TICKS) return true;
        report.accept(Component.literal(String.format(Locale.ROOT,
                "Bench over %d ticks: %.3f ms/tick average, %.3f ms worst; %.0f spawns/tick; peak %d live particles",
                ticks, totalNanos / 1e6 / ticks, maxNanos / 1e6, attempts / (double) ticks, peakParticles)));
        return false;
    }
}
