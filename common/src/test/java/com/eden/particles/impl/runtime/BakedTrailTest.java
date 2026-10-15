package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.definition.Physics;
import com.eden.particles.impl.definition.RandomInt;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.ScaledCurve;
import com.eden.particles.impl.definition.SpriteRef;
import com.eden.particles.impl.definition.TrailSpec;
import com.eden.particles.impl.math.FloatCurve;
import com.eden.particles.impl.math.Gradient;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakedTrailTest {

    private static final ScaledCurve FALLBACK_WIDTH = ScaledCurve.constant(0.5);
    private static final ScaledCurve FALLBACK_ALPHA = ScaledCurve.constant(0.8);
    private static final Gradient FALLBACK_COLOR = Gradient.constant(0xFF0000);

    @Test
    void bakesWithExplicitValues() {
        TrailSpec spec = new TrailSpec(
                15,
                24,
                0.15f,
                Optional.of(ScaledCurve.of(FloatCurve.linear(new double[]{0.0, 1.0}, new double[]{0.2, 0.8}))),
                Optional.of(ScaledCurve.of(FloatCurve.linear(new double[]{0.0, 1.0}, new double[]{1.0, 0.0}))),
                Optional.of(Gradient.constant(0x00FF00)),
                Optional.of(SpriteRef.single(ResourceLocation.fromNamespaceAndPath("eden_particles", "particle/spark"))),
                TrailSpec.Mode.TILE,
                1.5f,
                Optional.of(RenderMode.Blend.ADDITIVE),
                Optional.of(true)
        );

        BakedTrail baked = BakedTrail.bake(spec, FALLBACK_WIDTH, FALLBACK_ALPHA, FALLBACK_COLOR);
        assertEquals(15, baked.lifetime());
        assertEquals(24, baked.maxPoints());
        assertEquals(0.15f, baked.minDistance(), 1e-6f);
        assertEquals(TrailSpec.Mode.TILE, baked.mode());
        assertEquals(1.5f, baked.tileLength(), 1e-6f);
        assertEquals(Optional.of(RenderMode.Blend.ADDITIVE), baked.blend());
        assertEquals(Optional.of(true), baked.emissive());
        assertTrue(baked.sprite().isPresent());

        assertEquals(0.2f, baked.sampleWidth(0f), 1e-3f);
        assertEquals(0.8f, baked.sampleWidth(1f), 1e-3f);
        assertEquals(1.0f, baked.sampleAlpha(0f), 1e-3f);
        assertEquals(0.0f, baked.sampleAlpha(1f), 1e-3f);
        assertEquals(0x00FF00, baked.color().sample(0.5f));
    }

    @Test
    void bakesWithFallbacksWhenFieldsOmitted() {
        TrailSpec spec = new TrailSpec(
                10,
                16,
                0.05f,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                TrailSpec.Mode.STRETCH,
                1.0f,
                Optional.empty(),
                Optional.empty()
        );

        BakedTrail baked = BakedTrail.bake(spec, FALLBACK_WIDTH, FALLBACK_ALPHA, FALLBACK_COLOR);
        assertEquals(0.5f, baked.width().curve().sample(0.5f), 1e-3f);
        assertEquals(0.8f, baked.alpha().curve().sample(0.5f), 1e-3f);
        assertEquals(0xFF0000, baked.color().sample(0.5f));
        assertFalse(baked.sprite().isPresent());
        assertFalse(baked.blend().isPresent());
        assertFalse(baked.emissive().isPresent());
    }

    @Test
    void templateEvaluationScalesTrailParameters() {
        TrailSpec trailSpec = new TrailSpec(
                12,
                20,
                0.1f,
                Optional.of(ScaledCurve.of(FloatCurve.linear(new double[]{0.0, 1.0}, new double[]{0.4, 0.1}))),
                Optional.of(ScaledCurve.constant(0.9)),
                Optional.empty(),
                Optional.empty(),
                TrailSpec.Mode.STRETCH,
                1.0f,
                Optional.empty(),
                Optional.empty()
        );

        ParticleTemplate template = new ParticleTemplate(
                new RenderMode.Ribbon(SpriteRef.single(ResourceLocation.fromNamespaceAndPath("eden_particles", "particle/spark")), RenderMode.Blend.TRANSLUCENT, false),
                RandomInt.of(20),
                Optional.of(ScaledCurve.constant(0.5)),
                Optional.of(ScaledCurve.constant(1.0)),
                Optional.empty(),
                Optional.empty(),
                Physics.DEFAULT,
                Optional.empty(),
                Optional.empty(),
                Optional.of(trailSpec)
        );

        BakedTemplate baked = BakedTemplate.bake(template);
        assertTrue(baked.trail().isPresent());

        TemplateValues values = new TemplateValues();
        baked.evaluate(new float[0], values);

        assertEquals(1.0f, values.trailWidth, 1e-5f);
        assertEquals(1.0f, values.trailAlpha, 1e-5f);
    }
}
