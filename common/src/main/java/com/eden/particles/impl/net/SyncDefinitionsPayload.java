package com.eden.particles.impl.net;

import com.eden.particles.Constants;
import com.eden.particles.impl.load.DefinitionError;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: one batch of datapack definitions as JSON text. {@code first} resets the client's datapack layer;
 * {@code last} applies it. A full sync is one or more batches.
 */
public record SyncDefinitionsPayload(boolean first, boolean last, List<Entry> entries) implements CustomPacketPayload {

    /** Longest JSON text accepted for a single definition. */
    public static final int MAX_JSON_LENGTH = 262_144;

    public record Entry(DefinitionError.Kind kind, ResourceLocation id, String json) {
    }

    public static final Type<SyncDefinitionsPayload> TYPE = new Type<>(Constants.id("sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDefinitionsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.first);
                buf.writeBoolean(p.last);
                buf.writeVarInt(p.entries.size());
                for (Entry entry : p.entries) {
                    buf.writeEnum(entry.kind);
                    buf.writeResourceLocation(entry.id);
                    buf.writeUtf(entry.json, MAX_JSON_LENGTH);
                }
            },
            buf -> {
                boolean first = buf.readBoolean();
                boolean last = buf.readBoolean();
                int count = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(Math.max(0, Math.min(count, buf.readableBytes())));
                for (int i = 0; i < count; i++) {
                    entries.add(new Entry(buf.readEnum(DefinitionError.Kind.class), buf.readResourceLocation(), buf.readUtf(MAX_JSON_LENGTH)));
                }
                return new SyncDefinitionsPayload(first, last, List.copyOf(entries));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
