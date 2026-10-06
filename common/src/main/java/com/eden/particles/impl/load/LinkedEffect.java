package com.eden.particles.impl.load;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** An effect with every emitter's template resolved; {@code templates} is index-aligned with the emitters. */
public record LinkedEffect(ResourceLocation id, ParticleEffect definition, List<ParticleTemplate> templates) {
}
