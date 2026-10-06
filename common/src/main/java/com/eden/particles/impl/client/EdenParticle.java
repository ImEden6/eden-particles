package com.eden.particles.impl.client;

import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.runtime.BakedTemplate;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Follows its template's size, alpha, colour and speed curves over its life. */
public final class EdenParticle extends TextureSheetParticle {

    private final BakedTemplate template;
    private final float scale;
    private final float tintR, tintG, tintB;
    private final boolean emissive;
    private final ParticleRenderType renderType;

    EdenParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                 BakedTemplate template, RenderMode.Sprite render, int lifetime, float scale, int tint, TextureAtlasSprite sprite) {
        // The 4-argument constructor; the 7-argument one randomizes the velocity.
        super(level, x, y, z);
        this.template = template;
        this.scale = scale;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = lifetime;
        this.gravity = template.physics().gravity() * scale;
        this.friction = template.physics().drag();
        this.hasPhysics = template.physics().collide();
        this.tintR = ((tint >> 16) & 0xFF) / 255f;
        this.tintG = ((tint >> 8) & 0xFF) / 255f;
        this.tintB = (tint & 0xFF) / 255f;
        this.emissive = render.emissive();
        this.renderType = render.blend() == RenderMode.Blend.ADDITIVE ? EdenRenderTypes.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        setSprite(sprite);
    }

    /** Vanilla's tick, except displacement is scaled by the speed curve rather than stored into velocity. */
    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= 0.04 * gravity;
        float s = template.speed().sample(age / (float) lifetime);
        move(xd * s, yd * s, zd * s);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        if (onGround) {
            xd *= 0.7;
            zd *= 0.7;
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float t = lifeFraction(partialTicks);
        int rgb = template.color().sample(t);
        rCol = ((rgb >> 16) & 0xFF) / 255f * tintR;
        gCol = ((rgb >> 8) & 0xFF) / 255f * tintG;
        bCol = (rgb & 0xFF) / 255f * tintB;
        alpha = Math.max(0f, Math.min(1f, template.alpha().sample(t)));
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return Math.max(0f, template.size().sample(lifeFraction(partialTicks))) * scale;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return emissive ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return renderType;
    }

    private float lifeFraction(float partialTicks) {
        return Math.min(1f, (age + partialTicks) / lifetime);
    }
}
