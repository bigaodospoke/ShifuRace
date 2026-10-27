package com.shifurace.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Ponto/vetor simples, sem depender das classes do Minecraft (para a lógica poder ser testada). */
data class Vec(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec(x * k, y * k, z * k)
    fun horizontalDistance(o: Vec): Double = sqrt((x - o.x) * (x - o.x) + (z - o.z) * (z - o.z))
    fun distance(o: Vec): Double = sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z))
}

/** Cruzamento de um portão: [t] é a fração do movimento do tick em que o portão foi cortado. */
data class Crossing(val t: Double, val forward: Boolean)

/**
 * Portão da pista: um retângulo vertical entre dois pontos no plano XZ, de [minY] até [maxY].
 * "Para frente" é o lado da normal à esquerda de A→B (multiplicado pelo sinal guardado na pista).
 */
data class GateShape(
    val ax: Double,
    val az: Double,
    val bx: Double,
    val bz: Double,
    val minY: Double,
    val maxY: Double,
) {
    val width: Double get() = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
    val center: Vec get() = Vec((ax + bx) / 2, minY, (az + bz) / 2)

    /** Normal unitária "à esquerda" de A→B, no plano XZ. */
    fun normal(): Pair<Double, Double> {
        val w = width
        if (w < 1e-9) return 0.0 to 1.0
        return -(bz - az) / w to (bx - ax) / w
    }

    /** Direção unitária de A→B, no plano XZ. */
    fun along(): Pair<Double, Double> {
        val w = width
        if (w < 1e-9) return 1.0 to 0.0
        return (bx - ax) / w to (bz - az) / w
    }

    /**
     * Verifica se o movimento [from]→[to] corta o portão. O portão é estendido meio bloco em cada ponta,
     * para cobrir os blocos clicados por inteiro. Intervalo meio aberto [0, 1): se o movimento terminar
     * exatamente no portão, o cruzamento conta no tick seguinte, nunca duas vezes.
     */
    fun crossing(from: Vec, to: Vec): Crossing? {
        val rx = to.x - from.x
        val rz = to.z - from.z
        val gx = bx - ax
        val gz = bz - az
        val denom = cross(rx, rz, gx, gz)
        if (abs(denom) < 1e-12) return null
        val qx = ax - from.x
        val qz = az - from.z
        val t = cross(qx, qz, gx, gz) / denom
        val s = cross(qx, qz, rx, rz) / denom
        if (t < 0.0 || t >= 1.0) return null
        val ext = if (width > 1e-9) 0.5 / width else 0.0
        if (s < -ext || s > 1.0 + ext) return null
        val y = from.y + t * (to.y - from.y)
        if (y < minY || y > maxY) return null
        val (nx, nz) = normal()
        return Crossing(t, rx * nx + rz * nz > 0)
    }

    /** Lado do portão em que o ponto está: positivo = lado da normal. */
    fun side(p: Vec): Double = cross(bx - ax, bz - az, p.x - ax, p.z - az)

    companion object {
        private fun cross(ax: Double, az: Double, bx: Double, bz: Double) = ax * bz - az * bx

        /**
         * Monta o portão a partir dos dois blocos clicados (coordenadas de bloco).
         * Vai de 1 bloco abaixo do mais baixo até [height] blocos acima do mais alto.
         */
        fun fromBlocks(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, height: Int): GateShape = GateShape(
            ax = x1 + 0.5,
            az = z1 + 0.5,
            bx = x2 + 0.5,
            bz = z2 + 0.5,
            minY = min(y1, y2).toDouble(),
            maxY = max(y1, y2) + 1.0 + height,
        )
    }
}

object Placement {
    /** Yaw do Minecraft para olhar na direção (dx, dz). */
    fun yawToward(dx: Double, dz: Double): Float = Math.toDegrees(atan2(-dx, dz)).toFloat()

    /**
     * Posições lado a lado logo atrás da linha de largada (sem grid marcado): fileiras paralelas à linha,
     * espaçadas [spacing] blocos, a primeira [back] blocos atrás e as seguintes [rowGap] blocos mais para trás.
     * [forwardSign] é +1 quando "para frente" é o lado da normal do portão, -1 quando é o oposto.
     */
    fun startPositions(
        gate: GateShape,
        forwardSign: Int,
        count: Int,
        spacing: Double = 2.5,
        back: Double = 3.0,
        rowGap: Double = 4.0,
    ): List<Pair<Double, Double>> {
        if (count <= 0) return emptyList()
        val (nx0, nz0) = gate.normal()
        val nx = nx0 * forwardSign
        val nz = nz0 * forwardSign
        val (ux, uz) = gate.along()
        val usable = max(0.0, gate.width - 1.0)
        val perRow = max(1, floor(usable / spacing).toInt() + 1)
        val cx = (gate.ax + gate.bx) / 2
        val cz = (gate.az + gate.bz) / 2
        return List(count) { i ->
            val row = i / perRow
            val inRow = min(perRow, count - row * perRow)
            val col = i % perRow
            val offset = (col - (inRow - 1) / 2.0) * spacing
            val dist = back + row * rowGap
            (cx + ux * offset - nx * dist) to (cz + uz * offset - nz * dist)
        }
    }

    /**
     * Ponto de volta num portão: no centro, [ahead] blocos para o lado de onde fica o próximo portão
     * ([towardX], [towardZ]), para não reativar o mesmo portão nem cair de novo no mesmo buraco.
     */
    fun respawnPoint(gate: GateShape, towardX: Double, towardZ: Double, ahead: Double = 2.0): Pair<Double, Double> {
        val (nx, nz) = gate.normal()
        val cx = (gate.ax + gate.bx) / 2
        val cz = (gate.az + gate.bz) / 2
        val sign = if ((towardX - cx) * nx + (towardZ - cz) * nz >= 0) 1.0 else -1.0
        return (cx + nx * ahead * sign) to (cz + nz * ahead * sign)
    }
}
