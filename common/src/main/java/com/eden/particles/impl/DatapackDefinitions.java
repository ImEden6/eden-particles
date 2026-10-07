package com.eden.particles.impl;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ServerImages;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.DefinitionFiles;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.impl.net.Batches;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server side of datapack definitions ({@code data/<ns>/eden_particles/...}). The server only checks that files parse;
 * templates are linked on clients, since a datapack effect may use a template from a client resource pack.
 */
public class DatapackDefinitions extends SimplePreparableReloadListener<DatapackDefinitions.Prepared> {

    public static final ResourceLocation ID = Constants.id("datapack_definitions");

    /** Payload budget per batch; vanilla rejects custom payloads over 1 MiB. */
    static final int BATCH_BYTES = 512 * 1024;

    private static volatile List<SyncDefinitionsPayload.Entry> synced = List.of();
    private static int revision;
    /** Revision each player last received; new player objects on every login, so weak keys clear themselves. */
    private static final Map<ServerPlayer, Integer> sent = new WeakHashMap<>();

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
        if (!entries.equals(synced)) {
            synced = List.copyOf(entries);
            revision++;
        }
        ServerImages.replace(prepared.images());

        List<DefinitionError> errors = new ArrayList<>(parsed.errors());
        errors.addAll(prepared.imageErrors());
        LoadReport report = new LoadReport(parsed.effects().size(), parsed.templates().size(), errors);
        if (!entries.isEmpty() || !prepared.images().isEmpty() || !errors.isEmpty()) {
            Constants.LOG.info("Datapack definitions: {}, {} image(s)", report.summary(), prepared.images().size());
        }
        errors.forEach(error -> Constants.LOG.error("{}", error));
    }

    /**
     * Sends the current datapack definitions, then offers images; an empty sync still clears what the client had.
     * Safe to call repeatedly: a player is only sent what changed since their last sync.
     */
    public static void syncTo(ServerPlayer player) {
        if (!Services.NETWORK.canSend(player, SyncDefinitionsPayload.TYPE)) return;
        Integer last = sent.put(player, revision);
        if (last == null || last != revision) {
            for (SyncDefinitionsPayload payload : batches(synced, BATCH_BYTES)) {
                Services.NETWORK.send(player, payload);
            }
        }
        ServerImages.syncTo(player);
    }

    /** Always at least one batch, so the client's layer is replaced even when there's nothing to send. */
    static List<SyncDefinitionsPayload> batches(List<SyncDefinitionsPayload.Entry> entries, int budgetBytes) {
        List<List<SyncDefinitionsPayload.Entry>> groups = Batches.split(entries, DatapackDefinitions::estimateBytes, budgetBytes);
        List<SyncDefinitionsPayload> payloads = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            payloads.add(new SyncDefinitionsPayload(i == 0, i == groups.size() - 1, groups.get(i)));
        }
        return payloads;
    }

    /** UTF-8 worst case (3 bytes per char for the BMP) plus framing. */
    private static int estimateBytes(SyncDefinitionsPayload.Entry entry) {
        return entry.json().length() * 3 + entry.id().toString().length() + 16;
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
