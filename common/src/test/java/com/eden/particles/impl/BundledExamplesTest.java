package com.eden.particles.impl;

import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.BakedEffect;
import com.eden.particles.impl.runtime.EffectHost;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.PlayParams;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Everything the mod ships must load without errors and actually spawn particles. */
class BundledExamplesTest {

    @Test
    void everyExampleLoadsAndSpawns() {
        TestDefinitions.Loaded loaded = TestDefinitions.load(TestDefinitions.BUNDLED);
        assertTrue(loaded.report().errors().isEmpty(), loaded.report().errors().toString());
        assertEquals(23, loaded.report().effects(), loaded.store().effectIds().toString());

        for (Map.Entry<ResourceLocation, BakedEffect> entry : loaded.baked().entrySet()) {
            ResourceLocation id = entry.getKey();
            List<EffectInstance> active = new ArrayList<>();
            EffectHost host = (child, anchor, params) -> {
                EffectInstance instance = new EffectInstance(loaded.baked().get(child), anchor, params);
                active.add(instance);
                return instance;
            };
            EffectInstance root = new EffectInstance(entry.getValue(), Anchor.fixed(0, 64, 0), PlayParams.of(id.hashCode()));
            active.add(root);
            int spawned = 0;
            for (int tick = 0; tick < 200 && !active.isEmpty(); tick++) {
                for (int i = 0; i < active.size(); i++) {
                    EffectInstance instance = active.get(i);
                    instance.setHost(host);
                    if (!instance.prepare()) continue;
                    spawned += instance.tick((t, x, y, z, vx, vy, vz, life, scale, tint, variant, values, frame) -> {
                        assertTrue(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z), id + " spawned at a non-finite position");
                        assertTrue(Double.isFinite(vx) && Double.isFinite(vy) && Double.isFinite(vz), id + " has a non-finite velocity");
                        assertTrue(life >= 1, id + " lifetime " + life);
                    }, 1f, 1f);
                }
                active.removeIf(EffectInstance::isFinished);
            }
            assertTrue(spawned > 0, id + " never spawned anything");
        }
    }
}
