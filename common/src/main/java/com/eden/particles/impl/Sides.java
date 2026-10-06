package com.eden.particles.impl;

import java.util.Optional;

/** Holds the client hooks once the client initialises; empty on a dedicated server. */
public final class Sides {

    private static volatile ClientHooks client;

    private Sides() {
    }

    public static void installClient(ClientHooks hooks) {
        client = hooks;
    }

    public static Optional<ClientHooks> client() {
        return Optional.ofNullable(client);
    }
}
