package com.eden.particles.impl.definition;

import com.eden.particles.impl.expr.Expr;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceCodecTest {

    private static void assertContains(String expected, String actual) {
        assertTrue(actual.contains(expected), "expected '" + expected + "' in: " + actual);
    }

    private static void assertRoundTrips(String json) {
        var encoded = Json.encode(Force.CODEC, Json.decode(Force.CODEC, json));
        assertEquals(encoded, Json.encode(Force.CODEC, Json.decode(Force.CODEC, encoded)));
    }

    @Test
    void everyTypeRoundTrips() {
        assertRoundTrips("{\"type\": \"turbulence\", \"strength\": 0.03, \"scale\": 1.5, \"flow\": 0.02, \"octaves\": 2}");
        assertRoundTrips("{\"type\": \"attractor\", \"point\": [0, \"height\", 0], \"strength\": [[0, 0], [1, 0.1]], "
                + "\"falloff\": \"linear\", \"radius\": 6, \"absorb\": 0.3}");
        assertRoundTrips("{\"type\": \"attractor\", \"point\": \"target\", \"strength\": \"pull\", \"falloff\": \"inverse_square\"}");
        assertRoundTrips("{\"type\": \"vortex\", \"axis\": [0, 0, 1], \"strength\": 0.04, \"pull\": 0.01, \"lift\": 0.02, \"radius\": 3}");
    }

    @Test
    void defaults() {
        Force.Turbulence t = (Force.Turbulence) Json.decode(Force.CODEC, "{\"type\": \"turbulence\", \"strength\": 0.1}");
        assertEquals(Expr.ONE, t.scale());
        assertEquals(Expr.ZERO, t.flow());
        assertEquals(1, t.octaves());
        Force.Vortex v = (Force.Vortex) Json.decode(Force.CODEC, "{\"type\": \"vortex\", \"strength\": 0.1}");
        assertEquals(Force.Point.ORIGIN, v.point());
        assertEquals(ExprVec3.of(0, 1, 0), v.axis());
        assertEquals(Force.Falloff.NONE, v.falloff());
    }

    @Test
    void points() {
        Force.Attractor origin = (Force.Attractor) Json.decode(Force.CODEC, "{\"type\": \"attractor\", \"point\": \"origin\", \"strength\": 1}");
        assertEquals(Force.Point.ORIGIN, origin.point());
        Force.Attractor offset = (Force.Attractor) Json.decode(Force.CODEC, "{\"type\": \"attractor\", \"point\": [1, 2, 3], \"strength\": 1}");
        assertEquals(new Force.Point(false, ExprVec3.of(1, 2, 3)), offset.point());
        assertContains("unknown point 'targte' (did you mean 'target'?)",
                Json.error(Force.CODEC, "{\"type\": \"attractor\", \"point\": \"targte\", \"strength\": 1}"));
        assertContains("expected a point", Json.error(Force.CODEC, "{\"type\": \"attractor\", \"point\": 3, \"strength\": 1}"));
    }

    @Test
    void mistakesFailLoudly() {
        assertContains("unknown force type 'vortx'", Json.error(Force.CODEC, "{\"type\": \"vortx\", \"strength\": 1}"));
        assertContains("'linear' fades to nothing at 'radius', so it needs one",
                Json.error(Force.CODEC, "{\"type\": \"attractor\", \"strength\": 1, \"falloff\": \"linear\"}"));
        assertContains("must be 1 or 2, got 3", Json.error(Force.CODEC, "{\"type\": \"turbulence\", \"strength\": 1, \"octaves\": 3}"));
        assertContains("must be greater than 0", Json.error(Force.CODEC, "{\"type\": \"turbulence\", \"strength\": 1, \"scale\": 0}"));
        assertContains("must not be [0, 0, 0]", Json.error(Force.CODEC, "{\"type\": \"vortex\", \"strength\": 1, \"axis\": [0, 0, 0]}"));
        assertContains("must be at least 0", Json.error(Force.CODEC, "{\"type\": \"attractor\", \"strength\": 1, \"absorb\": -1}"));
        assertContains("at most 8 forces, got 9", Json.error(Force.LIST,
                "[" + String.join(", ", java.util.Collections.nCopies(9, "{\"type\": \"turbulence\", \"strength\": 1}")) + "]"));
    }

    @Test
    void physicsCarriesForcesAndOmitsAnEmptyList() {
        Physics physics = Json.decode(Physics.CODEC, "{\"forces\": [{\"type\": \"turbulence\", \"strength\": \"power\"}]}");
        assertInstanceOf(Force.Turbulence.class, physics.forces().get(0));
        Set<String> names = new TreeSet<>();
        physics.names(names);
        assertEquals(Set.of("power"), names);
        assertFalse(Json.encode(Physics.CODEC, Physics.DEFAULT).getAsJsonObject().has("forces"));
        var encoded = Json.encode(Physics.CODEC, physics);
        assertEquals(encoded, Json.encode(Physics.CODEC, Json.decode(Physics.CODEC, encoded)));
        assertEquals(List.of(), Physics.DEFAULT.forces());
    }

    @Test
    void vanillaParticlesRejectForces() {
        String error = Json.error(ParticleTemplate.CODEC, "{\"render\": {\"type\": \"vanilla\", \"particle\": \"minecraft:flame\"}, "
                + "\"lifetime\": 10, \"physics\": {\"forces\": [{\"type\": \"turbulence\", \"strength\": 1}]}}");
        assertContains("remove [physics.forces]", error);
    }
}
