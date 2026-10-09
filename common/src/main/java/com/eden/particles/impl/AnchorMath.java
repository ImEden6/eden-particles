package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.impl.math.Rotations;
import com.eden.particles.impl.runtime.Anchor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Where an {@link EffectAnchor} is right now, and which way it faces. Shared by the server's range checks and the client. */
public final class AnchorMath {

    private AnchorMath() {
    }

    /** False once an entity at either end is removed. */
    public static boolean place(EffectAnchor anchor, Anchor.Placement out) {
        if (!placeOne(anchor, out.position, out.facing)) return false;
        out.distance = 0f;
        if (anchor.target().isEmpty()) return true;
        Vector3d to = new Vector3d();
        if (!placeOne(anchor.target().get(), to, new Quaternionf())) return false;
        double dx = to.x - out.position.x, dy = to.y - out.position.y, dz = to.z - out.position.z;
        out.distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        Rotations.lookAt(dx, dy, dz, out.facing);
        return true;
    }

    /** The start point, for range checks; an entity's last position if it's gone. */
    public static Vec3 position(EffectAnchor anchor) {
        Vector3d out = new Vector3d();
        placeOne(anchor, out, new Quaternionf());
        return new Vec3(out.x, out.y, out.z);
    }

    private static boolean placeOne(EffectAnchor anchor, Vector3d position, Quaternionf facing) {
        Entity entity = anchor.entity().orElse(null);
        if (entity == null) {
            Vec3 at = anchor.start().position();
            position.set(at.x, at.y, at.z);
            facing.identity();
            return true;
        }
        position.set(entity.getX(), anchor.atEyes() ? entity.getEyeY() : entity.getY(), entity.getZ());
        Vec3 offset = anchor.offset();
        switch (anchor.turning()) {
            case NONE -> facing.identity();
            case BODY -> facing.set(Rotations.fromYawPitchRoll(bodyYaw(entity), 0f, 0f));
            case LOOK -> facing.set(Rotations.fromYawPitchRoll(headYaw(entity), entity.getXRot(), 0f));
        }
        if (anchor.turning() == EffectAnchor.Turning.NONE) {
            position.add(offset.x, offset.y, offset.z);
        } else {
            Vector3f turned = facing.transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
            position.add(turned.x, turned.y, turned.z);
        }
        return !entity.isRemoved();
    }

    private static float bodyYaw(Entity entity) {
        return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
    }

    private static float headYaw(Entity entity) {
        return entity instanceof LivingEntity ? entity.getYHeadRot() : entity.getYRot();
    }
}
