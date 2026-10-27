package com.shifurace.trophy

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/** Dados gravados no troféu de corrida: quem venceu, em qual pista, em quanto tempo e quando. */
data class TrophyInfo(
    val owner: String,
    val track: String,
    val timeMs: Long,
    val laps: Int,
    val racers: Int,
    val date: String,
    /** Cor da taça (RGB), a cor da pista. */
    val color: Int,
) {
    companion object {
        val CODEC: Codec<TrophyInfo> = RecordCodecBuilder.create { i ->
            i.group(
                Codec.STRING.fieldOf("owner").forGetter(TrophyInfo::owner),
                Codec.STRING.fieldOf("track").forGetter(TrophyInfo::track),
                Codec.LONG.fieldOf("time_ms").forGetter(TrophyInfo::timeMs),
                Codec.INT.optionalFieldOf("laps", 1).forGetter(TrophyInfo::laps),
                Codec.INT.optionalFieldOf("racers", 0).forGetter(TrophyInfo::racers),
                Codec.STRING.fieldOf("date").forGetter(TrophyInfo::date),
                Codec.INT.optionalFieldOf("color", 0xF5C542).forGetter(TrophyInfo::color),
            ).apply(i, ::TrophyInfo)
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, TrophyInfo> = object : StreamCodec<ByteBuf, TrophyInfo> {
            override fun decode(buf: ByteBuf) = TrophyInfo(
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.INT.decode(buf),
            )

            override fun encode(buf: ByteBuf, value: TrophyInfo) {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.owner)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.track)
                ByteBufCodecs.VAR_LONG.encode(buf, value.timeMs)
                ByteBufCodecs.VAR_INT.encode(buf, value.laps)
                ByteBufCodecs.VAR_INT.encode(buf, value.racers)
                ByteBufCodecs.STRING_UTF8.encode(buf, value.date)
                ByteBufCodecs.INT.encode(buf, value.color)
            }
        }
    }
}
