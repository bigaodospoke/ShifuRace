package com.shifurace

import com.shifurace.core.IdleTracker
import com.shifurace.core.TrackRecords
import com.shifurace.core.Vec
import com.shifurace.util.TimeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsTest {
    @Test
    fun `uma linha por jogador e rankings separados por voltas`() {
        val r = TrackRecords()
        r.submit(3, "u1", "Shifu", 90_000, "01/11/26", 1)
        r.submit(3, "u1", "Shifu", 85_000, "02/11/26", 2)
        r.submit(3, "u2", "Ana", 88_000, "02/11/26", 3)
        r.submit(1, "u2", "Ana", 30_000, "02/11/26", 4)

        val top3 = r.top(3, 5)
        assertEquals(listOf("Shifu", "Ana"), top3.map { it.name })
        assertEquals(85_000, top3[0].timeMs)
        assertEquals(listOf(1, 3), r.lapCounts())
        assertEquals(1, r.top(1, 5).size)
    }

    @Test
    fun `tempo pior nao substitui e nao entra`() {
        val r = TrackRecords()
        r.submit(2, "u1", "Shifu", 60_000, "d", 1)
        val res = r.submit(2, "u1", "Shifu", 61_000, "d", 2)
        assertFalse(res.improved)
        assertNull(res.rank)
        assertEquals(60_000, r.top(2, 5)[0].timeMs)
    }

    @Test
    fun `recorde novo e empate fica com quem fez antes`() {
        val r = TrackRecords()
        assertTrue(r.submit(2, "u1", "A", 60_000, "d", 1).newRecord)
        val empate = r.submit(2, "u2", "B", 60_000, "d", 2)
        assertEquals(2, empate.rank)
        assertFalse(empate.newRecord)
        val melhorou = r.submit(2, "u1", "A", 59_000, "d", 3)
        assertTrue(melhorou.newRecord)
        assertTrue(r.submit(2, "u3", "C", 58_000, "d", 4).newRecord)
    }

    @Test
    fun `limpar e remover`() {
        val r = TrackRecords()
        r.submit(1, "u1", "Shifu", 1, "d", 1)
        r.submit(2, "u1", "Shifu", 2, "d", 1)
        r.submit(2, "u2", "Ana", 3, "d", 1)
        assertEquals(2, r.remove(null, "shifu"))
        assertEquals(listOf(2), r.lapCounts())
        r.clear(2)
        assertTrue(r.lapCounts().isEmpty())
    }

    @Test
    fun `parado conta ticks e reinicia ao andar`() {
        val idle = IdleTracker(2.0)
        assertEquals(0, idle.update(Vec(0.0, 0.0, 0.0), 100))
        assertEquals(400, idle.update(Vec(1.5, 0.0, 0.0), 500))
        assertEquals(0, idle.update(Vec(3.0, 0.0, 0.0), 600))
        assertEquals(10, idle.update(Vec(3.0, 0.0, 1.0), 610))
        idle.reset(Vec(50.0, 0.0, 0.0), 700)
        assertEquals(5, idle.update(Vec(50.0, 0.0, 0.0), 705))
    }

    @Test
    fun `formato dos tempos`() {
        assertEquals("1:14.221", TimeFormat.race(74_221))
        assertEquals("0:05.007", TimeFormat.race(5_007))
        assertEquals("1:02:34.221", TimeFormat.race(3_754_221))
        assertEquals("-0.412", TimeFormat.delta(-412))
        assertEquals("+1.842", TimeFormat.delta(1_842))
        assertEquals("+1:05.000", TimeFormat.delta(65_000))
    }
}
