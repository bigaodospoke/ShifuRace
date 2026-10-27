package com.shifurace.core

/** Qual portão foi cruzado: a linha de largada/chegada ou o checkpoint de índice [index] (0 = primeiro). */
sealed interface GateRef {
    data object Line : GateRef
    data class Checkpoint(val index: Int) : GateRef
}

sealed interface ProgressEvent {
    /** Passou o checkpoint [index]; [splitMs] é o tempo desde o início da volta; [deltaMs] compara com a melhor volta. */
    data class CheckpointPassed(val index: Int, val splitMs: Long, val deltaMs: Long?) : ProgressEvent

    /** Fechou a volta [lapNumber] (1 = primeira). */
    data class LapCompleted(
        val lapNumber: Int,
        val lapMs: Long,
        val personalBest: Boolean,
        val finished: Boolean,
        val totalMs: Long,
    ) : ProgressEvent
}

/**
 * Progresso de um piloto: checkpoints na ordem, voltas, tempos parciais e melhor volta.
 * Tempos em ms contados a partir do "VAI".
 */
class RacerProgress(val checkpointCount: Int, val laps: Int) {
    init {
        require(checkpointCount >= 1) { "a pista precisa de pelo menos 1 checkpoint" }
        require(laps >= 1) { "a corrida precisa de pelo menos 1 volta" }
    }

    /** Voltas completas. */
    var lapsDone = 0
        private set

    /** Próximo portão esperado na volta: 0 até checkpointCount-1 são checkpoints; checkpointCount é a linha. */
    var nextGate = 0
        private set

    var lapStartMs = 0L
        private set

    val lapTimes = mutableListOf<Long>()
    private val currentSplits = mutableListOf<Long>()
    var bestLapSplits: List<Long>? = null
        private set
    var bestLapMs: Long? = null
        private set

    /** Tempo de corrida em cada portão passado, em ordem (usado para diferença até o líder). */
    val gateTimes = mutableListOf<Long>()

    var finishedAtMs: Long? = null
        private set

    /** Último delta calculado (para a HUD). */
    var lastDeltaMs: Long? = null
        private set

    val finished: Boolean get() = finishedAtMs != null
    val gatesPassed: Int get() = gateTimes.size
    val gatesPerLap: Int get() = checkpointCount + 1

    /** Volta que está sendo corrida agora (1 = primeira); depois de terminar, fica na última. */
    val currentLap: Int get() = (lapsDone + 1).coerceAtMost(laps)

    /** Último portão passado (para onde o piloto volta): a linha no começo de cada volta. */
    val lastGate: GateRef get() = if (nextGate == 0) GateRef.Line else GateRef.Checkpoint(nextGate - 1)

    /** Próximo portão a passar. */
    val upcomingGate: GateRef get() = if (nextGate >= checkpointCount) GateRef.Line else GateRef.Checkpoint(nextGate)

    fun onCross(gate: GateRef, forward: Boolean, ms: Long): ProgressEvent? {
        if (finished) return null
        return when (gate) {
            is GateRef.Checkpoint -> {
                if (gate.index != nextGate || nextGate >= checkpointCount) return null
                val split = ms - lapStartMs
                val best = bestLapSplits
                val delta = if (best != null && gate.index < best.size) split - best[gate.index] else null
                currentSplits.add(split)
                gateTimes.add(ms)
                nextGate++
                lastDeltaMs = delta
                ProgressEvent.CheckpointPassed(gate.index, split, delta)
            }
            GateRef.Line -> {
                if (!forward || nextGate != checkpointCount) return null
                val lapMs = ms - lapStartMs
                lapTimes.add(lapMs)
                gateTimes.add(ms)
                val previousBest = bestLapMs
                val personalBest = previousBest == null || lapMs < previousBest
                if (personalBest) {
                    bestLapMs = lapMs
                    bestLapSplits = currentSplits.toList()
                }
                currentSplits.clear()
                lapsDone++
                nextGate = 0
                lapStartMs = ms
                lastDeltaMs = null
                val done = lapsDone >= laps
                if (done) finishedAtMs = ms
                ProgressEvent.LapCompleted(lapsDone, lapMs, personalBest, done, ms)
            }
        }
    }
}
