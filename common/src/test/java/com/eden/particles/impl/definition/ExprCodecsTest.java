package com.eden.particles.impl.definition;

import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.expr.ExprParser;
import com.eden.particles.impl.expr.InputNames;
import com.eden.particles.impl.math.FloatCurve;
import com.eden.particles.impl.math.ParticleRandom;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExprCodecsTest {

    private static void assertContains(String expected, String actual) {
        assertTrue(actual.contains(expected), "expected '" + expected + "' in: " + actual);
    }

    @Test
    void scaledCurvesEncodeInTheirShortestForm() {
        assertEquals(Json.encode(CurveCodecs.CURVE, FloatCurve.constant(2)), Json.encode(CurveCodecs.SCALED, ScaledCurve.constant(2)));
        ScaledCurve expression = Json.decode(CurveCodecs.SCALED, "\"power * 2\"");
        assertEquals("\"(power * 2)\"", Json.encode(CurveCodecs.SCALED, expression).toString());
        ScaledCurve scaled = Json.decode(CurveCodecs.SCALED, "{\"keys\": [[0, 0], [1, 3]], \"scale\": \"power\"}");
        assertEquals("power", Json.encode(CurveCodecs.SCALED, scaled).getAsJsonObject().get("scale").getAsString());
        assertEquals(6.0, Json.decode(CurveCodecs.SCALED, "\"2 * 3\"").curve().keyValue(0), "constant expressions fold");
        assertEquals(FloatCurve.Mode.EASED, Json.decode(CurveCodecs.SCALED, "[[0, 1], [1, 2]]").curve().mode());
    }

    @Test
    void randomValuesEncodeExpressionsAndConstants() {
        RandomFloat uniform = Json.decode(RandomFloat.CODEC, "[\"power\", 2]");
        assertEquals("[\"power\",2.0]", Json.encode(RandomFloat.CODEC, uniform).toString());
        assertEquals("\"(power * 2)\"", Json.encode(RandomFloat.CODEC, Json.decode(RandomFloat.CODEC, "\"power * 2\"")).toString());
        assertEquals("3", Json.encode(RandomInt.CODEC, RandomInt.of(3)).toString());
        assertEquals("[\"lo\",5]", Json.encode(RandomInt.CODEC, Json.decode(RandomInt.CODEC, "[\"lo\", 5]")).toString());
        assertEquals("\"(n + 1)\"", Json.encode(RandomInt.CODEC, Json.decode(RandomInt.CODEC, "\"n + 1\"")).toString());
    }

    @Test
    void constantsAreRangeCheckedAndExpressionsWait() {
        assertContains("min (3.0) must not exceed max (1.0)", Json.error(RandomFloat.CODEC, "[3, 1]"));
        assertContains("min (5) must not exceed max (2)", Json.error(RandomInt.CODEC, "{\"min\": 5, \"max\": 2}"));
        assertContains("must be at least 1, got 0", Json.error(RandomInt.codecAtLeast(1), "0"));
        Json.decode(RandomInt.codecAtLeast(1), "\"power\"");
        assertContains("expected a whole number", Json.error(RandomInt.CODEC, "2.5"));
        assertContains("must be at least 0, got -1", Json.error(ExprCodecs.atLeast(ExprCodecs.NUMBER, 0), "-1"));
        assertContains("must be at least 0.5, got 0.25", Json.error(ExprCodecs.atLeast(ExprCodecs.NUMBER, 0.5f), "0.25"));
        assertContains("must be between 0.0 and 180.0, got 200.0", Json.error(ExprCodecs.in(ExprCodecs.NUMBER, 0, 180), "200"));
        Json.decode(ExprCodecs.in(ExprCodecs.NUMBER, 0, 180), "\"angle\"");
        assertContains("expected [x, y, z], got 2 values", Json.error(ExprCodecs.VEC3, "[1, \"a\"]"));
        assertContains("must be greater than 0 and at most 1, got 2.0", Json.error(Physics.CODEC, "{\"drag\": 2}"));
        assertContains("half_angle: must be greater than 0", Json.error(VelocityMode.CODEC, "{\"type\": \"cone\", \"half_angle\": 0, \"speed\": 1}"));
        assertContains("direction: must not be zero", Json.error(VelocityMode.CODEC, "{\"type\": \"fixed\", \"direction\": [0, 0, 0], \"speed\": 1}"));
        Json.decode(VelocityMode.CODEC, "{\"type\": \"fixed\", \"direction\": [0, 0, \"d\"], \"speed\": 1}");
        assertContains("expected a number or expression (number or string), got true", Json.error(ExprCodecs.NUMBER, "true"));
    }

    @Test
    void rangesEvaluateAtRuntime() {
        ParticleRandom random = new ParticleRandom();
        random.reset(1);
        RandomInt reversed = new RandomInt(ExprParser.parse("hi"), Expr.constant(2));
        float[] inputs = new float[InputNames.count()];
        inputs[InputNames.id("hi")] = 9;
        assertEquals(9, reversed.sample(random, inputs), "a reversed range takes its lower end");
        RandomFloat normal = new RandomFloat.Normal(1, 0, 1, 1);
        assertEquals(1f, normal.sample(random, inputs));
        Set<String> names = new TreeSet<>();
        normal.names(names);
        reversed.names(names);
        assertEquals(Set.of("hi"), names);
    }
}
