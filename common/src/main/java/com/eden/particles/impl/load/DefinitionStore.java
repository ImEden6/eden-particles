package com.eden.particles.impl.load;

import com.eden.particles.impl.definition.ParticleEffect;
import com.eden.particles.impl.definition.ParticleTemplate;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Linked effects from every layer. Layers are merged before linking, later layers overriding earlier ones by id,
 * so a higher layer can reference templates from a lower one.
 */
public final class DefinitionStore {

    /** In priority order, lowest first. */
    public enum Layer { ASSETS, DATAPACK }

    private final Map<Layer, ParsedDefinitions> layers = new EnumMap<>(Layer.class);
    private final List<Consumer<LoadReport>> listeners = new CopyOnWriteArrayList<>();
    private final Predicate<ResourceLocation> particleTypeExists;
    private volatile Map<ResourceLocation, LinkedEffect> effects = Map.of();
    private volatile LoadReport lastReport = new LoadReport(0, 0, List.of());

    public DefinitionStore(Predicate<ResourceLocation> particleTypeExists) {
        this.particleTypeExists = particleTypeExists;
    }

    public synchronized LoadReport replace(Layer layer, ParsedDefinitions definitions) {
        layers.put(layer, definitions);

        Map<ResourceLocation, ParticleTemplate> templates = new HashMap<>();
        Map<ResourceLocation, ParticleEffect> rawEffects = new HashMap<>();
        Set<ResourceLocation> failedTemplates = new HashSet<>();
        Map<ResourceLocation, String> sources = new HashMap<>();
        List<DefinitionError> errors = new ArrayList<>();
        for (ParsedDefinitions parsed : layers.values()) {
            templates.putAll(parsed.templates());
            rawEffects.putAll(parsed.effects());
            sources.putAll(parsed.sources());
            failedTemplates.removeAll(parsed.templates().keySet());
            failedTemplates.addAll(parsed.failedTemplates());
            errors.addAll(parsed.errors());
        }

        Linker.Result linked = Linker.link(templates, rawEffects, failedTemplates, sources, particleTypeExists);
        errors.addAll(linked.errors());
        effects = linked.effects();
        LoadReport report = new LoadReport(linked.effects().size(), templates.size(), List.copyOf(errors));
        lastReport = report;
        listeners.forEach(listener -> listener.accept(report));
        return report;
    }

    public Optional<LinkedEffect> effect(ResourceLocation id) {
        return Optional.ofNullable(effects.get(id));
    }

    public Set<ResourceLocation> effectIds() {
        return effects.keySet();
    }

    public LoadReport lastReport() {
        return lastReport;
    }

    public void onReload(Consumer<LoadReport> listener) {
        listeners.add(listener);
    }
}
