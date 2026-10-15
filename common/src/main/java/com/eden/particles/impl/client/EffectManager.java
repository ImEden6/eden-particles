package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.client.light.DynamicLights;
import com.eden.particles.impl.definition.Trigger;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.math.Rotations;
import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.BakedEffect;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.PlayParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ticks playing effects on the client and applies the quality setting, distance fade and spawn budget. Sounds aren't thinned. */
public final class EffectManager {

    static final int MAX_ACTIVE = 512;
    static final int SPAWN_BUDGET_PER_TICK = 2000;
    /** Effects that particles may start per tick when they die or land. */
    static final int MAX_TRIGGERED_PER_TICK = 64;
    /** Vanilla keeps 1/10 × 2/3 of {@code decreased}-flagged particles on Minimal. */
    private static final float IMPORTANT_ON_MINIMAL = 1f / 15f;

    private final List<EffectInstance> active = new ArrayList<>();
    private final ClientImages images = new ClientImages(this::onImagesChanged);
    private final ClientSpawnSink sink = new ClientSpawnSink(images);
    private final ClientSoundSink sounds = new ClientSoundSink();
    private Map<ResourceLocation, BakedEffect> baked = Map.of();
    private ClientLevel level;
    private int attemptsLastTick;
    private int triggeredThisTick;
    private boolean warnedFull;
    Bench bench;

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
        instance.setHost((child, childAnchor, childParams) -> play(child, childAnchor, childParams).orElse(null));
        instance.setSounds(sounds);
        instance.setLights(DynamicLights.INSTANCE);
        active.add(instance);
        return Optional.of(instance);
    }

    /** Plays a particle's {@code on_death} or {@code on_land} effect where it is. */
    void trigger(Trigger trigger, double x, double y, double z, double vx, double vy, double vz,
                 float scale, int tint, float[] inputs, long seed) {
        if (triggeredThisTick >= MAX_TRIGGERED_PER_TICK) return;
        Quaternionf facing = trigger.aim() == Trigger.Aim.VELOCITY ? Rotations.lookAt(vx, vy, vz, new Quaternionf()) : new Quaternionf();
        play(trigger.effect(), Anchor.fixed(x, y, z), new PlayParams(scale, facing, tint, seed)).ifPresent(instance -> {
            triggeredThisTick++;
            int i = 0;
            for (String name : trigger.inputs().keySet()) instance.setInput(name, inputs[i++]);
        });
    }

    ClientImages images() {
        return images;
    }

    private void onImagesChanged() {
        sink.clearCaches();
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
        long start = System.nanoTime();
        triggeredThisTick = 0;
        sink.beginTick();
        sounds.beginTick();
        attemptsLastTick = active.isEmpty() ? 0 : tickActive(minecraft);
        int dropped = sink.drainDropped();
        if (bench != null && !bench.record(System.nanoTime() - start, attemptsLastTick, dropped)) {
            bench = null;
        }
    }

    private int tickActive(Minecraft minecraft) {
        ParticleStatus status = minecraft.options.particles().get();
        float quality = switch (status) {
            case ALL -> 1f;
            case DECREASED -> 2f / 3f;
            case MINIMAL -> 0f;
        };
        float importantQuality = status == ParticleStatus.MINIMAL ? IMPORTANT_ON_MINIMAL : quality;
        // Spawn budget, tightened as the live queues near vanilla's eviction limit so effects thin out evenly.
        int allowed = Math.min(SPAWN_BUDGET_PER_TICK, Math.max(0, sink.headroom()));
        float budget = Math.min(1f, allowed / (float) Math.max(1, attemptsLastTick));
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();

        int attempts = 0;
        // By index: sub-effects started during this loop are appended and run this same tick, after their parent.
        for (int i = 0; i < active.size(); i++) {
            EffectInstance instance = active.get(i);
            if (!instance.prepare()) continue;
            float fade = distanceFade(instance, camera);
            attempts += instance.tick(sink, quality * fade * budget, importantQuality * fade * budget);
        }
        active.removeIf(EffectInstance::isFinished);
        return attempts;
    }

    void clear() {
        active.forEach(EffectInstance::stop);
        active.clear();
        warnedFull = false;
        bench = null;
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
        // Playing effects keep their old definitions until they end; stopping them would lose server-started
        // persistent effects, which the server won't send again.
        sink.clearCaches();
        sounds.clearCaches();
    }
}
