package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.definition.SubEffect;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.Linker;
import com.eden.particles.impl.math.Rotations;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SubEffectsTest {

    private static final String DOT = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": 20}";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static ParticleEffect parse(String json) {
        DataResult<ParticleEffect> result = ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.result().orElseGet(() -> fail(result.error().orElseThrow().message()));
    }

    private static String parseError(String json) {
        return ParticleEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().orElseThrow().message();
    }

    private static String templateError(String json) {
        return ParticleTemplate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().orElseThrow().message();
    }

    /** Effects by path, as JSON. */
    private static Linker.Result link(Map<String, String> effects) {
        Map<ResourceLocation, ParticleEffect> parsed = new LinkedHashMap<>();
        effects.forEach((path, json) -> parsed.put(id(path), parse(json)));
        return Linker.link(Map.of(), parsed, Set.of(), Map.of(), t -> true);
    }

    private static Map<ResourceLocation, String> errors(Linker.Result result) {
        Map<ResourceLocation, String> errors = new HashMap<>();
        for (DefinitionError e : result.errors()) errors.put(e.id(), e.message());
        return errors;
    }

    /** A tiny manager: plays effects by id, ticking children in the same pass after their parent. */
    private static final class Host implements EffectHost {
        final Map<ResourceLocation, BakedEffect> baked = new HashMap<>();
        final List<EffectInstance> active = new ArrayList<>();
        final List<String> started = new ArrayList<>();
        final Map<EffectInstance, List<double[]>> spawns = new HashMap<>();
        int tick;

        Host(Linker.Result result) {
            if (!result.errors().isEmpty()) fail(result.errors().toString());
            result.effects().forEach((id, linked) -> baked.put(id, BakedEffect.bake(linked)));
        }

        @Override
        public EffectInstance start(ResourceLocation effect, Anchor anchor, PlayParams params) {
            BakedEffect fx = baked.get(effect);
            if (fx == null) return null;
            EffectInstance instance = new EffectInstance(fx, anchor, params);
            instance.setHost(this);
            active.add(instance);
            started.add(tick + ":" + effect.getPath());
            return instance;
        }

        void tick() {
            for (int i = 0; i < active.size(); i++) {
                EffectInstance instance = active.get(i);
                if (!instance.prepare()) continue;
                List<double[]> at = spawns.computeIfAbsent(instance, k -> new ArrayList<>());
                instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values) -> at.add(new double[]{x, z}), 1f, 1f);
            }
            active.removeIf(EffectInstance::isFinished);
            tick++;
        }
    }

    private static final String CHILD = "{\"duration\": 10, \"inputs\": {\"power\": 0}, \"emitters\": [{\"duration\": 10, \"template\": " + DOT
            + ", \"offset\": [\"power\", 0, 0], \"spawn\": {\"rate\": 1}}]}";

    @Test
    void subEffectsMixWithEmittersAndRunToTheEndByDefault() {
        ParticleEffect effect = parse("{\"duration\": 20, \"emitters\": [{\"duration\": 20, \"template\": " + DOT
                + ", \"spawn\": {\"rate\": 1}}, {\"effect\": \"test:child\", \"start\": 5}]}");
        assertEquals(1, effect.emitters().size());
        SubEffect sub = effect.subEffects().get(0);
        assertEquals(15, sub.duration());
        assertEquals(20, sub.end());
        assertTrue(parseError("{\"duration\": 5, \"emitters\": [{\"effect\": \"test:child\", \"start\": 5}]}")
                .contains("at emitters[0]: starts at tick 5, past the effect's duration of 5"));
        assertTrue(parseError("{\"duration\": 5, \"emitters\": [{\"effect\": \"test:child\", \"start\": 2, \"duration\": 4}]}")
                .contains("runs from tick 2 to 6, past the effect's duration of 5"));
    }

    @Test
    void triggersAreCheckedWhenTemplatesParse() {
        String landing = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": 5, \"on_land\": {\"effect\": \"test:splash\"}}";
        assertTrue(templateError(landing).contains("at on_land: needs \"physics\": {\"collide\": true}"));
        String vanilla = "{\"render\": {\"type\": \"vanilla\", \"particle\": \"minecraft:flame\"}, \"lifetime\": 5, \"on_death\": {\"effect\": \"test:pop\"}}";
        assertTrue(templateError(vanilla).contains("remove [on_death]"));
        String chance = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": 5, \"on_death\": {\"effect\": \"test:pop\", \"chance\": 2}}";
        assertTrue(templateError(chance).contains("at on_death.chance: must be between 0.0 and 1.0"));
    }

    @Test
    void missingAndFailedChildrenFailTheirParents() {
        Map<ResourceLocation, String> errors = errors(link(Map.of(
                "parent", "{\"duration\": 5, \"emitters\": [{\"effect\": \"test:chlid\"}]}",
                "child", CHILD,
                "uses_broken", "{\"duration\": 5, \"emitters\": [{\"effect\": \"test:broken\"}]}",
                "broken", "{\"duration\": 5, \"emitters\": [{\"duration\": 5, \"template\": \"test:nope\", \"spawn\": {\"rate\": 1}}]}")));
        assertEquals("at emitters[0].effect: unknown effect 'test:chlid' (did you mean 'test:child'?)", errors.get(id("parent")));
        assertEquals("at emitters[0].effect: effect 'test:broken' failed to load (see its error)", errors.get(id("uses_broken")));
        assertFalse(errors.containsKey(id("child")));
    }

    @Test
    void loopsThroughSubEffectsOrTriggersAreRejected() {
        String pop = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": 5, \"on_death\": {\"effect\": \"test:a\"}}";
        Linker.Result result = link(Map.of(
                "a", "{\"duration\": 5, \"emitters\": [{\"effect\": \"test:b\"}]}",
                "b", "{\"duration\": 5, \"emitters\": [{\"duration\": 5, \"template\": " + pop + ", \"spawn\": {\"rate\": 1}}]}",
                "c", "{\"duration\": 5, \"emitters\": [{\"effect\": \"test:a\"}]}",
                "ok", CHILD));
        Map<ResourceLocation, String> errors = errors(result);
        assertTrue(errors.get(id("a")).startsWith("plays itself in a loop: test:"), errors.toString());
        assertTrue(errors.get(id("b")).startsWith("plays itself in a loop: test:"), errors.toString());
        assertEquals("at emitters[0].effect: effect 'test:a' failed to load (see its error)", errors.get(id("c")));
        assertEquals(Set.of(id("ok")), result.effects().keySet());
    }

    @Test
    void inputsPassedToChildrenMustBeDeclaredOnBothSides() {
        Map<ResourceLocation, String> errors = errors(link(Map.of(
                "child", CHILD,
                "typo", "{\"duration\": 5, \"emitters\": [{\"effect\": \"test:child\", \"inputs\": {\"powr\": 1}}]}",
                "parent_missing", "{\"duration\": 5, \"emitters\": [{\"effect\": \"test:child\", \"inputs\": {\"power\": \"heat\"}}]}")));
        assertEquals("at emitters[0].effect: sets input 'powr', which 'test:child' doesn't declare (did you mean 'power'?)", errors.get(id("typo")));
        assertTrue(errors.get(id("parent_missing")).startsWith("at emitters[0]: uses input 'heat'"));
    }

    @Test
    void childrenStartOnTimeFollowTheParentAndStopAtTheEndOfTheirWindow() {
        Host host = new Host(link(Map.of("child", CHILD,
                "parent", "{\"duration\": 10, \"inputs\": {\"power\": 1}, \"emitters\": [{\"effect\": \"test:child\", \"start\": 2, \"duration\": 3,"
                        + " \"offset\": [0, 0, 1], \"inputs\": {\"power\": \"power\"}}]}")));
        EffectInstance parent = host.start(id("parent"), out -> {
            out.position.set(10, 0, 0);
            out.facing.set(Rotations.fromYawPitchRoll(90f, 0f, 0f)); // +Z faces −X
            out.distance = 0f;
            return true;
        }, PlayParams.of(1));
        host.tick();
        host.tick();
        assertEquals(List.of("0:parent"), host.started);
        host.tick();
        assertEquals(List.of("0:parent", "2:child"), host.started, "started on tick 2, in the same pass");
        EffectInstance child = host.active.get(1);
        // The parent's offset [0, 0, 1] and the child's own [power, 0, 0] both turn with the parent: +Z → −X, +X → +Z.
        assertSpawn(9, 1, host.spawns.get(child).get(0));

        parent.setInput("power", 3);
        host.tick();
        assertSpawn(9, 3, host.spawns.get(child).get(1));
        host.tick();
        host.tick();
        assertTrue(child.isFinished(), "stopped after its 3-tick window");
        assertEquals(3, host.spawns.get(child).size());
    }

    private static void assertSpawn(double x, double z, double[] actual) {
        assertEquals(x, actual[0], 1e-5);
        assertEquals(z, actual[1], 1e-5);
    }

    @Test
    void loopingParentsReplayChildrenAndLateJoinersStartThemMidway() {
        Linker.Result linked = link(Map.of("child", CHILD,
                "parent", "{\"duration\": 4, \"loop\": true, \"emitters\": [{\"effect\": \"test:child\", \"start\": 1}]}"));
        Host host = new Host(linked);
        EffectInstance parent = host.start(id("parent"), Anchor.fixed(0, 0, 0), PlayParams.of(1));
        for (int i = 0; i < 9; i++) host.tick();
        assertEquals(List.of("0:parent", "1:child", "5:child"), host.started);

        Host late = new Host(linked);
        EffectInstance joiner = late.start(id("parent"), Anchor.fixed(0, 0, 0), PlayParams.of(1));
        joiner.fastForward(6); // second loop, tick 2: inside the child's window
        late.tick();
        assertEquals(List.of("0:parent", "0:child"), late.started);

        parent.stop();
        host.tick();
        assertTrue(host.active.isEmpty(), "stopping the parent stops its children");
    }

    @Test
    void triggerRollsAreDeterministicAndMatchTheChance() {
        int hits = 0;
        for (int i = 0; i < 10_000; i++) {
            float variant = i / 10_000f;
            boolean a = Triggers.roll(variant, Triggers.DEATH_SALT, 0.3f);
            assertEquals(a, Triggers.roll(variant, Triggers.DEATH_SALT, 0.3f));
            if (a) hits++;
        }
        assertEquals(3000, hits, 150);
        assertTrue(Triggers.roll(0.5f, Triggers.LAND_SALT, 1f));
        assertFalse(Triggers.roll(0.5f, Triggers.LAND_SALT, 0f));
    }

    @Test
    void triggerValuesAreEvaluatedFromTheSpawningEffect() {
        String template = "{\"render\": {\"type\": \"sprite\", \"sprite\": \"test:dot\"}, \"lifetime\": 5, \"physics\": {\"collide\": true},"
                + " \"on_death\": {\"effect\": \"test:child\", \"chance\": \"power / 4\", \"inputs\": {\"power\": \"power * 2\"}},"
                + " \"on_land\": {\"effect\": \"test:child\", \"aim\": \"velocity\"}}";
        Host host = new Host(link(Map.of("child", CHILD,
                "fx", "{\"duration\": 2, \"inputs\": {\"power\": 1}, \"emitters\": [{\"duration\": 2, \"template\": " + template
                        + ", \"spawn\": {\"rate\": 1}}]}")));
        EffectInstance fx = host.start(id("fx"), Anchor.fixed(0, 0, 0), PlayParams.of(1));
        List<TemplateValues> seen = new ArrayList<>();
        fx.prepare();
        fx.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values) -> {
            assertTrue(t.onDeath().isPresent() && t.onLand().isPresent());
            TemplateValues copy = new TemplateValues();
            copy.deathChance = values.deathChance;
            copy.deathInputs = values.deathInputs;
            copy.landChance = values.landChance;
            seen.add(copy);
        }, 1f, 1f);
        assertEquals(0.25f, seen.get(0).deathChance);
        assertEquals(2f, seen.get(0).deathInputs[0]);
        assertEquals(1f, seen.get(0).landChance);
    }
}
