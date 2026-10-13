package com.eden.particles.impl.client;

import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.SpriteRef;
import com.eden.particles.impl.definition.Trigger;
import com.eden.particles.impl.runtime.BakedTemplate;
import com.eden.particles.impl.runtime.EffectFrame;
import com.eden.particles.impl.runtime.ParticleForces;
import com.eden.particles.impl.runtime.Triggers;
import com.eden.particles.impl.runtime.TemplateValues;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;

/** Follows its template's size, alpha, colour, speed and sprite-frame curves over its life, and its forces. */
public final class EdenParticle extends TextureSheetParticle {

    private final BakedTemplate template;
    private final SpriteRef spriteRef;
    private final ResolvedSprite resolved;
    private final float variant;
    private final float scale;
    private final float sizeScale;
    private final float alphaScale;
    private final float speedScale;
    private final float tintR, tintG, tintB;
    private final boolean emissive;
    private final ParticleRenderType renderType;
    private int frame;
    private final int tint;
    private final Trigger onDeath;
    private final Trigger onLand;
    private final float deathChance;
    private final float landChance;
    private final float[] deathInputs;
    private final float[] landInputs;
    private boolean landed;
    private final ParticleForces forces;

    EdenParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                 BakedTemplate template, RenderMode.Sprite render, ResolvedSprite resolved,
                 int lifetime, float scale, int tint, float variant, TemplateValues values, EffectFrame effectFrame) {
        // The 4-argument constructor; the 7-argument one randomizes the velocity.
        super(level, x, y, z);
        this.template = template;
        this.spriteRef = render.sprite();
        this.resolved = resolved;
        this.variant = variant;
        this.scale = scale;
        this.tint = tint;
        this.onDeath = template.onDeath().orElse(null);
        this.onLand = template.onLand().orElse(null);
        this.deathChance = values.deathChance;
        this.landChance = values.landChance;
        this.deathInputs = values.deathInputs;
        this.landInputs = values.landInputs;
        this.sizeScale = values.size;
        this.alphaScale = values.alpha;
        this.speedScale = values.speed;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = lifetime;
        this.gravity = values.gravity * scale;
        this.friction = values.drag;
        this.hasPhysics = template.physics().collide();
        this.forces = template.forces().isEmpty() ? null : new ParticleForces(template.forces(), values.forces, effectFrame, scale);
        this.tintR = ((tint >> 16) & 0xFF) / 255f;
        this.tintG = ((tint >> 8) & 0xFF) / 255f;
        this.tintB = (tint & 0xFF) / 255f;
        this.emissive = render.emissive();
        this.renderType = EdenRenderTypes.of(resolved.synced(), render.blend());
        setSprite(resolved.first());
        this.frame = spriteRef.frameAt(0, lifetime, variant);
    }

    /** Vanilla's tick, except displacement is scaled by the speed curve rather than stored into velocity. */
    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            die();
            return;
        }
        frame = spriteRef.frameAt(age, lifetime, variant);
        yd -= 0.04 * gravity;
        if (forces != null) {
            if (!forces.accelerate(x, y, z, age, age / (float) lifetime)) {
                die();
                return;
            }
            xd += forces.ax;
            yd += forces.ay;
            zd += forces.az;
        }
        float s = template.speed().curve().sample(age / (float) lifetime) * speedScale;
        move(xd * s, yd * s, zd * s);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        if (onGround) {
            if (onLand != null && !landed) {
                landed = true;
                fire(onLand, Triggers.LAND_SALT, landChance, landInputs);
            }
            xd *= 0.7;
            zd *= 0.7;
        }
    }

    private void die() {
        if (onDeath != null) fire(onDeath, Triggers.DEATH_SALT, deathChance, deathInputs);
        remove();
    }

    private void fire(Trigger trigger, long salt, float chance, float[] inputs) {
        if (Triggers.roll(variant, salt, chance)) {
            ClientEffects.MANAGER.trigger(trigger, x, y, z, xd, yd, zd, scale, tint, inputs, Triggers.seed(variant, salt));
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float t = lifeFraction(partialTicks);
        int rgb = template.color().sample(t);
        rCol = ((rgb >> 16) & 0xFF) / 255f * tintR;
        gCol = ((rgb >> 8) & 0xFF) / 255f * tintG;
        bCol = (rgb & 0xFF) / 255f * tintB;
        alpha = Math.max(0f, Math.min(1f, template.alpha().curve().sample(t) * alphaScale));
        super.render(buffer, camera, partialTicks);
    }

    @Override
    protected float getU0() {
        return resolved.uvs()[frame * 4];
    }

    @Override
    protected float getU1() {
        return resolved.uvs()[frame * 4 + 1];
    }

    @Override
    protected float getV0() {
        return resolved.uvs()[frame * 4 + 2];
    }

    @Override
    protected float getV1() {
        return resolved.uvs()[frame * 4 + 3];
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return Math.max(0f, template.size().curve().sample(lifeFraction(partialTicks)) * sizeScale) * scale;
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
