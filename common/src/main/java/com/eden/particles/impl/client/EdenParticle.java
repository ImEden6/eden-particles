package com.eden.particles.impl.client;

import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.SpriteRef;
import com.eden.particles.impl.definition.TrailSpec;
import com.eden.particles.impl.definition.Trigger;
import com.eden.particles.impl.math.RibbonMath;
import com.eden.particles.impl.math.TrailHistory;
import com.eden.particles.impl.runtime.BakedTemplate;
import com.eden.particles.impl.runtime.BakedTrail;
import com.eden.particles.impl.runtime.EffectFrame;
import com.eden.particles.impl.runtime.ParticleForces;
import com.eden.particles.impl.runtime.TemplateValues;
import com.eden.particles.impl.runtime.Triggers;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** Follows its template's size, alpha, colour, speed, sprite curves, forces, and ribbon trails. */
public final class EdenParticle extends TextureSheetParticle {

    private static final int MAX_SCRATCH_POINTS = 65;
    private static final float[] SCRATCH_PX = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_PY = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_PZ = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_TIMES = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_WIDTHS = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_LEFT_X = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_LEFT_Y = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_LEFT_Z = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_RIGHT_X = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_RIGHT_Y = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_RIGHT_Z = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_U = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_R = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_G = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_B = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_A = new float[MAX_SCRATCH_POINTS];
    private static final float[] SCRATCH_DIST = new float[MAX_SCRATCH_POINTS];

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
    private final boolean hasQuad;
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
    private boolean deathFired;
    private final ParticleForces forces;

    private final BakedTrail bakedTrail;
    private final TrailHistory trailHistory;
    private final ResolvedSprite trailResolved;
    private final SpriteRef trailSpriteRef;
    private int trailFrame;
    private final float trailWidthScale;
    private final float trailAlphaScale;
    private final boolean trailEmissive;

    EdenParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                 BakedTemplate template, RenderMode.Custom render, ResolvedSprite resolved, ResolvedSprite trailResolved,
                 int lifetime, float scale, int tint, float variant, TemplateValues values, EffectFrame effectFrame) {
        super(level, x, y, z);
        this.template = template;
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
        this.spriteRef = render.sprite();
        this.emissive = render.emissive();
        this.hasQuad = !(render instanceof RenderMode.Ribbon);
        RenderMode.Blend blend = !hasQuad && template.trail().flatMap(BakedTrail::blend).isPresent()
                ? template.trail().get().blend().get()
                : render.blend();
        boolean synced = !hasQuad && trailResolved != null ? trailResolved.synced() : resolved.synced();
        this.renderType = EdenRenderTypes.of(synced, blend);

        setSprite(resolved.first());
        this.frame = spriteRef.frameAt(0, lifetime, variant);

        if (template.trail().isPresent()) {
            this.bakedTrail = template.trail().get();
            this.trailHistory = new TrailHistory(bakedTrail.maxPoints(), bakedTrail.minDistance());
            this.trailHistory.record((float) x, (float) y, (float) z, 0f);
            this.trailResolved = trailResolved != null ? trailResolved : resolved;
            this.trailWidthScale = values.trailWidth;
            this.trailAlphaScale = values.trailAlpha;
            this.trailEmissive = bakedTrail.emissive().orElse(render.emissive());
            this.trailSpriteRef = this.trailResolved == resolved ? spriteRef : bakedTrail.sprite().orElse(spriteRef);
            this.trailFrame = trailSpriteRef.frameAt(0, lifetime, variant);
        } else {
            this.bakedTrail = null;
            this.trailHistory = null;
            this.trailResolved = null;
            this.trailSpriteRef = null;
            this.trailWidthScale = 1f;
            this.trailAlphaScale = 1f;
            this.trailEmissive = false;
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;

        if (age++ >= lifetime) {
            checkDeath();
            if (trailHistory != null && trailHistory.size() > 1) {
                trailHistory.record((float) x, (float) y, (float) z, (float) (age - 1));
                trailHistory.pruneOlderThan(age, bakedTrail.lifetime());
                if (trailHistory.size() <= 1) {
                    remove();
                }
                return;
            }
            remove();
            return;
        }

        frame = spriteRef.frameAt(age, lifetime, variant);
        if (trailSpriteRef != null) trailFrame = trailSpriteRef.frameAt(age, lifetime, variant);
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

        if (trailHistory != null) {
            trailHistory.record((float) xo, (float) yo, (float) zo, (float) (age - 1));
            trailHistory.pruneOlderThan(age, bakedTrail.lifetime());
        }

        if (onGround) {
            if (onLand != null && !landed) {
                landed = true;
                fire(onLand, Triggers.LAND_SALT, landChance, landInputs);
            }
            xd *= 0.7;
            zd *= 0.7;
        }
    }

    private void checkDeath() {
        if (!deathFired) {
            deathFired = true;
            if (onDeath != null) fire(onDeath, Triggers.DEATH_SALT, deathChance, deathInputs);
        }
    }

    private void die() {
        checkDeath();
        // Ends life now so tick() lets the trail fade out.
        if (trailHistory != null && trailHistory.size() > 1) lifetime = age - 1;
        else remove();
    }

    private void fire(Trigger trigger, long salt, float chance, float[] inputs) {
        if (Triggers.roll(variant, salt, chance)) {
            ClientEffects.MANAGER.trigger(trigger, x, y, z, xd, yd, zd, scale, tint, inputs, Triggers.seed(variant, salt));
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (trailHistory != null && trailHistory.size() >= 1) {
            renderTrail(buffer, camera, partialTicks);
        }
        if (hasQuad && age <= lifetime) {
            float t = lifeFraction(partialTicks);
            int rgb = template.color().sample(t);
            rCol = ((rgb >> 16) & 0xFF) / 255f * tintR;
            gCol = ((rgb >> 8) & 0xFF) / 255f * tintG;
            bCol = (rgb & 0xFF) / 255f * tintB;
            alpha = Math.max(0f, Math.min(1f, template.alpha().curve().sample(t) * alphaScale));
            super.render(buffer, camera, partialTicks);
        }
    }

    private void renderTrail(VertexConsumer buffer, Camera camera, float partialTicks) {
        float headX = (float) (xo + (x - xo) * partialTicks);
        float headY = (float) (yo + (y - yo) * partialTicks);
        float headZ = (float) (zo + (z - zo) * partialTicks);
        float currentTickTime = (age - 1) + partialTicks;

        boolean isDead = age > lifetime;
        int histSize = trailHistory.size();
        if (histSize == 0 && isDead) return;

        float hdx = histSize > 0 ? headX - trailHistory.getX(0) : 1f;
        float hdy = histSize > 0 ? headY - trailHistory.getY(0) : 0f;
        float hdz = histSize > 0 ? headZ - trailHistory.getZ(0) : 0f;
        boolean headDegenerate = histSize > 0 && (hdx * hdx + hdy * hdy + hdz * hdz) < 1e-8f;

        int count;
        if (isDead) {
            int histLimit = Math.min(histSize, MAX_SCRATCH_POINTS);
            for (int j = 0; j < histLimit; j++) {
                SCRATCH_PX[j] = trailHistory.getX(j);
                SCRATCH_PY[j] = trailHistory.getY(j);
                SCRATCH_PZ[j] = trailHistory.getZ(j);
                SCRATCH_TIMES[j] = trailHistory.getTime(j);
            }
            count = histLimit;
        } else if (headDegenerate) {
            SCRATCH_PX[0] = headX;
            SCRATCH_PY[0] = headY;
            SCRATCH_PZ[0] = headZ;
            SCRATCH_TIMES[0] = currentTickTime;
            int histLimit = Math.min(histSize - 1, MAX_SCRATCH_POINTS - 1);
            for (int j = 0; j < histLimit; j++) {
                int idx = j + 1;
                SCRATCH_PX[idx] = trailHistory.getX(j + 1);
                SCRATCH_PY[idx] = trailHistory.getY(j + 1);
                SCRATCH_PZ[idx] = trailHistory.getZ(j + 1);
                SCRATCH_TIMES[idx] = trailHistory.getTime(j + 1);
            }
            count = histLimit + 1;
        } else {
            SCRATCH_PX[0] = headX;
            SCRATCH_PY[0] = headY;
            SCRATCH_PZ[0] = headZ;
            SCRATCH_TIMES[0] = currentTickTime;
            int histLimit = Math.min(histSize, MAX_SCRATCH_POINTS - 1);
            for (int j = 0; j < histLimit; j++) {
                int idx = j + 1;
                SCRATCH_PX[idx] = trailHistory.getX(j);
                SCRATCH_PY[idx] = trailHistory.getY(j);
                SCRATCH_PZ[idx] = trailHistory.getZ(j);
                SCRATCH_TIMES[idx] = trailHistory.getTime(j);
            }
            count = histLimit + 1;
        }
        if (count < 2) return;

        float camX = (float) camera.getPosition().x;
        float camY = (float) camera.getPosition().y;
        float camZ = (float) camera.getPosition().z;

        Vector3f camUp = camera.getUpVector();

        for (int i = 0; i < count; i++) {
            float nodeAge = Math.max(0f, currentTickTime - SCRATCH_TIMES[i]);
            float frac = Math.min(1f, nodeAge / bakedTrail.lifetime());
            SCRATCH_WIDTHS[i] = Math.max(0f, bakedTrail.sampleWidth(frac) * trailWidthScale * scale);

            int rgb = bakedTrail.color().sample(frac);
            SCRATCH_R[i] = ((rgb >> 16) & 0xFF) / 255f * tintR;
            SCRATCH_G[i] = ((rgb >> 8) & 0xFF) / 255f * tintG;
            SCRATCH_B[i] = (rgb & 0xFF) / 255f * tintB;
            SCRATCH_A[i] = Math.max(0f, Math.min(1f, bakedTrail.sampleAlpha(frac) * trailAlphaScale));
        }

        RibbonMath.computeExtrusions(
                SCRATCH_PX, SCRATCH_PY, SCRATCH_PZ, SCRATCH_WIDTHS, count,
                camX, camY, camZ, camUp.x(), camUp.y(), camUp.z(),
                SCRATCH_LEFT_X, SCRATCH_LEFT_Y, SCRATCH_LEFT_Z,
                SCRATCH_RIGHT_X, SCRATCH_RIGHT_Y, SCRATCH_RIGHT_Z
        );

        int uv = trailFrame * 4;
        float u0 = trailResolved.uvs()[uv];
        float u1 = trailResolved.uvs()[uv + 1];
        float v0 = trailResolved.uvs()[uv + 2];
        float v1 = trailResolved.uvs()[uv + 3];
        int light = trailEmissive ? LightTexture.FULL_BRIGHT : getLightColor(partialTicks);

        float xL0 = SCRATCH_LEFT_X[0], yL0 = SCRATCH_LEFT_Y[0], zL0 = SCRATCH_LEFT_Z[0];
        float xR0 = SCRATCH_RIGHT_X[0], yR0 = SCRATCH_RIGHT_Y[0], zR0 = SCRATCH_RIGHT_Z[0];
        float r0 = SCRATCH_R[0], g0 = SCRATCH_G[0], b0 = SCRATCH_B[0], a0 = SCRATCH_A[0];

        if (bakedTrail.mode() == TrailSpec.Mode.TILE) {
            SCRATCH_DIST[0] = 0f;
            for (int i = 1; i < count; i++) {
                float dx = SCRATCH_PX[i] - SCRATCH_PX[i - 1];
                float dy = SCRATCH_PY[i] - SCRATCH_PY[i - 1];
                float dz = SCRATCH_PZ[i] - SCRATCH_PZ[i - 1];
                SCRATCH_DIST[i] = SCRATCH_DIST[i - 1] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }

            float tileLen = bakedTrail.tileLength();
            float uSpan = u1 - u0;

            for (int i = 1; i < count; i++) {
                float xL1 = SCRATCH_LEFT_X[i], yL1 = SCRATCH_LEFT_Y[i], zL1 = SCRATCH_LEFT_Z[i];
                float xR1 = SCRATCH_RIGHT_X[i], yR1 = SCRATCH_RIGHT_Y[i], zR1 = SCRATCH_RIGHT_Z[i];
                float r1 = SCRATCH_R[i], g1 = SCRATCH_G[i], b1 = SCRATCH_B[i], a1 = SCRATCH_A[i];

                float d0 = SCRATCH_DIST[i - 1];
                float d1 = SCRATCH_DIST[i];
                float segLen = d1 - d0;
                if (segLen < 1e-4f) {
                    xL0 = xL1; yL0 = yL1; zL0 = zL1;
                    xR0 = xR1; yR0 = yR1; zR0 = zR1;
                    r0 = r1; g0 = g1; b0 = b1; a0 = a1;
                    continue;
                }

                int k0 = (int) Math.floor(d0 / tileLen);
                int k1 = (int) Math.floor(d1 / tileLen);

                if (k0 == k1) {
                    float f0 = (d0 - k0 * tileLen) / tileLen;
                    float f1 = (d1 - k0 * tileLen) / tileLen;
                    float uA = u0 + uSpan * f0;
                    float uB = u0 + uSpan * f1;

                    quad(buffer, light, xL0, yL0, zL0, xL1, yL1, zL1, xR1, yR1, zR1, xR0, yR0, zR0,
                            uA, uB, v0, v1, r0, g0, b0, a0, r1, g1, b1, a1);
                } else {
                    float curXL = xL0, curYL = yL0, curZL = zL0;
                    float curXR = xR0, curYR = yR0, curZR = zR0;
                    float curR = r0, curG = g0, curB = b0, curA = a0;
                    float uStart = u0 + uSpan * ((d0 - k0 * tileLen) / tileLen);

                    for (int k = k0 + 1; k <= k1; k++) {
                        float dSplit = k * tileLen;
                        float tSplit = (dSplit - d0) / segLen;
                        float splitXL = lerp(xL0, xL1, tSplit);
                        float splitYL = lerp(yL0, yL1, tSplit);
                        float splitZL = lerp(zL0, zL1, tSplit);
                        float splitXR = lerp(xR0, xR1, tSplit);
                        float splitYR = lerp(yR0, yR1, tSplit);
                        float splitZR = lerp(zR0, zR1, tSplit);
                        float splitR = lerp(r0, r1, tSplit);
                        float splitG = lerp(g0, g1, tSplit);
                        float splitB = lerp(b0, b1, tSplit);
                        float splitA = lerp(a0, a1, tSplit);

                        quad(buffer, light, curXL, curYL, curZL, splitXL, splitYL, splitZL, splitXR, splitYR, splitZR, curXR, curYR, curZR,
                                uStart, u1, v0, v1, curR, curG, curB, curA, splitR, splitG, splitB, splitA);

                        curXL = splitXL; curYL = splitYL; curZL = splitZL;
                        curXR = splitXR; curYR = splitYR; curZR = splitZR;
                        curR = splitR; curG = splitG; curB = splitB; curA = splitA;
                        uStart = u0;
                    }

                    if (d1 > k1 * tileLen + 1e-6f) {
                        float f1 = (d1 - k1 * tileLen) / tileLen;
                        float uEnd = u0 + uSpan * f1;
                        quad(buffer, light, curXL, curYL, curZL, xL1, yL1, zL1, xR1, yR1, zR1, curXR, curYR, curZR,
                                uStart, uEnd, v0, v1, curR, curG, curB, curA, r1, g1, b1, a1);
                    }
                }

                xL0 = xL1; yL0 = yL1; zL0 = zL1;
                xR0 = xR1; yR0 = yR1; zR0 = zR1;
                r0 = r1; g0 = g1; b0 = b1; a0 = a1;
            }
        } else {
            float invCount = 1f / (count - 1);
            for (int i = 0; i < count; i++) {
                SCRATCH_U[i] = u0 + (u1 - u0) * (i * invCount);
            }
            float u0Val = SCRATCH_U[0];

            for (int i = 1; i < count; i++) {
                float xL1 = SCRATCH_LEFT_X[i], yL1 = SCRATCH_LEFT_Y[i], zL1 = SCRATCH_LEFT_Z[i];
                float xR1 = SCRATCH_RIGHT_X[i], yR1 = SCRATCH_RIGHT_Y[i], zR1 = SCRATCH_RIGHT_Z[i];
                float u1Val = SCRATCH_U[i];
                float r1 = SCRATCH_R[i], g1 = SCRATCH_G[i], b1 = SCRATCH_B[i], a1 = SCRATCH_A[i];

                float dx = SCRATCH_PX[i] - SCRATCH_PX[i - 1];
                float dy = SCRATCH_PY[i] - SCRATCH_PY[i - 1];
                float dz = SCRATCH_PZ[i] - SCRATCH_PZ[i - 1];
                if (dx * dx + dy * dy + dz * dz < 1e-8f) {
                    xL0 = xL1; yL0 = yL1; zL0 = zL1;
                    xR0 = xR1; yR0 = yR1; zR0 = zR1;
                    u0Val = u1Val;
                    r0 = r1; g0 = g1; b0 = b1; a0 = a1;
                    continue;
                }

                quad(buffer, light, xL0, yL0, zL0, xL1, yL1, zL1, xR1, yR1, zR1, xR0, yR0, zR0,
                        u0Val, u1Val, v0, v1, r0, g0, b0, a0, r1, g1, b1, a1);

                xL0 = xL1; yL0 = yL1; zL0 = zL1;
                xR0 = xR1; yR0 = yR1; zR0 = zR1;
                u0Val = u1Val;
                r0 = r1; g0 = g1; b0 = b1; a0 = a1;
            }
        }
    }

    /**
     * One strip segment from its start edge (L0, R0) to its end edge (L1, R1), in camera-relative space, wound to face
     * the camera.
     */
    private static void quad(VertexConsumer buffer, int light,
                             float xL0, float yL0, float zL0, float xL1, float yL1, float zL1,
                             float xR1, float yR1, float zR1, float xR0, float yR0, float zR0,
                             float uA, float uB, float v0, float v1,
                             float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
        buffer.addVertex(xL0, yL0, zL0).setUv(uA, v0).setColor(r0, g0, b0, a0).setLight(light);
        if (RibbonMath.facesCamera(xL0, yL0, zL0, xL1, yL1, zL1, xR1, yR1, zR1)) {
            buffer.addVertex(xL1, yL1, zL1).setUv(uB, v0).setColor(r1, g1, b1, a1).setLight(light);
            buffer.addVertex(xR1, yR1, zR1).setUv(uB, v1).setColor(r1, g1, b1, a1).setLight(light);
            buffer.addVertex(xR0, yR0, zR0).setUv(uA, v1).setColor(r0, g0, b0, a0).setLight(light);
        } else {
            buffer.addVertex(xR0, yR0, zR0).setUv(uA, v1).setColor(r0, g0, b0, a0).setLight(light);
            buffer.addVertex(xR1, yR1, zR1).setUv(uB, v1).setColor(r1, g1, b1, a1).setLight(light);
            buffer.addVertex(xL1, yL1, zL1).setUv(uB, v0).setColor(r1, g1, b1, a1).setLight(light);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
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

    /** NeoForge's culling box; covers the trail so it isn't culled with the head. */
    public AABB getRenderBoundingBox(float partialTicks) {
        AABB box = getBoundingBox().inflate(1.0);
        if (trailHistory == null) return box;
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        for (int i = 0; i < trailHistory.size(); i++) {
            minX = Math.min(minX, trailHistory.getX(i)); maxX = Math.max(maxX, trailHistory.getX(i));
            minY = Math.min(minY, trailHistory.getY(i)); maxY = Math.max(maxY, trailHistory.getY(i));
            minZ = Math.min(minZ, trailHistory.getZ(i)); maxZ = Math.max(maxZ, trailHistory.getZ(i));
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.0);
    }

    /** Forge's culling uses the head's box only, so trailed particles opt out. */
    public boolean shouldCull() {
        return trailHistory == null;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return renderType;
    }

    private float lifeFraction(float partialTicks) {
        return Math.min(1f, (age + partialTicks) / lifetime);
    }
}
