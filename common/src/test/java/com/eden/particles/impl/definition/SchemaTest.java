package com.eden.particles.impl.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of(System.getProperty("eden.root", ".."));
    private static final Path SCHEMA = ROOT.resolve("docs/schema/effect.schema.json");
    private static final List<Path> PACKS = List.of(
            ROOT.resolve("common/src/main/resources/assets/eden_particles/eden_particles"),
            ROOT.resolve("scripts/smoke-datapack/data/eden_smoke/eden_particles"));

    @Test
    void everyShippedFileMatchesTheSchema() throws IOException {
        JsonSchema effect = schema("effect");
        JsonSchema template = schema("template");
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path pack : PACKS) {
            checked += check(pack.resolve("effects"), effect, problems);
            checked += check(pack.resolve("templates"), template, problems);
        }
        assertTrue(checked > 20, "only found " + checked + " files");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void schemaCatchesTypos() throws IOException {
        JsonNode bad = JSON.readTree("{\"duration\": 10, \"emitters\": [{\"template\": \"a:b\", \"duration\": 10, \"spawn\": {\"rate\": 1}, \"ofset\": [0, 1, 0]}]}");
        assertFalse(schema("effect").validate(bad).isEmpty());
    }

    private static JsonSchema schema(String root) throws IOException {
        ObjectNode node = (ObjectNode) JSON.readTree(SCHEMA.toFile());
        node.put("$ref", "#/$defs/" + root);
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(node);
    }

    private static int check(Path dir, JsonSchema schema, List<String> problems) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).toList();
        }
        for (Path file : files) {
            Set<ValidationMessage> errors = schema.validate(JSON.readTree(file.toFile()));
            if (!errors.isEmpty()) problems.add(ROOT.relativize(file) + ": " + errors.iterator().next());
        }
        return files.size();
    }
}
