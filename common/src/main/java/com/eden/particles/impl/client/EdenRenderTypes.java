package com.eden.particles.impl.client;

import com.eden.particles.impl.definition.RenderMode;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A render type decides blending and which atlas is bound, so each (atlas, blend) pair needs its own. Vanilla only
 * draws the types in {@code ParticleEngine.RENDER_ORDER}; NeoForge and Forge draw every type, and on Fabric a mixin
 * appends {@link #CUSTOM}.
 */
public final class EdenRenderTypes {

    /** Adds light ({@code src·α + dst}) without writing depth, so overlapping glows brighten instead of occluding. */
    public static final ParticleRenderType ADDITIVE = additive(TextureAtlas.LOCATION_PARTICLES, "EDEN_PARTICLES_ADDITIVE");
    public static final ParticleRenderType SYNCED_TRANSLUCENT = translucent(SyncedAtlas.LOCATION, "EDEN_PARTICLES_SYNCED_TRANSLUCENT");
    public static final ParticleRenderType SYNCED_ADDITIVE = additive(SyncedAtlas.LOCATION, "EDEN_PARTICLES_SYNCED_ADDITIVE");

    public static final List<ParticleRenderType> CUSTOM = List.of(ADDITIVE, SYNCED_TRANSLUCENT, SYNCED_ADDITIVE);

    private EdenRenderTypes() {
    }

    static ParticleRenderType of(boolean synced, RenderMode.Blend blend) {
        boolean additive = blend == RenderMode.Blend.ADDITIVE;
        if (synced) return additive ? SYNCED_ADDITIVE : SYNCED_TRANSLUCENT;
        return additive ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static ParticleRenderType additive(ResourceLocation texture, String name) {
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                RenderSystem.depthMask(false);
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /** Same state as vanilla's PARTICLE_SHEET_TRANSLUCENT, with a different atlas. */
    private static ParticleRenderType translucent(ResourceLocation texture, String name) {
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
                RenderSystem.depthMask(true);
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
