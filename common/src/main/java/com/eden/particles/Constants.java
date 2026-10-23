package com.eden.particles;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {

    public static final String MOD_ID = "eden_particles";
    public static final String MOD_NAME = "Eden's Particles";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    public static final int PROTOCOL = 2;

    private Constants() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Payload ids carry the protocol, so a client and server on different protocols just don't see each other's channels. */
    public static ResourceLocation payload(String path) {
        return id("v" + PROTOCOL + "/" + path);
    }
}
