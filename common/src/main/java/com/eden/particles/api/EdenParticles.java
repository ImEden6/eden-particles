package com.eden.particles.api;

import com.eden.particles.impl.ServerEffects;
import com.eden.particles.impl.Sides;
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
}
