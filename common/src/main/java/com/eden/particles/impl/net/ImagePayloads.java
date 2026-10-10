package com.eden.particles.impl.net;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ImageLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Datapack images need the player's consent: the server sends an {@link Offer}, the client answers with a
 * {@link Consent} listing the images it doesn't have cached, and only an accepted offer is followed by {@link Images}
 * carrying those.
 */
public final class ImagePayloads {

    private static final int HASH_BYTES = 32;

    private ImagePayloads() {
    }

    /** One offered image: its id, SHA-256 and size in bytes. */
    public record Entry(ResourceLocation id, byte[] sha256, int size) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry e && id.equals(e.id) && Arrays.equals(sha256, e.sha256) && size == e.size;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * id.hashCode() + Arrays.hashCode(sha256)) + size;
        }
    }

    /** Server → client. {@code revision} identifies this set of images, so a stale answer is ignored. */
    public record Offer(int revision, List<Entry> entries, long totalBytes) implements CustomPacketPayload {

        public static final Type<Offer> TYPE = new Type<>(Constants.id("image_offer"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.revision);
                    buf.writeVarInt(p.entries.size());
                    for (Entry entry : p.entries) {
                        buf.writeResourceLocation(entry.id);
                        buf.writeByteArray(entry.sha256);
                        buf.writeVarInt(entry.size);
                    }
                    buf.writeVarLong(p.totalBytes);
                },
                buf -> {
                    int revision = buf.readVarInt();
                    int count = Math.min(buf.readVarInt(), ImageLimits.MAX_IMAGES);
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new Entry(buf.readResourceLocation(), buf.readByteArray(HASH_BYTES), buf.readVarInt()));
                    }
                    return new Offer(revision, List.copyOf(entries), buf.readVarLong());
                });

        public List<ResourceLocation> ids() {
            return entries.stream().map(Entry::id).toList();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server. {@code need} lists the offer's indices the client doesn't have; empty when declining. */
    public record Consent(int revision, boolean accepted, List<Integer> need) implements CustomPacketPayload {

        public static final Type<Consent> TYPE = new Type<>(Constants.id("image_consent"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Consent> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.revision);
                    buf.writeBoolean(p.accepted);
                    buf.writeVarInt(p.need.size());
                    p.need.forEach(buf::writeVarInt);
                },
                buf -> {
                    int revision = buf.readVarInt();
                    boolean accepted = buf.readBoolean();
                    int count = Math.min(buf.readVarInt(), ImageLimits.MAX_IMAGES);
                    List<Integer> need = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) need.add(buf.readVarInt());
                    return new Consent(revision, accepted, List.copyOf(need));
                });

        public static Consent decline(int revision) {
            return new Consent(revision, false, List.of());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Image(ResourceLocation id, byte[] bytes) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Image image && id.equals(image.id) && Arrays.equals(bytes, image.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + Arrays.hashCode(bytes);
        }
    }

    /**
     * Server → client, after consent: the images of offer {@code revision} the client asked for. Batched like
     * definitions: {@code first} resets, {@code last} applies. A single empty batch for a revision the client didn't
     * accept clears its images.
     */
    public record Images(int revision, boolean first, boolean last, List<Image> images) implements CustomPacketPayload {

        public static final Type<Images> TYPE = new Type<>(Constants.id("images"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Images> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.revision);
                    buf.writeBoolean(p.first);
                    buf.writeBoolean(p.last);
                    buf.writeVarInt(p.images.size());
                    for (Image image : p.images) {
                        buf.writeResourceLocation(image.id);
                        buf.writeByteArray(image.bytes);
                    }
                },
                buf -> {
                    int revision = buf.readVarInt();
                    boolean first = buf.readBoolean();
                    boolean last = buf.readBoolean();
                    int count = Math.min(buf.readVarInt(), ImageLimits.MAX_IMAGES);
                    List<Image> images = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        images.add(new Image(buf.readResourceLocation(), buf.readByteArray(ImageLimits.MAX_FILE_BYTES)));
                    }
                    return new Images(revision, first, last, List.copyOf(images));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
