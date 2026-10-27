package com.shifurace.config

import com.shifurace.ShifuRace
import com.shifurace.util.SafeFiles
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

/** config/shifurace/config.json */
class RaceConfig {
    /** Pokémon emprestado, no formato do /pokegive (espécie e propriedades). */
    var pokemon = "porytronz level=50"

    /** Bônus de montaria igual para todos: 0 = mínimo da faixa da espécie, 1 = máximo. */
    var boostMontaria = 1.0

    var maxPilotos = 16
    var segundosContagem = 3
    var segundosParadoAviso = 20
    var segundosParadoExpulsao = 30

    /** Quem não se afasta mais que isso (em blocos) conta como parado. */
    var raioParado = 2.0

    /** Intervalo mínimo entre dois usos da tecla/comando de voltar ao checkpoint. */
    var segundosRecargaVoltar = 3

    /** Corridas com menos pilotos que isso na largada não dão troféu. */
    var minimoPilotosTrofeu = 2

    /** Altura dos portões novos, acima do bloco clicado mais alto. */
    var alturaPortao = 6

    /** Abaixo do portão mais baixo da pista menos isso, o piloto caiu no vazio. */
    var margemVazio = 20

    /** Piloto a mais que isso do próprio Pokémon (teleporte, /home etc.) sai da corrida. */
    var distanciaMaximaDoPokemon = 64.0

    var segundosTrocaPlacar = 8
    var linhasPlacar = 5

    fun sanitize() {
        boostMontaria = boostMontaria.coerceIn(0.0, 1.0)
        maxPilotos = maxPilotos.coerceIn(1, 64)
        segundosContagem = segundosContagem.coerceIn(1, 10)
        segundosParadoExpulsao = segundosParadoExpulsao.coerceAtLeast(5)
        segundosParadoAviso = segundosParadoAviso.coerceIn(1, segundosParadoExpulsao)
        raioParado = raioParado.coerceAtLeast(0.5)
        segundosRecargaVoltar = segundosRecargaVoltar.coerceAtLeast(0)
        minimoPilotosTrofeu = minimoPilotosTrofeu.coerceAtLeast(1)
        alturaPortao = alturaPortao.coerceIn(2, 32)
        margemVazio = margemVazio.coerceAtLeast(3)
        distanciaMaximaDoPokemon = distanciaMaximaDoPokemon.coerceAtLeast(8.0)
        segundosTrocaPlacar = segundosTrocaPlacar.coerceAtLeast(2)
        linhasPlacar = linhasPlacar.coerceIn(1, 10)
    }
}

object ConfigManager {
    val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    var config = RaceConfig()
        private set

    fun load() {
        val file = FabricLoader.getInstance().configDir.resolve(ShifuRace.MOD_ID).resolve("config.json")
        config = if (Files.exists(file)) {
            try {
                Files.newBufferedReader(file).use { GSON.fromJson(it, RaceConfig::class.java) } ?: RaceConfig()
            } catch (e: Exception) {
                ShifuRace.LOGGER.error("Erro lendo config/shifurace/config.json — usando padrão. Corrija o JSON! ({})", e.message)
                RaceConfig()
            }
        } else {
            RaceConfig()
        }
        config.sanitize()
        try {
            // Regrava para incluir opções novas com o valor padrão
            SafeFiles.writeAtomic(file, GSON.toJson(config))
        } catch (e: Exception) {
            ShifuRace.LOGGER.error("Não foi possível salvar config/shifurace/config.json", e)
        }
    }
}
