package com.eden.particles.impl.definition;

import com.eden.particles.impl.math.Easings;
import com.eden.particles.impl.math.FloatCurve;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionParsingTest {

    @Test
    void arcaneBurstParses() {
        ParticleEffect effect = Json.decode(ParticleEffect.CODEC, Json.fixture("effects/arcane_burst.json"));
        assertEquals(60, effect.duration());
        assertFalse(effect.loop());
        assertEquals(ParticleEffect.DEFAULT_RANGE, effect.range());
        assertEquals(3, effect.emitters().size());

        EmitterTrack charge = effect.emitters().get(0);
        assertEquals("charge", charge.name().orElseThrow());
        assertEquals(new TemplateRef.Reference(ResourceLocation.fromNamespaceAndPath("eden_particles", "arcane_mote")), charge.template());
        EmitterShape.Ring ring = assertInstanceOf(EmitterShape.Ring.class, charge.shape());
        assertEquals(2.0, ring.radius().evaluate(0), 1e-9);
        assertEquals(0.3 + (2.0 - 0.3) * (1 - 0.125), ring.radius().evaluate(0.5), 1e-9); // in_cubic(0.5) = 0.125
        assertEquals(18.0, charge.spin().orElseThrow().evaluate(0.3), 0.0);
        assertEquals(7.0, charge.spawn().rate().orElseThrow().evaluate(0.5), 1e-9);

        EmitterTrack flash = effect.emitters().get(1);
        assertEquals(EmitterTrack.Distribution.EVEN, flash.distribution());
        assertTrue(flash.important());
        TemplateRef.Inline inline = assertInstanceOf(TemplateRef.Inline.class, flash.template());
        assertEquals(new RandomInt(8, 14), inline.template().lifetime());
        assertEquals(0.85f, inline.template().physics().drag());
        VelocityMode.Radial radial = assertInstanceOf(VelocityMode.Radial.class, flash.velocity());
        assertTrue(radial.speed().byDistance());
        assertEquals(new RandomFloat.Uniform(1.5f, 2.5f), radial.speed().value());

        EmitterTrack embers = effect.emitters().get(2);
        assertTrue(embers.spawn().jitter());
        VelocityMode.Fixed fixed = assertInstanceOf(VelocityMode.Fixed.class, embers.velocity());
        assertEquals(1f, fixed.direction().y());
    }

    @Test
    void kitchenSinkCoversEveryShapeAndVelocity() {
        ParticleEffect effect = Json.decode(ParticleEffect.CODEC, Json.fixture("effects/kitchen_sink.json"));
        assertTrue(effect.loop());
        assertEquals(96f, effect.range());
        assertInstanceOf(EmitterShape.Box.class, effect.emitters().get(0).shape());
        assertInstanceOf(VelocityMode.Cone.class, effect.emitters().get(0).velocity());
        assertInstanceOf(RandomFloat.Normal.class, ((VelocityMode.Cone) effect.emitters().get(0).velocity()).speed().value());

        EmitterTrack hemisphere = effect.emitters().get(1);
        assertEquals(-1f, hemisphere.axis().y(), 1e-6);
        assertEquals(0, hemisphere.spawn().bursts().get(0).at());
        assertEquals(new RandomInt(3, 6), hemisphere.spawn().bursts().get(0).count());

        EmitterTrack line = effect.emitters().get(2);
        assertEquals(FloatCurve.Mode.MONOTONE, line.spin().orElseThrow().mode());
        FloatCurve rate = line.spawn().rate().orElseThrow();
        assertTrue(rate.isDiscontinuous());
        assertEquals(Easings.STEP, rate.easingInto(2));

        EmitterShape.Sphere sphere = assertInstanceOf(EmitterShape.Sphere.class, effect.emitters().get(3).shape());
        assertEquals(0.25f, sphere.innerRadius());

        EmitterTrack minimal = effect.emitters().get(4);
        assertEquals(EmitterShape.POINT, minimal.shape());
        assertEquals(VelocityMode.NONE, minimal.velocity());
        assertEquals(EmitterTrack.UP, minimal.axis());
    }

    @Test
    void templatesParse() {
        ParticleTemplate mote = Json.decode(ParticleTemplate.CODEC, Json.fixture("templates/arcane_mote.json"));
        RenderMode.Sprite sprite = assertInstanceOf(RenderMode.Sprite.class, mote.render());
        assertEquals(RenderMode.Blend.ADDITIVE, sprite.blend());
        assertEquals(0xD8C4FF, mote.color().orElseThrow().evaluate(0));
        assertEquals(0.25, mote.size().orElseThrow().evaluate(0.15), 1e-9);

        ParticleTemplate smoke = Json.decode(ParticleTemplate.CODEC, Json.fixture("templates/campfire_smoke.json"));
        assertInstanceOf(RenderMode.Vanilla.class, smoke.render());
        assertEquals(new RandomInt(40, 80), smoke.lifetime());
        assertTrue(smoke.physics().collide());
    }

    @ParameterizedTest
    @ValueSource(strings = {"effects/arcane_burst.json", "effects/kitchen_sink.json"})
    void effectsRoundTrip(String path) {
        assertRoundTrip(ParticleEffect.CODEC, Json.fixture(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"templates/arcane_mote.json", "templates/campfire_smoke.json"})
    void templatesRoundTrip(String path) {
        assertRoundTrip(ParticleTemplate.CODEC, Json.fixture(path));
    }

    /** Decoding what we encoded must encode identically. */
    private static <A> void assertRoundTrip(Codec<A> codec, JsonElement source) {
        JsonElement first = Json.encode(codec, Json.decode(codec, source));
        JsonElement second = Json.encode(codec, Json.decode(codec, first));
        assertEquals(first, second);
    }
}
