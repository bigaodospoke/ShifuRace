package com.shifurace.core

/** Um tempo no ranking: o melhor tempo total de um jogador numa corrida de N voltas. */
data class RecordEntry(
    val uuid: String,
    var name: String,
    var timeMs: Long,
    var date: String,
    var epochMs: Long,
)

data class SubmitResult(
    /** Posição do jogador no ranking depois de enviar (1 = recorde), ou null se o tempo não melhorou o dele. */
    val rank: Int?,
    /** O tempo enviado virou o melhor do jogador nesse ranking. */
    val improved: Boolean,
    /** O tempo enviado é o novo 1º lugar do ranking. */
    val newRecord: Boolean,
)

/** Rankings de uma pista, um por número de voltas. Guarda o melhor tempo de cada jogador. */
class TrackRecords(val byLaps: MutableMap<Int, MutableList<RecordEntry>> = sortedMapOf()) {

    fun submit(laps: Int, uuid: String, name: String, timeMs: Long, date: String, epochMs: Long): SubmitResult {
        val list = byLaps.getOrPut(laps) { mutableListOf() }
        val existing = list.firstOrNull { it.uuid == uuid }
        val improved = when {
            existing == null -> {
                list.add(RecordEntry(uuid, name, timeMs, date, epochMs))
                true
            }
            timeMs < existing.timeMs -> {
                existing.name = name
                existing.timeMs = timeMs
                existing.date = date
                existing.epochMs = epochMs
                true
            }
            else -> {
                existing.name = name
                false
            }
        }
        if (!improved) return SubmitResult(null, false, false)
        val ordered = sorted(list)
        val rank = ordered.indexOfFirst { it.uuid == uuid } + 1
        // Só chega em 1º melhorando o próprio tempo se passou todo mundo (empate fica com quem fez antes)
        return SubmitResult(rank, true, rank == 1)
    }

    /** Top [n] do ranking de [laps] voltas. Empate: quem fez primeiro fica na frente. */
    fun top(laps: Int, n: Int): List<RecordEntry> = sorted(byLaps[laps].orEmpty()).take(n)

    /** Números de voltas que têm pelo menos um tempo, em ordem crescente. */
    fun lapCounts(): List<Int> = byLaps.filterValues { it.isNotEmpty() }.keys.sorted()

    fun clear(laps: Int?) {
        if (laps == null) byLaps.clear() else byLaps.remove(laps)
    }

    /** Remove um jogador pelo nome (sem diferenciar maiúsculas). Devolve quantos tempos saíram. */
    fun remove(laps: Int?, name: String): Int {
        var removed = 0
        for ((l, list) in byLaps) {
            if (laps != null && l != laps) continue
            val before = list.size
            list.removeAll { it.name.equals(name, ignoreCase = true) }
            removed += before - list.size
        }
        return removed
    }

    private fun sorted(list: List<RecordEntry>) = list.sortedWith(compareBy<RecordEntry> { it.timeMs }.thenBy { it.epochMs })
}
