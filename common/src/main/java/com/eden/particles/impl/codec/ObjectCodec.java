package com.eden.particles.impl.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Codec for a JSON object, written imperatively. Every key the decoder asks for is recorded; keys left over
 * afterwards are rejected as unknown, so typos fail loudly instead of being ignored.
 * <p>
 * Errors are formatted {@code at <path>: <message>}, with paths built up as nested codecs report failures.
 */
public final class ObjectCodec<A> implements Codec<A> {

    private final String what;
    private final Function<Fields, A> decoder;
    private final BiConsumer<A, Out> encoder;

    private ObjectCodec(String what, Function<Fields, A> decoder, BiConsumer<A, Out> encoder) {
        this.what = what;
        this.decoder = decoder;
        this.encoder = encoder;
    }

    public static <A> ObjectCodec<A> of(String what, Function<Fields, A> decoder, BiConsumer<A, Out> encoder) {
        return new ObjectCodec<>(what, decoder, encoder);
    }

    /** For {@link Codecs#byKind} branches, where a separate encoder handles encoding. */
    public static <A> ObjectCodec<A> decoder(String what, Function<Fields, A> decoder) {
        return new ObjectCodec<>(what, decoder, (value, out) -> {
            throw new UnsupportedOperationException("decode only");
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
        if (Codecs.kindOf(ops, input) != Codecs.Kind.MAP) {
            return DataResult.error(() -> "expected " + what + " object, got " + Codecs.describe(input));
        }
        MapLike<T> map = ops.getMap(input).getOrThrow();
        Fields fields = new Fields((DynamicOps<Object>) ops, (MapLike<Object>) map);
        try {
            A value = decoder.apply(fields);
            fields.rejectUnknownKeys();
            return DataResult.success(Pair.of(value, ops.empty()));
        } catch (DecodeException e) {
            return DataResult.error(e::getMessage);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
        Out out = new Out((DynamicOps<Object>) ops);
        encoder.accept(input, out);
        return (DataResult<T>) out.builder.build(prefix);
    }

    /** Prefixes {@code name} onto an error's path. */
    static String atPath(String name, String message) {
        if (message.startsWith("at ")) {
            String rest = message.substring(3);
            return "at " + name + (rest.startsWith("[") ? "" : ".") + rest;
        }
        return "at " + name + ": " + message;
    }

    static DecodeException fail(String message) {
        return new DecodeException(message);
    }

    public static final class Fields {

        private final DynamicOps<Object> ops;
        private final MapLike<Object> map;
        private final Set<String> present = new LinkedHashSet<>();
        private final Set<String> known = new LinkedHashSet<>();

        private Fields(DynamicOps<Object> ops, MapLike<Object> map) {
            this.ops = ops;
            this.map = map;
            map.entries().forEach(entry -> present.add(ops.getStringValue(entry.getFirst()).getOrThrow()));
        }

        public boolean has(String name) {
            known.add(name);
            return present.contains(name);
        }

        public <V> V required(String name, Codec<V> codec) {
            if (!has(name)) {
                Set<String> unread = new LinkedHashSet<>(present);
                unread.removeAll(known);
                String hint = Suggestions.closest(name, unread).map(s -> " (found '" + s + "', a typo?)").orElse("");
                throw fail("missing required field '" + name + "'" + hint);
            }
            return parse(name, codec);
        }

        public <V> Optional<V> optional(String name, Codec<V> codec) {
            return has(name) ? Optional.of(parse(name, codec)) : Optional.empty();
        }

        public <V> V optional(String name, Codec<V> codec, V fallback) {
            return has(name) ? parse(name, codec) : fallback;
        }

        /** Fails with the message attached to field {@code name}. */
        public DecodeException failAt(String name, String message) {
            return fail(atPath(name, message));
        }

        public DecodeException fail(String message) {
            return ObjectCodec.fail(message);
        }

        public <V> V orFail(DataResult<V> result) {
            return result.result().orElseThrow(() -> fail(result.error().orElseThrow().message()));
        }

        private <V> V parse(String name, Codec<V> codec) {
            DataResult<V> result = codec.parse(ops, map.get(name));
            return result.result().orElseThrow(() -> fail(atPath(name, result.error().orElseThrow().message())));
        }

        private void rejectUnknownKeys() {
            for (String key : present) {
                if (!known.contains(key)) {
                    List<String> expected = List.copyOf(known);
                    throw fail("unknown field '" + key + "'" + Suggestions.didYouMean(key, expected)
                            + "; expected one of " + expected);
                }
            }
        }
    }

    public static final class Out {

        private final DynamicOps<Object> ops;
        private final RecordBuilder<Object> builder;

        private Out(DynamicOps<Object> ops) {
            this.ops = ops;
            this.builder = ops.mapBuilder();
        }

        public <V> Out put(String name, Codec<V> codec, V value) {
            builder.add(name, value, codec);
            return this;
        }

        public <V> Out put(String name, Codec<V> codec, Optional<V> value) {
            value.ifPresent(v -> builder.add(name, v, codec));
            return this;
        }
    }
}
