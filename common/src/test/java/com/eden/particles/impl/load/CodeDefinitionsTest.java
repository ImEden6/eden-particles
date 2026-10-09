package com.eden.particles.impl.load;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeDefinitionsTest {

    private static final String DOT = """
            {"render": {"type": "sprite", "sprite": "test:dot"}, "lifetime": 20}""";

    private static String effect(int duration) {
        return """
                {"duration": %d, "emitters": [{"duration": 10, "template": "test:dot", "spawn": {"rate": 1}}]}""".formatted(duration);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static RawDefinition raw(String path, String json) {
        return new RawDefinition(id(path), "test_pack", JsonParser.parseString(json));
    }

    private static DefinitionStore store() {
        return new DefinitionStore(type -> true);
    }

    private static CodeDefinitions codeWith(int duration) {
        CodeDefinitions code = new CodeDefinitions();
        code.putTemplate(id("dot"), JsonParser.parseString(DOT));
        code.putEffect(id("fx"), JsonParser.parseString(effect(duration)));
        return code;
    }

    @Test
    void effectUsingRegisteredTemplateLinks() {
        DefinitionStore store = store();
        LoadReport report = store.replace(DefinitionStore.Layer.CODE, codeWith(10).parse());

        assertTrue(report.errors().isEmpty(), report.errors().toString());
        assertTrue(store.effect(id("fx")).isPresent());
    }

    @Test
    void badEffectErrorsAreReportedForItsIdOnly() {
        CodeDefinitions code = codeWith(10);
        code.putEffect(id("bad"), JsonParser.parseString("{\"duraton\": 10}"));
        LoadReport report = store().replace(DefinitionStore.Layer.CODE, code.parse());

        List<String> errors = CodeDefinitions.errorsFor(report, id("bad"));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(m -> m.contains("duration") || m.contains("duraton")), errors.toString());
        assertTrue(CodeDefinitions.errorsFor(report, id("fx")).isEmpty());
        assertEquals(1, report.errors().size());
    }

    @Test
    void layersOverrideInOrderAssetsCodeDatapack() {
        DefinitionStore store = store();
        store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.parse(
                List.of(), List.of(raw("fx", effect(10))), List.of()));
        store.replace(DefinitionStore.Layer.CODE, codeWith(20).parse());
        assertEquals(20, store.effect(id("fx")).orElseThrow().definition().duration());

        store.replace(DefinitionStore.Layer.DATAPACK, ParsedDefinitions.parse(
                List.of(), List.of(raw("fx", effect(30))), List.of()));
        assertEquals(30, store.effect(id("fx")).orElseThrow().definition().duration());

        store.replace(DefinitionStore.Layer.DATAPACK, ParsedDefinitions.EMPTY);
        assertEquals(20, store.effect(id("fx")).orElseThrow().definition().duration());
    }

    @Test
    void removeReportsWhetherAnythingWasRemoved() {
        CodeDefinitions code = codeWith(10);
        DefinitionStore store = store();
        store.replace(DefinitionStore.Layer.CODE, code.parse());
        assertTrue(store.effect(id("fx")).isPresent());

        assertTrue(code.remove(id("fx")));
        assertFalse(code.remove(id("fx")));
        store.replace(DefinitionStore.Layer.CODE, code.parse());
        assertTrue(store.effect(id("fx")).isEmpty());
        assertFalse(store.effectIds().contains(id("fx")));
    }
}
