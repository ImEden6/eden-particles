package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * Plays {@code effect} where a particle dies or lands. {@code chance} and {@code inputs} are expressions over the
 * spawning effect's inputs, evaluated when the particle spawns.
 */
public record Trigger(ResourceLocation effect, Expr chance, Aim aim, Map<String, Expr> inputs) {

    /** {@code velocity} turns the played effect's +Z along the particle's velocity. */
    public enum Aim { NONE, VELOCITY }

    private static final Codec<Aim> AIM = Codecs.enumCodec(Aim.class, "aim");

    public static final Codec<Trigger> CODEC = ObjectCodec.of("trigger", f -> new Trigger(
            f.required("effect", Codecs.ID),
            f.optional("chance", ExprCodecs.in(ExprCodecs.NUMBER, 0f, 1f), Expr.ONE),
            f.optional("aim", AIM, Aim.NONE),
            f.optional("inputs", ExprCodecs.INPUT_MAP, Map.of())), (t, out) -> out
            .put("effect", Codecs.ID, t.effect)
            .put("chance", ExprCodecs.NUMBER, t.chance)
            .put("aim", AIM, t.aim)
            .put("inputs", ExprCodecs.INPUT_MAP, t.inputs));

    public void names(Set<String> out) {
        chance.names(out);
        inputs.values().forEach(e -> e.names(out));
    }
}
