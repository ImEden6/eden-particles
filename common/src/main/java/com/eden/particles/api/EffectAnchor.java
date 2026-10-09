package com.eden.particles.api;

import com.eden.particles.impl.AnchorMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Where an effect plays: a fixed point, an entity it follows (ending when the entity is removed), or the line between
 * two anchors. Immutable; the builder-style methods return new anchors.
 */
public final class EffectAnchor {

    /** Which way an entity anchor faces. The effect and its offset turn with it. */
    public enum Turning {
        /** World axes; the offset is in world space. */
        NONE,
        /** The entity's body yaw. */
        BODY,
        /** The entity's head yaw and pitch, which is where a player is looking. */
        LOOK
    }

    private final Vec3 position;
    private final Entity entity;
    private final Vec3 offset;
    private final boolean eyes;
    private final Turning turning;
    private final EffectAnchor target;

    private EffectAnchor(Vec3 position, Entity entity, Vec3 offset, boolean eyes, Turning turning, EffectAnchor target) {
        this.position = position;
        this.entity = entity;
        this.offset = offset;
        this.eyes = eyes;
        this.turning = turning;
        this.target = target;
    }

    public static EffectAnchor at(Vec3 position) {
        return new EffectAnchor(Objects.requireNonNull(position), null, Vec3.ZERO, false, Turning.NONE, null);
    }

    public static EffectAnchor at(double x, double y, double z) {
        return at(new Vec3(x, y, z));
    }

    /** The bottom centre of the block. */
    public static EffectAnchor at(BlockPos pos) {
        return at(Vec3.atBottomCenterOf(pos));
    }

    public static EffectAnchor entity(Entity entity) {
        return new EffectAnchor(entity.position(), Objects.requireNonNull(entity), Vec3.ZERO, false, Turning.NONE, null);
    }

    /** @param offset world-space offset from the entity's position (it doesn't turn with the entity) */
    public static EffectAnchor entity(Entity entity, Vec3 offset) {
        return entity(entity).offset(offset);
    }

    /**
     * Plays at {@code from}, turned so the effect's +Z axis points at {@code to}. Effects read the length between them
     * as the {@code distance} input. Either end may follow an entity; the effect ends when either is removed.
     */
    public static EffectAnchor between(EffectAnchor from, EffectAnchor to) {
        if (from.target != null || to.target != null) throw new IllegalArgumentException("between() anchors can't be nested");
        return new EffectAnchor(from.position, from.entity, from.offset, from.eyes, from.turning, to);
    }

    /** Entity anchors: start from eye height instead of the feet. */
    public EffectAnchor eyes() {
        requireEntity("eyes()");
        return new EffectAnchor(position, entity, offset, true, turning, target);
    }

    /** Entity anchors: turn with the entity. The offset is then in the entity's frame: x left, y up, z forward. */
    public EffectAnchor turning(Turning turning) {
        requireEntity("turning()");
        return new EffectAnchor(position, entity, offset, eyes, Objects.requireNonNull(turning), target);
    }

    /** Entity anchors: replaces the offset (world space, or the entity's frame when turning). Fixed anchors: moves the point. */
    public EffectAnchor offset(Vec3 offset) {
        Objects.requireNonNull(offset);
        if (target != null) throw new IllegalStateException("offset() goes on the ends of a between() anchor");
        if (entity == null) return at(position.add(offset));
        return new EffectAnchor(position, entity, offset, eyes, turning, target);
    }

    public EffectAnchor offset(double x, double y, double z) {
        return offset(new Vec3(x, y, z));
    }

    /** The anchor's current position, including eye height and offset; for {@link #between} anchors, the start. */
    public Vec3 position() {
        return entity == null ? position : AnchorMath.position(this);
    }

    /** The followed entity; for {@link #between} anchors, the start's. */
    public Optional<Entity> entity() {
        return Optional.ofNullable(entity);
    }

    public Vec3 offset() {
        return offset;
    }

    public boolean atEyes() {
        return eyes;
    }

    public Turning turning() {
        return turning;
    }

    /** The far end of a {@link #between} anchor. */
    public Optional<EffectAnchor> target() {
        return Optional.ofNullable(target);
    }

    /** This anchor without its far end. */
    public EffectAnchor start() {
        return target == null ? this : new EffectAnchor(position, entity, offset, eyes, turning, null);
    }

    private void requireEntity(String method) {
        if (entity == null) throw new IllegalStateException(method + " needs an entity anchor");
        if (target != null) throw new IllegalStateException(method + " goes on the ends of a between() anchor");
    }
}
