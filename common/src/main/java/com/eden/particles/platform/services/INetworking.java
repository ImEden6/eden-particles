package com.eden.particles.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public interface INetworking {

    /** False when the player's client doesn't have this mod. */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    void send(ServerPlayer player, CustomPacketPayload payload);
}
