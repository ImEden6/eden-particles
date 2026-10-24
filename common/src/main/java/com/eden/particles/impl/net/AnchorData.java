package com.eden.particles.impl.net;

import com.eden.particles.api.EffectAnchor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One end of an anchor on the wire. {@code position} is where it was when sent: the fallback if the client doesn't
 * have the entity.
 *
 * @param entityId -1 for a fixed position
 */
public record AnchorData(Vec3 position, int entityId, Vec3 offset, boolean eyes, EffectAnchor.Turning turning) {

    private static final EffectAnchor.Turning[] TURNINGS = EffectAnchor.Turning.values();

    public static final StreamCodec<FriendlyByteBuf, AnchorData> STREAM_CODEC = StreamCodec.of(
            (buf, a) -> {
                buf.writeVec3(a.position);
                buf.writeVarInt(a.entityId + 1);
                buf.writeVec3(a.offset);
                buf.writeBoolean(a.eyes);
                buf.writeByte(a.turning.ordinal());
            },
            buf -> new AnchorData(buf.readVec3(), buf.readVarInt() - 1, buf.readVec3(), buf.readBoolean(),
                    TURNINGS[Math.floorMod(buf.readByte(), TURNINGS.length)]));

    public static AnchorData of(EffectAnchor anchor) {
        return new AnchorData(anchor.position(), anchor.entity().map(Entity::getId).orElse(-1),
                anchor.offset(), anchor.atEyes(), anchor.turning());
    }

    public boolean waiting(Level level) {
        return entityId >= 0 && (level == null || level.getEntity(entityId) == null);
    }

    /** Rebuilds the anchor on the client, falling back to the sent position if the entity isn't loaded. */
    public EffectAnchor toAnchor(Level level) {
        Entity entity = entityId >= 0 && level != null ? level.getEntity(entityId) : null;
        if (entity == null) return EffectAnchor.at(position);
        EffectAnchor anchor = EffectAnchor.entity(entity).offset(offset).turning(turning);
        return eyes ? anchor.eyes() : anchor;
    }
}
