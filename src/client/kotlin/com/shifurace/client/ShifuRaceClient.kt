package com.shifurace.client

import com.mojang.blaze3d.platform.InputConstants
import com.shifurace.client.hud.RaceHud
import com.shifurace.client.render.GateRenderer
import com.shifurace.client.render.TrophyRenderer
import com.shifurace.client.screen.ResultScreen
import com.shifurace.network.EditViewPayload
import com.shifurace.network.LapToastPayload
import com.shifurace.network.RaceHudPayload
import com.shifurace.network.RaceResultPayload
import com.shifurace.network.RaceTrackPayload
import com.shifurace.network.ResetRequestPayload
import com.shifurace.registry.ModBlockEntities
import com.shifurace.registry.ModBlocks
import com.shifurace.registry.ModComponents
import com.shifurace.registry.ModItems
import com.shifurace.trophy.TrophyBlockEntity
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import org.lwjgl.glfw.GLFW

object ShifuRaceClient : ClientModInitializer {
    /** G e não R: no launcher do servidor, R já é "soltar Pokémon" do Cobblemon e "ver receita" do JEI. */
    private val resetKey = KeyMapping("key.shifurace.voltar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.shifurace")

    /** Resultado que chegou com outra tela aberta. */
    private var pendingResult: RaceResultPayload? = null

    override fun onInitializeClient() {
        BlockEntityRenderers.register(ModBlockEntities.TROPHY, ::TrophyRenderer)
        KeyBindingHelper.registerKeyBinding(resetKey)

        // A taça do troféu sai na cor da pista
        ColorProviderRegistry.BLOCK.register({ _, level, pos, tint ->
            if (tint != 0 || level == null || pos == null) return@register -1
            val info = (level.getBlockEntity(pos) as? TrophyBlockEntity)?.info ?: return@register -1
            info.color or 0xFF000000.toInt()
        }, ModBlocks.TROPHY)
        ColorProviderRegistry.ITEM.register({ stack, tint ->
            if (tint != 0) return@register -1
            (stack.get(ModComponents.TROPHY_INFO)?.color ?: 0xF5C542) or 0xFF000000.toInt()
        }, ModItems.TROPHY)

        ClientPlayNetworking.registerGlobalReceiver(RaceHudPayload.TYPE) { payload, _ -> ClientRace.update(payload) }
        ClientPlayNetworking.registerGlobalReceiver(RaceTrackPayload.TYPE) { payload, _ -> ClientRace.setTrack(payload) }
        ClientPlayNetworking.registerGlobalReceiver(EditViewPayload.TYPE) { payload, _ -> ClientRace.setEdit(payload) }
        ClientPlayNetworking.registerGlobalReceiver(LapToastPayload.TYPE) { payload, _ -> ClientRace.showToast(payload) }
        ClientPlayNetworking.registerGlobalReceiver(RaceResultPayload.TYPE) { payload, _ -> pendingResult = payload }

        HudRenderCallback.EVENT.register { graphics, _ -> RaceHud.render(graphics) }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GateRenderer::render)

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            while (resetKey.consumeClick()) {
                if (ClientRace.hud.active && ClientRace.hud.state == RaceHudPayload.STATE_RUNNING) {
                    ClientPlayNetworking.send(ResetRequestPayload())
                }
            }
            showPendingResult(mc)
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ClientRace.clear()
            pendingResult = null
        }
    }

    private fun showPendingResult(mc: Minecraft) {
        val result = pendingResult ?: return
        if (mc.screen != null) return
        pendingResult = null
        mc.setScreen(ResultScreen(result))
    }
}
