package com.eden.particles.impl.client;

import com.eden.particles.api.EdenParticles;
import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.InputArgs;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.DefinitionFiles;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.LoadReport;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * {@code /eden_particles_client errors}, {@code play}, {@code preview}, {@code reload} and {@code bench}. A separate root from the
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
                                .executes(context -> play(context.getSource(), context.getArgument("effect", ResourceLocation.class), "", feedback))
                                .then(RequiredArgumentBuilder.<S, String>argument("inputs", StringArgumentType.greedyString())
                                        .executes(context -> play(context.getSource(), context.getArgument("effect", ResourceLocation.class),
                                                context.getArgument("inputs", String.class), feedback)))))
                .then(LiteralArgumentBuilder.<S>literal("preview")
                        .then(LiteralArgumentBuilder.<S>literal("stop").executes(context -> {
                            feedback.accept(context.getSource(), Component.literal(Preview.stop() ? "Preview stopped" : "Nothing is previewing"));
                            return 1;
                        }))
                        .then(RequiredArgumentBuilder.<S, ResourceLocation>argument("effect", new EffectIdArgument())
                                .executes(context -> preview(context.getSource(), context.getArgument("effect", ResourceLocation.class), "", feedback))
                                .then(RequiredArgumentBuilder.<S, String>argument("inputs", StringArgumentType.greedyString())
                                        .executes(context -> preview(context.getSource(), context.getArgument("effect", ResourceLocation.class),
                                                context.getArgument("inputs", String.class), feedback)))))
                .then(LiteralArgumentBuilder.<S>literal("reload").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    LoadReport report = ClientEffects.STORE.replace(DefinitionStore.Layer.ASSETS,
                            DefinitionFiles.read(minecraft.getResourceManager()).parse());
                    ClientEffects.report(report);
                    ChatFormatting colour = report.errors().isEmpty() ? ChatFormatting.WHITE : ChatFormatting.RED;
                    feedback.accept(context.getSource(), Component.literal(report.summary()).withStyle(colour));
                    return report.errors().size();
                }))
                .then(LiteralArgumentBuilder.<S>literal("bench")
                        .then(RequiredArgumentBuilder.<S, ResourceLocation>argument("effect", new EffectIdArgument())
                                .executes(context -> bench(context.getSource(), context.getArgument("effect", ResourceLocation.class), DEFAULT_BENCH_COUNT, feedback))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(1, MAX_BENCH_COUNT))
                                        .executes(context -> bench(context.getSource(), context.getArgument("effect", ResourceLocation.class),
                                                context.getArgument("count", Integer.class), feedback)))));
    }

    private static <S> EffectParams params(S source, ResourceLocation effect, String inputs, BiConsumer<S, Component> feedback) {
        if (!EdenParticles.isLoaded(effect)) {
            feedback.accept(source, Component.literal("Unknown effect " + effect).withStyle(ChatFormatting.RED));
            return null;
        }
        try {
            return InputArgs.apply(inputs, EffectParams.builder()).build();
        } catch (IllegalArgumentException e) {
            feedback.accept(source, Component.literal("Bad inputs: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return null;
        }
    }

    private static <S> int preview(S source, ResourceLocation effect, String inputs, BiConsumer<S, Component> feedback) {
        EffectParams params = params(source, effect, inputs, feedback);
        if (params == null) return 0;
        Preview.start(effect, params);
        feedback.accept(source, Component.literal("Previewing " + effect + ". Edit it, run reload, and it replays with the changes."));
        return 1;
    }

    private static <S> int play(S source, ResourceLocation effect, String inputs, BiConsumer<S, Component> feedback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return 0;
        EffectParams params = params(source, effect, inputs, feedback);
        if (params == null) return 0;
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 at = minecraft.player.getEyePosition().add(look.x * 4, -1.0, look.z * 4);
        EdenParticles.play(minecraft.level, effect, EffectAnchor.at(at), params);
        feedback.accept(source, Component.literal("Playing " + effect));
        return 1;
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
