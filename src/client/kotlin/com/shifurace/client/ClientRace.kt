package com.shifurace.client

import com.shifurace.network.EditViewPayload
import com.shifurace.network.GateView
import com.shifurace.network.LapToastPayload
import com.shifurace.network.RaceHudPayload
import com.shifurace.network.RaceTrackPayload
import kotlin.math.abs

/** O que o cliente sabe da corrida em que o jogador está. */
object ClientRace {
    var hud: RaceHudPayload = RaceHudPayload.INACTIVE
        private set
    var gates: List<GateView> = emptyList()
        private set
    var edit: EditViewPayload = EditViewPayload.OFF
        private set

    var toast: LapToastPayload? = null
        private set
    var toastUntil = 0L
        private set

    // Relógio local acertado pelo servidor, para o cronômetro andar liso entre um pacote e outro
    private var baseRaceMs = 0L
    private var baseLocal = 0L

    val active: Boolean get() = hud.active

    /** Controles travados durante a contagem 3, 2, 1. */
    val frozen: Boolean get() = hud.active && hud.state == RaceHudPayload.STATE_COUNTDOWN

    fun update(payload: RaceHudPayload) {
        val now = System.currentTimeMillis()
        val predicted = baseRaceMs + (now - baseLocal)
        if (!hud.active || payload.state != RaceHudPayload.STATE_RUNNING || hud.state != RaceHudPayload.STATE_RUNNING ||
            abs(payload.raceMs - predicted) > 250
        ) {
            baseRaceMs = payload.raceMs
            baseLocal = now
        }
        hud = payload
    }

    fun setTrack(payload: RaceTrackPayload) {
        gates = payload.gates
    }

    fun setEdit(payload: EditViewPayload) {
        edit = payload
    }

    fun showToast(payload: LapToastPayload) {
        toast = payload
        toastUntil = System.currentTimeMillis() + 3500
    }

    /** Tempo de corrida agora (ms desde o VAI). */
    fun raceNow(): Long = if (hud.state == RaceHudPayload.STATE_RUNNING) baseRaceMs + (System.currentTimeMillis() - baseLocal) else hud.raceMs

    fun gate(index: Int): GateView? = gates.firstOrNull { it.index == index }

    fun clear() {
        hud = RaceHudPayload.INACTIVE
        gates = emptyList()
        edit = EditViewPayload.OFF
        toast = null
    }
}
