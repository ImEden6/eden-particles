package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.load.DefinitionFiles;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.ParsedDefinitions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Reads {@code assets/<ns>/eden_particles/{templates,effects}/**.json}. Parses off-thread, publishes on apply. */
public class EffectReloadListener extends SimplePreparableReloadListener<ParsedDefinitions> {

    public static final ResourceLocation ID = Constants.id("definitions");

    @Override
    protected ParsedDefinitions prepare(ResourceManager resources, ProfilerFiller profiler) {
        return DefinitionFiles.read(resources).parse();
    }

    @Override
    protected void apply(ParsedDefinitions parsed, ResourceManager resources, ProfilerFiller profiler) {
        ClientEffects.report(ClientEffects.STORE.replace(DefinitionStore.Layer.ASSETS, parsed));
    }
}
