package com.shifurace.race

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/** Regras da corrida que dependem de eventos do Minecraft e do Cobblemon. */
object RaceEvents {
    /** Porytronz de corrida carregados do disco que talvez tenham sobrado de uma queda do servidor. */
    private val toCheck = mutableListOf<Entity>()

    fun register() {
        // Ninguém toma dano na corrida; lava, fogo, sufocar, afogar e vazio levam ao checkpoint
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
            val owner = when {
                entity is ServerPlayer && RaceManager.raceOf(entity.uuid) != null -> entity.uuid
                entity is PokemonEntity && Rentals.isRental(entity) -> RaceManager.racerOfRental(entity)?.second?.uuid
                    ?: return@register false
                else -> return@register true
            }
            if (isHazard(source)) RaceManager.onHazard(owner)
            false
        }

        // O piloto monta clicando no próprio Porytronz; ninguém mais mexe nele
        UseEntityCallback.EVENT.register { player, world, _, entity, _ ->
            if (world.isClientSide || entity !is PokemonEntity || !Rentals.isRental(entity)) return@register InteractionResult.PASS
            if (player is ServerPlayer && RaceManager.tryMount(player, entity)) InteractionResult.SUCCESS else InteractionResult.FAIL
        }
        AttackEntityCallback.EVENT.register { _, world, _, entity, _ ->
            if (!world.isClientSide && Rentals.isRental(entity)) InteractionResult.FAIL else InteractionResult.PASS
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> RaceManager.onDisconnect(handler.player) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> RaceManager.onJoin(handler.player) }
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> if (entity is ServerPlayer) RaceManager.onDeath(entity) }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ -> RaceManager.onRespawn(newPlayer) }
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { player, _, _ -> RaceManager.onChangeDimension(player) }

        ServerEntityEvents.ENTITY_LOAD.register { entity, _ -> if (Rentals.isRental(entity)) toCheck += entity }

        registerCobblemon()
    }

    private fun registerCobblemon() {
        // Quem pilota o Porytronz de corrida é o piloto, mesmo sem ser o "dono" (o Pokémon não tem dono)
        CobblemonEvents.SELECT_DRIVER.subscribe(Priority.NORMAL) { event ->
            val rider = event.options.firstOrNull { it is Player } ?: return@subscribe
            val vehicle = rider.vehicle as? PokemonEntity ?: return@subscribe
            if (vehicle.ownerUUID != null) return@subscribe
            if (vehicle.level().isClientSide || Rentals.isRental(vehicle)) event.suggest(rider, 100)
        }

        CobblemonEvents.RIDE_EVENT_PRE.subscribe(Priority.HIGH) { event ->
            val target = event.pokemon
            val racer = RaceManager.racerOf(event.player.uuid)
            val rentalOwner = if (Rentals.isRental(target)) RaceManager.racerOfRental(target)?.second else null
            when {
                Rentals.isRental(target) && rentalOwner?.uuid != event.player.uuid -> event.cancel()
                racer != null && racer.rentalUuid != target.uuid -> {
                    event.cancel()
                    event.player.displayClientMessage(Component.literal("Na corrida você só pode montar no seu Porytronz.").withStyle(ChatFormatting.RED), true)
                }
            }
        }

        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST) { event ->
            if (Rentals.isRental(event.pokemon)) event.cancel()
        }

        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGH) { event ->
            val owner = event.pokemon.getOwnerPlayer() ?: return@subscribe
            if (RaceManager.racerOf(owner.uuid) != null) {
                event.cancel()
                owner.displayClientMessage(Component.literal("Não dá para soltar Pokémon durante a corrida.").withStyle(ChatFormatting.RED), true)
            }
        }

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGH) { event ->
            if (event.battle.players.any { RaceManager.racerOf(it.uuid) != null }) event.cancel()
        }
    }

    /** Remove Porytronz de corrida que voltaram do disco sem corrida ativa (servidor caiu no meio). */
    fun tick(server: MinecraftServer) {
        if (toCheck.isEmpty()) return
        val active = RaceManager.activeRentalIds()
        toCheck.forEach { if (it.uuid !in active) Rentals.discard(it) }
        toCheck.clear()
    }

    // Afogar fica de fora: a pista tem trecho de água, e quem afunda pode apertar a tecla de voltar
    private fun isHazard(source: DamageSource): Boolean =
        source.`is`(DamageTypeTags.IS_FIRE) || source.`is`(DamageTypes.LAVA) || source.`is`(DamageTypes.HOT_FLOOR) ||
            source.`is`(DamageTypes.IN_WALL) || source.`is`(DamageTypes.FELL_OUT_OF_WORLD)
}
