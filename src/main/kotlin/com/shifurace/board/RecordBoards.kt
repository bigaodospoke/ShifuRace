package com.shifurace.board

import com.shifurace.config.ConfigManager
import com.shifurace.data.RaceStorage
import com.shifurace.race.Msg
import com.shifurace.race.RaceManager
import com.shifurace.track.BoardData
import com.shifurace.track.TrackData
import com.shifurace.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ChunkPos
import java.util.UUID

/**
 * Placar de recordes de cada pista: um Text Display parado acima da largada que vai alternando
 * entre os rankings de cada número de voltas ("RANKING · CORRIDA DE 3 VOLTAS").
 */
object RecordBoards {
    private const val TAG = "shifurace_placar"
    private const val SCALE = 1.6f

    private class Cycle(var index: Int = 0, var nextSwitch: Long = 0, var showLaps: Int? = null)

    private val cycles = hashMapOf<String, Cycle>()

    fun place(server: MinecraftServer, track: TrackData, level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float) {
        remove(server, track)
        track.dimensao = level.dimension().location().toString()
        track.placar = BoardData(x, y, z, yaw, null)
        RaceStorage.saveTracks()
        refresh(server, track, force = true)
    }

    fun remove(server: MinecraftServer, track: TrackData) {
        val board = track.placar ?: return
        val level = RaceManager.levelOf(server, track)
        board.uuid?.let { id -> level?.getEntity(UUID.fromString(id))?.discard() }
        track.placar = null
        RaceStorage.saveTracks()
    }

    /** Alguém entrou num ranking: o placar mostra esse ranking na hora. */
    fun onRecordsChanged(track: TrackData, laps: Int) {
        val cycle = cycles.getOrPut(track.key) { Cycle() }
        cycle.showLaps = laps
        cycle.nextSwitch = 0
    }

    fun tick(server: MinecraftServer) {
        if (server.tickCount % 20 != 0) return
        for (track in RaceStorage.tracks.values) refresh(server, track, force = false)
    }

    private fun refresh(server: MinecraftServer, track: TrackData, force: Boolean) {
        val board = track.placar ?: return
        val level = RaceManager.levelOf(server, track) ?: return
        val pos = BlockPos.containing(board.x, board.y, board.z)
        if (!level.isLoaded(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos))) return

        val now = server.tickCount.toLong()
        val cycle = cycles.getOrPut(track.key) { Cycle() }
        var entity = board.uuid?.let { level.getEntity(UUID.fromString(it)) } as? Display.TextDisplay
        val created = entity == null
        if (entity == null) {
            entity = spawn(level, board) ?: return
            board.uuid = entity.stringUUID
            RaceStorage.saveTracks()
        }
        if (!force && !created && now < cycle.nextSwitch) return

        val records = RaceStorage.records(track)
        val lapCounts = records.lapCounts()
        val laps = when {
            cycle.showLaps != null && cycle.showLaps in lapCounts -> cycle.showLaps!!.also { cycle.index = lapCounts.indexOf(it) }
            lapCounts.isEmpty() -> null
            else -> {
                if (!created && !force) cycle.index++
                lapCounts[Math.floorMod(cycle.index, lapCounts.size)]
            }
        }
        cycle.showLaps = null
        cycle.nextSwitch = now + ConfigManager.config.segundosTrocaPlacar * 20L
        setText(level, entity, text(track, laps))
    }

    private fun text(track: TrackData, laps: Int?): Component {
        val out = Component.literal("✦ ${track.nome.uppercase()} ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        if (laps == null) {
            out.append(Component.literal("\nRANKING DA PISTA").withStyle(ChatFormatting.AQUA))
            out.append(Component.literal("\n\nNenhum tempo ainda.\nSeja o primeiro!").withStyle(ChatFormatting.WHITE))
            return out
        }
        out.append(Component.literal("\nRANKING · CORRIDA DE ${Msg.laps(laps).uppercase()}").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        out.append(Component.literal("\n"))
        val size = ConfigManager.config.linhasPlacar
        val top = RaceStorage.records(track).top(laps, size)
        for (i in 0 until size) {
            val entry = top.getOrNull(i)
            val line: MutableComponent = if (entry == null) {
                Component.literal("\n${i + 1}º  —").withStyle(ChatFormatting.DARK_GRAY)
            } else {
                Component.literal("\n${i + 1}º  ${entry.name}  ·  ${TimeFormat.race(entry.timeMs)}  ·  ${entry.date}")
            }
            if (entry != null) {
                when (i) {
                    0 -> line.withStyle { it.withColor(TextColor.fromRgb(0xFFD23F)).withBold(true) }
                    1 -> line.withStyle { it.withColor(TextColor.fromRgb(0xE0E0E0)) }
                    2 -> line.withStyle { it.withColor(TextColor.fromRgb(0xE0904A)) }
                    else -> line.withStyle(ChatFormatting.WHITE)
                }
            }
            out.append(line)
        }
        return out
    }

    private fun spawn(level: ServerLevel, board: BoardData): Display.TextDisplay? {
        val entity = EntityType.TEXT_DISPLAY.create(level) ?: return null
        val tag = entity.saveWithoutId(CompoundTag())
        tag.putString("billboard", "fixed")
        tag.putString("alignment", "center")
        tag.putInt("line_width", 320)
        tag.putInt("background", 0xA0000000.toInt())
        tag.putBoolean("shadow", true)
        val transform = CompoundTag()
        transform.put("translation", floats(0f, 0f, 0f))
        transform.put("left_rotation", floats(0f, 0f, 0f, 1f))
        transform.put("scale", floats(SCALE, SCALE, SCALE))
        transform.put("right_rotation", floats(0f, 0f, 0f, 1f))
        tag.put("transformation", transform)
        entity.load(tag)
        entity.moveTo(board.x, board.y, board.z, board.yaw, 0f)
        entity.addTag(TAG)
        if (!level.addFreshEntity(entity)) return null
        return entity
    }

    private fun setText(level: ServerLevel, entity: Display.TextDisplay, text: Component) {
        val tag = entity.saveWithoutId(CompoundTag())
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()))
        entity.load(tag)
    }

    private fun floats(vararg values: Float): ListTag = ListTag().apply { values.forEach { add(FloatTag.valueOf(it)) } }
}
