package com.eden.particles.impl.load;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.TemplateRef;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Resolves template references. An effect with any unresolvable emitter is dropped and reported. */
public final class Linker {

    public record Result(Map<ResourceLocation, LinkedEffect> effects, List<DefinitionError> errors) {
    }

    private Linker() {
    }

    /**
     * @param sources           pack each effect or template came from, for error messages
     * @param particleTypeExists whether a vanilla render mode's particle type is registered
     */
    public static Result link(Map<ResourceLocation, ParticleTemplate> templates,
                              Map<ResourceLocation, ParticleEffect> effects,
                              Set<ResourceLocation> failedTemplates,
                              Map<ResourceLocation, String> sources,
                              Predicate<ResourceLocation> particleTypeExists) {
        List<DefinitionError> errors = new ArrayList<>();
        Set<ResourceLocation> badTemplates = new HashSet<>(failedTemplates);

        for (Map.Entry<ResourceLocation, ParticleTemplate> entry : templates.entrySet()) {
            String problem = checkTemplate(entry.getValue(), particleTypeExists);
            if (problem != null) {
                errors.add(new DefinitionError(DefinitionError.Kind.TEMPLATE, entry.getKey(), sources.getOrDefault(entry.getKey(), ""), problem));
                badTemplates.add(entry.getKey());
            }
        }

        List<String> knownIds = templates.keySet().stream().map(ResourceLocation::toString).toList();
        Map<ResourceLocation, LinkedEffect> linked = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, ParticleEffect> entry : effects.entrySet()) {
            ParticleEffect effect = entry.getValue();
            List<ParticleTemplate> resolved = new ArrayList<>(effect.emitters().size());
            String problem = null;
            for (int i = 0; i < effect.emitters().size() && problem == null; i++) {
                EmitterTrack track = effect.emitters().get(i);
                String at = "at emitters[" + i + "].template: ";
                switch (track.template()) {
                    case TemplateRef.Inline inline -> {
                        String inlineProblem = checkTemplate(inline.template(), particleTypeExists);
                        if (inlineProblem != null) problem = at + inlineProblem;
                        else resolved.add(inline.template());
                    }
                    case TemplateRef.Reference ref -> {
                        ParticleTemplate template = templates.get(ref.id());
                        if (badTemplates.contains(ref.id())) {
                            problem = at + "template '" + ref.id() + "' failed to load (see its error)";
                        } else if (template == null) {
                            problem = at + "unknown template '" + ref.id() + "'" + Codecs.didYouMean(ref.id().toString(), knownIds);
                        } else {
                            resolved.add(template);
                        }
                    }
                }
            }
            if (problem != null) {
                errors.add(new DefinitionError(DefinitionError.Kind.EFFECT, entry.getKey(), sources.getOrDefault(entry.getKey(), ""), problem));
            } else {
                linked.put(entry.getKey(), new LinkedEffect(entry.getKey(), effect, List.copyOf(resolved)));
            }
        }
        return new Result(Map.copyOf(linked), List.copyOf(errors));
    }

    private static String checkTemplate(ParticleTemplate template, Predicate<ResourceLocation> particleTypeExists) {
        if (template.render() instanceof RenderMode.Vanilla vanilla && !particleTypeExists.test(vanilla.particle())) {
            return "at render.particle: unknown particle type '" + vanilla.particle() + "'";
        }
        return null;
    }
}
