package com.eden.particles.impl.net;

import com.eden.particles.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: play {@code effect}. {@code position} is where the anchor was when sent; with an entity anchor
 * it is the fallback if the client doesn't have that entity. Rotation is yaw, pitch, roll in degrees.
 *
 * @param entityId -1 for a fixed position
 */
public record PlayEffectPayload(
        int instanceId,
        ResourceLocation effect,
        Vec3 position,
        int entityId,
        Vec3 offset,
        float scale,
        float yaw,
        float pitch,
        float roll,
        int tint,
        long seed) implements CustomPacketPayload {

    public static final Type<PlayEffectPayload> TYPE = new Type<>(Constants.id("play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.instanceId);
                buf.writeResourceLocation(p.effect);
                buf.writeVec3(p.position);
                buf.writeVarInt(p.entityId + 1);
                buf.writeVec3(p.offset);
                buf.writeFloat(p.scale);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.pitch);
                buf.writeFloat(p.roll);
                buf.writeInt(p.tint);
                buf.writeLong(p.seed);
            },
            buf -> new PlayEffectPayload(
                    buf.readVarInt(),
                    buf.readResourceLocation(),
                    buf.readVec3(),
                    buf.readVarInt() - 1,
                    buf.readVec3(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
