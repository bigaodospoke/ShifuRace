package com.shifurace.race

import com.cobblemon.mod.common.api.entity.Despawner
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.shifurace.ShifuRace
import com.shifurace.config.ConfigManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

/**
 * Porytronz de corrida: um Pokémon sem dono, fora de qualquer time ou PC, que existe só como entidade na pista.
 * Assim não tem como ser guardado, trocado ou vendido, e todos recebem exatamente o mesmo bônus de montaria.
 */
object Rentals {
    /** Marca salva na entidade; uma entidade com ela fora de corrida ativa é removida ao carregar. */
    const val TAG = "shifurace_aluguel"

    private val NEVER_DESPAWN = object : Despawner<PokemonEntity> {
        override fun beginTracking(entity: PokemonEntity) {}
        override fun shouldDespawn(entity: PokemonEntity): Boolean = false
    }

    fun isRental(entity: Entity?): Boolean = entity is PokemonEntity && entity.tags.contains(TAG)

    /** Por que o Pokémon da config não serve para corrida, ou null se está tudo certo. */
    fun problem(): String? {
        val config = ConfigManager.config
        val props = try {
            PokemonProperties.parse(config.pokemon)
        } catch (e: Exception) {
            return "a config 'pokemon' (${config.pokemon}) não é válida: ${e.message}"
        }
        val name = props.species ?: return "a config 'pokemon' (${config.pokemon}) não diz a espécie"
        // Sem essa checagem o Cobblemon criaria um Pokémon aleatório no lugar de uma espécie que não existe
        val species = PokemonSpecies.getByName(name.substringAfter(':')) ?: return "a espécie '$name' não existe neste servidor"
        if (species.standardForm.riding.behaviours.isNullOrEmpty()) return "a espécie '${species.name}' não é montável"
        return null
    }

    /** Cria o Porytronz de corrida em ([x], [y], [z]) olhando para [yaw]. Devolve null se a espécie não servir. */
    fun spawn(level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float): PokemonEntity? {
        val config = ConfigManager.config
        problem()?.let {
            ShifuRace.LOGGER.error("Pokémon de corrida: {}", it)
            return null
        }
        val entity = try {
            PokemonProperties.parse(config.pokemon).createEntity(level)
        } catch (e: Exception) {
            ShifuRace.LOGGER.error("Não consegui criar o Pokémon de corrida '{}'", config.pokemon, e)
            return null
        }
        val pokemon = entity.pokemon
        // Mesmo bônus para todos: velocidade, aceleração etc. iguais em qualquer Porytronz de corrida
        pokemon.setRideBoosts(RidingStat.entries.associateWith { (pokemon.getMaxRideBoost(it) * config.boostMontaria).toFloat() })
        entity.addTag(TAG)
        entity.despawner = NEVER_DESPAWN
        entity.entityData.set(PokemonEntity.UNBATTLEABLE, true)
        entity.setPersistenceRequired()
        entity.moveTo(x, y, z, yaw, 0f)
        entity.yHeadRot = yaw
        entity.yBodyRot = yaw
        if (!level.addFreshEntity(entity)) return null
        return entity
    }

    /** Cria um Porytronz de corrida de teste, descreve espécie e atributos de montaria e apaga. */
    fun diagnose(level: ServerLevel, x: Double, y: Double, z: Double): List<String> {
        problem()?.let { return listOf("PROBLEMA: $it") }
        val entity = spawn(level, x, y, z, 0f) ?: return listOf("PROBLEMA: não foi possível criar a entidade (veja o log)")
        val pokemon = entity.pokemon
        val lines = mutableListOf(
            "Espécie: ${pokemon.species.name} (nível ${pokemon.level})",
            "Estilos de montaria: ${pokemon.form.riding.behaviours?.keys?.joinToString() ?: "nenhum"}",
        )
        val style = pokemon.form.riding.behaviours?.keys?.firstOrNull() ?: RidingStyle.LAND
        RidingStat.entries.forEach { stat ->
            lines += "  ${stat.name}: ${"%.1f".format(pokemon.getRideStat(style, stat))} (bônus ${"%.1f".format(pokemon.getRideBoost(stat))} de ${"%.1f".format(pokemon.getMaxRideBoost(stat))})"
        }
        discard(entity)
        return lines
    }

    fun discard(entity: Entity?) {
        if (entity != null && !entity.isRemoved) {
            entity.ejectPassengers()
            entity.discard()
        }
    }
}
