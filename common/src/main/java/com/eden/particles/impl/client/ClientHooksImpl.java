package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.AnchorMath;
import com.eden.particles.impl.ClientHooks;
import com.eden.particles.impl.ParamChecks;
import com.eden.particles.impl.math.Rotations;
import com.eden.particles.impl.net.EffectState;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.UpdateEffectPayload;
import com.eden.particles.impl.load.CodeDefinitions;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.load.DefinitionStore;
import com.eden.particles.impl.load.LoadReport;
import com.eden.particles.impl.load.ParsedDefinitions;
import com.eden.particles.impl.load.RawDefinition;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.eden.particles.impl.runtime.Anchor;
import com.eden.particles.impl.runtime.EffectInstance;
import com.eden.particles.impl.runtime.PlayParams;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class ClientHooksImpl implements ClientHooks {

    private final EffectManager manager;
    private final Map<Integer, EffectInstance> fromServer = new HashMap<>();
    private final Set<ResourceLocation> warnedUnknown = new HashSet<>();
    private final Set<String> warnedInputs = new HashSet<>();
    private final List<SyncDefinitionsPayload.Entry> incoming = new ArrayList<>();
    private final CodeDefinitions code = new CodeDefinitions();
    private List<SyncDefinitionsPayload.Entry> applied = List.of();

    ClientHooksImpl(EffectManager manager) {
        this.manager = manager;
        ClientEffects.STORE.onReload(report -> {
            warnedUnknown.clear();
            warnedInputs.clear();
        });
    }

    @Override
    public EffectHandle play(ResourceLocation effect, EffectAnchor anchor, EffectParams params) {
        long seed = params.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
        return start(effect, follow(anchor), EffectState.of(params), seed)
                .<EffectHandle>map(instance -> new Handle(this, effect, instance)).orElse(EffectHandle.NONE);
    }

    @Override
    public boolean isLoaded(ResourceLocation effect) {
        return manager.isLoaded(effect);
    }

    @Override
    public Collection<ResourceLocation> effectIds() {
        return ClientEffects.STORE.effectIds();
    }

    @Override
    public void onReload(Runnable listener) {
        ClientEffects.STORE.onReload(report -> listener.run());
    }

    @Override
    public List<String> registerEffect(ResourceLocation id, JsonElement json) {
        code.putEffect(id, json.deepCopy());
        return relinkCode(id);
    }

    @Override
    public List<String> registerTemplate(ResourceLocation id, JsonElement json) {
        code.putTemplate(id, json.deepCopy());
        return relinkCode(id);
    }

    @Override
    public boolean unregister(ResourceLocation id) {
        if (!code.remove(id)) return false;
        ClientEffects.report(ClientEffects.STORE.replace(DefinitionStore.Layer.CODE, code.parse()));
        return true;
    }

    @Override
    public void handle(PlayEffectPayload p) {
        fromServer.values().removeIf(EffectInstance::isFinished);
        Level level = Minecraft.getInstance().level;
        EffectAnchor from = p.anchor().toAnchor(level);
        EffectAnchor anchor = p.target().map(to -> EffectAnchor.between(from, to.toAnchor(level))).orElse(from);
        start(p.effect(), follow(anchor), p.state(), p.seed())
                .ifPresent(instance -> {
                    instance.fastForward(p.elapsed());
                    EffectInstance previous = fromServer.put(p.instanceId(), instance);
                    if (previous != null) previous.stop();
                });
    }

    @Override
    public void handle(StopEffectPayload p) {
        if (p.instanceId() == StopEffectPayload.ALL) {
            fromServer.values().forEach(EffectInstance::stop);
            fromServer.clear();
        } else {
            EffectInstance instance = fromServer.remove(p.instanceId());
            if (instance != null) instance.stop();
        }
    }

    @Override
    public void handle(UpdateEffectPayload p) {
        EffectInstance instance = fromServer.get(p.instanceId());
        if (instance != null && !instance.isFinished()) apply(instance, p.state());
    }

    @Override
    public void handle(SyncDefinitionsPayload payload) {
        if (payload.first()) incoming.clear();
        incoming.addAll(payload.entries());
        if (!payload.last()) return;
        List<SyncDefinitionsPayload.Entry> received = List.copyOf(incoming);
        incoming.clear();
        // /reload re-sends unchanged definitions; replacing them would needlessly stop every playing effect.
        if (received.equals(applied)) return;
        applied = received;
        ClientEffects.report(ClientEffects.STORE.replace(DefinitionStore.Layer.DATAPACK, parse(received)));
    }

    @Override
    public void handle(ImagePayloads.Offer payload) {
        manager.images().handle(payload);
    }

    @Override
    public void handle(ImagePayloads.Images payload) {
        manager.images().handle(payload);
    }

    /** Drops the server's datapack definitions after disconnecting. */
    void tick(Minecraft minecraft) {
        if (minecraft.getConnection() == null && !applied.isEmpty()) {
            applied = List.of();
            ClientEffects.STORE.replace(DefinitionStore.Layer.DATAPACK, ParsedDefinitions.EMPTY);
        }
    }

    private List<String> relinkCode(ResourceLocation id) {
        LoadReport report = ClientEffects.STORE.replace(DefinitionStore.Layer.CODE, code.parse());
        ClientEffects.report(report);
        return CodeDefinitions.errorsFor(report, id);
    }

    private static ParsedDefinitions parse(List<SyncDefinitionsPayload.Entry> entries) {
        List<RawDefinition> templates = new ArrayList<>();
        List<RawDefinition> effects = new ArrayList<>();
        List<DefinitionError> errors = new ArrayList<>();
        for (SyncDefinitionsPayload.Entry entry : entries) {
            try {
                RawDefinition raw = new RawDefinition(entry.id(), "server datapack", JsonParser.parseString(entry.json()));
                (entry.kind() == DefinitionError.Kind.TEMPLATE ? templates : effects).add(raw);
            } catch (JsonParseException e) {
                errors.add(new DefinitionError(entry.kind(), entry.id(), "server datapack", "invalid JSON: " + e.getMessage()));
            }
        }
        return ParsedDefinitions.parse(templates, effects, errors);
    }

    private Optional<EffectInstance> start(ResourceLocation effect, Anchor anchor, EffectState state, long seed) {
        PlayParams params = new PlayParams(state.scale(), Rotations.fromYawPitchRoll(state.yaw(), state.pitch(), state.roll()), state.tint(), seed);
        Optional<EffectInstance> instance = manager.play(effect, anchor, params);
        if (instance.isEmpty() && !manager.isLoaded(effect) && warnedUnknown.add(effect)) {
            Constants.LOG.warn("Unknown effect '{}'; is the resource pack that defines it loaded?", effect);
        }
        instance.ifPresent(i -> state.inputs().forEach((name, value) -> setInput(effect, i, name, value)));
        return instance;
    }

    private void apply(EffectInstance instance, EffectState state) {
        instance.setScale(state.scale());
        instance.setRotation(Rotations.fromYawPitchRoll(state.yaw(), state.pitch(), state.roll()));
        instance.setTint(state.tint());
        state.inputs().forEach((name, value) -> setInput(instance.effect().id(), instance, name, value));
    }

    private void setInput(ResourceLocation effect, EffectInstance instance, String name, float value) {
        if (!instance.setInput(name, value) && warnedInputs.add(effect + "/" + name)) {
            Constants.LOG.warn("Effect '{}' has no input '{}'; declared inputs are {}", effect, name,
                    instance.effect().inputIds().keySet());
        }
    }

    private static Anchor follow(EffectAnchor anchor) {
        if (anchor.entity().isEmpty() && anchor.target().isEmpty()) {
            Vec3 at = anchor.position();
            return Anchor.fixed(at.x, at.y, at.z);
        }
        return out -> AnchorMath.place(anchor, out);
    }

    private record Handle(ClientHooksImpl hooks, ResourceLocation effect, EffectInstance instance) implements EffectHandle {
        @Override
        public void stop() {
            instance.stop();
        }

        @Override
        public boolean isAlive() {
            return !instance.isFinished();
        }

        @Override
        public void setInput(String name, float value) {
            ParamChecks.input(name, value);
            hooks.setInput(effect, instance, name, value);
        }

        @Override
        public void setTint(int rgb) {
            instance.setTint(rgb & 0xFFFFFF);
        }

        @Override
        public void setScale(float scale) {
            ParamChecks.scale(scale);
            instance.setScale(scale);
        }

        @Override
        public void setRotation(float yaw, float pitch, float roll) {
            ParamChecks.rotation(yaw, pitch, roll);
            instance.setRotation(Rotations.fromYawPitchRoll(yaw, pitch, roll));
        }
    }
}
