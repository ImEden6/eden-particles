package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.runtime.BakedTemplate;
import com.eden.particles.impl.runtime.SpawnSink;
import com.eden.particles.mixin.ParticleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ClientSpawnSink implements SpawnSink {

    private final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();
    private final Set<ResourceLocation> warnedMissing = new HashSet<>();

    @Override
    public void spawn(BakedTemplate template, double x, double y, double z, double vx, double vy, double vz,
                      int lifetime, float scale, int tint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        switch (template.render()) {
            case RenderMode.Sprite sprite -> minecraft.particleEngine.add(new EdenParticle(minecraft.level, x, y, z, vx, vy, vz,
                    template, sprite, lifetime, scale, tint, sprite(sprite.sprite())));
            case RenderMode.Vanilla vanilla -> {
                // The linker only admits simple particle types.
                SimpleParticleType type = (SimpleParticleType) BuiltInRegistries.PARTICLE_TYPE.get(vanilla.particle());
                Particle particle = minecraft.particleEngine.createParticle(type, x, y, z, vx, vy, vz);
                if (particle == null) return;
                particle.setLifetime(lifetime);
                if (scale != 1f) particle.scale(scale);
                ParticleAccessor accessor = (ParticleAccessor) particle;
                accessor.eden_particles$setGravity(template.physics().gravity() * scale);
                accessor.eden_particles$setFriction(template.physics().drag());
                accessor.eden_particles$setHasPhysics(template.physics().collide());
            }
        }
    }

    void clearCaches() {
        sprites.clear();
        warnedMissing.clear();
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
