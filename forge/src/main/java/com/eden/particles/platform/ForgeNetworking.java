package com.eden.particles.platform;

import com.eden.particles.Constants;
import com.eden.particles.impl.Sides;
import com.eden.particles.impl.images.ServerImages;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.impl.net.UpdateEffectPayload;
import com.eden.particles.platform.services.INetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

public class ForgeNetworking implements INetworking {

    private static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder.named(Constants.id("main"))
            .networkProtocolVersion(Constants.PROTOCOL)
            .optional()
            .payloadChannel()
            .play()
            .clientbound()
            .addMain(PlayEffectPayload.TYPE, PlayEffectPayload.STREAM_CODEC, (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)))
            .addMain(StopEffectPayload.TYPE, StopEffectPayload.STREAM_CODEC, (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)))
            .addMain(UpdateEffectPayload.TYPE, UpdateEffectPayload.STREAM_CODEC, (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)))
            .addMain(SyncDefinitionsPayload.TYPE, SyncDefinitionsPayload.STREAM_CODEC, (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)))
            .addMain(ImagePayloads.Offer.TYPE, ImagePayloads.Offer.STREAM_CODEC, (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)))
            .addMain(ImagePayloads.Images.TYPE, ImagePayloads.Images.STREAM_CODEC, (payload, context) -> Sides.client().ifPresent(client -> client.handle(payload)))
            .serverbound()
            .addMain(ImagePayloads.Consent.TYPE, ImagePayloads.Consent.STREAM_CODEC, (payload, context) -> ServerImages.onConsent(context.getSender(), payload))
            .build();

    /** Channels must exist before players connect; called from the mod constructor. */
    public static void init() {
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return CHANNEL.isRemotePresent(player.connection.getConnection());
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }
}
