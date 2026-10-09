package com.eden.particles.impl.sync;

import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionSyncTest {

    private static SyncDefinitionsPayload.Entry entry(int i, int jsonLength) {
        return new SyncDefinitionsPayload.Entry(DefinitionError.Kind.EFFECT,
                ResourceLocation.fromNamespaceAndPath("test", "fx" + i), "x".repeat(jsonLength));
    }

    @Test
    void nothingToSyncStillSendsOneBatchThatClearsTheClient() {
        List<SyncDefinitionsPayload> batches = DefinitionSync.batches(List.of(), 1000);
        assertEquals(1, batches.size());
        assertTrue(batches.get(0).first());
        assertTrue(batches.get(0).last());
        assertTrue(batches.get(0).entries().isEmpty());
    }

    @Test
    void largeSetsSplitIntoOrderedBatchesWithinBudget() {
        List<SyncDefinitionsPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) entries.add(entry(i, 100));
        List<SyncDefinitionsPayload> batches = DefinitionSync.batches(entries, 2000);

        assertTrue(batches.size() > 1);
        assertTrue(batches.get(0).first());
        assertTrue(batches.get(batches.size() - 1).last());
        List<SyncDefinitionsPayload.Entry> rejoined = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            assertEquals(i == 0, batches.get(i).first());
            assertEquals(i == batches.size() - 1, batches.get(i).last());
            rejoined.addAll(batches.get(i).entries());
        }
        assertEquals(entries, rejoined);
    }

    @Test
    void anOversizedEntryStillGetsABatchOfItsOwn() {
        List<SyncDefinitionsPayload> batches = DefinitionSync.batches(List.of(entry(0, 10), entry(1, 5000), entry(2, 10)), 1000);
        assertEquals(3, batches.size());
        assertEquals(1, batches.get(1).entries().size());
    }

    @Test
    void payloadRoundTrips() {
        SyncDefinitionsPayload payload = new SyncDefinitionsPayload(true, false, List.of(
                new SyncDefinitionsPayload.Entry(DefinitionError.Kind.TEMPLATE, ResourceLocation.fromNamespaceAndPath("a", "t"), "{\"lifetime\": 20}"),
                new SyncDefinitionsPayload.Entry(DefinitionError.Kind.EFFECT, ResourceLocation.fromNamespaceAndPath("a", "e/f"), "{\"é\": \"ü\"}")));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        SyncDefinitionsPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, SyncDefinitionsPayload.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }

    @Test
    void eachPlayerGetsEachRevisionOnce() {
        DefinitionSync<String> sync = new DefinitionSync<>(new HashMap<>());
        assertEquals(1, sync.sync("alice").size(), "even an empty set is sent once, to clear the client");
        assertTrue(sync.sync("alice").isEmpty());

        sync.replace(List.of(entry(0, 10)));
        assertEquals(List.of(entry(0, 10)), sync.sync("alice").get(0).entries());
        assertEquals(1, sync.sync("bob").size());
        assertTrue(sync.sync("alice").isEmpty());

        sync.replace(List.of(entry(0, 10)));
        assertTrue(sync.sync("alice").isEmpty(), "an equal set keeps its revision");
    }
}
