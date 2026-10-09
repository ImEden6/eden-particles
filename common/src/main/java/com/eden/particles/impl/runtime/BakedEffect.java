package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.expr.InputNames;
import com.eden.particles.impl.load.LinkedEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code defaults} holds each declared input's default at its {@link InputNames} id. */
public record BakedEffect(ResourceLocation id, ParticleEffect definition, List<BakedEmitter> emitters,
                          Map<String, Integer> inputIds, float[] defaults) {

    public static BakedEffect bake(LinkedEffect linked) {
        List<BakedEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < linked.definition().emitters().size(); i++) {
            emitters.add(BakedEmitter.bake(i, linked.definition().emitters().get(i), BakedTemplate.bake(linked.templates().get(i))));
        }
        Map<String, Integer> ids = new HashMap<>();
        linked.definition().inputs().keySet().forEach(name -> ids.put(name, InputNames.id(name)));
        float[] defaults = new float[InputNames.count()];
        linked.definition().inputs().forEach((name, value) -> defaults[ids.get(name)] = value);
        return new BakedEffect(linked.id(), linked.definition(), List.copyOf(emitters), Map.copyOf(ids), defaults);
    }

    public int duration() {
        return definition.duration();
    }
}
