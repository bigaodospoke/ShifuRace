package com.shifurace.client.screen

import com.shifurace.network.RaceResultPayload
import com.shifurace.util.TimeFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Resultado final da corrida: posição, tempo total e melhor volta de cada piloto. */
class ResultScreen(private val result: RaceResultPayload) : Screen(Component.literal("Resultado da corrida")) {
    private val panelW = 300
    private val rowH = 14
    private val panelH get() = 64 + result.rows.size.coerceAtMost(12) * rowH + (if (result.note.isNotEmpty()) 12 else 0) + 28

    override fun init() {
        val left = (width - panelW) / 2
        val top = (height - panelH) / 2
        addRenderableWidget(
            Button.builder(Component.literal("Fechar")) { onClose() }
                .bounds(left + panelW / 2 - 40, top + panelH - 24, 80, 18)
                .build(),
        )
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(g, mouseX, mouseY, partialTick)
        val left = (width - panelW) / 2
        val top = (height - panelH) / 2
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, 0xFFE10600.toInt())
        g.fill(left, top, left + panelW, top + panelH, 0xF0101014.toInt())

        val title = "RESULTADO · ${result.trackName.uppercase()}"
        g.drawString(font, title, left + (panelW - font.width(title)) / 2, top + 8, 0xFFFFD23F.toInt(), true)
        val sub = if (result.laps == 1) "1 volta" else "${result.laps} voltas"
        g.drawString(font, sub, left + (panelW - font.width(sub)) / 2, top + 20, 0xFFC8C8C8.toInt(), true)

        var y = top + 40
        g.drawString(font, "POS", left + 10, y, 0xFF8A8A8A.toInt(), false)
        g.drawString(font, "PILOTO", left + 40, y, 0xFF8A8A8A.toInt(), false)
        g.drawString(font, "TEMPO", left + 165, y, 0xFF8A8A8A.toInt(), false)
        g.drawString(font, "MELHOR VOLTA", left + 225, y, 0xFF8A8A8A.toInt(), false)
        y += 12
        for (row in result.rows.take(12)) {
            if (row.self) g.fill(left + 4, y - 2, left + panelW - 4, y + rowH - 3, 0x30FFFFFF)
            val posColor = when (row.position) {
                1 -> 0xFFFFD23F.toInt()
                2 -> 0xFFE0E0E0.toInt()
                3 -> 0xFFE0904A.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            g.drawString(font, "${row.position}º", left + 10, y, posColor, true)
            g.drawString(font, font.plainSubstrByWidth(row.name, 120), left + 40, y, 0xFFFFFFFF.toInt(), true)
            val total = if (row.totalMs >= 0) TimeFormat.race(row.totalMs) else "não terminou"
            g.drawString(font, total, left + 165, y, if (row.totalMs >= 0) 0xFFFFFFFF.toInt() else 0xFFFF5A5A.toInt(), true)
            val best = if (row.bestLapMs >= 0) TimeFormat.race(row.bestLapMs) else "—"
            g.drawString(font, best, left + 225, y, if (row.fastest) 0xFFC86BFF.toInt() else 0xFFC8C8C8.toInt(), true)
            y += rowH
        }
        if (result.note.isNotEmpty()) {
            g.drawString(font, result.note, left + (panelW - font.width(result.note)) / 2, y + 2, 0xFFFF5A5A.toInt(), true)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
