package com.eden.particles.impl.codec;

/** Thrown inside object decoders; {@link ObjectCodec} turns it into a {@code DataResult} error. */
public final class DecodeException extends RuntimeException {

    DecodeException(String message) {
        super(message, null, false, false);
    }
}
