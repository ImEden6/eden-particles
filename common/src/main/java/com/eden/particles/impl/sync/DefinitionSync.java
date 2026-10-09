package com.eden.particles.impl.sync;

import com.eden.particles.impl.net.Batches;
import com.eden.particles.impl.net.SyncDefinitionsPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server side of definition sync, without networking: what to send each player. */
public final class DefinitionSync<P> {

    /** Payload budget per batch; vanilla rejects custom payloads over 1 MiB. */
    static final int BATCH_BYTES = 512 * 1024;

    private List<SyncDefinitionsPayload.Entry> entries = List.of();
    private int revision;
    private final SentRevisions<P> sent;

    public DefinitionSync(Map<P, Integer> backing) {
        this.sent = new SentRevisions<>(backing);
    }

    public void replace(List<SyncDefinitionsPayload.Entry> next) {
        if (next.equals(entries)) return;
        entries = List.copyOf(next);
        revision++;
    }

    public List<SyncDefinitionsPayload.Entry> entries() {
        return entries;
    }

    /** The current definitions, or nothing if this player already has them. An empty set still clears the client. */
    public List<SyncDefinitionsPayload> sync(P player) {
        return sent.mark(player, revision) ? batches(entries, BATCH_BYTES) : List.of();
    }

    /** Always at least one batch, so the client's layer is replaced even when there's nothing to send. */
    static List<SyncDefinitionsPayload> batches(List<SyncDefinitionsPayload.Entry> entries, int budgetBytes) {
        List<List<SyncDefinitionsPayload.Entry>> groups = Batches.split(entries, DefinitionSync::estimateBytes, budgetBytes);
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
}
