package com.shifurace.trophy

import com.shifurace.race.Msg
import com.shifurace.registry.ModComponents
import com.shifurace.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.block.Block

class TrophyItem(block: Block, properties: Properties) : BlockItem(block, properties) {

    override fun getName(stack: ItemStack): Component {
        val info = stack.get(ModComponents.TROPHY_INFO)
        val base = Component.translatable(getDescriptionId(stack))
        if (info == null) return base.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        return base.append(" · ${info.track}").withStyle { it.withColor(TextColor.fromRgb(info.color)).withBold(true) }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) {
        val info = stack.get(ModComponents.TROPHY_INFO)
        tooltip.add(Component.translatable("item.shifurace.trofeu.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
        if (info == null) return
        tooltip.add(Component.empty())
        fun line(key: String, value: String, color: ChatFormatting) =
            tooltip.add(Component.translatable(key, Component.literal(value).withStyle(color)).withStyle(ChatFormatting.GRAY))
        line("item.shifurace.trofeu.dono", info.owner, ChatFormatting.YELLOW)
        line("item.shifurace.trofeu.pista", info.track, ChatFormatting.AQUA)
        line("item.shifurace.trofeu.tempo", "${TimeFormat.race(info.timeMs)} (${Msg.laps(info.laps)})", ChatFormatting.WHITE)
        if (info.racers > 0) line("item.shifurace.trofeu.pilotos", info.racers.toString(), ChatFormatting.WHITE)
        line("item.shifurace.trofeu.data", info.date, ChatFormatting.WHITE)
    }
}
