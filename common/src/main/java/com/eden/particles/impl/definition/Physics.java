package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Vanilla particle physics: {@code gravity} scales 0.04 blocks/tick², {@code drag} is the per-tick friction. */
public record Physics(float gravity, float drag, boolean collide) {

    public static final Physics DEFAULT = new Physics(0f, 0.98f, false);

    private static final Codec<Float> DRAG = Codecs.FLOAT.validate(d -> d > 0f && d <= 1f
            ? DataResult.success(d)
            : DataResult.error(() -> "must be greater than 0 and at most 1, got " + d));

    public static final Codec<Physics> CODEC = ObjectCodec.of("physics",
            f -> new Physics(
                    f.optional("gravity", Codecs.FLOAT, DEFAULT.gravity),
                    f.optional("drag", DRAG, DEFAULT.drag),
                    f.optional("collide", Codecs.BOOL, DEFAULT.collide)),
            (p, out) -> out.put("gravity", Codecs.FLOAT, p.gravity).put("drag", Codecs.FLOAT, p.drag).put("collide", Codecs.BOOL, p.collide));
}
