package com.shifurace

import com.shifurace.board.RecordBoards
import com.shifurace.command.RaceCommands
import com.shifurace.config.ConfigManager
import com.shifurace.data.RaceStorage
import com.shifurace.network.ServerNetworking
import com.shifurace.race.RaceEvents
import com.shifurace.race.RaceManager
import com.shifurace.registry.ModBlockEntities
import com.shifurace.registry.ModBlocks
import com.shifurace.registry.ModComponents
import com.shifurace.registry.ModItems
import com.shifurace.tool.EditTool
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.resources.ResourceLocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ShifuRace : ModInitializer {
    const val MOD_ID = "shifurace"
    val LOGGER: Logger = LoggerFactory.getLogger("ShifuRace")

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    override fun onInitialize() {
        ModComponents.register()
        ModBlocks.register()
        ModBlockEntities.register()
        ModItems.register()
        ServerNetworking.register()
        ConfigManager.load()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> RaceCommands.register(dispatcher) }

        ServerLifecycleEvents.SERVER_STARTED.register { srv ->
            RaceStorage.load(srv)
            RaceManager.attach(srv)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { srv -> RaceManager.shutdown(srv) }
        ServerLifecycleEvents.SERVER_STOPPED.register { RaceStorage.unload() }
        ServerTickEvents.END_SERVER_TICK.register { srv ->
            RaceManager.tick(srv)
            RaceEvents.tick(srv)
            RecordBoards.tick(srv)
            EditTool.tick(srv)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> EditTool.onDisconnect(handler.player) }

        RaceEvents.register()
        EditTool.register()
        LOGGER.info("ShifuRace carregado — boa corrida!")
    }
}
