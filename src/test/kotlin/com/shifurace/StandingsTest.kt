package com.shifurace

import com.shifurace.core.Gap
import com.shifurace.core.GateRef
import com.shifurace.core.RacerProgress
import com.shifurace.core.StandingInput
import com.shifurace.core.Standings
import kotlin.test.Test
import kotlin.test.assertEquals

class StandingsTest {
    private fun progresso(vararg tempos: Long, cps: Int = 2, laps: Int = 3): RacerProgress {
        val p = RacerProgress(cps, laps)
        tempos.forEachIndexed { i, t ->
            val idx = i % (cps + 1)
            p.onCross(if (idx == cps) GateRef.Line else GateRef.Checkpoint(idx), true, t)
        }
        return p
    }

    private fun entrada(id: String, p: RacerProgress, dist: Double = 0.0, chegada: Int? = null, fora: Boolean = false) =
        StandingInput(id, p, chegada, dist, fora)

    @Test
    fun `ordem por portoes passados e distancia`() {
        val a = entrada("a", progresso(1000, 2000), dist = 30.0)
        val b = entrada("b", progresso(1100, 2100), dist = 10.0)
        val c = entrada("c", progresso(1200, 2200, 3200))
        val ordem = Standings.order(listOf(a, b, c)).map { it.id }
        assertEquals(listOf("c", "b", "a"), ordem)
    }

    @Test
    fun `quem terminou vem primeiro, quem saiu por ultimo`() {
        val terminou = entrada("t", progresso(1, 2, 3, cps = 2, laps = 1), chegada = 1)
        val correndo = entrada("r", progresso(5, 6))
        val saiu = entrada("s", progresso(9, 10, 11, 12), fora = true)
        val resultado = Standings.compute(listOf(saiu, correndo, terminou))
        assertEquals(listOf("t", "r", "s"), resultado.map { it.id })
        assertEquals(Gap.Leader, resultado[0].gap)
        assertEquals(Gap.Out, resultado[2].gap)
    }

    @Test
    fun `diferenca para o lider no mesmo portao`() {
        val lider = entrada("l", progresso(10_000, 20_000, 30_000, 40_000))
        val segundo = entrada("s", progresso(11_800, 22_500, 31_842))
        val resultado = Standings.compute(listOf(segundo, lider))
        assertEquals(Gap.Time(1_842), resultado[1].gap)
    }

    @Test
    fun `volta de atraso`() {
        // 2 checkpoints + linha = 3 portões por volta
        val lider = entrada("l", progresso(1, 2, 3, 4, 5))
        val atrasado = entrada("a", progresso(10, 11))
        val nada = entrada("n", progresso())
        val resultado = Standings.compute(listOf(lider, atrasado, nada))
        assertEquals(Gap.Laps(1), resultado[1].gap)
        assertEquals(Gap.Unknown, resultado[2].gap)
    }
}
