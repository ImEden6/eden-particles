package com.eden.particles.impl.net;

import com.eden.particles.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Server → client: play {@code effect}. {@code target} is the far end of a two-point anchor.
 *
 * @param elapsed ticks since the effect started; non-zero when re-sent to a late joiner
 */
public record PlayEffectPayload(
        int instanceId,
        ResourceLocation effect,
        AnchorData anchor,
        Optional<AnchorData> target,
        EffectState state,
        long seed,
        int elapsed) implements CustomPacketPayload {

    public static final Type<PlayEffectPayload> TYPE = new Type<>(Constants.id("play"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<AnchorData>> TARGET =
            ByteBufCodecs.optional(AnchorData.STREAM_CODEC).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.instanceId);
                buf.writeResourceLocation(p.effect);
                AnchorData.STREAM_CODEC.encode(buf, p.anchor);
                TARGET.encode(buf, p.target);
                EffectState.STREAM_CODEC.encode(buf, p.state);
                buf.writeLong(p.seed);
                buf.writeVarInt(p.elapsed);
            },
            buf -> new PlayEffectPayload(
                    buf.readVarInt(),
                    buf.readResourceLocation(),
                    AnchorData.STREAM_CODEC.decode(buf),
                    TARGET.decode(buf),
                    EffectState.STREAM_CODEC.decode(buf),
                    buf.readLong(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
