package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/** A template id (resolved when definitions are linked) or an inline template. */
public sealed interface TemplateRef {

    record Reference(ResourceLocation id) implements TemplateRef {
    }

    record Inline(ParticleTemplate template) implements TemplateRef {
    }

    Codec<TemplateRef> CODEC = Codecs.byKind("a template id or inline template", Codecs.<TemplateRef>encoder((ref, ops) -> switch (ref) {
                case Reference r -> Codecs.encodeWith(Codecs.ID, r.id(), ops);
                case Inline i -> Codecs.encodeWith(ParticleTemplate.CODEC, i.template(), ops);
            }))
            .when(Codecs.Kind.STRING, Codecs.ID.xmap(Reference::new, r -> ((Reference) r).id()))
            .when(Codecs.Kind.MAP, ParticleTemplate.CODEC.xmap(Inline::new, r -> ((Inline) r).template()))
            .build();
}
