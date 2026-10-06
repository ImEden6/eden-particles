package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.BakedEffect;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.PlayParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ticks playing effects on the client and applies the quality setting, distance fade and spawn budget. */
public final class EffectManager {

    static final int MAX_ACTIVE = 512;
    static final int SPAWN_BUDGET_PER_TICK = 2000;
    /** Vanilla keeps 1/10 × 2/3 of {@code decreased}-flagged particles on Minimal. */
    private static final float IMPORTANT_ON_MINIMAL = 1f / 15f;

    private final List<EffectInstance> active = new ArrayList<>();
    private final ClientSpawnSink sink = new ClientSpawnSink();
    private Map<ResourceLocation, BakedEffect> baked = Map.of();
    private ClientLevel level;
    private int attemptsLastTick;
    private boolean warnedFull;

    EffectManager(DefinitionStore store) {
        store.onReload(report -> rebake(store));
    }

    public Optional<EffectInstance> play(ResourceLocation id, Anchor anchor, PlayParams params) {
        BakedEffect effect = baked.get(id);
        if (effect == null) return Optional.empty();
        if (active.size() >= MAX_ACTIVE) {
            if (!warnedFull) {
                Constants.LOG.warn("{} effects are already playing; ignoring new ones until some finish", MAX_ACTIVE);
                warnedFull = true;
            }
            return Optional.empty();
        }
        EffectInstance instance = new EffectInstance(effect, anchor, params);
        active.add(instance);
        return Optional.of(instance);
    }

    public boolean isLoaded(ResourceLocation id) {
        return baked.containsKey(id);
    }

    public int activeCount() {
        return active.size();
    }

    void tick(Minecraft minecraft) {
        if (minecraft.level != level) {
            clear();
            level = minecraft.level;
        }
        if (level == null || minecraft.isPaused()) return;
        Demo.tick(minecraft, this);
        if (active.isEmpty()) {
            attemptsLastTick = 0;
            return;
        }

        ParticleStatus status = minecraft.options.particles().get();
        float quality = switch (status) {
            case ALL -> 1f;
            case DECREASED -> 2f / 3f;
            case MINIMAL -> 0f;
        };
        float importantQuality = status == ParticleStatus.MINIMAL ? IMPORTANT_ON_MINIMAL : quality;
        float budget = Math.min(1f, SPAWN_BUDGET_PER_TICK / (float) Math.max(1, attemptsLastTick));
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();

        int attempts = 0;
        for (Iterator<EffectInstance> it = active.iterator(); it.hasNext(); ) {
            EffectInstance instance = it.next();
            if (!instance.prepare()) {
                it.remove();
                continue;
            }
            float fade = distanceFade(instance, camera);
            attempts += instance.tick(sink, quality * fade * budget, importantQuality * fade * budget);
            if (instance.isFinished()) it.remove();
        }
        attemptsLastTick = attempts;
    }

    void clear() {
        active.clear();
        warnedFull = false;
    }

    /** Density fades from full at 3/8 of the effect's range to none at 3/4 of it. */
    private static float distanceFade(EffectInstance instance, Vec3 camera) {
        float range = instance.effect().definition().range();
        double dx = instance.position().x() - camera.x;
        double dy = instance.position().y() - camera.y;
        double dz = instance.position().z() - camera.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float start = range * 0.375f;
        float end = range * 0.75f;
        return Math.max(0f, Math.min(1f, 1f - (distance - start) / (end - start)));
    }

    private void rebake(DefinitionStore store) {
        Map<ResourceLocation, BakedEffect> next = new HashMap<>();
        for (ResourceLocation id : store.effectIds()) {
            store.effect(id).ifPresent(linked -> next.put(id, BakedEffect.bake(linked)));
        }
        baked = Map.copyOf(next);
        clear();
        sink.clearCaches();
    }
}
