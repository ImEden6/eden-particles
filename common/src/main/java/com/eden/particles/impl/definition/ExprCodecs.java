package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.expr.ExprParser;
import com.eden.particles.impl.expr.InputNames;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Input names to expressions, for passing inputs to another effect. */
    public static final Codec<Map<String, Expr>> INPUT_MAP = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Map<String, Expr>, T>> decode(DynamicOps<T> ops, T input) {
            if (Codecs.kindOf(ops, input) != Codecs.Kind.MAP) {
                return DataResult.error(() -> "expected an object of input names to numbers or expressions");
            }
            Map<String, Expr> inputs = new LinkedHashMap<>();
            for (var entry : ops.getMap(input).getOrThrow().entries().toList()) {
                String name = ops.getStringValue(entry.getFirst()).getOrThrow();
                if (!InputNames.isValid(name) || name.equals(InputNames.DISTANCE)) {
                    return DataResult.error(() -> "at " + name + ": not an input that can be set");
                }
                DataResult<Expr> value = NUMBER.parse(ops, entry.getSecond());
                if (value.error().isPresent()) return DataResult.error(() -> "at " + name + ": " + value.error().get().message());
                inputs.put(name, value.getOrThrow());
            }
            return DataResult.success(Pair.of(Collections.unmodifiableMap(inputs), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(Map<String, Expr> input, DynamicOps<T> ops, T prefix) {
            return Codec.unboundedMap(Codec.STRING, NUMBER).encode(input, ops, prefix);
        }
    };

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
