package com.shifurace.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.shifurace.board.RecordBoards
import com.shifurace.config.ConfigManager
import com.shifurace.data.RaceStorage
import com.shifurace.race.Msg
import com.shifurace.race.RaceManager
import com.shifurace.race.RaceState
import com.shifurace.race.Rentals
import com.shifurace.tool.EditTool
import com.shifurace.track.GateData
import com.shifurace.track.TrackData
import com.shifurace.trophy.TrophyService
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.network.chat.Component

object RaceCommands {
    private val TRACKS = SuggestionProvider<CommandSourceStack> { _, builder ->
        SharedSuggestionProvider.suggest(RaceStorage.tracks.values.map { it.nome }, builder)
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("corrida")
            // ---------------------------------------------------------- jogadores
            .then(Commands.literal("entrar")
                .executes { join(it, null) }
                .then(Commands.argument("pista", StringArgumentType.word()).suggests(TRACKS).executes { join(it, StringArgumentType.getString(it, "pista")) }))
            .then(Commands.literal("sair").executes { ctx ->
                result(ctx, RaceManager.leave(ctx.source.playerOrException))
            })
            .then(Commands.literal("voltar").executes { ctx ->
                result(ctx, RaceManager.requestReset(ctx.source.playerOrException))
            })
            // ---------------------------------------------------------- staff
            .then(staff("pista")
                .then(Commands.literal("criar").then(Commands.argument("nome", StringArgumentType.word()).executes(::createTrack)))
                .then(Commands.literal("remover").then(trackArg().executes(::removeTrack)))
                .then(Commands.literal("lista").executes(::listTracks))
                .then(Commands.literal("cor").then(trackArg().then(Commands.argument("hex", StringArgumentType.word()).executes(::trackColor)))))
            .then(staff("ferramenta").then(trackArg().executes { ctx ->
                EditTool.start(ctx.source.playerOrException, track(ctx) ?: return@executes 0)
                1
            }))
            .then(staff("editar").then(trackArg().executes { ctx ->
                val player = ctx.source.playerOrException
                val t = track(ctx) ?: return@executes 0
                val on = !EditTool.isViewing(player, t)
                EditTool.setViewing(player, t, on)
                ok(ctx, if (on) "Mostrando os portões da pista ${t.nome}." else "Portões escondidos.")
            }))
            .then(staff("checkpoint").then(trackArg()
                .then(Commands.literal("remover").then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(::removeCheckpoint)))
                .then(Commands.literal("inserir").then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes { ctx ->
                    val t = track(ctx) ?: return@executes 0
                    val n = IntegerArgumentType.getInteger(ctx, "numero").coerceAtMost(t.checkpoints.size + 1)
                    EditTool.setInsert(ctx.source.playerOrException, t, n - 1)
                    1
                }))
                .then(Commands.literal("limpar").executes(::clearCheckpoints))
                .then(Commands.literal("adicionar").then(Commands.argument("de", BlockPosArgument.blockPos()).then(Commands.argument("ate", BlockPosArgument.blockPos()).executes { ctx ->
                    val t = editable(ctx) ?: return@executes 0
                    val gate = gateFrom(ctx, t) ?: return@executes 0
                    t.checkpoints.add(gate)
                    RaceStorage.saveTracks()
                    EditTool.refreshViewers(ctx.source.server, t)
                    ok(ctx, "Checkpoint ${t.checkpoints.size} marcado.")
                })))))
            .then(staff("linha").then(trackArg().then(Commands.literal("definir").then(Commands.argument("de", BlockPosArgument.blockPos()).then(Commands.argument("ate", BlockPosArgument.blockPos()).executes { ctx ->
                val t = editable(ctx) ?: return@executes 0
                val gate = gateFrom(ctx, t) ?: return@executes 0
                t.linha = gate
                // Sentido: para onde quem digitou está olhando (no console, o lado da normal; use "inverter" se precisar)
                val look = ctx.source.entity?.lookAngle
                val (nx, nz) = gate.shape().normal()
                t.sentidoLinha = if (look == null || look.x * nx + look.z * nz >= 0) 1 else -1
                RaceStorage.saveTracks()
                EditTool.refreshViewers(ctx.source.server, t)
                ok(ctx, "Linha de largada/chegada da pista ${t.nome} marcada.")
            })))).then(trackArg().then(Commands.literal("inverter").executes { ctx ->
                val t = editable(ctx) ?: return@executes 0
                t.sentidoLinha = -t.sentidoLinha
                RaceStorage.saveTracks()
                EditTool.refreshViewers(ctx.source.server, t)
                ok(ctx, "Sentido da corrida invertido na pista ${t.nome}.")
            })))
            .then(staff("voltas").then(trackArg().then(Commands.argument("voltas", IntegerArgumentType.integer(1, 99)).executes { ctx ->
                val t = track(ctx) ?: return@executes 0
                t.voltasPadrao = IntegerArgumentType.getInteger(ctx, "voltas")
                RaceStorage.saveTracks()
                ok(ctx, "A pista ${t.nome} agora tem ${Msg.laps(t.voltasPadrao)} por padrão.")
            })))
            .then(staff("placar").then(trackArg()
                .executes { ctx ->
                    val src = ctx.source
                    val t = track(ctx) ?: return@executes 0
                    val y = src.entity?.eyeY ?: src.position.y
                    // Virado para quem colocou (o texto olha para o lado oposto ao que o jogador olha)
                    RecordBoards.place(src.server, t, src.level, src.position.x, y, src.position.z, src.rotation.y + 180f)
                    ok(ctx, "Placar da pista ${t.nome} colocado aqui.")
                }
                .then(Commands.literal("remover").executes { ctx ->
                    val t = track(ctx) ?: return@executes 0
                    RecordBoards.remove(ctx.source.server, t)
                    ok(ctx, "Placar removido.")
                })))
            .then(staff("abrir").then(trackArg()
                .executes { ctx -> open(ctx, null) }
                .then(Commands.argument("voltas", IntegerArgumentType.integer(1, 99)).executes { ctx -> open(ctx, IntegerArgumentType.getInteger(ctx, "voltas")) })))
            .then(staff("iniciar")
                .executes { ctx -> withRace(ctx, null) { RaceManager.start(ctx.source.server, it) } }
                .then(trackArg().executes { ctx -> withRace(ctx, track(ctx) ?: return@executes 0) { RaceManager.start(ctx.source.server, it) } }))
            .then(staff("encerrar")
                .executes { ctx -> withRace(ctx, null) { RaceManager.end(ctx.source.server, it) } }
                .then(trackArg().executes { ctx -> withRace(ctx, track(ctx) ?: return@executes 0) { RaceManager.end(ctx.source.server, it) } }))
            .then(staff("recordes").then(trackArg()
                .then(Commands.literal("limpar")
                    .executes { ctx -> clearRecords(ctx, null) }
                    .then(Commands.argument("voltas", IntegerArgumentType.integer(1, 99)).executes { ctx -> clearRecords(ctx, IntegerArgumentType.getInteger(ctx, "voltas")) }))
                .then(Commands.literal("remover").then(Commands.argument("jogador", StringArgumentType.word())
                    .executes { ctx -> removeRecord(ctx, null) }
                    .then(Commands.argument("voltas", IntegerArgumentType.integer(1, 99)).executes { ctx -> removeRecord(ctx, IntegerArgumentType.getInteger(ctx, "voltas")) })))))
            .then(staff("trofeu").then(Commands.argument("jogador", EntityArgument.player()).then(trackArg().executes { ctx ->
                val target = EntityArgument.getPlayer(ctx, "jogador")
                val t = track(ctx) ?: return@executes 0
                TrophyService.give(target, t, 0, t.voltasPadrao, 0)
                ok(ctx, "Troféu da pista ${t.nome} entregue para ${target.gameProfile.name}.")
            })))
            .then(staff("recarregar").executes { ctx ->
                ConfigManager.load()
                ok(ctx, "Configuração recarregada.")
            })
            .then(staff("diagnostico").executes { ctx ->
                val pos = ctx.source.position
                val lines = Rentals.diagnose(ctx.source.level, pos.x, pos.y, pos.z)
                val text = Msg.prefix().append(Component.literal("Diagnóstico do Pokémon de corrida (${ConfigManager.config.pokemon}):"))
                lines.forEach { text.append(Component.literal("\n$it")) }
                ctx.source.sendSuccess({ text }, false)
                if (lines.any { it.startsWith("PROBLEMA") }) 0 else 1
            })
        dispatcher.register(root)
    }

    private fun staff(name: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires { it.hasPermission(2) }

    private fun trackArg() = Commands.argument("pista", StringArgumentType.word()).suggests(TRACKS)

    private fun track(ctx: CommandContext<CommandSourceStack>): TrackData? {
        val name = StringArgumentType.getString(ctx, "pista")
        val t = RaceStorage.track(name)
        if (t == null) ctx.source.sendFailure(Msg.error("Pista $name não existe. Use /corrida pista lista."))
        return t
    }

    private fun editable(ctx: CommandContext<CommandSourceStack>): TrackData? {
        val t = track(ctx) ?: return null
        if (RaceManager.isTrackBusy(t)) {
            ctx.source.sendFailure(Msg.error("Há uma corrida nessa pista; espere ela acabar para editar."))
            return null
        }
        return t
    }

    private fun ok(ctx: CommandContext<CommandSourceStack>, text: String): Int {
        ctx.source.sendSuccess({ Msg.info(text) }, false)
        return 1
    }

    private fun result(ctx: CommandContext<CommandSourceStack>, error: String?): Int {
        if (error != null) {
            ctx.source.sendFailure(Msg.error(error))
            return 0
        }
        return 1
    }

    private fun join(ctx: CommandContext<CommandSourceStack>, name: String?): Int =
        result(ctx, RaceManager.join(ctx.source.playerOrException, name))

    private fun open(ctx: CommandContext<CommandSourceStack>, laps: Int?): Int {
        val t = track(ctx) ?: return 0
        return result(ctx, RaceManager.open(ctx.source.server, t, laps ?: t.voltasPadrao))
    }

    /** Executa na corrida da pista dada, ou na única corrida ativa. */
    private fun withRace(ctx: CommandContext<CommandSourceStack>, given: TrackData?, action: (TrackData) -> String?): Int {
        val t = given ?: run {
            val active = RaceManager.races().toList()
            when (active.size) {
                0 -> return result(ctx, "Não há nenhuma corrida aberta.")
                1 -> active[0].track
                else -> return result(ctx, "Há mais de uma corrida: diga a pista (${active.joinToString { it.track.nome }}).")
            }
        }
        val error = action(t)
        if (error == null) {
            val state = RaceManager.raceOf(t)?.state
            ctx.source.sendSuccess({ Msg.info(if (state == RaceState.COUNTDOWN) "Largada dada na pista ${t.nome}!" else "Feito.") }, false)
            return 1
        }
        return result(ctx, error)
    }

    /** Portão entre as coordenadas "de" e "ate" do comando. */
    private fun gateFrom(ctx: CommandContext<CommandSourceStack>, t: TrackData): GateData? {
        val a = BlockPosArgument.getBlockPos(ctx, "de")
        val b = BlockPosArgument.getBlockPos(ctx, "ate")
        if (a == b) {
            ctx.source.sendFailure(Msg.error("Os dois pontos são o mesmo bloco."))
            return null
        }
        val dimension = ctx.source.level.dimension().location().toString()
        if ((t.linha != null || t.checkpoints.isNotEmpty()) && t.dimensao != dimension) {
            ctx.source.sendFailure(Msg.error("A pista fica em ${t.dimensao}; rode o comando lá."))
            return null
        }
        t.dimensao = dimension
        return GateData(a.x, a.y, a.z, b.x, b.y, b.z, ConfigManager.config.alturaPortao)
    }

    private fun createTrack(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "nome")
        if (RaceStorage.track(name) != null) return result(ctx, "Já existe uma pista chamada $name.")
        val t = TrackData(nome = name, dimensao = ctx.source.level.dimension().location().toString())
        RaceStorage.tracks[t.key] = t
        RaceStorage.saveTracks()
        ctx.source.player?.let { EditTool.start(it, t) }
        return ok(ctx, "Pista $name criada. Marque a linha de largada/chegada com a ferramenta.")
    }

    private fun removeTrack(ctx: CommandContext<CommandSourceStack>): Int {
        val t = editable(ctx) ?: return 0
        RecordBoards.remove(ctx.source.server, t)
        RaceStorage.tracks.remove(t.key)
        RaceStorage.saveTracks()
        RaceStorage.deleteRecords(t)
        EditTool.onTrackRemoved(ctx.source.server, t.key)
        return ok(ctx, "Pista ${t.nome} removida (com os recordes).")
    }

    private fun listTracks(ctx: CommandContext<CommandSourceStack>): Int {
        if (RaceStorage.tracks.isEmpty()) return ok(ctx, "Nenhuma pista criada. Use /corrida pista criar <nome>.")
        val text = Msg.prefix().append(Component.literal("Pistas:"))
        RaceStorage.tracks.values.forEach { t ->
            val status = t.missing()?.let { "falta $it" } ?: "${t.checkpoints.size} checkpoints, ${Msg.laps(t.voltasPadrao)}"
            val race = RaceManager.raceOf(t)?.let { " · corrida ${it.state.name.lowercase()}" } ?: ""
            text.append(Component.literal("\n  ${t.nome} — $status$race"))
        }
        ctx.source.sendSuccess({ text }, false)
        return 1
    }

    private fun trackColor(ctx: CommandContext<CommandSourceStack>): Int {
        val t = track(ctx) ?: return 0
        val hex = StringArgumentType.getString(ctx, "hex").removePrefix("#")
        val color = hex.toIntOrNull(16)?.takeIf { hex.length == 6 } ?: return result(ctx, "Use uma cor como F5C542 (6 dígitos hexadecimais).")
        t.corTrofeu = color
        RaceStorage.saveTracks()
        return ok(ctx, "Os troféus da pista ${t.nome} agora saem na cor #${hex.uppercase()}.")
    }

    private fun removeCheckpoint(ctx: CommandContext<CommandSourceStack>): Int {
        val t = editable(ctx) ?: return 0
        val n = IntegerArgumentType.getInteger(ctx, "numero")
        if (n > t.checkpoints.size) return result(ctx, "A pista só tem ${t.checkpoints.size} checkpoints.")
        t.checkpoints.removeAt(n - 1)
        RaceStorage.saveTracks()
        EditTool.refreshViewers(ctx.source.server, t)
        return ok(ctx, "Checkpoint $n removido; os seguintes foram renumerados.")
    }

    private fun clearCheckpoints(ctx: CommandContext<CommandSourceStack>): Int {
        val t = editable(ctx) ?: return 0
        t.checkpoints.clear()
        RaceStorage.saveTracks()
        EditTool.refreshViewers(ctx.source.server, t)
        return ok(ctx, "Todos os checkpoints da pista ${t.nome} foram apagados.")
    }

    private fun clearRecords(ctx: CommandContext<CommandSourceStack>, laps: Int?): Int {
        val t = track(ctx) ?: return 0
        RaceStorage.records(t).clear(laps)
        RaceStorage.saveRecords(t)
        RecordBoards.onRecordsChanged(t, laps ?: 0)
        return ok(ctx, if (laps == null) "Todos os rankings da pista ${t.nome} foram apagados." else "Ranking de ${Msg.laps(laps)} apagado.")
    }

    private fun removeRecord(ctx: CommandContext<CommandSourceStack>, laps: Int?): Int {
        val t = track(ctx) ?: return 0
        val name = StringArgumentType.getString(ctx, "jogador")
        val removed = RaceStorage.records(t).remove(laps, name)
        RaceStorage.saveRecords(t)
        RecordBoards.onRecordsChanged(t, laps ?: 0)
        return ok(ctx, "$removed tempo(s) de $name removido(s).")
    }
}
