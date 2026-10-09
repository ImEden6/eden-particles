package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectParams;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * {@code /eden_particles play <effect> [pos] [targets | persistent | with <inputs>]},
 * {@code beam <effect> <from> <to> [<inputs>]} and {@code stop [targets]}; a plain {@code stop} also forgets the
 * level's persistent effects. Inputs are written {@code power=2 spread=0.5}. Effect ids aren't suggested: they live in
 * client resource packs, which the server can't see.
 */
public final class ServerCommands {

    private ServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eden_particles")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .executes(c -> play(c, c.getSource().getPosition(), null, ""))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), null, ""))
                                        .then(Commands.literal("persistent")
                                                .executes(c -> playPersistent(c, Vec3Argument.getVec3(c, "pos"))))
                                        .then(Commands.literal("with")
                                                .then(Commands.argument("inputs", StringArgumentType.greedyString())
                                                        .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), null,
                                                                StringArgumentType.getString(c, "inputs")))))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(c -> play(c, Vec3Argument.getVec3(c, "pos"), EntityArgument.getPlayers(c, "targets"), ""))))))
                .then(Commands.literal("beam")
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .then(Commands.argument("from", EntityArgument.entity())
                                        .then(Commands.argument("to", EntityArgument.entity())
                                                .executes(c -> beam(c, ""))
                                                .then(Commands.argument("inputs", StringArgumentType.greedyString())
                                                        .executes(c -> beam(c, StringArgumentType.getString(c, "inputs"))))))))
                .then(Commands.literal("stop")
                        .executes(c -> {
                            ServerEffects.forgetPersistent(c.getSource().getLevel());
                            return stop(c, c.getSource().getLevel().players());
                        })
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> stop(c, EntityArgument.getPlayers(c, "targets"))))));
    }

    private static int play(CommandContext<CommandSourceStack> context, Vec3 pos, Collection<ServerPlayer> targets, String inputs) {
        CommandSourceStack source = context.getSource();
        ResourceLocation effect = ResourceLocationArgument.getId(context, "effect");
        EffectParams params = params(source, inputs);
        if (params == null) return 0;
        EffectAnchor anchor = EffectAnchor.at(pos);
        ServerEffects.Result result = targets == null
                ? ServerEffects.play(source.getLevel(), effect, anchor, params)
                : ServerEffects.send(List.copyOf(targets), effect, anchor, params, source.getLevel());
        return report(source, effect, result, targets == null);
    }

    private static int beam(CommandContext<CommandSourceStack> context, String inputs) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation effect = ResourceLocationArgument.getId(context, "effect");
        Entity from = EntityArgument.getEntity(context, "from");
        Entity to = EntityArgument.getEntity(context, "to");
        EffectParams params = params(source, inputs);
        if (params == null) return 0;
        EffectAnchor anchor = EffectAnchor.between(EffectAnchor.entity(from).eyes(), EffectAnchor.entity(to).eyes());
        return report(source, effect, ServerEffects.play(source.getLevel(), effect, anchor, params), true);
    }

    private static EffectParams params(CommandSourceStack source, String inputs) {
        try {
            return InputArgs.apply(inputs, EffectParams.builder()).build();
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Bad inputs: " + e.getMessage()));
            return null;
        }
    }

    private static int report(CommandSourceStack source, ResourceLocation effect, ServerEffects.Result result, boolean byRange) {
        if (result.recipients() == 0) {
            source.sendFailure(Component.literal(byRange
                    ? "No players with Eden's Particles installed within " + (int) EffectParams.DEFAULT_RANGE + " blocks"
                    : "None of those players have Eden's Particles installed"));
            return 0;
        }
        int count = result.recipients();
        source.sendSuccess(() -> Component.literal("Playing " + effect + " for " + count + (count == 1 ? " player" : " players")), true);
        return count;
    }

    private static int playPersistent(CommandContext<CommandSourceStack> context, Vec3 pos) {
        CommandSourceStack source = context.getSource();
        ResourceLocation effect = ResourceLocationArgument.getId(context, "effect");
        ServerEffects.Result result = ServerEffects.play(source.getLevel(), effect, EffectAnchor.at(pos),
                EffectParams.builder().persistent().build());
        int count = result.recipients();
        source.sendSuccess(() -> Component.literal("Playing " + effect + " persistently, for " + count + " player(s) in range now"
                + " and anyone who arrives later; /eden_particles stop ends it"), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        ServerEffects.stopAll(List.copyOf(targets));
        context.getSource().sendSuccess(() -> Component.literal("Stopped server-started effects for " + targets.size()
                + (targets.size() == 1 ? " player" : " players")), true);
        return targets.size();
    }
}
