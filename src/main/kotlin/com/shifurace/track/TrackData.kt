package com.shifurace.track

import com.shifurace.core.GateRef
import com.shifurace.core.GateShape

/** Portão salvo: os dois blocos clicados e a altura. */
class GateData(
    var x1: Int = 0,
    var y1: Int = 0,
    var z1: Int = 0,
    var x2: Int = 0,
    var y2: Int = 0,
    var z2: Int = 0,
    var altura: Int = 6,
) {
    fun shape(): GateShape = GateShape.fromBlocks(x1, y1, z1, x2, y2, z2, altura)
}

/** Onde fica o placar da pista e qual entidade é ele. */
class BoardData(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var yaw: Float = 0f,
    var uuid: String? = null,
)

class TrackData(
    var nome: String = "",
    var dimensao: String = "minecraft:overworld",
    var linha: GateData? = null,
    /** +1: "para frente" é o lado da normal à esquerda do portão da linha; -1: o lado oposto. */
    var sentidoLinha: Int = 1,
    var checkpoints: MutableList<GateData> = mutableListOf(),
    var voltasPadrao: Int = 3,
    /** Cor do troféu desta pista (RGB). */
    var corTrofeu: Int = 0xF5C542,
    var placar: BoardData? = null,
) {
    val key: String get() = nome.lowercase()

    fun gate(ref: GateRef): GateData? = when (ref) {
        GateRef.Line -> linha
        is GateRef.Checkpoint -> checkpoints.getOrNull(ref.index)
    }

    /** Portão que vem depois de [ref] na volta (o seguinte do último checkpoint é a linha). */
    fun after(ref: GateRef): GateRef = when (ref) {
        GateRef.Line -> if (checkpoints.isEmpty()) GateRef.Line else GateRef.Checkpoint(0)
        is GateRef.Checkpoint -> if (ref.index + 1 >= checkpoints.size) GateRef.Line else GateRef.Checkpoint(ref.index + 1)
    }

    /** Y mínimo dos portões, para saber quando o piloto caiu no vazio. */
    fun lowestY(): Int = (listOfNotNull(linha) + checkpoints).minOfOrNull { minOf(it.y1, it.y2) } ?: 0

    /** O que falta para a pista poder receber corridas, ou null se está pronta. */
    fun missing(): String? = when {
        linha == null -> "a linha de largada/chegada"
        checkpoints.isEmpty() -> "pelo menos 1 checkpoint"
        else -> null
    }
}
