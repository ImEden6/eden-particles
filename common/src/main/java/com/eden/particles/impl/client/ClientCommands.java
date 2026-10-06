package com.eden.particles.impl.client;

import com.eden.particles.api.EdenParticles;
import com.eden.particles.api.EffectAnchor;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.LoadReport;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * {@code /eden_particles_client errors}, {@code play <effect>} and {@code bench <effect> [count]}. A separate root from the
 * server command, because Fabric resolves a shared root against the client tree only.
 */
public final class ClientCommands {

    private static final int MAX_LISTED = 10;
    private static final int DEFAULT_BENCH_COUNT = 25;
    private static final int MAX_BENCH_COUNT = EffectManager.MAX_ACTIVE / 2;

    private ClientCommands() {
    }

    /** Built over any source type, since Fabric and (Neo)Forge use different client command sources. */
    public static <S> LiteralArgumentBuilder<S> create(BiConsumer<S, Component> feedback) {
        return LiteralArgumentBuilder.<S>literal("eden_particles_client")
                .then(LiteralArgumentBuilder.<S>literal("errors").executes(context -> {
                    LoadReport report = ClientEffects.STORE.lastReport();
                    feedback.accept(context.getSource(), Component.literal(report.summary()));
                    for (int i = 0; i < Math.min(MAX_LISTED, report.errors().size()); i++) {
                        DefinitionError error = report.errors().get(i);
                        feedback.accept(context.getSource(), Component.literal(error.toString()).withStyle(ChatFormatting.RED));
                    }
                    if (report.errors().size() > MAX_LISTED) {
                        feedback.accept(context.getSource(), Component.literal("…and " + (report.errors().size() - MAX_LISTED)
                                + " more in the log").withStyle(ChatFormatting.GRAY));
                    }
                    return report.errors().size();
                }))
                .then(LiteralArgumentBuilder.<S>literal("play")
                        .then(RequiredArgumentBuilder.<S, ResourceLocation>argument("effect", new EffectIdArgument())
                                .executes(context -> {
                                    ResourceLocation effect = context.getArgument("effect", ResourceLocation.class);
                                    Minecraft minecraft = Minecraft.getInstance();
                                    if (minecraft.player == null || minecraft.level == null) return 0;
                                    if (!EdenParticles.isLoaded(effect)) {
                                        feedback.accept(context.getSource(), Component.literal("Unknown effect " + effect).withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    Vec3 look = minecraft.player.getLookAngle();
                                    Vec3 at = minecraft.player.getEyePosition().add(look.x * 4, -1.0, look.z * 4);
                                    EdenParticles.play(minecraft.level, effect, EffectAnchor.at(at));
                                    feedback.accept(context.getSource(), Component.literal("Playing " + effect));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("bench")
                        .then(RequiredArgumentBuilder.<S, ResourceLocation>argument("effect", new EffectIdArgument())
                                .executes(context -> bench(context.getSource(), context.getArgument("effect", ResourceLocation.class), DEFAULT_BENCH_COUNT, feedback))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, MAX_BENCH_COUNT))
                                        .executes(context -> bench(context.getSource(), context.getArgument("effect", ResourceLocation.class),
                                                context.getArgument("count", Integer.class), feedback)))));
    }

    private static <S> int bench(S source, ResourceLocation effect, int count, BiConsumer<S, Component> feedback) {
        if (!Bench.start(ClientEffects.MANAGER, effect, count, message -> feedback.accept(source, message))) {
            feedback.accept(source, Component.literal("Unknown effect " + effect).withStyle(ChatFormatting.RED));
            return 0;
        }
        feedback.accept(source, Component.literal("Benchmarking " + count + " × " + effect + " for " + Bench.TICKS + " ticks…"));
        return count;
    }
}
