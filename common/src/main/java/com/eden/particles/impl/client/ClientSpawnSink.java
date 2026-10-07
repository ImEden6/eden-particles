package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.runtime.BakedTemplate;
import com.eden.particles.impl.runtime.SpawnSink;
import com.eden.particles.mixin.ParticleAccessor;
import com.eden.particles.mixin.ParticleEngineAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

final class ClientSpawnSink implements SpawnSink {

    /** Vanilla evicts the oldest particle past 16,384 per render type; stay below, leaving room for others' particles. */
    static final int LIVE_LIMIT = 15_000;

    private final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
    private final Set<ResourceLocation> warnedMissing = new HashSet<>();
    /** Added via {@code ParticleEngine.add} this tick; they only reach the live queues on vanilla's next tick. */
    private final Map<ParticleRenderType, Integer> pending = new IdentityHashMap<>();
    private int dropped;

    @Override
    public void spawn(BakedTemplate template, double x, double y, double z, double vx, double vy, double vz,
                      int lifetime, float scale, int tint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        switch (template.render()) {
            case RenderMode.Sprite sprite -> {
                if (!admit(sprite.blend() == RenderMode.Blend.ADDITIVE ? EdenRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT)) return;
                minecraft.particleEngine.add(new EdenParticle(minecraft.level, x, y, z, vx, vy, vz,
                        template, sprite, lifetime, scale, tint, sprite(sprite.sprite())));
            }
            case RenderMode.Vanilla vanilla -> {
                // The linker only admits simple particle types.
                SimpleParticleType type = (SimpleParticleType) BuiltInRegistries.PARTICLE_TYPE.get(vanilla.particle());
                Particle particle = engine().eden_particles$makeParticle(type, x, y, z, vx, vy, vz);
                if (particle == null || !admit(particle.getRenderType())) return;
                particle.setLifetime(lifetime);
                if (scale != 1f) particle.scale(scale);
                ParticleAccessor accessor = (ParticleAccessor) particle;
                accessor.eden_particles$setGravity(template.physics().gravity() * scale);
                accessor.eden_particles$setFriction(template.physics().drag());
                accessor.eden_particles$setHasPhysics(template.physics().collide());
                minecraft.particleEngine.add(particle);
            }
        }
    }

    void beginTick() {
        pending.clear();
    }

    /** Smallest remaining room across the render types Eden sprites use. */
    int headroom() {
        return Math.min(LIVE_LIMIT - live(EdenRenderTypes.ADDITIVE), LIVE_LIMIT - live(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT));
    }

    int drainDropped() {
        int count = dropped;
        dropped = 0;
        return count;
    }

    void clearCaches() {
        sprites.clear();
        warnedMissing.clear();
    }

    private boolean admit(ParticleRenderType type) {
        if (live(type) >= LIVE_LIMIT) {
            dropped++;
            return false;
        }
        pending.merge(type, 1, Integer::sum);
        return true;
    }

    private int live(ParticleRenderType type) {
        Queue<Particle> queue = engine().eden_particles$getParticles().get(type);
        return (queue == null ? 0 : queue.size()) + pending.getOrDefault(type, 0);
    }

    private static ParticleEngineAccessor engine() {
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        return (ParticleEngineAccessor) engine;
    }

    private TextureAtlasSprite sprite(ResourceLocation id) {
        return sprites.computeIfAbsent(id, key -> {
            TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            TextureAtlasSprite sprite = atlas.getSprite(key);
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) && warnedMissing.add(key)) {
                Constants.LOG.warn("Missing particle sprite '{}'; expected assets/{}/textures/particle/{}.png",
                        key, key.getNamespace(), key.getPath());
            }
            return sprite;
        });
    }
}
