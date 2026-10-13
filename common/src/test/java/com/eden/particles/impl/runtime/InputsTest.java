package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.load.Linker;
import com.eden.particles.impl.math.Rotations;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InputsTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "fx");
    private static final ResourceLocation SHARED = ResourceLocation.fromNamespaceAndPath("test", "shared");
    private static final String DOT = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": 20}";

    record Spawn(double x, double y, double z, double vx, double vy, double vz, int lifetime, float size, float gravity) {
    }

    private static ParticleEffect parse(String json) {
        DataResult<ParticleEffect> result = ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.result().orElseGet(() -> fail(result.error().orElseThrow().message()));
    }

    private static String parseError(String json) {
        DataResult<ParticleEffect> result = ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.error().map(DataResult.Error::message).orElseGet(() -> fail("expected an error"));
    }

    private static Linker.Result link(String effectJson, String sharedTemplate) {
        Map<ResourceLocation, ParticleTemplate> templates = sharedTemplate == null ? Map.of()
                : Map.of(SHARED, ParticleTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(sharedTemplate)).getOrThrow());
        return Linker.link(templates, Map.of(ID, parse(effectJson)), Set.of(), Map.of(), t -> true);
    }

    private static BakedEffect bake(String effectJson) {
        Linker.Result result = link(effectJson, null);
        if (!result.errors().isEmpty()) fail(result.errors().toString());
        return BakedEffect.bake(result.effects().get(ID));
    }

    private static List<Spawn> tick(EffectInstance instance) {
        List<Spawn> spawns = new ArrayList<>();
        assertTrue(instance.prepare());
        instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values, frame) ->
                spawns.add(new Spawn(x, y, z, vx, vy, vz, life, values.size, values.gravity)), 1f, 1f);
        return spawns;
    }

    @Test
    void inputsParseInOrderAndBadNamesAreRejected() {
        ParticleEffect effect = parse("{\"duration\": 1, \"inputs\": {\"power\": 2, \"spread\": 0.5}, \"emitters\": [{\"duration\": 1, \"template\": "
                + DOT + ", \"spawn\": {\"rate\": \"power\"}}]}");
        assertEquals(List.of("power", "spread"), List.copyOf(effect.inputs().keySet()));
        assertEquals(2f, effect.inputs().get("power"));

        String base = "{\"duration\": 1, \"emitters\": [{\"duration\": 1, \"template\": " + DOT + ", \"spawn\": {\"rate\": 1}}], \"inputs\": ";
        assertTrue(parseError(base + "{\"distance\": 1}}").contains("'distance' is built in"));
        assertTrue(parseError(base + "{\"2fast\": 1}}").contains("input names use letters"));
        assertTrue(parseError(base + "{\"power\": \"high\"}}").contains("at inputs.power: expected a number"), parseError(base + "{\"power\": \"high\"}}"));
    }

    @Test
    void expressionErrorsCarryTheirPath() {
        String error = parseError("{\"duration\": 1, \"emitters\": [{\"duration\": 1, \"template\": " + DOT
                + ", \"spawn\": {\"rate\": \"power *\"}}]}");
        assertTrue(error.startsWith("at emitters[0].spawn.rate: in 'power *' at column 8"), error);
    }

    @Test
    void undeclaredInputsFailToLinkWithASuggestion() {
        Linker.Result own = link("{\"duration\": 1, \"inputs\": {\"power\": 1}, \"emitters\": [{\"duration\": 1, \"template\": "
                + DOT + ", \"spawn\": {\"rate\": \"powr * 2\"}}]}", null);
        assertTrue(own.effects().isEmpty());
        assertEquals("at emitters[0]: uses input 'powr', which the effect doesn't declare (did you mean 'power'?); "
                + "add it to \"inputs\" with a default value", own.errors().get(0).message());

        String shared = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": \"heat * 10\"}";
        Linker.Result viaTemplate = link("{\"duration\": 1, \"emitters\": [{\"duration\": 1, \"template\": \"test:shared\", "
                + "\"spawn\": {\"rate\": 1}}]}", shared);
        assertTrue(viaTemplate.errors().get(0).message().startsWith("at emitters[0].template: uses input 'heat'"));

        Linker.Result declared = link("{\"duration\": 1, \"inputs\": {\"heat\": 1}, \"emitters\": [{\"duration\": 1, "
                + "\"template\": \"test:shared\", \"spawn\": {\"rate\": \"distance\"}}]}", shared);
        assertTrue(declared.errors().isEmpty(), "distance is built in, heat is declared");
    }

    @Test
    void emitterValuesFollowInputsTickByTick() {
        BakedEffect fx = bake("{\"duration\": 10, \"inputs\": {\"power\": 1}, \"emitters\": [{\"duration\": 10, \"template\": " + DOT
                + ", \"shape\": {\"type\": \"ring\", \"radius\": {\"keys\": [[0, 1], [1, 1]], \"scale\": \"power\"}},"
                + " \"spawn\": {\"rate\": \"power * 4\"}}]}");
        EffectInstance instance = new EffectInstance(fx, Anchor.fixed(0, 0, 0), PlayParams.of(1));
        List<Spawn> first = tick(instance);
        assertEquals(4, first.size());
        for (Spawn s : first) assertEquals(1.0, Math.hypot(s.x, s.z), 1e-5);

        assertTrue(instance.setInput("power", 2));
        assertFalse(instance.setInput("nope", 2));
        List<Spawn> second = tick(instance);
        assertEquals(8, second.size());
        for (Spawn s : second) assertEquals(2.0, Math.hypot(s.x, s.z), 1e-5);

        instance.setInput("power", -3);
        assertEquals(0, tick(instance).size(), "negative rates spawn nothing");
    }

    @Test
    void fractionalRatesCarryAcrossInputChanges() {
        BakedEffect fx = bake("{\"duration\": 20, \"inputs\": {\"rate\": 0.5}, \"emitters\": [{\"duration\": 20, \"template\": " + DOT
                + ", \"spawn\": {\"rate\": \"rate\"}}]}");
        EffectInstance instance = new EffectInstance(fx, Anchor.fixed(0, 0, 0), PlayParams.of(1));
        int total = 0;
        for (int i = 0; i < 10; i++) total += tick(instance).size();
        instance.setInput("rate", 0.25f);
        for (int i = 0; i < 8; i++) total += tick(instance).size();
        assertEquals(7, total); // 10 × 0.5 + 8 × 0.25
    }

    @Test
    void templateValuesAreFixedAtSpawnAndLifetimeIsClamped() {
        BakedEffect fx = bake("{\"duration\": 4, \"inputs\": {\"power\": 1}, \"emitters\": [{\"duration\": 4, \"template\": "
                + "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": \"power * 10\", \"size\": \"power\","
                + " \"physics\": {\"gravity\": \"power / 2\"}}, \"spawn\": {\"bursts\": [{\"at\": 0, \"count\": \"power\"}, {\"at\": 1, \"count\": \"power\"}]}}]}");
        EffectInstance instance = new EffectInstance(fx, Anchor.fixed(0, 0, 0), PlayParams.of(1));
        Spawn a = tick(instance).get(0);
        assertEquals(10, a.lifetime);
        assertEquals(1f, a.size);
        assertEquals(0.5f, a.gravity);

        instance.setInput("power", 3);
        List<Spawn> next = tick(instance);
        assertEquals(3, next.size());
        assertEquals(30, next.get(0).lifetime);
        assertEquals(3f, next.get(0).size);

        BakedEffect zero = bake("{\"duration\": 1, \"inputs\": {\"power\": 0}, \"emitters\": [{\"duration\": 1, \"template\": "
                + "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": \"power\"}, \"spawn\": {\"bursts\": [{\"count\": 1}]}}]}");
        assertEquals(1, tick(new EffectInstance(zero, Anchor.fixed(0, 0, 0), PlayParams.of(1))).get(0).lifetime);
    }

    @Test
    void anchorFacingTurnsOffsetsAndVelocities() {
        BakedEffect fx = bake("{\"duration\": 1, \"emitters\": [{\"duration\": 1, \"template\": " + DOT + ", \"offset\": [0, 0, 1],"
                + " \"velocity\": {\"type\": \"fixed\", \"direction\": [0, 0, 1], \"speed\": 0.5}, \"spawn\": {\"bursts\": [{\"count\": 1}]}}]}");
        Quaternionf facingWest = Rotations.fromYawPitchRoll(90f, 0f, 0f); // yaw 90 faces −X
        EffectInstance instance = new EffectInstance(fx, out -> {
            out.position.set(10, 0, 0);
            out.facing.set(facingWest);
            out.distance = 0f;
            return true;
        }, PlayParams.of(1));
        Spawn s = tick(instance).get(0);
        assertEquals(9.0 - 0.5 * 0.0, s.x, 1e-5);
        assertEquals(0.0, s.z, 1e-5);
        assertEquals(-0.5, s.vx, 1e-5);
    }

    @Test
    void twoPointAnchorsSetDistanceAndAimAlongZ() {
        BakedEffect fx = bake("{\"duration\": 1, \"emitters\": [{\"duration\": 1, \"template\": " + DOT
                + ", \"shape\": {\"type\": \"line\", \"from\": [0, 0, 0], \"to\": [0, 0, \"distance\"]},"
                + " \"spawn\": {\"bursts\": [{\"count\": \"distance * 4\"}]}}]}");
        EffectInstance instance = new EffectInstance(fx, out -> {
            out.position.set(0, 64, 0);
            Rotations.lookAt(5, 0, 0, out.facing);
            out.distance = 5f;
            return true;
        }, PlayParams.of(1));
        List<Spawn> spawns = tick(instance);
        assertEquals(20, spawns.size());
        for (Spawn s : spawns) {
            assertTrue(s.x >= -1e-5 && s.x <= 5 + 1e-5, "along +X: " + s);
            assertEquals(64.0, s.y, 1e-5);
            assertEquals(0.0, s.z, 1e-5);
        }
    }

    @Test
    void liveScaleRotationAndTintApplyToNewSpawns() {
        BakedEffect fx = bake("{\"duration\": 2, \"emitters\": [{\"duration\": 2, \"template\": " + DOT + ", \"offset\": [1, 0, 0],"
                + " \"spawn\": {\"rate\": 1}}]}");
        EffectInstance instance = new EffectInstance(fx, Anchor.fixed(0, 0, 0), PlayParams.of(1));
        assertEquals(1.0, tick(instance).get(0).x, 1e-6);
        instance.setScale(2f);
        instance.setRotation(Rotations.fromYawPitchRoll(180f, 0f, 0f));
        instance.setTint(0x112233);
        List<Integer> tints = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        instance.prepare();
        instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values, frame) -> {
            tints.add(tint);
            xs.add(x);
        }, 1f, 1f);
        assertEquals(List.of(0x112233), tints);
        assertEquals(-2.0, xs.get(0), 1e-5);
    }
}
