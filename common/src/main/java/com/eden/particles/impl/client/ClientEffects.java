package com.eden.particles.impl.client;

import com.eden.particles.impl.load.DefinitionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Client entry points for the loader glue. */
public final class ClientEffects {

    /** Vanilla render mode supports only option-less particle types. */
    public static final DefinitionStore STORE = new DefinitionStore(id -> BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType);

    public static final EffectManager MANAGER = new EffectManager(STORE);

    private ClientEffects() {
    }

    public static void tick() {
        MANAGER.tick(Minecraft.getInstance());
    }
}
