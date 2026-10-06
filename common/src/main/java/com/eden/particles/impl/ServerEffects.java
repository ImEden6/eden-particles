package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Sends effects to clients. The server never simulates particles and doesn't know effect definitions. */
public final class ServerEffects {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private ServerEffects() {
    }

    /** @param recipients players the effect was sent to */
    public record Result(EffectHandle handle, int recipients) {
    }

    public static Result play(ServerLevel level, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        Vec3 at = anchor.position();
        double rangeSq = (double) params.range() * params.range();
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) <= rangeSq) targets.add(player);
        }
        return send(targets, effect, anchor, params, level);
    }

    public static Result send(List<ServerPlayer> players, ResourceLocation effect, EffectAnchor anchor,
                                    EffectParams params, ServerLevel level) {
        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (Services.NETWORK.canSend(player, PlayEffectPayload.TYPE)) recipients.add(player);
        }
        if (recipients.isEmpty()) return new Result(EffectHandle.NONE, 0);

        int id = NEXT_ID.getAndUpdate(i -> i == Integer.MAX_VALUE ? 0 : i + 1);
        long seed = params.seed().orElseGet(() -> level.getRandom().nextLong());
        Entity entity = anchor.entity().orElse(null);
        PlayEffectPayload payload = new PlayEffectPayload(id, effect, anchor.position(),
                entity != null ? entity.getId() : -1, anchor.offset(),
                params.scale(), params.yaw(), params.pitch(), params.roll(), params.tint(), seed);
        recipients.forEach(player -> Services.NETWORK.send(player, payload));
        return new Result(new Handle(id, recipients, entity), recipients.size());
    }

    /** Sends a stop for every server-started effect to these players. */
    public static void stopAll(List<ServerPlayer> players) {
        StopEffectPayload payload = new StopEffectPayload(StopEffectPayload.ALL);
        for (ServerPlayer player : players) {
            if (Services.NETWORK.canSend(player, StopEffectPayload.TYPE)) Services.NETWORK.send(player, payload);
        }
    }

    private static final class Handle implements EffectHandle {
        private final int id;
        private final List<ServerPlayer> recipients;
        private final Entity entity;
        private boolean stopped;

        Handle(int id, List<ServerPlayer> recipients, Entity entity) {
            this.id = id;
            this.recipients = recipients;
            this.entity = entity;
        }

        @Override
        public void stop() {
            if (stopped) return;
            stopped = true;
            StopEffectPayload payload = new StopEffectPayload(id);
            for (ServerPlayer player : recipients) {
                if (!player.hasDisconnected()) Services.NETWORK.send(player, payload);
            }
        }

        @Override
        public boolean isAlive() {
            return !stopped && (entity == null || !entity.isRemoved());
        }
    }
}
