package com.shifurace.client.hud

import com.shifurace.client.ClientRace
import com.shifurace.network.LapToastPayload
import com.shifurace.network.RaceHudPayload
import com.shifurace.network.StandingView
import com.shifurace.util.TimeFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.sin

/**
 * HUD estilo transmissão de F1: tempo da volta grande no topo com a diferença para a melhor volta,
 * torre de posições à esquerda e aviso de volta fechada no meio da tela.
 */
object RaceHud {
    private const val PANEL = 0x99000000.toInt()
    private const val SELF = 0x40FFFFFF
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val MUTED = 0xFFC8C8C8.toInt()
    private const val YELLOW = 0xFFFFD23F.toInt()
    private const val GREEN = 0xFF4CFF6A.toInt()
    private const val RED = 0xFFFF5A5A.toInt()
    private const val PURPLE = 0xFFC86BFF.toInt()
    private const val F1_RED = 0xFFE10600.toInt()
    private const val MAX_ROWS = 10

    fun render(g: GuiGraphics) {
        val hud = ClientRace.hud
        if (!hud.active) return
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        val font = mc.font
        renderTop(g, font, hud)
        renderTower(g, font, hud)
        renderToast(g, font)
        renderIdle(g, font, hud)
    }

    private fun renderTop(g: GuiGraphics, font: Font, hud: RaceHudPayload) {
        val cx = g.guiWidth() / 2
        val y = 4
        when (hud.state) {
            RaceHudPayload.STATE_OPEN -> {
                box(g, cx, y, 170, 30)
                centered(g, font, "AGUARDANDO LARGADA", cx, y + 5, YELLOW)
                centered(g, font, "${hud.trackName} · ${laps(hud.laps)} · ${hud.standings.size} piloto(s)", cx, y + 17, MUTED)
            }
            RaceHudPayload.STATE_COUNTDOWN -> {
                box(g, cx, y, 150, 30)
                centered(g, font, "PREPARAR", cx, y + 5, YELLOW)
                val secs = (hud.countdownTicks + 19) / 20
                centered(g, font, if (secs > 0) "Largada em $secs" else "VAI!", cx, y + 17, WHITE)
            }
            RaceHudPayload.STATE_FINISHED -> {
                box(g, cx, y, 170, 40)
                val pos = hud.standings.firstOrNull { it.self }?.position ?: 0
                centered(g, font, "CHEGADA · ${pos}º LUGAR", cx, y + 5, YELLOW)
                big(g, font, TimeFormat.race(hud.finishMs.coerceAtLeast(0)), cx, y + 16, WHITE)
            }
            else -> {
                val lapMs = (ClientRace.raceNow() - hud.lapStartMs).coerceAtLeast(0)
                val hasDelta = hud.deltaMs != RaceHudPayload.NONE
                box(g, cx, y, 160, if (hasDelta) 52 else 41)
                val cp = if (hud.nextGate >= hud.checkpointCount) "CHEGADA" else "CP ${hud.nextGate + 1}/${hud.checkpointCount}"
                centered(g, font, "VOLTA ${hud.currentLap}/${hud.laps} · $cp", cx, y + 5, MUTED)
                big(g, font, TimeFormat.race(lapMs), cx, y + 17, WHITE)
                if (hasDelta) {
                    val d = hud.deltaMs
                    val text = TimeFormat.delta(d)
                    val w = font.width(text) + font.width(" vs sua melhor")
                    val x = cx - w / 2
                    g.drawString(font, text, x, y + 40, if (d <= 0) GREEN else RED, true)
                    g.drawString(font, " vs sua melhor", x + font.width(text), y + 40, MUTED, true)
                }
                // Última volta e melhor volta embaixo, pequeno
                val last = hud.lastLapMs.takeIf { it != RaceHudPayload.NONE }
                val best = hud.bestLapMs.takeIf { it != RaceHudPayload.NONE }
                if (last != null || best != null) {
                    val yy = y + (if (hasDelta) 52 else 41) + 3
                    val parts = buildList {
                        if (last != null) add("ÚLTIMA " + TimeFormat.race(last) to MUTED)
                        if (best != null) add("MELHOR " + TimeFormat.race(best) to if (best == hud.fastestLapMs) PURPLE else GREEN)
                    }
                    val total = parts.sumOf { font.width(it.first) } + 12 * (parts.size - 1)
                    var x = cx - total / 2
                    parts.forEach { (text, color) ->
                        g.drawString(font, text, x, yy, color, true)
                        x += font.width(text) + 12
                    }
                }
            }
        }
    }

    private fun renderTower(g: GuiGraphics, font: Font, hud: RaceHudPayload) {
        if (hud.standings.isEmpty()) return
        val rows = hud.standings.take(MAX_ROWS).toMutableList()
        hud.standings.firstOrNull { it.self && it !in rows }?.let { rows[rows.size - 1] = it }
        val x = 4
        var y = 4
        val w = 132
        val h = 12
        g.fill(x, y, x + 3, y + rows.size * h + 2, F1_RED)
        g.fill(x + 3, y, x + w, y + rows.size * h + 2, PANEL)
        y += 1
        for (s in rows) {
            if (s.self) g.fill(x + 3, y, x + w, y + h, SELF)
            g.drawString(font, "${s.position}", x + 6, y + 2, YELLOW, true)
            val gap = gapText(s, hud)
            val gapW = font.width(gap)
            val nameMax = w - 22 - gapW - 6
            val name = if (font.width(s.name) > nameMax) font.plainSubstrByWidth(s.name, nameMax - font.width("…")) + "…" else s.name
            g.drawString(font, name, x + 20, y + 2, if (s.self) WHITE else 0xFFE8E8E8.toInt(), true)
            g.drawString(font, gap, x + w - gapW - 3, y + 2, if (s.finished) GREEN else MUTED, true)
            y += h
        }
    }

    private fun gapText(s: StandingView, hud: RaceHudPayload): String = when (s.gapKind) {
        StandingView.GAP_LEADER -> if (s.finished) "CHEGOU" else "LÍDER"
        StandingView.GAP_TIME -> "+" + TimeFormat.delta(s.gapValue).removePrefix("+")
        StandingView.GAP_LAPS -> if (s.gapValue == 1L) "+1 volta" else "+${s.gapValue} voltas"
        StandingView.GAP_OUT -> "SAIU"
        else -> if (hud.state == RaceHudPayload.STATE_RUNNING) "—" else ""
    }

    private fun renderToast(g: GuiGraphics, font: Font) {
        val toast = ClientRace.toast ?: return
        if (System.currentTimeMillis() > ClientRace.toastUntil) return
        val cx = g.guiWidth() / 2
        val y = (g.guiHeight() * 0.38).toInt()
        val (label, color) = when (toast.kind) {
            LapToastPayload.FASTEST -> "MELHOR VOLTA DA CORRIDA" to PURPLE
            LapToastPayload.PERSONAL_BEST -> "SUA MELHOR VOLTA" to GREEN
            else -> "" to WHITE
        }
        val main = "VOLTA ${toast.lap}  ${TimeFormat.race(toast.lapMs)}"
        val text = if (label.isEmpty()) main else "$main  ·  $label"
        val w = font.width(text) + 20
        g.fill(cx - w / 2, y, cx + w / 2, y + 16, PANEL)
        g.fill(cx - w / 2, y, cx + w / 2, y + 2, color)
        centered(g, font, text, cx, y + 5, if (label.isEmpty()) WHITE else color)
    }

    private fun renderIdle(g: GuiGraphics, font: Font, hud: RaceHudPayload) {
        if (hud.idleKickIn <= 0) return
        val blink = sin(System.currentTimeMillis() / 120.0) > -0.3
        if (!blink) return
        val cx = g.guiWidth() / 2
        val y = (g.guiHeight() * 0.62).toInt()
        val text = "MEXA-SE! Expulsão em ${hud.idleKickIn}s"
        val w = font.width(text) * 2 + 16
        g.fill(cx - w / 2, y - 4, cx + w / 2, y + 20, PANEL)
        big(g, font, text, cx, y, RED)
    }

    private fun box(g: GuiGraphics, cx: Int, y: Int, w: Int, h: Int) {
        g.fill(cx - w / 2, y, cx + w / 2, y + h, PANEL)
        g.fill(cx - w / 2, y + h - 1, cx + w / 2, y + h, F1_RED)
    }

    private fun centered(g: GuiGraphics, font: Font, text: String, cx: Int, y: Int, color: Int) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, true)
    }

    /** Texto com o dobro do tamanho, centralizado. */
    private fun big(g: GuiGraphics, font: Font, text: String, cx: Int, y: Int, color: Int) {
        val pose = g.pose()
        pose.pushPose()
        pose.translate(cx.toFloat(), y.toFloat(), 0f)
        pose.scale(2f, 2f, 1f)
        g.drawString(font, text, -font.width(text) / 2, 0, color, true)
        pose.popPose()
    }

    private fun laps(n: Int) = if (n == 1) "1 volta" else "$n voltas"
}
