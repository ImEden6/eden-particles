package com.eden.particles.impl.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionErrorTest {

    private static final String TEMPLATE = "\"template\": \"eden_particles:mote\"";

    private static String effect(String emitter) {
        return "{\"duration\": 40, \"emitters\": [" + emitter + "]}";
    }

    private static void assertContains(String expected, String actual) {
        assertTrue(actual.contains(expected), "expected error containing\n  " + expected + "\nbut got\n  " + actual);
    }

    @Test
    void unknownFieldSuggestsTheIntendedOne() {
        String error = Json.error(ParticleEffect.CODEC, "{\"duraton\": 40, \"emitters\": []}");
        assertEquals("missing required field 'duration' (found 'duraton', a typo?)", error);
    }

    @Test
    void unknownOptionalFieldIsRejected() {
        String error = Json.error(ParticleEffect.CODEC,
                effect("{\"duration\": 10, " + TEMPLATE + ", \"spawn\": {\"rate\": 1}, \"ofset\": [0, 1, 0]}"));
        assertContains("at emitters[0]: unknown field 'ofset' (did you mean 'offset'?)", error);
    }

    @Test
    void nestedErrorsCarryTheirPath() {
        String error = Json.error(ParticleEffect.CODEC,
                effect("{\"duration\": 10, " + TEMPLATE + ", \"spawn\": {\"rate\": 1}, \"shape\": {\"type\": \"sphere\", \"radius\": true}}"));
        assertContains("at emitters[0].shape.radius: expected a curve or expression (number or string or list or map), got true", error);
    }

    @Test
    void unknownShapeTypeSuggests() {
        String error = Json.error(EmitterShape.CODEC, "{\"type\": \"sphre\", \"radius\": 1}");
        assertContains("at type: unknown shape type 'sphre' (did you mean 'sphere'?)", error);
    }

    @Test
    void unknownEasingSuggests() {
        String error = Json.error(CurveCodecs.CURVE, "{\"keys\": [[0, 0], [1, 1]], \"ease\": \"in_cubci\"}");
        assertContains("unknown easing 'in_cubci' (did you mean 'in_cubic'?)", error);
    }

    @Test
    void wholeNumbersAreEnforced() {
        String error = Json.error(ParticleEffect.CODEC, "{\"duration\": 40.5, \"emitters\": []}");
        assertEquals("at duration: expected a whole number, got 40.5", error);
    }

    @Test
    void booleansAreNotNumbers() {
        String error = Json.error(CurveCodecs.CURVE, "true");
        assertContains("expected a curve", error);
    }

    @Test
    void emitterMustFitTheTimeline() {
        String error = Json.error(ParticleEffect.CODEC, effect("{\"start\": 30, \"duration\": 20, " + TEMPLATE + ", \"spawn\": {\"rate\": 1}}"));
        assertEquals("at emitters[0]: runs from tick 30 to 50, past the effect's duration of 40", error);
    }

    @Test
    void burstsMustFallInsideTheEmitter() {
        String error = Json.error(ParticleEffect.CODEC,
                effect("{\"duration\": 5, " + TEMPLATE + ", \"spawn\": {\"bursts\": [{\"at\": 5, \"count\": 3}]}}"));
        assertContains("at emitters[0].spawn.bursts[0]: fires at tick 5, but the emitter only lasts 5 ticks", error);
    }

    @Test
    void duplicateEmitterNames() {
        String one = "{\"name\": \"a\", \"duration\": 5, " + TEMPLATE + ", \"spawn\": {\"rate\": 1}}";
        String error = Json.error(ParticleEffect.CODEC, effect(one + "," + one));
        assertContains("duplicate emitter name 'a'", error);
    }

    @Test
    void spawnNeedsSomething() {
        assertContains("needs a 'rate', 'bursts', or both", Json.error(SpawnSpec.CODEC, "{}"));
        assertContains("at jitter: only applies to 'rate'", Json.error(SpawnSpec.CODEC, "{\"bursts\": [{\"count\": 1}], \"jitter\": true}"));
    }

    @Test
    void curveKeysMustBeOrderedAndInRange() {
        assertContains("strictly increasing", Json.error(CurveCodecs.CURVE, "[[0.5, 1], [0.2, 2]]"));
        assertContains("key time must be between 0 and 1, got 1.5", Json.error(CurveCodecs.CURVE, "[[0, 1], [1.5, 2]]"));
        assertContains("first key has no segment", Json.error(CurveCodecs.CURVE, "{\"keys\": [{\"t\": 0, \"v\": 0, \"ease\": \"in_quad\"}]}"));
        assertContains("easing only applies to mode 'eased'",
                Json.error(CurveCodecs.CURVE, "{\"keys\": [[0, 0], [1, 1]], \"ease\": \"in_quad\", \"mode\": \"monotone\"}"));
    }

    @Test
    void vanillaParticlesRejectCurves() {
        String error = Json.error(ParticleTemplate.CODEC,
                "{\"render\": {\"type\": \"vanilla\", \"particle\": \"minecraft:flame\"}, \"lifetime\": 10, \"size\": 0.2, \"alpha\": [[0, 1], [1, 0]]}");
        assertEquals("vanilla particles can't follow per-particle curves or triggers; remove [size, alpha] or use render type 'sprite'", error);
    }

    @Test
    void valueChecks() {
        assertContains("at lifetime: must be at least 1, got 0",
                Json.error(ParticleTemplate.CODEC, "{\"render\": {\"type\": \"vanilla\", \"particle\": \"minecraft:flame\"}, \"lifetime\": 0}"));
        assertContains("at drag: must be greater than 0 and at most 1, got 1.5", Json.error(Physics.CODEC, "{\"drag\": 1.5}"));
        assertContains("min (3.0) must not exceed max (1.0)", Json.error(RandomFloat.CODEC, "[3, 1]"));
        assertContains("invalid colour '#FFF', expected #RRGGBB", Json.error(CurveCodecs.GRADIENT, "\"#FFF\""));
        assertContains("at inner_radius: has no effect when 'surface' is true",
                Json.error(EmitterShape.CODEC, "{\"type\": \"sphere\", \"radius\": 1, \"inner_radius\": 0.5, \"surface\": true}"));
        assertContains("at direction: must not be zero",
                Json.error(VelocityMode.CODEC, "{\"type\": \"fixed\", \"direction\": [0, 0, 0], \"speed\": 1}"));
        assertContains("'even' needs a sphere, disc or ring shape",
                Json.error(EmitterTrack.CODEC, "{\"duration\": 5, " + TEMPLATE + ", \"distribution\": \"even\", \"spawn\": {\"rate\": 1}}"));
        assertContains("cubic_bezier x values must be in [0,1]", Json.error(CurveCodecs.EASING, "{\"cubic_bezier\": [1.5, 0, 0.5, 1]}"));
    }
}
