package com.eden.particles.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/** Where an effect plays: a fixed point, or an entity it follows (ending when the entity is removed). */
public final class EffectAnchor {

    private final Vec3 position;
    private final Entity entity;
    private final Vec3 offset;

    private EffectAnchor(Vec3 position, Entity entity, Vec3 offset) {
        this.position = position;
        this.entity = entity;
        this.offset = offset;
    }

    public static EffectAnchor at(Vec3 position) {
        return new EffectAnchor(Objects.requireNonNull(position), null, Vec3.ZERO);
    }

    public static EffectAnchor at(double x, double y, double z) {
        return at(new Vec3(x, y, z));
    }

    /** The bottom centre of the block. */
    public static EffectAnchor at(BlockPos pos) {
        return at(Vec3.atBottomCenterOf(pos));
    }

    public static EffectAnchor entity(Entity entity) {
        return entity(entity, Vec3.ZERO);
    }

    /** @param offset world-space offset from the entity's position (it doesn't turn with the entity) */
    public static EffectAnchor entity(Entity entity, Vec3 offset) {
        return new EffectAnchor(entity.position(), Objects.requireNonNull(entity), Objects.requireNonNull(offset));
    }

    /** The anchor's current position, including any offset. */
    public Vec3 position() {
        return entity != null ? entity.position().add(offset) : position;
    }

    public Optional<Entity> entity() {
        return Optional.ofNullable(entity);
    }

    public Vec3 offset() {
        return offset;
    }
}
