package com.eden.particles.impl;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ServerImages;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

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
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) failures.add(message);
    }
}
