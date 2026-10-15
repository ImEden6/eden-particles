package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.LightCue;
import com.eden.particles.impl.definition.SoundCue;
import com.eden.particles.impl.definition.SubEffect;
import com.eden.particles.impl.expr.InputNames;
import com.eden.particles.impl.load.LinkedEffect;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code defaults} holds each declared input's default at its {@link InputNames} id. */
public record BakedEffect(ResourceLocation id, ParticleEffect definition, List<BakedEmitter> emitters,
                          List<BakedSubEffect> subEffects, List<SoundCue> sounds, List<BakedLight> lights,
                          Map<String, Integer> inputIds, float[] defaults) {

    public static BakedEffect bake(LinkedEffect linked) {
        List<BakedEmitter> emitters = new ArrayList<>();
        List<EmitterTrack> tracks = linked.definition().emitters();
        for (int i = 0; i < tracks.size(); i++) {
            emitters.add(BakedEmitter.bake(i, tracks.get(i), BakedTemplate.bake(linked.templates().get(i))));
        }
        List<BakedSubEffect> subEffects = new ArrayList<>();
        List<SubEffect> subs = linked.definition().subEffects();
        for (int i = 0; i < subs.size(); i++) subEffects.add(BakedSubEffect.bake(i, subs.get(i)));
        Map<String, Integer> ids = new HashMap<>();
        linked.definition().inputs().keySet().forEach(name -> ids.put(name, InputNames.id(name)));
        float[] defaults = new float[InputNames.count()];
        linked.definition().inputs().forEach((name, value) -> defaults[ids.get(name)] = value);
        return new BakedEffect(linked.id(), linked.definition(), List.copyOf(emitters), List.copyOf(subEffects),
                linked.definition().sounds(), linked.definition().lights().stream().map(BakedLight::bake).toList(),
                Map.copyOf(ids), defaults);
    }

    public int duration() {
        return definition.duration();
    }
}
