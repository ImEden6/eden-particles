package com.eden.particles.impl;

import com.eden.particles.Constants;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.DefinitionFiles;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server side of datapack definitions ({@code data/<ns>/eden_particles/...}). The server only checks that files parse;
 * templates are linked on clients, since a datapack effect may use a template from a client resource pack.
 */
public class DatapackDefinitions extends SimplePreparableReloadListener<DatapackDefinitions.Prepared> {

    public static final ResourceLocation ID = Constants.id("datapack_definitions");

    /** Payload budget per batch; vanilla rejects custom payloads over 1 MiB. */
    static final int BATCH_BYTES = 512 * 1024;

    private static volatile List<SyncDefinitionsPayload.Entry> synced = List.of();

    record Prepared(DefinitionFiles.Read read, ParsedDefinitions parsed) {
    }

    @Override
    protected Prepared prepare(ResourceManager resources, ProfilerFiller profiler) {
        DefinitionFiles.Read read = DefinitionFiles.read(resources);
        return new Prepared(read, read.parse());
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resources, ProfilerFiller profiler) {
        ParsedDefinitions parsed = prepared.parsed();
        List<SyncDefinitionsPayload.Entry> entries = new ArrayList<>();
        collect(prepared.read().templates(), parsed.templates().keySet(), DefinitionError.Kind.TEMPLATE, entries);
        collect(prepared.read().effects(), parsed.effects().keySet(), DefinitionError.Kind.EFFECT, entries);
        synced = List.copyOf(entries);

        LoadReport report = new LoadReport(parsed.effects().size(), parsed.templates().size(), parsed.errors());
        if (!entries.isEmpty() || !report.errors().isEmpty()) {
            Constants.LOG.info("Datapack definitions: {}", report.summary());
        }
        report.errors().forEach(error -> Constants.LOG.error("{}", error));
    }

    /** Sends the current datapack definitions; an empty sync still clears what the client had. */
    public static void syncTo(ServerPlayer player) {
        if (!Services.NETWORK.canSend(player, SyncDefinitionsPayload.TYPE)) return;
        for (SyncDefinitionsPayload payload : batches(synced, BATCH_BYTES)) {
            Services.NETWORK.send(player, payload);
        }
    }

    /** Always at least one batch, so the client's layer is replaced even when there's nothing to send. */
    static List<SyncDefinitionsPayload> batches(List<SyncDefinitionsPayload.Entry> entries, int budgetBytes) {
        List<List<SyncDefinitionsPayload.Entry>> groups = new ArrayList<>();
        List<SyncDefinitionsPayload.Entry> current = new ArrayList<>();
        int size = 0;
        for (SyncDefinitionsPayload.Entry entry : entries) {
            int cost = estimateBytes(entry);
            if (!current.isEmpty() && size + cost > budgetBytes) {
                groups.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(entry);
            size += cost;
        }
        groups.add(current);
        List<SyncDefinitionsPayload> payloads = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            payloads.add(new SyncDefinitionsPayload(i == 0, i == groups.size() - 1, List.copyOf(groups.get(i))));
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
