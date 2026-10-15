package com.eden.particles.impl.definition;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class TrailCodecTest {

    private static void assertContains(String expected, String actual) {
        assertTrue(actual.contains(expected), "expected '" + expected + "' in:\n  " + actual);
    }

    private static void assertRoundTrips(String json) {
        var decoded = Json.decode(TrailSpec.CODEC, json);
        var encoded = Json.encode(TrailSpec.CODEC, decoded);
        assertEquals(encoded, Json.encode(TrailSpec.CODEC, Json.decode(TrailSpec.CODEC, encoded)));
    }

    @Test
    void fullTrailSpecRoundTrips() {
        assertRoundTrips("{\"lifetime\": 15, \"max_points\": 24, \"min_distance\": 0.05, \"mode\": \"tile\", "
                + "\"tile_length\": 2.0, \"width\": [[0, 0.3], [1, 0]], \"alpha\": [[0, 1], [1, 0]], "
                + "\"color\": [{\"t\": 0, \"color\": \"#FF0000\"}, {\"t\": 1, \"color\": \"#0000FF\"}], "
                + "\"sprite\": \"eden_particles:ribbon\", \"blend\": \"additive\", \"emissive\": true}");
    }

    @Test
    void defaultValuesAreFilled() {
        TrailSpec spec = Json.decode(TrailSpec.CODEC, "{\"lifetime\": 10}");
        assertEquals(10, spec.lifetime());
        assertEquals(16, spec.maxPoints());
        assertEquals(0.02f, spec.minDistance(), 1e-5f);
        assertEquals(TrailSpec.Mode.STRETCH, spec.mode());
        assertEquals(1.0f, spec.tileLength(), 1e-5f);
        assertTrue(spec.width().isEmpty());
        assertTrue(spec.alpha().isEmpty());
        assertTrue(spec.color().isEmpty());
        assertTrue(spec.sprite().isEmpty());
        assertTrue(spec.blend().isEmpty());
        assertTrue(spec.emissive().isEmpty());
    }

    @Test
    void validatesRanges() {
        assertContains("must be at least 1", Json.error(TrailSpec.CODEC, "{\"lifetime\": 0}"));
        assertContains("must be between 3 and 64", Json.error(TrailSpec.CODEC, "{\"lifetime\": 10, \"max_points\": 2}"));
        assertContains("must be between 3 and 64", Json.error(TrailSpec.CODEC, "{\"lifetime\": 10, \"max_points\": 65}"));
        assertContains("must be at least", Json.error(TrailSpec.CODEC, "{\"lifetime\": 10, \"min_distance\": 0}"));
        assertContains("unknown trail mode 'strech' (did you mean 'stretch'?)",
                Json.error(TrailSpec.CODEC, "{\"lifetime\": 10, \"mode\": \"strech\"}"));
    }

    @Test
    void collectsInputNames() {
        TrailSpec spec = Json.decode(TrailSpec.CODEC,
                "{\"lifetime\": 10, \"width\": {\"keys\": [[0, 0.1], [1, 0]], \"scale\": \"power\"}, "
                        + "\"alpha\": {\"keys\": [[0, 1], [1, 0]], \"scale\": \"fade\"}}");
        Set<String> names = new TreeSet<>();
        spec.names(names);
        assertEquals(Set.of("fade", "power"), names);
    }

    @Test
    void ribbonRenderModeParsesAndRoundTrips() {
        var decoded = Json.decode(RenderMode.CODEC, "{\"type\": \"ribbon\", \"sprite\": \"eden_particles:slash\", \"blend\": \"additive\", \"emissive\": true}");
        assertInstanceOf(RenderMode.Ribbon.class, decoded);
        RenderMode.Ribbon ribbon = (RenderMode.Ribbon) decoded;
        assertEquals(RenderMode.Blend.ADDITIVE, ribbon.blend());
        assertTrue(ribbon.emissive());

        assertContains("unknown render type 'ribon' (did you mean 'ribbon'?)",
                Json.error(RenderMode.CODEC, "{\"type\": \"ribon\", \"sprite\": \"eden_particles:slash\"}"));
    }

    @Test
    void templateEnforcesValidationRules() {
        String missingTrail = "{\"render\": {\"type\": \"ribbon\", \"sprite\": \"eden_particles:slash\"}, \"lifetime\": 10}";
        assertContains("render type 'ribbon' needs a 'trail' block", Json.error(ParticleTemplate.CODEC, missingTrail));

        String validRibbon = "{\"render\": {\"type\": \"ribbon\", \"sprite\": \"eden_particles:slash\"}, \"lifetime\": 10, \"trail\": {\"lifetime\": 5}}";
        assertDoesNotThrow(() -> Json.decode(ParticleTemplate.CODEC, validRibbon));

        String validSprite = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"eden_particles:spark\"}, \"lifetime\": 10, \"trail\": {\"lifetime\": 5}}";
        assertDoesNotThrow(() -> Json.decode(ParticleTemplate.CODEC, validSprite));

        String vanillaWithTrail = "{\"render\": {\"type\": \"vanilla\", \"particle\": \"minecraft:smoke\"}, \"lifetime\": 10, \"trail\": {\"lifetime\": 5}}";
        String error = Json.error(ParticleTemplate.CODEC, vanillaWithTrail);
        assertContains("vanilla particles can't follow per-particle curves, triggers or forces", error);
        assertContains("remove [trail]", error);
    }
}
