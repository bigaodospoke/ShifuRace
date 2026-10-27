package com.shifurace.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.shifurace.trophy.TrophyBlockEntity
import com.shifurace.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor

/** Mostra o vencedor, a pista e o tempo flutuando sobre o troféu quando alguém chega perto. */
class TrophyRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<TrophyBlockEntity> {
    private val font: Font = context.font

    override fun render(
        blockEntity: TrophyBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val info = blockEntity.info ?: return
        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        if (camera.position.distanceToSqr(blockEntity.blockPos.center) > 6.0 * 6.0) return

        val lines = listOf(
            Component.literal("✦ ${info.owner} ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.literal(info.track).withStyle { it.withColor(TextColor.fromRgb(info.color)) },
            Component.literal("⌚ ${TimeFormat.race(info.timeMs)}").withStyle(ChatFormatting.AQUA),
        )
        poseStack.pushPose()
        poseStack.translate(0.5, 1.35, 0.5)
        poseStack.mulPose(mc.entityRenderDispatcher.cameraOrientation())
        poseStack.scale(0.02f, -0.02f, 0.02f)
        val matrix = poseStack.last().pose()
        val background = (mc.options.getBackgroundOpacity(0.35f) * 255).toInt() shl 24
        lines.forEachIndexed { i, line ->
            val x = -font.width(line) / 2f
            font.drawInBatch(line, x, i * 11f, -1, false, matrix, buffer, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT)
        }
        poseStack.popPose()
    }
}
