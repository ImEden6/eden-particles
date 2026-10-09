package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.expr.ExprParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

/**
 * A number or an expression string over inputs. Range checks apply to plain numbers at load time; expressions are
 * clamped where they're used.
 */
public final class ExprCodecs {

    public static final Codec<Expr> NUMBER = of(Codecs.FLOAT, false);

    /** A whole number, or an expression rounded where it's used. */
    public static final Codec<Expr> WHOLE = of(Codecs.INT.xmap(Integer::floatValue, Float::intValue), true);

    public static final Codec<ExprVec3> VEC3 = Codecs.list(NUMBER).comapFlatMap(
            values -> values.size() == 3
                    ? DataResult.success(new ExprVec3(values.get(0), values.get(1), values.get(2)))
                    : DataResult.error(() -> "expected [x, y, z], got " + values.size() + " values"),
            v -> List.of(v.x(), v.y(), v.z()));

    private ExprCodecs() {
    }

    public static Codec<Expr> atLeast(Codec<Expr> codec, float min) {
        return codec.validate(e -> !(e instanceof Expr.Const c) || c.value() >= min
                ? DataResult.success(e)
                : DataResult.error(() -> "must be at least " + format(min) + ", got " + format(((Expr.Const) e).value())));
    }

    public static Codec<Expr> in(Codec<Expr> codec, float min, float max) {
        return codec.validate(e -> !(e instanceof Expr.Const c) || (c.value() >= min && c.value() <= max)
                ? DataResult.success(e)
                : DataResult.error(() -> "must be between " + min + " and " + max + ", got " + ((Expr.Const) e).value()));
    }

    private static String format(float value) {
        return value == (int) value ? Integer.toString((int) value) : Float.toString(value);
    }

    private static Codec<Expr> of(Codec<Float> number, boolean whole) {
        return Codecs.byKind("a number or expression", Codecs.<Expr>encoder((e, ops) -> !(e instanceof Expr.Const c)
                        ? ops.createString(e.print())
                        : whole ? ops.createInt((int) c.value()) : ops.createFloat(c.value())))
                .when(Codecs.Kind.NUMBER, number.xmap(Expr::constant, e -> ((Expr.Const) e).value()))
                .when(Codecs.Kind.STRING, Codecs.STRING.comapFlatMap(ExprCodecs::parse, Expr::print))
                .build();
    }

    private static DataResult<Expr> parse(String text) {
        try {
            return DataResult.success(ExprParser.parse(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }
}
