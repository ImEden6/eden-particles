package com.eden.particles.impl.client;

import com.eden.particles.impl.load.DefinitionStore;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ClientEffects {

    public static final DefinitionStore STORE = new DefinitionStore(BuiltInRegistries.PARTICLE_TYPE::containsKey);

    private ClientEffects() {
    }
}
