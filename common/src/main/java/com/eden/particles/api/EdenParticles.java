package com.eden.particles.api;

import com.eden.particles.impl.ServerEffects;
import com.eden.particles.impl.Sides;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Plays effects defined in resource packs under {@code assets/<namespace>/eden_particles/effects/}.
 * <p>
 * Call {@link #play} from either side. On a client level the effect plays locally. On a server level it is sent to
 * players within {@link EffectParams#range()} of the anchor who have this mod installed.
 */
public final class EdenParticles {

    private EdenParticles() {
    }

    public static EffectHandle play(Level level, ResourceLocation effect, EffectAnchor anchor) {
        return play(level, effect, anchor, EffectParams.DEFAULT);
    }

    public static EffectHandle play(Level level, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        if (level.isClientSide()) {
            return Sides.client().map(client -> client.play(effect, anchor, params)).orElse(EffectHandle.NONE);
        }
        return ServerEffects.play((ServerLevel) level, effect, anchor, params).handle();
    }

    /** Sends the effect to one player only, regardless of range. */
    public static EffectHandle playFor(ServerPlayer player, ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        return ServerEffects.send(List.of(player), effect, anchor, params, player.serverLevel()).handle();
    }

    /** Client only: whether this client has the effect loaded. Always false on a dedicated server. */
    public static boolean isLoaded(ResourceLocation effect) {
        return Sides.client().map(client -> client.isLoaded(effect)).orElse(false);
    }

    /** Client only: runs after every resource reload that reloads effects. Ignored on a dedicated server. */
    public static void onReload(Runnable listener) {
        Sides.client().ifPresent(client -> client.onReload(listener));
    }

    /**
     * Client only: registers an effect from code, parsed like a resource-pack file and overriding one with the same id.
     * Every call relinks all effects, so register in batches. Returns this id's errors, empty if it loaded.
     */
    public static List<String> registerEffect(ResourceLocation id, JsonElement json) {
        return Sides.client().map(client -> client.registerEffect(id, json)).orElse(List.of());
    }

    /** Client only: like {@link #registerEffect}, for a template that effects reference by id. */
    public static List<String> registerTemplate(ResourceLocation id, JsonElement json) {
        return Sides.client().map(client -> client.registerTemplate(id, json)).orElse(List.of());
    }

    /** Client only: removes an effect or template registered from code. Returns whether anything was removed. */
    public static boolean unregister(ResourceLocation id) {
        return Sides.client().map(client -> client.unregister(id)).orElse(false);
    }
}
