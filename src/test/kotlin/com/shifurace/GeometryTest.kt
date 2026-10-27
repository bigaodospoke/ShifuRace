package com.shifurace

import com.shifurace.core.GateShape
import com.shifurace.core.Placement
import com.shifurace.core.Vec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryTest {
    // Portão de x=0 a x=10 em z=0 (blocos clicados em (0,64,0) e (10,64,0)), altura 6
    private val gate = GateShape.fromBlocks(0, 64, 0, 10, 64, 0, 6)

    @Test
    fun `corta o portao andando para frente e para tras`() {
        val (_, nz) = gate.normal()
        // a linha fica no centro dos blocos clicados (z = 0.5)
        val ida = gate.crossing(Vec(5.0, 65.0, -0.5), Vec(5.0, 65.0, 1.5))
        assertNotNull(ida)
        assertEquals(nz > 0, ida.forward)
        assertEquals(0.5, ida.t, 1e-9)

        val volta = gate.crossing(Vec(5.0, 65.0, 1.0), Vec(5.0, 65.0, -1.0))
        assertNotNull(volta)
        assertEquals(!ida.forward, volta.forward)
    }

    @Test
    fun `alta velocidade nao pula o portao`() {
        // 40 blocos num tick: muito mais que a espessura do portão
        val c = gate.crossing(Vec(3.0, 65.0, -19.5), Vec(3.0, 65.0, 20.5))
        assertNotNull(c)
        assertEquals(0.5, c.t, 1e-9)
    }

    @Test
    fun `fora da largura, acima ou abaixo nao conta`() {
        assertNull(gate.crossing(Vec(12.0, 65.0, -1.0), Vec(12.0, 65.0, 1.0)))
        assertNull(gate.crossing(Vec(5.0, 80.0, -1.0), Vec(5.0, 80.0, 1.0)))
        assertNull(gate.crossing(Vec(5.0, 60.0, -1.0), Vec(5.0, 60.0, 1.0)))
        // meio bloco de folga em cada ponta cobre o bloco clicado
        assertNotNull(gate.crossing(Vec(10.9, 65.0, -1.0), Vec(10.9, 65.0, 1.0)))
    }

    @Test
    fun `parado ou andando paralelo nao conta`() {
        assertNull(gate.crossing(Vec(5.0, 65.0, -1.0), Vec(5.0, 65.0, -1.0)))
        assertNull(gate.crossing(Vec(1.0, 65.0, 0.0), Vec(9.0, 65.0, 0.0)))
    }

    @Test
    fun `terminar em cima do portao conta so uma vez`() {
        val a = gate.crossing(Vec(5.0, 65.0, -1.0), Vec(5.0, 65.0, 0.5))
        val b = gate.crossing(Vec(5.0, 65.0, 0.5), Vec(5.0, 65.0, 1.5))
        assertNull(a)
        assertNotNull(b)
    }

    @Test
    fun `largada lado a lado atras da linha`() {
        val sinal = 1
        val posicoes = Placement.startPositions(gate, sinal, 3)
        assertEquals(3, posicoes.size)
        val (nx, nz) = gate.normal()
        posicoes.forEach { (x, z) ->
            assertTrue(gate.side(Vec(x, 65.0, z)) * sinal < 0, "tem que ficar atrás da linha")
            assertTrue(x in 0.0..10.0)
        }
        // centralizado: o do meio fica no meio da linha
        assertEquals(5.5, posicoes[1].first, 1e-9)
        assertEquals(-3.0 * nz, posicoes[1].second - 0.5, 1e-9)
        assertEquals(0.0, nx, 1e-9)
    }

    @Test
    fun `largada com mais pilotos que cabem abre outra fileira`() {
        // 10 blocos de largura, 2.5 de espaço: cabem 4 por fileira
        val posicoes = Placement.startPositions(gate, 1, 6)
        val primeira = posicoes.take(4).map { it.second }.toSet()
        val segunda = posicoes.drop(4).map { it.second }.toSet()
        assertEquals(1, primeira.size)
        assertEquals(1, segunda.size)
        assertTrue(abs(primeira.first() - segunda.first()) > 3.9)
    }

    @Test
    fun `ponto de volta fica do lado do proximo portao`() {
        val (_, z) = Placement.respawnPoint(gate, 5.0, 30.0)
        assertTrue(z > 0.5)
        val (_, z2) = Placement.respawnPoint(gate, 5.0, -30.0)
        assertTrue(z2 < 0.5)
    }

    @Test
    fun `yaw do minecraft`() {
        assertEquals(0f, Placement.yawToward(0.0, 1.0), 1e-4f)
        assertEquals(90f, Placement.yawToward(-1.0, 0.0), 1e-4f)
        assertFalse(Placement.yawToward(1.0, 0.0) > 0)
    }
}
