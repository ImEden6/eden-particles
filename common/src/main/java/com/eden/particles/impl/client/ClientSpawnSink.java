package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.SpriteRef;
import com.eden.particles.impl.runtime.BakedTemplate;
import com.eden.particles.impl.runtime.SpawnSink;
import com.eden.particles.impl.runtime.TemplateValues;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

final class ClientSpawnSink implements SpawnSink {

    /** Vanilla evicts the oldest particle past 16,384 per render type; stay below, leaving room for others' particles. */
    static final int LIVE_LIMIT = 15_000;

    private static final List<ParticleRenderType> CAPPED = List.of(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
            EdenRenderTypes.ADDITIVE, EdenRenderTypes.SYNCED_TRANSLUCENT, EdenRenderTypes.SYNCED_ADDITIVE);

    private final ClientImages images;
    private final Map<SpriteRef, Optional<ResolvedSprite>> resolved = new HashMap<>();
    private final Set<ResourceLocation> warnedMissing = new HashSet<>();
    private final Set<SpriteRef> warnedMixed = new HashSet<>();
    /** Added via {@code ParticleEngine.add} this tick; they only reach the live queues on vanilla's next tick. */
    private final Map<ParticleRenderType, Integer> pending = new IdentityHashMap<>();
    private int dropped;

    ClientSpawnSink(ClientImages images) {
        this.images = images;
    }

    @Override
    public void spawn(BakedTemplate template, double x, double y, double z, double vx, double vy, double vz,
                      int lifetime, float scale, int tint, float variant, TemplateValues values) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        switch (template.render()) {
            case RenderMode.Sprite sprite -> {
                // Empty while a server image is declined or hasn't arrived: skip rather than draw a placeholder.
                Optional<ResolvedSprite> frames = resolve(sprite.sprite());
                if (frames.isEmpty() || !admit(EdenRenderTypes.of(frames.get().synced(), sprite.blend()))) return;
                minecraft.particleEngine.add(new EdenParticle(minecraft.level, x, y, z, vx, vy, vz,
                        template, sprite, frames.get(), lifetime, scale, tint, variant, values));
            }
            case RenderMode.Vanilla vanilla -> {
                // The linker only admits simple particle types.
                SimpleParticleType type = (SimpleParticleType) BuiltInRegistries.PARTICLE_TYPE.get(vanilla.particle());
                Particle particle = engine().eden_particles$makeParticle(type, x, y, z, vx, vy, vz);
                if (particle == null || !admit(particle.getRenderType())) return;
                particle.setLifetime(lifetime);
                if (scale != 1f) particle.scale(scale);
                ParticleAccessor accessor = (ParticleAccessor) particle;
                accessor.eden_particles$setGravity(values.gravity * scale);
                accessor.eden_particles$setFriction(values.drag);
                accessor.eden_particles$setHasPhysics(template.physics().collide());
                minecraft.particleEngine.add(particle);
            }
        }
    }

    void beginTick() {
        pending.clear();
    }

    /** Smallest remaining room across the render types Eden spawns into. */
    int headroom() {
        int room = Integer.MAX_VALUE;
        for (ParticleRenderType type : CAPPED) {
            room = Math.min(room, LIVE_LIMIT - live(type));
        }
        return room;
    }

    int drainDropped() {
        int count = dropped;
        dropped = 0;
        return count;
    }

    void clearCaches() {
        resolved.clear();
        warnedMissing.clear();
        warnedMixed.clear();
    }

    private Optional<ResolvedSprite> resolve(SpriteRef ref) {
        Optional<ResolvedSprite> cached = resolved.get(ref);
        if (cached != null) return cached;
        Optional<ResolvedSprite> result = build(ref);
        resolved.put(ref, result);
        return result;
    }

    private Optional<ResolvedSprite> build(SpriteRef ref) {
        for (ResourceLocation id : ref.images()) {
            if (images.isWithheld(id)) return Optional.empty();
        }
        boolean synced = images.atlas().has(ref.images().get(0));
        return Optional.of(switch (ref.source()) {
            case SpriteRef.Source.Frames frames -> {
                float[] uvs = new float[frames.ids().size() * 4];
                TextureAtlasSprite first = null;
                for (int i = 0; i < frames.ids().size(); i++) {
                    ResourceLocation id = frames.ids().get(i);
                    if (images.atlas().has(id) != synced && warnedMixed.add(ref)) {
                        Constants.LOG.warn("Sprite frames {} mix server images and resource-pack sprites; frames from "
                                + "the other source show as missing", frames.ids());
                    }
                    TextureAtlasSprite sprite = synced ? images.atlas().sprite(id) : vanillaSprite(id);
                    if (first == null) first = sprite;
                    uvs[i * 4] = sprite.getU0();
                    uvs[i * 4 + 1] = sprite.getU1();
                    uvs[i * 4 + 2] = sprite.getV0();
                    uvs[i * 4 + 3] = sprite.getV1();
                }
                yield new ResolvedSprite(synced, first, uvs);
            }
            case SpriteRef.Source.Sheet sheet -> {
                TextureAtlasSprite sprite = synced ? images.atlas().sprite(sheet.id()) : vanillaSprite(sheet.id());
                float du = (sprite.getU1() - sprite.getU0()) / sheet.columns();
                float dv = (sprite.getV1() - sprite.getV0()) / sheet.rows();
                float[] uvs = new float[sheet.count() * 4];
                for (int i = 0; i < sheet.count(); i++) {
                    int column = i % sheet.columns();
                    int row = i / sheet.columns();
                    uvs[i * 4] = sprite.getU0() + du * column;
                    uvs[i * 4 + 1] = sprite.getU0() + du * (column + 1);
                    uvs[i * 4 + 2] = sprite.getV0() + dv * row;
                    uvs[i * 4 + 3] = sprite.getV0() + dv * (row + 1);
                }
                yield new ResolvedSprite(synced, sprite, uvs);
            }
        });
    }

    private TextureAtlasSprite vanillaSprite(ResourceLocation id) {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        TextureAtlasSprite sprite = atlas.getSprite(id);
        if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) && warnedMissing.add(id)) {
            Constants.LOG.warn("Missing particle sprite '{}'; expected assets/{}/textures/particle/{}.png or a server datapack image",
                    id, id.getNamespace(), id.getPath());
        }
        return sprite;
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
}
