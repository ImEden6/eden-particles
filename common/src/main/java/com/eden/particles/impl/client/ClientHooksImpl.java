package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.ClientHooks;
import com.eden.particles.impl.math.Rotations;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.PlayParams;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class ClientHooksImpl implements ClientHooks {

    private final EffectManager manager;
    private final Map<Integer, EffectInstance> fromServer = new HashMap<>();
    private final Set<ResourceLocation> warnedUnknown = new HashSet<>();

    ClientHooksImpl(EffectManager manager) {
        this.manager = manager;
        ClientEffects.STORE.onReload(report -> {
            fromServer.clear();
            warnedUnknown.clear();
        });
    }

    @Override
    public EffectHandle play(ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        Anchor runtimeAnchor = anchor.entity().map(entity -> follow(entity, anchor.offset())).orElseGet(() -> fixed(anchor.position()));
        long seed = params.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
        return start(effect, runtimeAnchor, toPlayParams(params.scale(), params.yaw(), params.pitch(), params.roll(), params.tint(), seed))
                .<EffectHandle>map(Handle::new).orElse(EffectHandle.NONE);
    }

    @Override
    public boolean isLoaded(ResourceLocation effect) {
        return manager.isLoaded(effect);
    }

    @Override
    public void onReload(Runnable listener) {
        ClientEffects.STORE.onReload(report -> listener.run());
    }

    @Override
    public void handle(PlayEffectPayload p) {
        fromServer.values().removeIf(EffectInstance::isFinished);
        Entity entity = p.entityId() >= 0 && Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getEntity(p.entityId()) : null;
        Anchor anchor = entity != null ? follow(entity, p.offset()) : fixed(p.position());
        start(p.effect(), anchor, toPlayParams(p.scale(), p.yaw(), p.pitch(), p.roll(), p.tint(), p.seed()))
                .ifPresent(instance -> fromServer.put(p.instanceId(), instance));
    }

    @Override
    public void handle(StopEffectPayload p) {
        if (p.instanceId() == StopEffectPayload.ALL) {
            fromServer.values().forEach(EffectInstance::stop);
            fromServer.clear();
        } else {
            EffectInstance instance = fromServer.remove(p.instanceId());
            if (instance != null) instance.stop();
        }
    }

    private Optional<EffectInstance> start(ResourceLocation effect, Anchor anchor, PlayParams params) {
        Optional<EffectInstance> instance = manager.play(effect, anchor, params);
        if (instance.isEmpty() && !manager.isLoaded(effect) && warnedUnknown.add(effect)) {
            Constants.LOG.warn("Unknown effect '{}'; is the resource pack that defines it loaded?", effect);
        }
        return instance;
    }

    private static PlayParams toPlayParams(float scale, float yaw, float pitch, float roll, int tint, long seed) {
        return new PlayParams(scale, Rotations.fromYawPitchRoll(yaw, pitch, roll), tint, seed);
    }

    private static Anchor fixed(Vec3 at) {
        return Anchor.fixed(at.x, at.y, at.z);
    }

    private static Anchor follow(Entity entity, Vec3 offset) {
        return out -> {
            if (entity.isRemoved()) return false;
            out.set(entity.getX() + offset.x, entity.getY() + offset.y, entity.getZ() + offset.z);
            return true;
        };
    }

    private record Handle(EffectInstance instance) implements EffectHandle {
        @Override
        public void stop() {
            instance.stop();
        }

        @Override
        public boolean isAlive() {
            return !instance.isFinished();
        }
    }
}
