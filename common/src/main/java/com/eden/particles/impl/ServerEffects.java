package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends effects to clients. The server never simulates particles and doesn't know effect definitions. Persistent
 * effects are remembered and re-sent to players who come into range later.
 */
public final class ServerEffects {

    /** How often persistent effects look for newly arrived players. */
    static final int LATE_JOIN_INTERVAL = 10;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final List<Handle> PERSISTENT = new ArrayList<>();

    private ServerEffects() {
    }

    /** @param recipients players the effect was sent to */
    public record Result(EffectHandle handle, int recipients) {
    }

    public static Result play(ServerLevel level, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        Handle handle = new Handle(level, effect, anchor, params);
        handle.sendToNewcomers();
        if (params.persistent()) {
            PERSISTENT.add(handle);
            return new Result(handle, handle.recipients.size());
        }
        return handle.recipients.isEmpty() ? new Result(EffectHandle.NONE, 0) : new Result(handle, handle.recipients.size());
    }

    /** Sends to exactly these players, once; {@link EffectParams#persistent()} is ignored. */
    public static Result send(List<ServerPlayer> players, ResourceLocation effect, EffectAnchor anchor,
                              EffectParams params, ServerLevel level) {
        Handle handle = new Handle(level, effect, anchor, params);
        players.forEach(handle::sendTo);
        return handle.recipients.isEmpty() ? new Result(EffectHandle.NONE, 0) : new Result(handle, handle.recipients.size());
    }

    /** Sends a stop for every server-started effect to these players. */
    public static void stopAll(List<ServerPlayer> players) {
        StopEffectPayload payload = new StopEffectPayload(StopEffectPayload.ALL);
        for (ServerPlayer player : players) {
            if (Services.NETWORK.canSend(player, StopEffectPayload.TYPE)) Services.NETWORK.send(player, payload);
        }
    }

    /** Forgets every persistent effect in this level, so nobody is re-sent them. */
    public static void forgetPersistent(ServerLevel level) {
        PERSISTENT.removeIf(handle -> {
            if (handle.level != level) return false;
            handle.stopped = true;
            return true;
        });
    }

    static int persistentCount() {
        return PERSISTENT.size();
    }

    /** Called every server tick by the loader glue. */
    public static void tick(MinecraftServer server) {
        if (SmokeTest.ENABLED) SmokeTest.tick(server);
        if (PERSISTENT.isEmpty() || server.getTickCount() % LATE_JOIN_INTERVAL != 0) return;
        for (Iterator<Handle> it = PERSISTENT.iterator(); it.hasNext(); ) {
            Handle handle = it.next();
            // Entries from a previous integrated-server session, or ended effects, are dropped.
            if (handle.level.getServer() != server || !handle.isAlive()) {
                it.remove();
                continue;
            }
            handle.sendToNewcomers();
        }
    }

    private static final class Handle implements EffectHandle {
        private final int id = NEXT_ID.getAndUpdate(i -> i == Integer.MAX_VALUE ? 0 : i + 1);
        private final ServerLevel level;
        private final ResourceLocation effect;
        private final EffectAnchor anchor;
        private final EffectParams params;
        private final long seed;
        private final long startTime;
        private final Entity entity;
        private final Set<UUID> recipients = new HashSet<>();
        private boolean stopped;

        Handle(ServerLevel level, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
            this.level = level;
            this.effect = effect;
            this.anchor = anchor;
            this.params = params;
            this.seed = params.seed().orElseGet(() -> level.getRandom().nextLong());
            this.startTime = level.getGameTime();
            this.entity = anchor.entity().orElse(null);
        }

        /** Sends to players in range who don't have it yet; forgets players who left the level so they get it again. */
        void sendToNewcomers() {
            recipients.removeIf(uuid -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                return player == null || player.serverLevel() != level;
            });
            double rangeSq = (double) params.range() * params.range();
            for (ServerPlayer player : level.players()) {
                if (!recipients.contains(player.getUUID()) && player.distanceToSqr(anchor.position()) <= rangeSq) {
                    sendTo(player);
                }
            }
        }

        void sendTo(ServerPlayer player) {
            if (!Services.NETWORK.canSend(player, PlayEffectPayload.TYPE)) return;
            int elapsed = (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - startTime);
            Services.NETWORK.send(player, new PlayEffectPayload(id, effect, anchor.position(),
                    entity != null ? entity.getId() : -1, anchor.offset(),
                    params.scale(), params.yaw(), params.pitch(), params.roll(), params.tint(), seed, elapsed));
            recipients.add(player.getUUID());
        }

        @Override
        public void stop() {
            if (stopped) return;
            stopped = true;
            PERSISTENT.remove(this);
            StopEffectPayload payload = new StopEffectPayload(id);
            for (UUID uuid : recipients) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) Services.NETWORK.send(player, payload);
            }
        }

        @Override
        public boolean isAlive() {
            // Players aren't removed when they change dimension, so check the level too.
            return !stopped && (entity == null || (!entity.isRemoved() && entity.level() == level));
        }
    }
}
