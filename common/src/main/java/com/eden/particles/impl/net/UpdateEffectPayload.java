package com.eden.particles.impl.net;

import com.eden.particles.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the full changeable state of a playing effect, after its handle's setters were called. */
public record UpdateEffectPayload(int instanceId, EffectState state) implements CustomPacketPayload {

    public static final Type<UpdateEffectPayload> TYPE = new Type<>(Constants.payload("update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.instanceId);
                EffectState.STREAM_CODEC.encode(buf, p.state);
            },
            buf -> new UpdateEffectPayload(buf.readVarInt(), EffectState.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
