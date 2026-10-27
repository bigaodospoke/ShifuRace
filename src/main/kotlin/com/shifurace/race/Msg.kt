package com.shifurace.race

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Textos e títulos das mensagens da corrida. */
object Msg {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yy")

    fun prefix(): MutableComponent = Component.literal("[Corrida] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)

    fun info(text: Component): Component = prefix().append(text.copy().withStyle(ChatFormatting.YELLOW))
    fun info(text: String): Component = info(Component.literal(text))
    fun error(text: String): Component = prefix().append(Component.literal(text).withStyle(ChatFormatting.RED))

    fun send(player: ServerPlayer, text: String) = player.sendSystemMessage(info(text))

    fun broadcast(server: MinecraftServer, text: Component) = server.playerList.broadcastSystemMessage(text, false)

    fun command(label: String, command: String, hover: String, color: ChatFormatting = ChatFormatting.GREEN): MutableComponent =
        Component.literal(label).withStyle {
            it.withColor(color).withBold(true)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)))
        }

    fun title(player: ServerPlayer, title: Component, subtitle: Component? = null, fadeIn: Int = 0, stay: Int = 25, fadeOut: Int = 8) {
        player.connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
        player.connection.send(ClientboundSetSubtitleTextPacket(subtitle ?: Component.empty()))
        player.connection.send(ClientboundSetTitleTextPacket(title))
    }

    fun today(): String = LocalDate.now().format(DATE)

    fun laps(n: Int): String = if (n == 1) "1 volta" else "$n voltas"
}
