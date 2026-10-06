package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.load.LinkedEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record BakedEffect(ResourceLocation id, ParticleEffect definition, List<BakedEmitter> emitters) {

    public static BakedEffect bake(LinkedEffect linked) {
        List<BakedEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < linked.definition().emitters().size(); i++) {
            emitters.add(BakedEmitter.bake(i, linked.definition().emitters().get(i), BakedTemplate.bake(linked.templates().get(i))));
        }
        return new BakedEffect(linked.id(), linked.definition(), List.copyOf(emitters));
    }

    public int duration() {
        return definition.duration();
    }
}
