package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.platform.Services;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads {@code assets/<ns>/eden_particles/{templates,effects}/**.json}. Parses off-thread, publishes on apply. */
public class EffectReloadListener extends SimplePreparableReloadListener<ParsedDefinitions> {

    public static final ResourceLocation ID = Constants.id("definitions");

    private static final FileToIdConverter TEMPLATES = FileToIdConverter.json("eden_particles/templates");
    private static final FileToIdConverter EFFECTS = FileToIdConverter.json("eden_particles/effects");
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    @Override
    protected ParsedDefinitions prepare(ResourceManager resources, ProfilerFiller profiler) {
        List<DefinitionError> readErrors = new ArrayList<>();
        List<RawDefinition> templates = read(resources, TEMPLATES, DefinitionError.Kind.TEMPLATE, readErrors);
        List<RawDefinition> effects = read(resources, EFFECTS, DefinitionError.Kind.EFFECT, readErrors);
        return ParsedDefinitions.parse(templates, effects, readErrors);
    }

    @Override
    protected void apply(ParsedDefinitions parsed, ResourceManager resources, ProfilerFiller profiler) {
        LoadReport report = ClientEffects.STORE.replace(DefinitionStore.Layer.ASSETS, parsed);
        Constants.LOG.info(report.summary());
        report.errors().forEach(error -> Constants.LOG.error("{}", error));
        if (!report.errors().isEmpty() && Services.PLATFORM.isDevelopmentEnvironment()) {
            SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), TOAST,
                    Component.literal(Constants.MOD_NAME + ": " + report.errors().size() + " definition error(s)"),
                    Component.literal(report.errors().get(0).toString()));
        }
    }

    private static List<RawDefinition> read(ResourceManager resources, FileToIdConverter converter,
                                            DefinitionError.Kind kind, List<DefinitionError> errors) {
        List<RawDefinition> raws = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : converter.listMatchingResources(resources).entrySet()) {
            ResourceLocation id = converter.fileToId(entry.getKey());
            String source = entry.getValue().sourcePackId();
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                raws.add(new RawDefinition(id, source, json));
            } catch (IOException | JsonParseException e) {
                errors.add(new DefinitionError(kind, id, source, "invalid JSON: " + rootMessage(e)));
            }
        }
        return raws;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
