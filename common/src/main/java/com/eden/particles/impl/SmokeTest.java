package com.eden.particles.impl;

import com.eden.particles.Constants;
import com.eden.particles.api.EdenParticles;
import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.images.ServerImages;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only check for a dedicated server started with {@code -Deden_particles.smoke=true} and the test datapack
 * ({@code scripts/smoke-server.sh}): once the server is ticking, exercise the server side, log the result and stop.
 */
final class SmokeTest {

    static final boolean ENABLED = Boolean.getBoolean("eden_particles.smoke");
    private static final int AT_TICK = 20;
    private static boolean done;

    private SmokeTest() {
    }

    static void tick(MinecraftServer server) {
        if (done || server.getTickCount() < AT_TICK) return;
        done = true;
        List<String> failures = new ArrayList<>();
        try {
            run(server, failures);
        } catch (Exception e) {
            Constants.LOG.error("Smoke test threw", e);
            failures.add("threw " + e);
        }
        if (failures.isEmpty()) {
            Constants.LOG.info("Eden's Particles smoke test passed");
        } else {
            Constants.LOG.error("Eden's Particles smoke test FAILED: {}", String.join("; ", failures));
        }
        server.halt(false);
        // Forge's dev server leaves a non-daemon thread behind, so exit once the server thread has finished saving.
        int status = failures.isEmpty() ? 0 : 1;
        Thread exit = new Thread(() -> {
            try {
                server.getRunningThread().join();
            } catch (InterruptedException ignored) {
            }
            System.exit(status);
        }, "Eden's Particles smoke exit");
        exit.setDaemon(true);
        exit.start();
    }

    private static void run(MinecraftServer server, List<String> failures) throws Exception {
        List<SyncDefinitionsPayload.Entry> entries = DatapackDefinitions.entries();
        check(failures, entries.stream().anyMatch(e -> e.kind() == DefinitionError.Kind.EFFECT && e.id().toString().equals("eden_smoke:burst")),
                "datapack effect eden_smoke:burst not loaded: " + entries);
        check(failures, entries.stream().anyMatch(e -> e.kind() == DefinitionError.Kind.TEMPLATE), "datapack template not loaded");
        check(failures, ServerImages.count() == 1, "expected 1 datapack image, got " + ServerImages.count());

        CommandDispatcher<CommandSourceStack> commands = server.getCommands().getDispatcher();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        check(failures, commands.execute("eden_particles play eden_smoke:burst 0 64 0 persistent", source) == 1, "play persistent failed");
        check(failures, ServerEffects.persistentCount() == 1, "persistent effect not tracked");
        commands.execute("eden_particles stop", source);
        check(failures, ServerEffects.persistentCount() == 0, "stop didn't forget the persistent effect");

        ServerLevel level = server.overworld();
        ArmorStand caster = stand(level, 0, 64, 0);
        ArmorStand target = stand(level, 6, 64, 0);
        EffectHandle beam = EdenParticles.play(level, ResourceLocation.parse("eden_smoke:burst"),
                EffectAnchor.between(EffectAnchor.entity(caster).eyes().turning(EffectAnchor.Turning.LOOK).offset(0, 0, 0.5),
                        EffectAnchor.entity(target)),
                EffectParams.builder().persistent().input("power", 2).build());
        check(failures, beam.isAlive(), "beam not alive");
        beam.setInput("power", 3);
        beam.setRotation(10, 0, 0);
        check(failures, ServerEffects.changedCount() == 1, "handle change not queued");
        ServerEffects.tick(server);
        check(failures, ServerEffects.changedCount() == 0, "handle change not flushed");
        target.discard();
        check(failures, !beam.isAlive(), "beam outlived its target");
        beam.stop();
        commands.execute("eden_particles beam eden_smoke:burst @e[type=armor_stand,limit=1] @e[type=armor_stand,limit=1] power=2", source);
        commands.execute("eden_particles play eden_smoke:burst 0 64 0 with power=2 spread=1", source);
        commands.execute("eden_particles attach eden_smoke:burst @e[type=armor_stand,limit=1] look eyes 0 0 0.5 power=1", source);
        commands.execute("eden_particles set power 2", source);
        caster.discard();
    }

    private static ArmorStand stand(ServerLevel level, double x, double y, double z) {
        ArmorStand stand = new ArmorStand(level, x, y, z);
        level.addFreshEntity(stand);
        return stand;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) failures.add(message);
    }
}
