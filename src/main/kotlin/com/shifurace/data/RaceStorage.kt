package com.shifurace.data

import com.shifurace.ShifuRace
import com.shifurace.config.ConfigManager
import com.shifurace.core.TrackRecords
import com.shifurace.track.TrackData
import com.shifurace.util.SafeFiles
import com.google.gson.reflect.TypeToken
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path

/** Para onde o jogador volta ao sair da corrida (onde estava antes do /corrida entrar). */
class ReturnPoint(
    var dimensao: String = "minecraft:overworld",
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    var yaw: Float = 0f,
    var pitch: Float = 0f,
)

/**
 * Dados do mundo em <mundo>/shifurace/: pistas.json, retornos.json e recordes/<pista>.json.
 * Tudo é gravado na hora em que muda (são arquivos pequenos e mudam pouco).
 */
object RaceStorage {
    private var dir: Path? = null

    val tracks = linkedMapOf<String, TrackData>()
    val returns = linkedMapOf<String, ReturnPoint>()
    private val records = hashMapOf<String, TrackRecords>()

    fun load(server: MinecraftServer) {
        val base = server.getWorldPath(LevelResource.ROOT).resolve(ShifuRace.MOD_ID).normalize()
        dir = base
        tracks.clear()
        returns.clear()
        records.clear()
        read<Map<String, TrackData>>(base.resolve("pistas.json"), object : TypeToken<Map<String, TrackData>>() {})
            ?.values?.forEach { if (it.nome.isNotBlank()) tracks[it.key] = it }
        read<Map<String, ReturnPoint>>(base.resolve("retornos.json"), object : TypeToken<Map<String, ReturnPoint>>() {})
            ?.let { returns.putAll(it) }
        ShifuRace.LOGGER.info("ShifuRace: {} pista(s) carregada(s)", tracks.size)
    }

    fun unload() {
        dir = null
        tracks.clear()
        returns.clear()
        records.clear()
    }

    fun track(name: String): TrackData? = tracks[name.lowercase()]

    fun saveTracks() = write("pistas.json", tracks)

    fun saveReturns() = write("retornos.json", returns)

    fun records(track: TrackData): TrackRecords = records.getOrPut(track.key) {
        val base = dir
        val loaded = if (base == null) null else read<TrackRecords>(recordsFile(base, track.key), object : TypeToken<TrackRecords>() {})
        loaded ?: TrackRecords()
    }

    fun saveRecords(track: TrackData) {
        val base = dir ?: return
        val value = records[track.key] ?: return
        try {
            SafeFiles.writeAtomic(recordsFile(base, track.key), ConfigManager.GSON.toJson(value))
        } catch (e: Exception) {
            ShifuRace.LOGGER.error("Não foi possível salvar os recordes da pista {}", track.nome, e)
        }
    }

    fun deleteRecords(track: TrackData) {
        records.remove(track.key)
        val base = dir ?: return
        try {
            Files.deleteIfExists(recordsFile(base, track.key))
        } catch (e: Exception) {
            ShifuRace.LOGGER.error("Não foi possível apagar os recordes da pista {}", track.nome, e)
        }
    }

    private fun recordsFile(base: Path, key: String): Path =
        base.resolve("recordes").resolve(key.replace(Regex("[^a-z0-9_-]"), "_") + ".json")

    private fun <T> read(file: Path, type: TypeToken<T>): T? {
        if (Files.notExists(file)) return null
        return try {
            Files.newBufferedReader(file).use { ConfigManager.GSON.fromJson(it, type.type) }
        } catch (e: Exception) {
            ShifuRace.LOGGER.error("Erro lendo {} ({})", file, e.message)
            null
        }
    }

    private fun write(name: String, value: Any) {
        val base = dir ?: return
        try {
            SafeFiles.writeAtomic(base.resolve(name), ConfigManager.GSON.toJson(value))
        } catch (e: Exception) {
            ShifuRace.LOGGER.error("Não foi possível salvar {}", name, e)
        }
    }
}
