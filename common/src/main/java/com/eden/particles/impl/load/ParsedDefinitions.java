package com.eden.particles.impl.load;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One layer's parsed definitions. A bad file is reported and skipped; it never aborts the others.
 *
 * @param sources pack each parsed template or effect came from
 */
public record ParsedDefinitions(
        Map<ResourceLocation, ParticleTemplate> templates,
        Map<ResourceLocation, ParticleEffect> effects,
        Set<ResourceLocation> failedTemplates,
        Map<ResourceLocation, String> sources,
        List<DefinitionError> errors) {

    public static final ParsedDefinitions EMPTY = new ParsedDefinitions(Map.of(), Map.of(), Set.of(), Map.of(), List.of());

    /** @param readErrors files that could not be read or were not valid JSON */
    public static ParsedDefinitions parse(Collection<RawDefinition> templates, Collection<RawDefinition> effects,
                                          List<DefinitionError> readErrors) {
        List<DefinitionError> errors = new ArrayList<>(readErrors);
        Set<ResourceLocation> failed = new HashSet<>();
        readErrors.stream().filter(e -> e.kind() == DefinitionError.Kind.TEMPLATE).forEach(e -> failed.add(e.id()));
        Map<ResourceLocation, String> sources = new HashMap<>();

        Map<ResourceLocation, ParticleTemplate> parsedTemplates = new LinkedHashMap<>();
        for (RawDefinition raw : templates) {
            parse(raw, ParticleTemplate.CODEC, DefinitionError.Kind.TEMPLATE, errors).ifPresentOrElse(t -> {
                parsedTemplates.put(raw.id(), t);
                sources.put(raw.id(), raw.source());
            }, () -> failed.add(raw.id()));
        }
        Map<ResourceLocation, ParticleEffect> parsedEffects = new LinkedHashMap<>();
        for (RawDefinition raw : effects) {
            parse(raw, ParticleEffect.CODEC, DefinitionError.Kind.EFFECT, errors).ifPresent(e -> {
                parsedEffects.put(raw.id(), e);
                sources.put(raw.id(), raw.source());
            });
        }
        return new ParsedDefinitions(Map.copyOf(parsedTemplates), Map.copyOf(parsedEffects), Set.copyOf(failed),
                Map.copyOf(sources), List.copyOf(errors));
    }

    private static <A> java.util.Optional<A> parse(RawDefinition raw, Codec<A> codec, DefinitionError.Kind kind, List<DefinitionError> errors) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, raw.json());
        result.error().ifPresent(e -> errors.add(new DefinitionError(kind, raw.id(), raw.source(), e.message())));
        return result.result();
    }
}
