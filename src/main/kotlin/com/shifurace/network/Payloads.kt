package com.shifurace.network

import com.shifurace.ShifuRace
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

private fun <T : CustomPacketPayload> type(path: String) = CustomPacketPayload.Type<T>(ShifuRace.id(path))

private fun <T> RegistryFriendlyByteBuf.writeEntries(list: List<T>, writer: (RegistryFriendlyByteBuf, T) -> Unit) {
    writeVarInt(list.size)
    list.forEach { writer(this, it) }
}
private fun <T> RegistryFriendlyByteBuf.readEntries(reader: (RegistryFriendlyByteBuf) -> T): List<T> =
    List(readVarInt()) { reader(this) }

// ---------------------------------------------------------------- dados

/** Portão para desenhar no cliente; [index] -1 é a linha de largada/chegada. */
data class GateView(val index: Int, val ax: Double, val az: Double, val bx: Double, val bz: Double, val minY: Double, val maxY: Double) {
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(index + 1)
        buf.writeDouble(ax); buf.writeDouble(az); buf.writeDouble(bx); buf.writeDouble(bz)
        buf.writeDouble(minY); buf.writeDouble(maxY)
    }

    companion object {
        fun read(buf: RegistryFriendlyByteBuf) = GateView(
            buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
        )
    }
}

/** Uma linha da torre de posições. */
data class StandingView(
    val position: Int,
    val name: String,
    val gapKind: Int,
    val gapValue: Long,
    val self: Boolean,
    val finished: Boolean,
) {
    companion object {
        const val GAP_LEADER = 0
        const val GAP_TIME = 1
        const val GAP_LAPS = 2
        const val GAP_UNKNOWN = 3
        const val GAP_OUT = 4
    }
}

data class ResultRow(val position: Int, val name: String, val totalMs: Long, val bestLapMs: Long, val fastest: Boolean, val self: Boolean)

// ---------------------------------------------------------------- servidor -> cliente

/** Geometria da pista da corrida em que o jogador está (para os arcos). [trackName] vazio = sem corrida. */
data class RaceTrackPayload(val trackName: String, val gates: List<GateView>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<RaceTrackPayload>("pista")
        val NONE = RaceTrackPayload("", emptyList())
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaceTrackPayload> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.trackName); buf.writeEntries(p.gates) { b, g -> g.write(b) } },
            { buf -> RaceTrackPayload(buf.readUtf(), buf.readEntries(GateView::read)) },
        )
    }
}

/** Estado da corrida para a HUD do piloto. */
data class RaceHudPayload(
    val active: Boolean,
    val state: Int,
    val trackName: String,
    val laps: Int,
    val currentLap: Int,
    val checkpointCount: Int,
    /** Próximo portão na volta: 0 até checkpointCount-1 = checkpoints; checkpointCount = linha. */
    val nextGate: Int,
    /** Tempo de corrida no servidor agora (para o cronômetro do cliente acertar o relógio). */
    val raceMs: Long,
    val lapStartMs: Long,
    val lastLapMs: Long,
    val bestLapMs: Long,
    val fastestLapMs: Long,
    val deltaMs: Long,
    val finishMs: Long,
    /** Segundos que faltam para a expulsão por ficar parado (0 = sem aviso). */
    val idleKickIn: Int,
    /** Ticks que faltam para o VAI (só na contagem). */
    val countdownTicks: Int,
    val standings: List<StandingView>,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val STATE_OPEN = 0
        const val STATE_COUNTDOWN = 1
        const val STATE_RUNNING = 2
        const val STATE_FINISHED = 3
        const val NONE = Long.MIN_VALUE

        val TYPE = type<RaceHudPayload>("hud")
        val INACTIVE = RaceHudPayload(false, 0, "", 0, 0, 0, 0, 0, 0, NONE, NONE, NONE, NONE, NONE, 0, 0, emptyList())
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaceHudPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBoolean(p.active); buf.writeVarInt(p.state); buf.writeUtf(p.trackName)
                buf.writeVarInt(p.laps); buf.writeVarInt(p.currentLap); buf.writeVarInt(p.checkpointCount); buf.writeVarInt(p.nextGate)
                buf.writeLong(p.raceMs); buf.writeLong(p.lapStartMs); buf.writeLong(p.lastLapMs); buf.writeLong(p.bestLapMs)
                buf.writeLong(p.fastestLapMs); buf.writeLong(p.deltaMs); buf.writeLong(p.finishMs)
                buf.writeVarInt(p.idleKickIn); buf.writeVarInt(p.countdownTicks)
                buf.writeEntries(p.standings) { b, s ->
                    b.writeVarInt(s.position); b.writeUtf(s.name); b.writeVarInt(s.gapKind); b.writeLong(s.gapValue)
                    b.writeBoolean(s.self); b.writeBoolean(s.finished)
                }
            },
            { buf ->
                RaceHudPayload(
                    buf.readBoolean(), buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readLong(), buf.readLong(), buf.readLong(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readEntries { StandingView(it.readVarInt(), it.readUtf(), it.readVarInt(), it.readLong(), it.readBoolean(), it.readBoolean()) },
                )
            },
        )
    }
}

/** Aviso de volta fechada no meio da tela. */
data class LapToastPayload(val lap: Int, val lapMs: Long, val kind: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val NORMAL = 0
        const val PERSONAL_BEST = 1
        const val FASTEST = 2

        val TYPE = type<LapToastPayload>("volta")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, LapToastPayload> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.lap); buf.writeLong(p.lapMs); buf.writeVarInt(p.kind) },
            { buf -> LapToastPayload(buf.readVarInt(), buf.readLong(), buf.readVarInt()) },
        )
    }
}

/** Resultado final da corrida. */
data class RaceResultPayload(val trackName: String, val laps: Int, val rows: List<ResultRow>, val note: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<RaceResultPayload>("resultado")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RaceResultPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.trackName); buf.writeVarInt(p.laps)
                buf.writeEntries(p.rows) { b, r ->
                    b.writeVarInt(r.position); b.writeUtf(r.name); b.writeLong(r.totalMs); b.writeLong(r.bestLapMs)
                    b.writeBoolean(r.fastest); b.writeBoolean(r.self)
                }
                buf.writeUtf(p.note)
            },
            { buf ->
                RaceResultPayload(
                    buf.readUtf(), buf.readVarInt(),
                    buf.readEntries { ResultRow(it.readVarInt(), it.readUtf(), it.readLong(), it.readLong(), it.readBoolean(), it.readBoolean()) },
                    buf.readUtf(),
                )
            },
        )
    }
}

/** Visualização dos portões para quem está montando a pista. [trackName] vazio = desliga. */
data class EditViewPayload(val trackName: String, val lineForward: Int, val gates: List<GateView>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<EditViewPayload>("edicao")
        val OFF = EditViewPayload("", 1, emptyList())
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, EditViewPayload> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.trackName); buf.writeVarInt(p.lineForward + 1); buf.writeEntries(p.gates) { b, g -> g.write(b) } },
            { buf -> EditViewPayload(buf.readUtf(), buf.readVarInt() - 1, buf.readEntries(GateView::read)) },
        )
    }
}

// ---------------------------------------------------------------- cliente -> servidor

/** Tecla de voltar ao último checkpoint. */
class ResetRequestPayload : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<ResetRequestPayload>("voltar")
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ResetRequestPayload> = StreamCodec.of({ _, _ -> }, { ResetRequestPayload() })
    }
}
