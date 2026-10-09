package com.eden.particles.impl.load;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Effect and template JSON registered from code, kept so the CODE layer can be rebuilt after each change. */
public final class CodeDefinitions {

    private static final String SOURCE = "registered in code";

    private final Map<ResourceLocation, JsonElement> effects = new LinkedHashMap<>();
    private final Map<ResourceLocation, JsonElement> templates = new LinkedHashMap<>();

    public void putEffect(ResourceLocation id, JsonElement json) {
        effects.put(id, json);
    }

    public void putTemplate(ResourceLocation id, JsonElement json) {
        templates.put(id, json);
    }

    /** @return whether an effect or template with this id was registered */
    public boolean remove(ResourceLocation id) {
        boolean effect = effects.remove(id) != null;
        boolean template = templates.remove(id) != null;
        return effect || template;
    }

    public ParsedDefinitions parse() {
        List<RawDefinition> templateDefs = new ArrayList<>();
        templates.forEach((id, json) -> templateDefs.add(new RawDefinition(id, SOURCE, json)));
        List<RawDefinition> effectDefs = new ArrayList<>();
        effects.forEach((id, json) -> effectDefs.add(new RawDefinition(id, SOURCE, json)));
        return ParsedDefinitions.parse(templateDefs, effectDefs, List.of());
    }

    /** @return the messages of the report's errors for this id, empty if it has none */
    public static List<String> errorsFor(LoadReport report, ResourceLocation id) {
        return report.errors().stream().filter(e -> e.id().equals(id)).map(DefinitionError::message).toList();
    }
}
