package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectParams;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * {@code /eden_particles play <effect> [pos] [targets]} and {@code /eden_particles stop [targets]}. Effect ids aren't
 * suggested: they live in client resource packs, which the server can't see.
 */
public final class ServerCommands {

    private ServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eden_particles")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .executes(c -> play(c, c.getSource().getPosition(), null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), null))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), EntityArgument.getPlayers(c, "targets")))))))
                .then(Commands.literal("stop")
                        .executes(c -> stop(c, c.getSource().getLevel().players()))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> stop(c, EntityArgument.getPlayers(c, "targets"))))));
    }

    private static int play(CommandContext<CommandSourceStack> context, Vec3 pos, Collection<ServerPlayer> targets) {
        CommandSourceStack source = context.getSource();
        ResourceLocation effect = ResourceLocationArgument.getId(context, "effect");
        EffectAnchor anchor = EffectAnchor.at(pos);
        ServerEffects.Result result = targets == null
                ? ServerEffects.play(source.getLevel(), effect, anchor, EffectParams.DEFAULT)
                : ServerEffects.send(List.copyOf(targets), effect, anchor, EffectParams.DEFAULT, source.getLevel());
        if (result.recipients() == 0) {
            source.sendFailure(Component.literal(targets == null
                    ? "No players with Eden's Particles installed within " + (int) EffectParams.DEFAULT_RANGE + " blocks"
                    : "None of those players have Eden's Particles installed"));
            return 0;
        }
        int count = result.recipients();
        source.sendSuccess(() -> Component.literal("Playing " + effect + " for " + count + (count == 1 ? " player" : " players")), true);
        return count;
    }

    private static int stop(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        ServerEffects.stopAll(List.copyOf(targets));
        context.getSource().sendSuccess(() -> Component.literal("Stopped server-started effects for " + targets.size()
                + (targets.size() == 1 ? " player" : " players")), true);
        return targets.size();
    }
}
