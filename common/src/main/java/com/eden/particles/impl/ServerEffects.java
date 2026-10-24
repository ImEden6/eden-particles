package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.net.AnchorData;
import com.eden.particles.impl.net.EffectState;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.UpdateEffectPayload;
import com.eden.particles.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends effects to clients. The server never simulates particles. Persistent effects, and effects whose duration
 * is known (from the datapack or {@link EffectParams#duration()}), are tracked and sent to players who come into
 * range while they play. Handle changes are sent once per tick.
 */
public final class ServerEffects {

    /** How often tracked effects look for newly arrived players. */
    static final int LATE_JOIN_INTERVAL = 10;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final List<Handle> TRACKED = new ArrayList<>();
    private static final Set<Handle> CHANGED = new LinkedHashSet<>();

    private ServerEffects() {
    }

    /** @param recipients players the effect was sent to */
    public record Result(EffectHandle handle, int recipients) {
    }

    public static Result play(ServerLevel level, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        Handle handle = new Handle(level, effect, anchor, params);
        handle.sendToNewcomers();
        OptionalInt duration = params.duration().isPresent() ? params.duration() : DatapackDefinitions.duration(effect);
        if (params.persistent() || duration.isPresent()) {
            if (!params.persistent()) handle.endTime = handle.startTime + duration.getAsInt();
            TRACKED.add(handle);
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

    /** Forgets every tracked effect in this level, so nobody arriving later is sent them. */
    public static void forgetTracked(ServerLevel level) {
        TRACKED.removeIf(handle -> {
            if (handle.level != level) return false;
            handle.stopped = true;
            return true;
        });
    }

    static int trackedCount() {
        return TRACKED.size();
    }

    static int changedCount() {
        return CHANGED.size();
    }

    /** Called every server tick by the loader glue. */
    public static void tick(MinecraftServer server) {
        if (SmokeTest.ENABLED) SmokeTest.tick(server);
        if (!CHANGED.isEmpty()) {
            for (Handle handle : CHANGED) {
                if (handle.level.getServer() == server) handle.sendUpdate();
            }
            CHANGED.clear();
        }
        if (TRACKED.isEmpty() || server.getTickCount() % LATE_JOIN_INTERVAL != 0) return;
        for (Iterator<Handle> it = TRACKED.iterator(); it.hasNext(); ) {
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
        private final float range;
        private final long seed;
        private final long startTime;
        private long endTime = Long.MAX_VALUE;
        private final List<Entity> entities = new ArrayList<>(2);
        private final Set<UUID> recipients = new HashSet<>();
        private EffectState state;
        private boolean stopped;

        Handle(ServerLevel level, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
            this.level = level;
            this.effect = effect;
            this.anchor = anchor;
            this.range = params.range();
            this.state = EffectState.of(params);
            this.seed = params.seed().orElseGet(() -> level.getRandom().nextLong());
            this.startTime = level.getGameTime();
            anchor.entity().ifPresent(entities::add);
            anchor.target().flatMap(EffectAnchor::entity).ifPresent(entities::add);
        }

        /** Sends to players in range who don't have it yet; forgets players who left the level so they get it again. */
        void sendToNewcomers() {
            recipients.removeIf(uuid -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                return player == null || player.serverLevel() != level;
            });
            double rangeSq = (double) range * range;
            for (ServerPlayer player : level.players()) {
                if (!recipients.contains(player.getUUID()) && inRange(player, rangeSq)) {
                    sendTo(player);
                }
            }
        }

        private boolean inRange(ServerPlayer player, double rangeSq) {
            if (player.distanceToSqr(anchor.position()) <= rangeSq) return true;
            return anchor.target().map(to -> player.distanceToSqr(to.position()) <= rangeSq).orElse(false);
        }

        void sendTo(ServerPlayer player) {
            if (!Services.NETWORK.canSend(player, PlayEffectPayload.TYPE)) return;
            int elapsed = (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - startTime);
            Services.NETWORK.send(player, new PlayEffectPayload(id, effect, AnchorData.of(anchor.start()),
                    anchor.target().map(AnchorData::of), state, seed, elapsed));
            recipients.add(player.getUUID());
        }

        void sendUpdate() {
            if (stopped) return;
            UpdateEffectPayload payload = new UpdateEffectPayload(id, state);
            for (UUID uuid : recipients) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) Services.NETWORK.send(player, payload);
            }
        }

        private void changed(EffectState next) {
            if (stopped || next.equals(state)) return;
            state = next;
            CHANGED.add(this);
        }

        @Override
        public void setInput(String name, float value) {
            ParamChecks.input(name, value);
            changed(state.withInput(name, value));
        }

        @Override
        public void setTint(int rgb) {
            changed(state.withTint(rgb & 0xFFFFFF));
        }

        @Override
        public void setScale(float scale) {
            ParamChecks.scale(scale);
            changed(state.withScale(scale));
        }

        @Override
        public void setRotation(float yaw, float pitch, float roll) {
            ParamChecks.rotation(yaw, pitch, roll);
            changed(state.withRotation(yaw, pitch, roll));
        }

        @Override
        public void stop() {
            if (stopped) return;
            stopped = true;
            TRACKED.remove(this);
            CHANGED.remove(this);
            StopEffectPayload payload = new StopEffectPayload(id);
            for (UUID uuid : recipients) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) Services.NETWORK.send(player, payload);
            }
        }

        @Override
        public boolean isAlive() {
            if (stopped || level.getGameTime() >= endTime) return false;
            // Players aren't removed when they change dimension, so check the level too.
            for (Entity entity : entities) {
                if (entity.isRemoved() || entity.level() != level) return false;
            }
            return true;
        }
    }
}
