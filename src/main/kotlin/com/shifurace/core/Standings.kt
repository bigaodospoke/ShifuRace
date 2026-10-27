package com.shifurace.core

/** Dados de um piloto para calcular a classificação. */
data class StandingInput(
    val id: String,
    val progress: RacerProgress,
    /** Ordem de chegada (1 = vencedor), ou null se ainda não terminou. */
    val finishOrder: Int?,
    /** Distância horizontal até o próximo portão (desempate entre quem passou o mesmo número de portões). */
    val distanceToNext: Double,
    /** Saiu, foi expulso ou a corrida foi encerrada antes de ele terminar. */
    val out: Boolean,
)

/** Diferença para o líder: tempo, voltas atrás ou nenhuma informação ainda. */
sealed interface Gap {
    data object Leader : Gap
    data class Time(val ms: Long) : Gap
    data class Laps(val laps: Int) : Gap
    data object Unknown : Gap
    data object Out : Gap
}

data class Standing(val id: String, val position: Int, val gap: Gap)

object Standings {
    /**
     * Ordem da corrida: quem terminou (pela ordem de chegada), depois quem ainda corre (mais portões passados,
     * depois menor distância até o próximo), e por fim quem saiu.
     */
    fun order(inputs: List<StandingInput>): List<StandingInput> {
        val finished = inputs.filter { it.finishOrder != null }.sortedBy { it.finishOrder }
        val running = inputs.filter { it.finishOrder == null && !it.out }
            .sortedWith(compareByDescending<StandingInput> { it.progress.gatesPassed }.thenBy { it.distanceToNext })
        val out = inputs.filter { it.finishOrder == null && it.out }
            .sortedWith(compareByDescending<StandingInput> { it.progress.gatesPassed }.thenBy { it.distanceToNext })
        return finished + running + out
    }

    /** Classificação com a diferença de cada um para o líder, medida no último portão que o piloto passou. */
    fun compute(inputs: List<StandingInput>): List<Standing> {
        val ordered = order(inputs)
        val leader = ordered.firstOrNull() ?: return emptyList()
        return ordered.mapIndexed { i, s ->
            val gap = when {
                i == 0 -> Gap.Leader
                s.out && s.finishOrder == null -> Gap.Out
                else -> gapTo(leader.progress, s.progress)
            }
            Standing(s.id, i + 1, gap)
        }
    }

    fun gapTo(leader: RacerProgress, racer: RacerProgress): Gap {
        val passed = racer.gatesPassed
        if (passed == 0) return Gap.Unknown
        val behind = leader.gatesPassed - passed
        val lapsBehind = behind / racer.gatesPerLap
        if (lapsBehind >= 1) return Gap.Laps(lapsBehind)
        val leaderTime = leader.gateTimes.getOrNull(passed - 1) ?: return Gap.Unknown
        return Gap.Time((racer.gateTimes[passed - 1] - leaderTime).coerceAtLeast(0))
    }
}
