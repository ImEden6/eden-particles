package com.eden.particles.impl;

import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.impl.runtime.BakedEffect;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Loads definition folders from the test classpath the way the mod does, for tests that need real effects. */
public final class TestDefinitions {

    public static final String BUNDLED = "/assets/eden_particles/eden_particles";
    public static final String FIXTURES = "/fixtures";

    private static final Set<ResourceLocation> VANILLA_TYPES = Set.of(
            ResourceLocation.withDefaultNamespace("campfire_cosy_smoke"),
            ResourceLocation.withDefaultNamespace("smoke"));

    private TestDefinitions() {
    }

    public record Loaded(DefinitionStore store, LoadReport report) {

        /** Every effect, baked, in id order. */
        public Map<ResourceLocation, BakedEffect> baked() {
            Map<ResourceLocation, BakedEffect> baked = new TreeMap<>();
            for (ResourceLocation id : store.effectIds()) baked.put(id, BakedEffect.bake(store.effect(id).orElseThrow()));
            return baked;
        }
    }

    /** Reads {@code <root>/templates} and {@code <root>/effects} under the {@code eden_particles} namespace. */
    public static Loaded load(String root) {
        DefinitionStore store = new DefinitionStore(VANILLA_TYPES::contains);
        LoadReport report = store.replace(DefinitionStore.Layer.ASSETS,
                ParsedDefinitions.parse(read(root + "/templates"), read(root + "/effects"), List.of()));
        return new Loaded(store, report);
    }

    private static List<RawDefinition> read(String folder) {
        try {
            Path root = Path.of(TestDefinitions.class.getResource(folder).toURI());
            List<RawDefinition> raws = new ArrayList<>();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    String path = root.relativize(file).toString().replace('\\', '/').replaceAll("\\.json$", "");
                    try (Reader reader = Files.newBufferedReader(file)) {
                        raws.add(new RawDefinition(ResourceLocation.fromNamespaceAndPath("eden_particles", path), "test", JsonParser.parseReader(reader)));
                    }
                }
            }
            return raws;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
