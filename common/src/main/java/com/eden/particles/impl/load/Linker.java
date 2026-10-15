package com.eden.particles.impl.load;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.LightCue;
import com.eden.particles.impl.definition.SoundCue;
import com.eden.particles.impl.definition.SubEffect;
import com.eden.particles.impl.definition.Track;
import com.eden.particles.impl.definition.TemplateRef;
import com.eden.particles.impl.expr.InputNames;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
            List<ParticleTemplate> resolved = new ArrayList<>(effect.tracks().size());
            String problem = null;
            for (int i = 0; i < effect.tracks().size() && problem == null; i++) {
                if (!(effect.tracks().get(i) instanceof EmitterTrack track)) continue;
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
            if (problem == null) problem = checkInputs(effect, resolved);
            if (problem != null) {
                errors.add(new DefinitionError(DefinitionError.Kind.EFFECT, entry.getKey(), sources.getOrDefault(entry.getKey(), ""), problem));
            } else {
                linked.put(entry.getKey(), new LinkedEffect(entry.getKey(), effect, List.copyOf(resolved)));
            }
        }
        linkReferences(linked, effects.keySet(), sources, errors);
        return new Result(Map.copyOf(linked), List.copyOf(errors));
    }

    /** An effect played by another: as a sub-effect, or by a template when its particles die or land. */
    private record Reference(String at, ResourceLocation target, Set<String> inputs) {
    }

    private static List<Reference> references(LinkedEffect effect) {
        List<Reference> refs = new ArrayList<>();
        List<Track> tracks = effect.definition().tracks();
        int emitter = 0;
        for (int i = 0; i < tracks.size(); i++) {
            switch (tracks.get(i)) {
                case SubEffect sub -> refs.add(new Reference("at emitters[" + i + "].effect: ", sub.effect(), sub.inputs().keySet()));
                case SoundCue cue -> {
                }
                case LightCue light -> {
                }
                case EmitterTrack track -> {
                    ParticleTemplate template = effect.templates().get(emitter++);
                    String at = "at emitters[" + i + "].template.";
                    template.onDeath().ifPresent(t -> refs.add(new Reference(at + "on_death.effect: ", t.effect(), t.inputs().keySet())));
                    template.onLand().ifPresent(t -> refs.add(new Reference(at + "on_land.effect: ", t.effect(), t.inputs().keySet())));
                }
            }
        }
        return refs;
    }

    /**
     * Drops effects that play a missing effect, pass it inputs it doesn't declare, or play themselves through a loop;
     * then effects that play those, until nothing changes.
     */
    private static void linkReferences(Map<ResourceLocation, LinkedEffect> linked, Set<ResourceLocation> defined,
                                       Map<ResourceLocation, String> sources, List<DefinitionError> errors) {
        Map<ResourceLocation, List<Reference>> refs = new LinkedHashMap<>();
        linked.forEach((id, effect) -> refs.put(id, references(effect)));
        List<String> knownIds = defined.stream().map(ResourceLocation::toString).sorted().toList();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ResourceLocation id : List.copyOf(linked.keySet())) {
                String problem = null;
                for (Reference ref : refs.get(id)) {
                    LinkedEffect target = linked.get(ref.target);
                    if (target == null) {
                        problem = ref.at + (defined.contains(ref.target)
                                ? "effect '" + ref.target + "' failed to load (see its error)"
                                : "unknown effect '" + ref.target + "'" + Codecs.didYouMean(ref.target.toString(), knownIds));
                        break;
                    }
                    for (String input : ref.inputs) {
                        if (!target.definition().inputs().containsKey(input)) {
                            problem = ref.at + "sets input '" + input + "', which '" + ref.target + "' doesn't declare"
                                    + Codecs.didYouMean(input, target.definition().inputs().keySet());
                            break;
                        }
                    }
                    if (problem != null) break;
                }
                if (problem != null) {
                    errors.add(new DefinitionError(DefinitionError.Kind.EFFECT, id, sources.getOrDefault(id, ""), problem));
                    linked.remove(id);
                    changed = true;
                }
            }
            if (changed) continue;
            List<ResourceLocation> cycle = findCycle(linked.keySet(), refs);
            if (cycle != null) {
                String path = cycle.stream().map(ResourceLocation::toString).collect(Collectors.joining(" → "));
                for (ResourceLocation id : cycle.subList(0, cycle.size() - 1)) {
                    errors.add(new DefinitionError(DefinitionError.Kind.EFFECT, id, sources.getOrDefault(id, ""),
                            "plays itself in a loop: " + path));
                    linked.remove(id);
                }
                changed = true;
            }
        }
    }

    /** A path that starts and ends at the same effect, or null. */
    private static List<ResourceLocation> findCycle(Set<ResourceLocation> ids, Map<ResourceLocation, List<Reference>> refs) {
        Set<ResourceLocation> done = new HashSet<>();
        for (ResourceLocation start : ids) {
            List<ResourceLocation> cycle = visit(start, ids, refs, new ArrayList<>(), done);
            if (cycle != null) return cycle;
        }
        return null;
    }

    private static List<ResourceLocation> visit(ResourceLocation id, Set<ResourceLocation> ids, Map<ResourceLocation, List<Reference>> refs,
                                                List<ResourceLocation> path, Set<ResourceLocation> done) {
        int at = path.indexOf(id);
        if (at >= 0) {
            List<ResourceLocation> cycle = new ArrayList<>(path.subList(at, path.size()));
            cycle.add(id);
            return cycle;
        }
        if (done.contains(id) || !ids.contains(id)) return null;
        path.add(id);
        for (Reference ref : refs.get(id)) {
            List<ResourceLocation> cycle = visit(ref.target, ids, refs, path, done);
            if (cycle != null) return cycle;
        }
        path.remove(path.size() - 1);
        done.add(id);
        return null;
    }

    /** Every input name an entry or its template uses must be declared by the effect, or be built in. */
    private static String checkInputs(ParticleEffect effect, List<ParticleTemplate> templates) {
        Set<String> declared = new LinkedHashSet<>(effect.inputs().keySet());
        declared.add(InputNames.DISTANCE);
        int emitter = 0;
        for (int i = 0; i < effect.tracks().size(); i++) {
            Track track = effect.tracks().get(i);
            Set<String> own = new TreeSet<>();
            track.names(own);
            for (String name : own) {
                if (!declared.contains(name)) return undeclared("at emitters[" + i + "]: ", name, declared);
            }
            if (!(track instanceof EmitterTrack)) continue;
            Set<String> fromTemplate = new TreeSet<>();
            templates.get(emitter++).names(fromTemplate);
            for (String name : fromTemplate) {
                if (!declared.contains(name)) return undeclared("at emitters[" + i + "].template: ", name, declared);
            }
        }
        return null;
    }

    private static String undeclared(String at, String name, Set<String> declared) {
        return at + "uses input '" + name + "', which the effect doesn't declare" + Codecs.didYouMean(name, declared)
                + "; add it to \"inputs\" with a default value";
    }

    private static String checkTemplate(ParticleTemplate template, Predicate<ResourceLocation> particleTypeExists) {
        if (template.render() instanceof RenderMode.Vanilla vanilla && !particleTypeExists.test(vanilla.particle())) {
            return "at render.particle: '" + vanilla.particle() + "' is not a known particle type without options";
        }
        return null;
    }
}
