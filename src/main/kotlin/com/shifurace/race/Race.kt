package com.shifurace.race

import com.shifurace.config.ConfigManager
import com.shifurace.core.IdleTracker
import com.shifurace.core.RacerProgress
import com.shifurace.core.Vec
import com.shifurace.track.TrackData
import java.util.UUID

enum class RaceState { OPEN, COUNTDOWN, RUNNING }

class Racer(val uuid: UUID, var name: String) {
    var rentalUuid: UUID? = null

    /** Criado no VAI. */
    var progress: RacerProgress? = null

    /** Ordem de chegada (1 = vencedor). */
    var finishOrder: Int? = null

    /** Saiu, foi expulso ou desconectou antes de terminar. */
    var out = false

    var prevPos: Vec? = null
    val idle = IdleTracker(ConfigManager.config.raioParado)
    var idleTicks = 0L

    /** Tick em que o piloto deve ser montado de novo depois de um teleporte (-1 = nenhum). */
    var remountAt = -1L

    /** Até esse tick, sair de cima do Pokémon não conta como "desmontou" (é o próprio mod teleportando). */
    var ignoreDismountUntil = -1L

    /** Desmontou durante a corrida: o Porytronz espera parado no checkpoint até o piloto montar de novo. */
    var waitingRemount = false
    var hold: Vec? = null
    var holdYaw = 0f

    /** Tocou lava/fogo/vazio neste tick (vem do evento de dano). */
    var pendingHazard = false
    var lastResetTick = -100_000L

    val finished: Boolean get() = finishOrder != null
    val active: Boolean get() = !out && !finished
}

class Race(val track: TrackData, val laps: Int) {
    var state = RaceState.OPEN
    val racers = LinkedHashMap<UUID, Racer>()

    /** Tick do VAI (o cronômetro conta a partir dele). */
    var goTick = 0L
    var startersCount = 0
    var finishCount = 0
    var fastestLapMs: Long? = null
    var fastestLapHolder: UUID? = null

    fun raceMs(tick: Long): Long = if (state == RaceState.RUNNING) (tick - goTick).coerceAtLeast(0) * 50 else 0

    fun allDone(): Boolean = racers.values.none { it.active }
}
