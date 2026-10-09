package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plays {@code effect} from {@code start} for {@code duration} ticks, following the parent's anchor. {@code offset} is
 * in the parent's frame, {@code rotation} is yaw, pitch, roll in degrees on top of the parent's, and {@code inputs} are
 * expressions over the parent's inputs. A duration of {@link #REST} runs to the end of the parent's timeline.
 */
public record SubEffect(
        Optional<String> name,
        ResourceLocation effect,
        int start,
        int duration,
        ExprVec3 offset,
        Vector3fc rotation,
        Expr scale,
        Map<String, Expr> inputs) implements Track {

    public static final int REST = -1;
    private static final Vector3fc NO_ROTATION = new Vector3f();

    public static final Codec<SubEffect> CODEC = ObjectCodec.of("sub-effect", f -> new SubEffect(
            f.optional("name", Codecs.STRING),
            f.required("effect", Codecs.ID),
            f.optional("start", Codecs.intAtLeast(0), 0),
            f.optional("duration", Codecs.intAtLeast(1), REST),
            f.optional("offset", ExprCodecs.VEC3, ExprVec3.ZERO),
            f.optional("rotation", Codecs.VEC3, NO_ROTATION),
            f.optional("scale", ExprCodecs.NUMBER, Expr.ONE),
            f.optional("inputs", ExprCodecs.INPUT_MAP, Map.of())), (s, out) -> out
            .put("name", Codecs.STRING, s.name)
            .put("effect", Codecs.ID, s.effect)
            .put("start", Codecs.INT, s.start)
            .put("duration", Codecs.INT, s.duration)
            .put("offset", ExprCodecs.VEC3, s.offset)
            .put("rotation", Codecs.VEC3, s.rotation)
            .put("scale", ExprCodecs.NUMBER, s.scale)
            .put("inputs", ExprCodecs.INPUT_MAP, s.inputs));

    SubEffect withDuration(int duration) {
        return new SubEffect(name, effect, start, duration, offset, rotation, scale, inputs);
    }

    @Override
    public void names(Set<String> out) {
        offset.names(out);
        scale.names(out);
        inputs.values().forEach(e -> e.names(out));
    }
}
