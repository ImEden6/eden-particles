package com.eden.particles.impl.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Primitive and structural codecs with strict checks and readable errors. */
public final class Codecs {

    public enum Kind { NUMBER, STRING, BOOLEAN, LIST, MAP, OTHER }

    public static final Codec<Float> FLOAT = number("a number", n -> {
        float f = n.floatValue();
        return Float.isFinite(f) ? DataResult.success(f) : DataResult.error(() -> "expected a finite number, got " + n);
    }, Codec.FLOAT);

    public static final Codec<Double> DOUBLE = number("a number", n -> {
        double d = n.doubleValue();
        return Double.isFinite(d) ? DataResult.success(d) : DataResult.error(() -> "expected a finite number, got " + n);
    }, Codec.DOUBLE);

    public static final Codec<Integer> INT = number("a whole number", n -> {
        double d = n.doubleValue();
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            return DataResult.error(() -> "expected a whole number, got " + n);
        }
        return DataResult.success((int) d);
    }, Codec.INT);

    public static final Codec<Boolean> BOOL = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Boolean, T>> decode(DynamicOps<T> ops, T input) {
            if (kindOf(ops, input) != Kind.BOOLEAN) {
                return DataResult.error(() -> "expected true or false, got " + describe(input));
            }
            return ops.getBooleanValue(input).map(b -> Pair.of(b, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(Boolean input, DynamicOps<T> ops, T prefix) {
            return Codec.BOOL.encode(input, ops, prefix);
        }
    };

    public static final Codec<String> STRING = new Codec<>() {
        @Override
        public <T> DataResult<Pair<String, T>> decode(DynamicOps<T> ops, T input) {
            if (kindOf(ops, input) != Kind.STRING) {
                return DataResult.error(() -> "expected a string, got " + describe(input));
            }
            return ops.getStringValue(input).map(s -> Pair.of(s, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(String input, DynamicOps<T> ops, T prefix) {
            return Codec.STRING.encode(input, ops, prefix);
        }
    };

    public static final Codec<ResourceLocation> ID = STRING.comapFlatMap(
            s -> {
                ResourceLocation id = ResourceLocation.tryParse(s);
                return id != null ? DataResult.success(id) : DataResult.error(() -> "invalid id '" + s + "'");
            },
            ResourceLocation::toString);

    public static final Codec<Vector3fc> VEC3 = list(FLOAT).comapFlatMap(
            values -> values.size() == 3
                    ? DataResult.success(new Vector3f(values.get(0), values.get(1), values.get(2)))
                    : DataResult.error(() -> "expected [x, y, z], got " + values.size() + " values"),
            v -> List.of(v.x(), v.y(), v.z()));

    /** {@code "#RRGGBB"} or {@code "RRGGBB"}, as {@code 0xRRGGBB}. */
    public static final Codec<Integer> COLOR = STRING.comapFlatMap(
            s -> {
                String hex = s.startsWith("#") ? s.substring(1) : s;
                if (hex.length() == 6 && hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                    return DataResult.success(Integer.parseInt(hex, 16));
                }
                return DataResult.error(() -> "invalid colour '" + s + "', expected #RRGGBB");
            },
            rgb -> String.format(Locale.ROOT, "#%06X", rgb));

    private Codecs() {
    }

    public static Codec<Integer> intAtLeast(int min) {
        return INT.validate(i -> i >= min ? DataResult.success(i) : DataResult.error(() -> "must be at least " + min + ", got " + i));
    }

    public static Codec<Integer> intIn(int min, int max) {
        return INT.validate(i -> i >= min && i <= max
                ? DataResult.success(i)
                : DataResult.error(() -> "must be between " + min + " and " + max + ", got " + i));
    }

    public static Codec<Float> floatIn(float min, float max) {
        return FLOAT.validate(f -> f >= min && f <= max
                ? DataResult.success(f)
                : DataResult.error(() -> "must be between " + min + " and " + max + ", got " + f));
    }

    public static Codec<Float> floatAtLeast(float min) {
        return FLOAT.validate(f -> f >= min ? DataResult.success(f) : DataResult.error(() -> "must be at least " + min + ", got " + f));
    }

    /** List whose element errors carry their index in the path. */
    public static <A> Codec<List<A>> list(Codec<A> element) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<List<A>, T>> decode(DynamicOps<T> ops, T input) {
                if (kindOf(ops, input) != Kind.LIST) {
                    return DataResult.error(() -> "expected a list, got " + describe(input));
                }
                List<T> raw = ops.getStream(input).getOrThrow().toList();
                List<A> values = new ArrayList<>(raw.size());
                for (int i = 0; i < raw.size(); i++) {
                    DataResult<A> result = element.parse(ops, raw.get(i));
                    if (result.error().isPresent()) {
                        String message = ObjectCodec.atPath("[" + i + "]", result.error().get().message());
                        return DataResult.error(() -> message);
                    }
                    values.add(result.result().orElseThrow());
                }
                return DataResult.success(Pair.of(List.copyOf(values), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(List<A> input, DynamicOps<T> ops, T prefix) {
                return element.listOf().encode(input, ops, prefix);
            }
        };
    }

    public static <A> Codec<List<A>> nonEmptyList(Codec<A> element) {
        return list(element).validate(l -> l.isEmpty() ? DataResult.error(() -> "must not be empty") : DataResult.success(l));
    }

    /** Lowercase enum names, with a suggestion on typos. */
    public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, String what) {
        Map<String, E> byName = new java.util.LinkedHashMap<>();
        for (E constant : type.getEnumConstants()) {
            byName.put(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        return STRING.comapFlatMap(
                s -> {
                    E value = byName.get(s);
                    return value != null ? DataResult.success(value) : DataResult.error(() -> unknownChoice(what, s, byName.keySet()));
                },
                e -> e.name().toLowerCase(Locale.ROOT));
    }

    @FunctionalInterface
    public interface EncodeFn<A> {
        Object encode(A value, DynamicOps<Object> ops);
    }

    /** Encode-only codec from a lambda; for the encoding side of {@link #byKind}. */
    @SuppressWarnings("unchecked")
    public static <A> Codec<A> encoder(EncodeFn<A> fn) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return DataResult.error(() -> "encode only");
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return DataResult.success((T) fn.encode(input, (DynamicOps<Object>) ops));
            }
        };
    }

    public static <A> Object encodeWith(Codec<A> codec, A value, DynamicOps<Object> ops) {
        return codec.encodeStart(ops, value).getOrThrow();
    }

    /** {@code " (did you mean 'x'?)"}, or empty when nothing is close. */
    public static String didYouMean(String value, Collection<String> options) {
        return Suggestions.didYouMean(value, options);
    }

    public static String unknownChoice(String what, String value, Collection<String> options) {
        return "unknown " + what + " '" + value + "'" + Suggestions.didYouMean(value, options) + "; expected one of " + options;
    }

    /** Decodes by JSON kind (number, string, list, object); encodes with {@code encoder}. */
    public static <A> KindBuilder<A> byKind(String what, Codec<A> encoder) {
        return new KindBuilder<>(what, encoder);
    }

    public static final class KindBuilder<A> {
        private final String what;
        private final Codec<A> encoder;
        private final Map<Kind, Codec<A>> decoders = new EnumMap<>(Kind.class);

        private KindBuilder(String what, Codec<A> encoder) {
            this.what = what;
            this.encoder = encoder;
        }

        public KindBuilder<A> when(Kind kind, Codec<A> decoder) {
            decoders.put(kind, decoder);
            return this;
        }

        public Codec<A> build() {
            Map<Kind, Codec<A>> table = new EnumMap<>(decoders);
            String accepted = table.keySet().stream().map(k -> k.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" or "));
            return new Codec<>() {
                @Override
                public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                    Codec<A> decoder = table.get(kindOf(ops, input));
                    if (decoder == null) {
                        return DataResult.error(() -> "expected " + what + " (" + accepted + "), got " + describe(input));
                    }
                    return decoder.decode(ops, input);
                }

                @Override
                public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                    return encoder.encode(input, ops, prefix);
                }
            };
        }
    }

    public static <T> Kind kindOf(DynamicOps<T> ops, T input) {
        if (input instanceof JsonElement json) {
            if (json instanceof JsonObject) return Kind.MAP;
            if (json instanceof JsonArray) return Kind.LIST;
            if (json instanceof JsonPrimitive p) {
                if (p.isBoolean()) return Kind.BOOLEAN;
                if (p.isNumber()) return Kind.NUMBER;
                if (p.isString()) return Kind.STRING;
            }
            return Kind.OTHER;
        }
        if (ops.getMap(input).result().isPresent()) return Kind.MAP;
        if (ops.getStream(input).result().isPresent()) return Kind.LIST;
        if (ops.getNumberValue(input).result().isPresent()) return Kind.NUMBER;
        if (ops.getStringValue(input).result().isPresent()) return Kind.STRING;
        return Kind.OTHER;
    }

    static String describe(Object input) {
        String text = String.valueOf(input);
        return text.length() > 40 ? text.substring(0, 37) + "..." : text;
    }

    private static <A> Codec<A> number(String what, Function<Number, DataResult<A>> convert, Codec<A> encoder) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                if (kindOf(ops, input) != Kind.NUMBER) {
                    return DataResult.error(() -> "expected " + what + ", got " + describe(input));
                }
                return ops.getNumberValue(input).flatMap(convert).map(v -> Pair.of(v, ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return encoder.encode(input, ops, prefix);
            }
        };
    }
}
