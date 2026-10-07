package com.eden.particles.impl;

import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.BakedEffect;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.PlayParams;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Everything the mod ships must load without errors and actually spawn particles. */
class BundledExamplesTest {

    private static List<RawDefinition> read(String kind) throws IOException, URISyntaxException {
        Path root = Path.of(BundledExamplesTest.class.getResource("/assets/eden_particles/eden_particles/" + kind).toURI());
        List<RawDefinition> raws = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String path = root.relativize(file).toString().replace('\\', '/').replaceAll("\\.json$", "");
                try (Reader reader = Files.newBufferedReader(file)) {
                    raws.add(new RawDefinition(ResourceLocation.fromNamespaceAndPath("eden_particles", path), "mod", JsonParser.parseReader(reader)));
                }
            }
        }
        return raws;
    }

    @Test
    void everyExampleLoadsAndSpawns() throws Exception {
        DefinitionStore store = new DefinitionStore(type -> type.equals(ResourceLocation.withDefaultNamespace("campfire_cosy_smoke")));
        LoadReport report = store.replace(DefinitionStore.Layer.ASSETS, ParsedDefinitions.parse(read("templates"), read("effects"), List.of()));

        assertTrue(report.errors().isEmpty(), report.errors().toString());
        assertEquals(7, report.effects(), store.effectIds().toString());

        for (ResourceLocation id : store.effectIds()) {
            BakedEffect effect = BakedEffect.bake(store.effect(id).orElseThrow());
            EffectInstance instance = new EffectInstance(effect, Anchor.fixed(0, 64, 0), PlayParams.of(id.hashCode()));
            int spawned = 0;
            for (int tick = 0; tick < 200 && instance.prepare(); tick++) {
                spawned += instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant) -> {
                    assertTrue(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z), id + " spawned at a non-finite position");
                    assertTrue(Double.isFinite(vx) && Double.isFinite(vy) && Double.isFinite(vz), id + " has a non-finite velocity");
                    assertTrue(life >= 1, id + " lifetime " + life);
                }, 1f, 1f);
            }
            assertTrue(spawned > 0, id + " never spawned anything");
        }
    }
}
