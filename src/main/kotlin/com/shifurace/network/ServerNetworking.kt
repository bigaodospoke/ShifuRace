package com.shifurace.network

import com.shifurace.race.Msg
import com.shifurace.race.RaceManager
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

object ServerNetworking {
    fun register() {
        PayloadTypeRegistry.playS2C().register(RaceTrackPayload.TYPE, RaceTrackPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RaceHudPayload.TYPE, RaceHudPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(LapToastPayload.TYPE, LapToastPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RaceResultPayload.TYPE, RaceResultPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(EditViewPayload.TYPE, EditViewPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ResetRequestPayload.TYPE, ResetRequestPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(ResetRequestPayload.TYPE) { _, context ->
            val player = context.player()
            RaceManager.requestReset(player)?.let {
                player.displayClientMessage(Component.literal(it).withStyle(ChatFormatting.RED), true)
            }
        }
    }

    fun send(player: ServerPlayer, payload: CustomPacketPayload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload)
    }

    @Suppress("unused")
    fun hint(player: ServerPlayer, text: String) = player.sendSystemMessage(Msg.info(text))
}
