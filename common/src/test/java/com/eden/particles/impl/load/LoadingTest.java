package com.eden.particles.impl.load;

import com.eden.particles.impl.expr.Expr;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingTest {

    private static final String MOTE = """
            {"render": {"type": "sprite", "sprite": "eden_particles:glow"}, "lifetime": 20}""";
    private static final String SMOKE = """
            {"render": {"type": "vanilla", "particle": "minecraft:smoke"}, "lifetime": 20}""";

    private static String effectUsing(String template) {
        return """
                {"duration": 10, "emitters": [{"duration": 10, "template": "%s", "spawn": {"rate": 1}}]}""".formatted(template);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static RawDefinition raw(String path, String json) {
        return new RawDefinition(id(path), "test_pack", JsonParser.parseString(json));
    }

    private static DefinitionStore store() {
        return new DefinitionStore(type -> type.equals(ResourceLocation.withDefaultNamespace("smoke")));
    }

    @Test
    void goodFilesLoadAndBadOnesAreReportedIndividually() {
        ParsedDefinitions parsed = ParsedDefinitions.parse(
                List.of(raw("mote", MOTE), raw("broken", "{\"lifetime\": 20}")),
                List.of(raw("ok", effectUsing("test:mote")), raw("typo", "{\"duraton\": 10}")),
                List.of());
        LoadReport report = store().replace(DefinitionStore.Layer.ASSETS, parsed);

        assertEquals(1, report.effects());
        assertEquals(1, report.templates());
        assertEquals(2, report.errors().size());
        assertEquals("template test:broken (pack 'test_pack'): missing required field 'render'", report.errors().get(0).toString());
        assertEquals("Loaded 1 effect and 1 template (2 errors)", report.summary());
    }

    @Test
    void linksReferencesAndSuggestsNearMisses() {
        DefinitionStore store = store();
        LoadReport report = store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.parse(
                List.of(raw("mote", MOTE)),
                List.of(raw("good", effectUsing("test:mote")), raw("bad", effectUsing("test:mtoe"))),
                List.of()));

        assertTrue(store.effect(id("good")).isPresent());
        assertEquals(store.effect(id("good")).orElseThrow().templates().get(0).lifetime().min(), Expr.constant(20));
        assertTrue(store.effect(id("bad")).isEmpty());
        assertEquals("at emitters[0].template: unknown template 'test:mtoe' (did you mean 'test:mote'?)", report.errors().get(0).message());
    }

    @Test
    void referencesToBrokenTemplatesSayWhy() {
        LoadReport report = store().replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.parse(
                List.of(raw("mote", "{\"render\": {\"type\": \"sprite\", \"sprite\": \"x:y\"}}")),
                List.of(raw("fx", effectUsing("test:mote"))),
                List.of()));
        assertEquals("at emitters[0].template: template 'test:mote' failed to load (see its error)", report.errors().get(1).message());
    }

    @Test
    void unreadableFilesCountAsFailedTemplates() {
        List<DefinitionError> readErrors = new ArrayList<>();
        readErrors.add(new DefinitionError(DefinitionError.Kind.TEMPLATE, id("mote"), "test_pack", "invalid JSON: Unterminated object"));
        LoadReport report = store().replace(DefinitionStore.Layer.ASSETS,
                ParsedDefinitions.parse(List.of(), List.of(raw("fx", effectUsing("test:mote"))), readErrors));
        assertEquals(2, report.errors().size());
        assertTrue(report.errors().get(1).message().contains("failed to load"));
    }

    @Test
    void vanillaParticleTypesAreChecked() {
        String flame = "{\"render\": {\"type\": \"vanilla\", \"particle\": \"minecraft:flaem\"}, \"lifetime\": 20}";
        DefinitionStore store = store();
        LoadReport report = store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.parse(
                List.of(raw("smoke", SMOKE), raw("flame", flame)),
                List.of(raw("smoky", effectUsing("test:smoke")), raw("fiery", effectUsing("test:flame"))),
                List.of()));
        assertEquals(Set.of(id("smoky")), store.effectIds());
        assertEquals("at render.particle: 'minecraft:flaem' is not a known particle type without options", report.errors().get(0).message());
    }

    @Test
    void higherLayersOverrideAndCanUseLowerTemplates() {
        DefinitionStore store = store();
        store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.parse(
                List.of(raw("mote", MOTE)),
                List.of(raw("fx", effectUsing("test:mote"))),
                List.of()));
        String longer = "{\"duration\": 99, \"emitters\": [{\"duration\": 10, \"template\": \"test:mote\", \"spawn\": {\"rate\": 1}}]}";
        LoadReport report = store.replace(DefinitionStore.Layer.DATAPACK, ParsedDefinitions.parse(
                List.of(), List.of(raw("fx", longer), raw("extra", effectUsing("test:mote"))), List.of()));

        assertTrue(report.errors().isEmpty(), report.errors().toString());
        assertEquals(99, store.effect(id("fx")).orElseThrow().definition().duration());
        assertEquals(2, report.effects());

        store.replace(DefinitionStore.Layer.DATAPACK, ParsedDefinitions.EMPTY);
        assertEquals(10, store.effect(id("fx")).orElseThrow().definition().duration());
        assertTrue(store.effect(id("extra")).isEmpty());
    }

    @Test
    void reloadListenersHearEveryReplace() {
        DefinitionStore store = store();
        List<LoadReport> heard = new ArrayList<>();
        store.onReload(heard::add);
        store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.EMPTY);
        store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.EMPTY);
        assertEquals(2, heard.size());
        assertEquals(heard.get(1), store.lastReport());
    }
}
