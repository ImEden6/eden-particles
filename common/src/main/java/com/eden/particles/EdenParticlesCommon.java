package com.eden.particles;

import com.eden.particles.impl.EffectSuggestions;
import com.eden.particles.platform.Services;

/**
 * Shared bootstrap, called from each loader's entrypoint.
 */
public final class EdenParticlesCommon {

    private EdenParticlesCommon() {
    }

    public static void init() {
        Constants.LOG.info("Initializing {} on {} ({})", Constants.MOD_NAME,
                Services.PLATFORM.getPlatformName(), Services.PLATFORM.getEnvironmentName());
        EffectSuggestions.init();
    }
}
