package com.shifurace.trophy

import com.shifurace.race.Msg
import com.shifurace.registry.ModComponents
import com.shifurace.registry.ModItems
import com.shifurace.track.TrackData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object TrophyService {
    fun create(owner: String, track: TrackData, timeMs: Long, laps: Int, racers: Int): ItemStack {
        val stack = ItemStack(ModItems.TROPHY)
        stack.set(ModComponents.TROPHY_INFO, TrophyInfo(owner, track.nome, timeMs, laps, racers, Msg.today(), track.corTrofeu))
        return stack
    }

    fun give(player: ServerPlayer, track: TrackData, timeMs: Long, laps: Int, racers: Int) {
        val stack = create(player.gameProfile.name, track, timeMs, laps, racers)
        if (!player.inventory.add(stack)) player.drop(stack, false)
        Msg.send(player, "Você ganhou o Troféu de Corrida da pista ${track.nome}!")
    }
}
