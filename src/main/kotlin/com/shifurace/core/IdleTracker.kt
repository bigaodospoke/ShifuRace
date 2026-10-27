package com.shifurace.core

/**
 * Conta há quanto tempo o piloto está parado: "parado" é não se afastar mais de [radius] blocos
 * do ponto onde ele estava quando começou a contar.
 */
class IdleTracker(private val radius: Double) {
    private var anchor: Vec? = null
    private var anchorTick = 0L

    /** Atualiza com a posição do tick atual e devolve há quantos ticks o piloto está parado. */
    fun update(pos: Vec, tick: Long): Long {
        val a = anchor
        if (a == null || a.distance(pos) > radius) {
            anchor = pos
            anchorTick = tick
        }
        return tick - anchorTick
    }

    /** Recomeça a contagem (largada, volta ao checkpoint etc.). */
    fun reset(pos: Vec, tick: Long) {
        anchor = pos
        anchorTick = tick
    }
}
