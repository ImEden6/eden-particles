package com.eden.particles.impl;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ServerImages;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.DefinitionFiles;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.impl.sync.DefinitionSync;
import com.eden.particles.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server side of datapack definitions ({@code data/<ns>/eden_particles/...}). The server only checks that files parse;
 * templates are linked on clients, since a datapack effect may use a template from a client resource pack.
 */
public class DatapackDefinitions extends SimplePreparableReloadListener<DatapackDefinitions.Prepared> {

    public static final ResourceLocation ID = Constants.id("datapack_definitions");

    /** Player objects are new on every login, so weak keys clear themselves. */
    private static final DefinitionSync<ServerPlayer> SYNC = new DefinitionSync<>(new WeakHashMap<>());
    private static Map<ResourceLocation, Integer> durations = Map.of();

    record Prepared(DefinitionFiles.Read read, ParsedDefinitions parsed, List<ImagePayloads.Image> images, List<DefinitionError> imageErrors) {
    }

    @Override
    protected Prepared prepare(ResourceManager resources, ProfilerFiller profiler) {
        DefinitionFiles.Read read = DefinitionFiles.read(resources);
        List<DefinitionError> imageErrors = new ArrayList<>();
        List<ImagePayloads.Image> images = ServerImages.read(resources, imageErrors);
        return new Prepared(read, read.parse(), images, imageErrors);
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resources, ProfilerFiller profiler) {
        ParsedDefinitions parsed = prepared.parsed();
        List<SyncDefinitionsPayload.Entry> entries = new ArrayList<>();
        collect(prepared.read().templates(), parsed.templates().keySet(), DefinitionError.Kind.TEMPLATE, entries);
        collect(prepared.read().effects(), parsed.effects().keySet(), DefinitionError.Kind.EFFECT, entries);
        SYNC.replace(entries);
        ServerImages.replace(prepared.images());
        Map<ResourceLocation, Integer> lengths = new HashMap<>();
        parsed.effects().forEach((id, effect) -> {
            if (!effect.loop()) lengths.put(id, effect.duration());
        });
        durations = Map.copyOf(lengths);

        List<DefinitionError> errors = new ArrayList<>(parsed.errors());
        errors.addAll(prepared.imageErrors());
        LoadReport report = new LoadReport(parsed.effects().size(), parsed.templates().size(), errors);
        if (!entries.isEmpty() || !prepared.images().isEmpty() || !errors.isEmpty()) {
            Constants.LOG.info("Datapack definitions: {}, {} image(s)", report.summary(), prepared.images().size());
        }
        errors.forEach(error -> Constants.LOG.error("{}", error));
    }

    static List<SyncDefinitionsPayload.Entry> entries() {
        return SYNC.entries();
    }

    /** Ticks a datapack effect plays, if it's one that ends. */
    static OptionalInt duration(ResourceLocation effect) {
        Integer ticks = durations.get(effect);
        return ticks == null ? OptionalInt.empty() : OptionalInt.of(ticks);
    }

    static List<ResourceLocation> effectIds() {
        return SYNC.entries().stream().filter(e -> e.kind() == DefinitionError.Kind.EFFECT).map(SyncDefinitionsPayload.Entry::id).toList();
    }

    /**
     * Sends the current datapack definitions, then offers images; an empty sync still clears what the client had.
     * Safe to call repeatedly: a player is only sent what changed since their last sync.
     */
    public static void syncTo(ServerPlayer player) {
        if (!Services.NETWORK.canSend(player, SyncDefinitionsPayload.TYPE)) return;
        SYNC.sync(player).forEach(payload -> Services.NETWORK.send(player, payload));
        ServerImages.syncTo(player);
    }

    private static void collect(List<RawDefinition> raws, Set<ResourceLocation> parsedIds, DefinitionError.Kind kind,
                                List<SyncDefinitionsPayload.Entry> out) {
        for (RawDefinition raw : raws) {
            if (!parsedIds.contains(raw.id())) continue;
            String json = raw.json().toString();
            if (json.length() > SyncDefinitionsPayload.MAX_JSON_LENGTH) {
                Constants.LOG.error("{} {} is too large to sync ({} characters, limit {})", kind, raw.id(), json.length(),
                        SyncDefinitionsPayload.MAX_JSON_LENGTH);
                continue;
            }
            out.add(new SyncDefinitionsPayload.Entry(kind, raw.id(), json));
        }
    }
}
