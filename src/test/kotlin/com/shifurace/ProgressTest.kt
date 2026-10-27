package com.shifurace

import com.shifurace.core.GateRef
import com.shifurace.core.ProgressEvent
import com.shifurace.core.RacerProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {
    private fun cp(i: Int) = GateRef.Checkpoint(i)

    /** Uma volta completa: checkpoints em [splits] ms depois de [start], linha em [start] + [lap]. */
    private fun RacerProgress.volta(start: Long, splits: List<Long>, lap: Long): ProgressEvent.LapCompleted {
        splits.forEachIndexed { i, s -> onCross(cp(i), true, start + s) }
        return assertIs(onCross(GateRef.Line, true, start + lap))
    }

    @Test
    fun `cruzar a linha logo depois da largada nao conta volta`() {
        val p = RacerProgress(checkpointCount = 2, laps = 2)
        assertNull(p.onCross(GateRef.Line, true, 500))
        assertEquals(0, p.lapsDone)
        assertEquals(0, p.gatesPassed)
    }

    @Test
    fun `checkpoints so valem na ordem`() {
        val p = RacerProgress(checkpointCount = 3, laps = 1)
        assertNull(p.onCross(cp(1), true, 1000))
        assertIs<ProgressEvent.CheckpointPassed>(p.onCross(cp(0), false, 2000))
        assertNull(p.onCross(cp(0), true, 2100))
        assertIs<ProgressEvent.CheckpointPassed>(p.onCross(cp(1), true, 3000))
        // linha antes do último checkpoint não fecha volta
        assertNull(p.onCross(GateRef.Line, true, 3500))
        assertIs<ProgressEvent.CheckpointPassed>(p.onCross(cp(2), true, 4000))
        // linha de ré não fecha volta
        assertNull(p.onCross(GateRef.Line, false, 4500))
        val fim = assertIs<ProgressEvent.LapCompleted>(p.onCross(GateRef.Line, true, 5000))
        assertTrue(fim.finished)
        assertEquals(5000, fim.totalMs)
        assertEquals(5000, p.finishedAtMs)
        // depois de terminar, nada mais conta
        assertNull(p.onCross(cp(0), true, 6000))
    }

    @Test
    fun `tempos de volta, melhor volta e delta`() {
        val p = RacerProgress(checkpointCount = 2, laps = 3)
        val v1 = p.volta(0, listOf(10_000, 20_000), 30_000)
        assertEquals(1, v1.lapNumber)
        assertEquals(30_000, v1.lapMs)
        assertTrue(v1.personalBest)

        // volta 2: mais rápida no 1º checkpoint (delta negativo), mais lenta no total
        val c0 = assertIs<ProgressEvent.CheckpointPassed>(p.onCross(cp(0), true, 30_000 + 9_500))
        assertEquals(-500, c0.deltaMs)
        val c1 = assertIs<ProgressEvent.CheckpointPassed>(p.onCross(cp(1), true, 30_000 + 21_000))
        assertEquals(1_000, c1.deltaMs)
        val v2 = assertIs<ProgressEvent.LapCompleted>(p.onCross(GateRef.Line, true, 30_000 + 32_000))
        assertEquals(32_000, v2.lapMs)
        assertEquals(false, v2.personalBest)
        assertEquals(30_000, p.bestLapMs)

        val v3 = p.volta(62_000, listOf(9_000, 18_000), 28_000)
        assertTrue(v3.personalBest)
        assertTrue(v3.finished)
        assertEquals(90_000, v3.totalMs)
        assertEquals(listOf(30_000L, 32_000L, 28_000L), p.lapTimes)
        assertEquals(listOf(9_000L, 18_000L), p.bestLapSplits)
    }

    @Test
    fun `ultimo portao para voltar`() {
        val p = RacerProgress(checkpointCount = 2, laps = 2)
        assertEquals(GateRef.Line, p.lastGate)
        assertEquals(cp(0), p.upcomingGate)
        p.onCross(cp(0), true, 1000)
        assertEquals(cp(0), p.lastGate)
        p.onCross(cp(1), true, 2000)
        assertEquals(cp(1), p.lastGate)
        assertEquals(GateRef.Line, p.upcomingGate)
        p.onCross(GateRef.Line, true, 3000)
        assertEquals(GateRef.Line, p.lastGate)
        assertEquals(2, p.currentLap)
    }
}
