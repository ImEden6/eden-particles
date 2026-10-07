package com.eden.particles.impl.load;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scans {@code <root>/<ns>/eden_particles/{templates,effects}/**.json} in a resource manager: {@code assets} on the
 * client, {@code data} on the server. Unreadable or malformed JSON becomes an error instead of being skipped silently.
 */
public final class DefinitionFiles {

    private static final FileToIdConverter TEMPLATES = FileToIdConverter.json("eden_particles/templates");
    private static final FileToIdConverter EFFECTS = FileToIdConverter.json("eden_particles/effects");

    public record Read(List<RawDefinition> templates, List<RawDefinition> effects, List<DefinitionError> errors) {

        public ParsedDefinitions parse() {
            return ParsedDefinitions.parse(templates, effects, errors);
        }
    }

    private DefinitionFiles() {
    }

    public static Read read(ResourceManager resources) {
        List<DefinitionError> errors = new ArrayList<>();
        List<RawDefinition> templates = read(resources, TEMPLATES, DefinitionError.Kind.TEMPLATE, errors);
        List<RawDefinition> effects = read(resources, EFFECTS, DefinitionError.Kind.EFFECT, errors);
        return new Read(templates, effects, errors);
    }

    private static List<RawDefinition> read(ResourceManager resources, FileToIdConverter converter,
                                            DefinitionError.Kind kind, List<DefinitionError> errors) {
        List<RawDefinition> raws = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : converter.listMatchingResources(resources).entrySet()) {
            ResourceLocation id = converter.fileToId(entry.getKey());
            String source = entry.getValue().sourcePackId();
            try (Reader reader = entry.getValue().openAsReader()) {
                raws.add(new RawDefinition(id, source, JsonParser.parseReader(reader)));
            } catch (IOException | JsonParseException e) {
                errors.add(new DefinitionError(kind, id, source, "invalid JSON: " + rootMessage(e)));
            }
        }
        return raws;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
