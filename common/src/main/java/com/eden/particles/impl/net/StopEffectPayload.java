package com.eden.particles.impl.net;

import com.eden.particles.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: stop the effect with this id, or every server-started effect when {@link #ALL}. */
public record StopEffectPayload(int instanceId) implements CustomPacketPayload {

    public static final int ALL = -1;

    public static final Type<StopEffectPayload> TYPE = new Type<>(Constants.id("stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StopEffectPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> new StopEffectPayload(i - 1), p -> p.instanceId + 1).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
