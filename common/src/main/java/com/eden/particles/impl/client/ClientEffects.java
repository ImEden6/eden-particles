package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.ClientHooks;
import com.eden.particles.impl.Sides;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** Client entry points for the loader glue. */
public final class ClientEffects {

    /** Vanilla render mode supports only option-less particle types. */
    public static final DefinitionStore STORE = new DefinitionStore(id -> BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType);

    public static final EffectManager MANAGER = new EffectManager(STORE);

    static final ClientHooksImpl HOOKS_IMPL = new ClientHooksImpl(MANAGER);
    public static final ClientHooks HOOKS = HOOKS_IMPL;

    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    private ClientEffects() {
    }

    public static void init() {
        Sides.installClient(HOOKS);
        Preview.init();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        HOOKS_IMPL.tick(minecraft);
        MANAGER.images().tick(minecraft);
        MANAGER.tick(minecraft);
    }

    /** Logs a load, plus a toast in development when something failed. */
    static void report(LoadReport report) {
        Constants.LOG.info(report.summary());
        report.errors().forEach(error -> Constants.LOG.error("{}", error));
        if (!report.errors().isEmpty() && Services.PLATFORM.isDevelopmentEnvironment()) {
            SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(), TOAST,
                    Component.literal(Constants.MOD_NAME + ": " + report.errors().size() + " definition error(s)"),
                    Component.literal(report.errors().get(0).toString()));
        }
    }
}
