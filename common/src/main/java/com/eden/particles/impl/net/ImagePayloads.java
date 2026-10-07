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
 * {@link Consent}, and only an accepted offer is followed by {@link Images}.
 */
public final class ImagePayloads {

    private ImagePayloads() {
    }

    /** Server → client. {@code revision} identifies this set of images, so a stale answer is ignored. */
    public record Offer(int revision, List<ResourceLocation> ids, long totalBytes) implements CustomPacketPayload {

        public static final Type<Offer> TYPE = new Type<>(Constants.id("image_offer"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.revision);
                    buf.writeVarInt(p.ids.size());
                    p.ids.forEach(buf::writeResourceLocation);
                    buf.writeVarLong(p.totalBytes);
                },
                buf -> {
                    int revision = buf.readVarInt();
                    int count = Math.min(buf.readVarInt(), ImageLimits.MAX_IMAGES);
                    List<ResourceLocation> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
                    return new Offer(revision, List.copyOf(ids), buf.readVarLong());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server. */
    public record Consent(int revision, boolean accepted) implements CustomPacketPayload {

        public static final Type<Consent> TYPE = new Type<>(Constants.id("image_consent"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Consent> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.revision);
                    buf.writeBoolean(p.accepted);
                },
                buf -> new Consent(buf.readVarInt(), buf.readBoolean()));

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

    /** Server → client, after consent. Batched like definitions: {@code first} resets, {@code last} applies. */
    public record Images(boolean first, boolean last, List<Image> images) implements CustomPacketPayload {

        public static final Type<Images> TYPE = new Type<>(Constants.id("images"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Images> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.first);
                    buf.writeBoolean(p.last);
                    buf.writeVarInt(p.images.size());
                    for (Image image : p.images) {
                        buf.writeResourceLocation(image.id);
                        buf.writeByteArray(image.bytes);
                    }
                },
                buf -> {
                    boolean first = buf.readBoolean();
                    boolean last = buf.readBoolean();
                    int count = Math.min(buf.readVarInt(), ImageLimits.MAX_IMAGES);
                    List<Image> images = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        images.add(new Image(buf.readResourceLocation(), buf.readByteArray(ImageLimits.MAX_FILE_BYTES)));
                    }
                    return new Images(first, last, List.copyOf(images));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
