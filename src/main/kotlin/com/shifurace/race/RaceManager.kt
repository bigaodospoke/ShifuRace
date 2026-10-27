package com.shifurace.race

import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.shifurace.ShifuRace
import com.shifurace.board.RecordBoards
import com.shifurace.config.ConfigManager
import com.shifurace.core.Gap
import com.shifurace.core.GateRef
import com.shifurace.core.Placement
import com.shifurace.core.ProgressEvent
import com.shifurace.core.RacerProgress
import com.shifurace.core.StandingInput
import com.shifurace.core.Standings
import com.shifurace.core.Vec
import com.shifurace.data.RaceStorage
import com.shifurace.data.ReturnPoint
import com.shifurace.network.GateView
import com.shifurace.network.LapToastPayload
import com.shifurace.network.RaceHudPayload
import com.shifurace.network.RaceResultPayload
import com.shifurace.network.RaceTrackPayload
import com.shifurace.network.ResultRow
import com.shifurace.network.ServerNetworking
import com.shifurace.network.StandingView
import com.shifurace.track.GateData
import com.shifurace.track.TrackData
import com.shifurace.trophy.TrophyService
import com.shifurace.util.TimeFormat
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToLong

object RaceManager {
    /** Corridas ativas por pista (uma por pista). */
    private val races = linkedMapOf<String, Race>()
    private val byPlayer = hashMapOf<UUID, Race>()

    private var server: MinecraftServer? = null
    private val now: Long get() = server?.tickCount?.toLong() ?: 0L

    fun attach(srv: MinecraftServer) {
        server = srv
    }

    fun races(): Collection<Race> = races.values

    fun raceOf(track: TrackData): Race? = races[track.key]

    /** Corrida em que o jogador está (inclusive se já terminou, até a corrida acabar). */
    fun raceOf(uuid: UUID): Race? = byPlayer[uuid]

    fun racerOf(uuid: UUID): Racer? = byPlayer[uuid]?.racers?.get(uuid)?.takeIf { !it.out }

    /** Piloto dono deste Porytronz de corrida, se houver. */
    fun racerOfRental(entity: PokemonEntity): Pair<Race, Racer>? {
        for (race in races.values) {
            val racer = race.racers.values.firstOrNull { it.rentalUuid == entity.uuid && !it.out } ?: continue
            return race to racer
        }
        return null
    }

    // ------------------------------------------------------------------ comandos

    fun open(server: MinecraftServer, track: TrackData, laps: Int): String? {
        track.missing()?.let { return "A pista ${track.nome} ainda não tem $it." }
        if (races.containsKey(track.key)) return "Já existe uma corrida na pista ${track.nome}."
        levelOf(server, track) ?: return "A dimensão da pista (${track.dimensao}) não existe neste servidor."
        races[track.key] = Race(track, laps)
        Msg.broadcast(
            server,
            Msg.prefix()
                .append(Component.literal("Inscrições abertas na pista ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(track.nome).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" (${Msg.laps(laps)})! ").withStyle(ChatFormatting.YELLOW))
                .append(Msg.command("[Entrar]", "/corrida entrar ${track.nome}", "Entrar na corrida (você recebe um Porytronz de corrida)")),
        )
        return null
    }

    fun join(player: ServerPlayer, trackName: String?): String? {
        val srv = player.server
        if (byPlayer.containsKey(player.uuid)) return "Você já está numa corrida. Use /corrida sair para sair."
        val race = if (trackName != null) {
            val track = RaceStorage.track(trackName) ?: return "Pista $trackName não existe."
            races[track.key] ?: return "Não há corrida aberta na pista ${track.nome}."
        } else {
            val open = races.values.filter { it.state == RaceState.OPEN }
            when (open.size) {
                0 -> return "Não há nenhuma corrida com inscrições abertas."
                1 -> open[0]
                else -> return "Há mais de uma corrida aberta: use /corrida entrar <pista>."
            }
        }
        if (race.state != RaceState.OPEN) return "As inscrições dessa corrida já fecharam."
        if (race.racers.size >= ConfigManager.config.maxPilotos) return "A corrida está cheia (${ConfigManager.config.maxPilotos} pilotos)."
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) return "Termine a batalha antes de entrar na corrida."
        if (player.isSpectator) return "Saia do modo espectador antes de entrar."
        val level = levelOf(srv, race.track) ?: return "A dimensão da pista não existe."
        val line = race.track.linha ?: return "A pista não tem linha de largada."
        Rentals.problem()?.let { return "O Pokémon de corrida não está disponível: $it." }

        RaceStorage.returns[player.uuid.toString()] = ReturnPoint(
            player.level().dimension().location().toString(), player.x, player.y, player.z, player.yRot, player.xRot,
        )
        RaceStorage.saveReturns()

        val racer = Racer(player.uuid, player.gameProfile.name)
        race.racers[player.uuid] = racer
        byPlayer[player.uuid] = race

        val (x, z) = Placement.startPositions(line.shape(), race.track.sentidoLinha, race.racers.size).last()
        val y = groundY(level, x, z, line)
        val yaw = forwardYaw(race.track)
        // O Porytronz é criado no tick seguinte, quando o chunk da largada já estiver carregado em volta do jogador
        player.stopRiding()
        player.teleportTo(level, x, y, z, yaw, 0f)

        ServerNetworking.send(player, trackPayload(race.track))
        sendHud(race)
        Msg.send(player, "Você entrou na corrida da pista ${race.track.nome}! Espere a largada em cima do seu Porytronz.")
        raceAudience(race).filter { it != player }.forEach { Msg.send(it, "${racer.name} entrou na corrida (${race.racers.size} pilotos).") }
        return null
    }

    fun leave(player: ServerPlayer): String? {
        val race = byPlayer[player.uuid] ?: return "Você não está em nenhuma corrida."
        val racer = race.racers[player.uuid] ?: return "Você não está em nenhuma corrida."
        if (racer.finished) {
            dropOut(race, racer, player, null)
            Msg.send(player, "Você saiu da área da corrida.")
        } else {
            dropOut(race, racer, player, "saiu da corrida")
        }
        return null
    }

    fun start(server: MinecraftServer, track: TrackData): String? {
        val race = races[track.key] ?: return "Não há corrida aberta na pista ${track.nome}."
        if (race.state != RaceState.OPEN) return "Essa corrida já começou."
        // Quem caiu do servidor enquanto esperava sai antes da largada
        race.racers.values.toList().forEach { if (server.playerList.getPlayer(it.uuid) == null) removeBeforeStart(race, it) }
        if (race.racers.isEmpty()) return "Ninguém entrou na corrida ainda."
        track.missing()?.let { return "A pista ainda não tem $it." }
        val level = levelOf(server, track) ?: return "A dimensão da pista não existe."
        val line = track.linha!!

        race.state = RaceState.COUNTDOWN
        race.startersCount = race.racers.size
        val config = ConfigManager.config
        race.goTick = now + 20 + config.segundosContagem * 20L
        val positions = Placement.startPositions(line.shape(), track.sentidoLinha, race.racers.size)
        val yaw = forwardYaw(track)
        race.racers.values.forEachIndexed { i, racer ->
            val player = server.playerList.getPlayer(racer.uuid) ?: return@forEachIndexed
            val rental = ensureRental(race, racer, player, level) ?: return@forEachIndexed
            val (x, z) = positions[i]
            val y = groundY(level, x, z, line)
            teleportPair(racer, player, rental, level, x, y, z, yaw, mount = true)
            racer.hold = Vec(x, y, z)
            racer.holdYaw = yaw
            ServerNetworking.send(player, trackPayload(track))
            Msg.title(player, Component.literal("PREPARAR").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), Component.literal("${track.nome} · ${Msg.laps(race.laps)}"))
        }
        sendHud(race)
        return null
    }

    fun end(server: MinecraftServer, track: TrackData): String? {
        val race = races[track.key] ?: return "Não há corrida na pista ${track.nome}."
        finishRace(server, race, "Corrida encerrada pela staff.")
        return null
    }

    /** Tecla de voltar ao checkpoint (ou /corrida voltar). */
    fun requestReset(player: ServerPlayer): String? {
        val race = byPlayer[player.uuid] ?: return "Você não está em nenhuma corrida."
        val racer = race.racers[player.uuid] ?: return "Você não está em nenhuma corrida."
        if (race.state != RaceState.RUNNING || !racer.active) return "Só dá para voltar ao checkpoint durante a corrida."
        val cooldown = ConfigManager.config.segundosRecargaVoltar * 20L
        val wait = racer.lastResetTick + cooldown - now
        if (wait > 0) return "Espere ${(wait + 19) / 20}s para voltar ao checkpoint de novo."
        resetToCheckpoint(race, racer, player, mounted = true)
        return null
    }

    // ------------------------------------------------------------------ tick

    fun tick(server: MinecraftServer) {
        for (race in races.values.toList()) {
            when (race.state) {
                RaceState.OPEN -> tickWaiting(server, race)
                RaceState.COUNTDOWN -> tickCountdown(server, race)
                RaceState.RUNNING -> tickRunning(server, race)
            }
            if (races[race.track.key] === race && now % 5 == 0L) sendHud(race)
        }
    }

    /** Antes da largada: mantém cada um com o seu Porytronz (sem regras de corrida ainda). */
    private fun tickWaiting(server: MinecraftServer, race: Race) {
        val level = levelOf(server, race.track) ?: return
        for (racer in race.racers.values.toList()) {
            val player = server.playerList.getPlayer(racer.uuid) ?: continue
            val rental = ensureRental(race, racer, player, level) ?: continue
            handleRemount(racer, player, rental)
            if (player.vehicle === rental) {
                racer.hold = null
            } else if (racer.remountAt < 0) {
                holdStill(racer, rental, keepCurrent = true)
            }
        }
    }

    private fun tickCountdown(server: MinecraftServer, race: Race) {
        val level = levelOf(server, race.track) ?: return
        val left = race.goTick - now
        val config = ConfigManager.config
        for (racer in race.racers.values.toList()) {
            val player = server.playerList.getPlayer(racer.uuid) ?: continue
            val rental = ensureRental(race, racer, player, level) ?: continue
            handleRemount(racer, player, rental)
            // Ninguém sai do lugar antes do VAI (o cliente também trava os controles)
            val hold = racer.hold
            if (hold != null && racer.remountAt < 0) {
                if (player.vehicle !== rental) {
                    player.startRiding(rental, true)
                } else if (Vec(rental.x, rental.y, rental.z).horizontalDistance(hold) > 1.5) {
                    teleportPair(racer, player, rental, level, hold.x, hold.y, hold.z, racer.holdYaw, mount = true)
                }
            }
            if (left > 0 && left % 20 == 0L && left <= config.segundosContagem * 20L) {
                val n = left / 20
                Msg.title(player, Component.literal("$n").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), null, 0, 18, 2)
                player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 0.8f)
            }
        }
        if (left <= 0) go(server, race, level)
    }

    private fun go(server: MinecraftServer, race: Race, level: ServerLevel) {
        race.state = RaceState.RUNNING
        race.goTick = now
        for (racer in race.racers.values) {
            racer.progress = RacerProgress(race.track.checkpoints.size, race.laps)
            racer.hold = null
            val player = server.playerList.getPlayer(racer.uuid) ?: continue
            val rental = rentalOf(level, racer)
            val pos = rental?.let { Vec(it.x, it.y, it.z) } ?: Vec(player.x, player.y, player.z)
            racer.prevPos = pos
            racer.idle.reset(pos, now)
            Msg.title(player, Component.literal("VAI!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), null, 0, 20, 10)
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 2f)
        }
        sendHud(race)
    }

    private fun tickRunning(server: MinecraftServer, race: Race) {
        val level = levelOf(server, race.track) ?: return
        val config = ConfigManager.config
        val voidY = race.track.lowestY() - config.margemVazio
        for (racer in race.racers.values.toList()) {
            if (racer.out) continue
            val player = server.playerList.getPlayer(racer.uuid) ?: continue
            val rental = ensureRental(race, racer, player, level) ?: continue
            handleRemount(racer, player, rental)
            if (racer.finished) {
                if (player.vehicle === rental) {
                    racer.hold = null
                } else if (racer.remountAt < 0) {
                    holdStill(racer, rental, keepCurrent = true)
                }
                continue
            }

            if (player.level() !== level || player.distanceTo(rental) > config.distanciaMaximaDoPokemon) {
                dropOut(race, racer, player, "se afastou do Porytronz")
                continue
            }

            val mounted = player.vehicle === rental
            if (!mounted && racer.remountAt < 0 && now > racer.ignoreDismountUntil && !racer.waitingRemount) {
                // Desmontou: volta para o último checkpoint e só segue quando montar de novo
                resetToCheckpoint(race, racer, player, mounted = false)
                player.displayClientMessage(Component.literal("Você desmontou! Clique no Porytronz para montar de novo.").withStyle(ChatFormatting.RED), true)
                continue
            }
            if (racer.waitingRemount) {
                if (mounted) {
                    racer.waitingRemount = false
                    racer.hold = null
                    racer.prevPos = Vec(rental.x, rental.y, rental.z)
                    racer.idle.reset(racer.prevPos!!, now)
                } else {
                    holdStill(racer, rental, keepCurrent = false)
                }
            }

            if (mounted && racer.remountAt < 0 &&
                (racer.pendingHazard || rental.isInLava || player.isInLava || rental.y < voidY || player.y < voidY)
            ) {
                resetToCheckpoint(race, racer, player, mounted = true)
                continue
            }
            racer.pendingHazard = false

            val pos = Vec(rental.x, rental.y, rental.z)
            val prev = racer.prevPos
            if (prev != null && mounted && racer.remountAt < 0) checkCrossings(server, race, racer, player, prev, pos)
            racer.prevPos = pos
            if (racer.finished) continue

            // Parado demais: aviso e expulsão
            val idleTicks = racer.idle.update(if (mounted) pos else Vec(player.x, player.y, player.z), now)
            racer.idleTicks = idleTicks
            if (idleTicks >= config.segundosParadoExpulsao * 20L) {
                dropOut(race, racer, player, "ficou parado por ${config.segundosParadoExpulsao}s")
            }
        }
        if (race.allDone()) finishRace(server, race, null)
    }

    // ------------------------------------------------------------------ portões e voltas

    private fun checkCrossings(server: MinecraftServer, race: Race, racer: Racer, player: ServerPlayer, prev: Vec, pos: Vec) {
        val progress = racer.progress ?: return
        var start = prev
        var tStart = 0.0
        repeat(3) {
            val ref = progress.upcomingGate
            val gate = race.track.gate(ref) ?: return
            val c = gate.shape().crossing(start, pos) ?: return
            val t = tStart + c.t * (1 - tStart)
            val forward = if (race.track.sentidoLinha > 0) c.forward else !c.forward
            val ms = ((now - 1 - race.goTick + t) * 50).roundToLong().coerceAtLeast(0)
            val event = progress.onCross(ref, forward, ms) ?: return
            handleProgress(server, race, racer, player, event)
            if (racer.finished) return
            tStart = t + 1e-6
            start = prev + (pos - prev) * tStart
        }
    }

    private fun handleProgress(server: MinecraftServer, race: Race, racer: Racer, player: ServerPlayer, event: ProgressEvent) {
        when (event) {
            is ProgressEvent.CheckpointPassed -> {
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f)
            }
            is ProgressEvent.LapCompleted -> {
                val fastest = race.fastestLapMs
                val kind = when {
                    fastest == null || event.lapMs < fastest -> {
                        race.fastestLapMs = event.lapMs
                        race.fastestLapHolder = racer.uuid
                        LapToastPayload.FASTEST
                    }
                    event.personalBest -> LapToastPayload.PERSONAL_BEST
                    else -> LapToastPayload.NORMAL
                }
                ServerNetworking.send(player, LapToastPayload(event.lapNumber, event.lapMs, kind))
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f)
                if (event.finished) {
                    onFinish(server, race, racer, player, event.totalMs)
                } else if (event.lapNumber == race.laps - 1) {
                    Msg.title(player, Component.empty(), Component.literal("ÚLTIMA VOLTA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), 0, 30, 10)
                }
            }
        }
        sendHud(race)
    }

    private fun onFinish(server: MinecraftServer, race: Race, racer: Racer, player: ServerPlayer, totalMs: Long) {
        val order = ++race.finishCount
        racer.finishOrder = order
        val time = TimeFormat.race(totalMs)
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f)
        if (order == 1) {
            raceAudience(race).forEach {
                Msg.title(
                    it, Component.literal("VENCEDOR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal("${racer.name} · $time").withStyle(ChatFormatting.YELLOW), 5, 50, 15,
                )
            }
            Msg.broadcast(
                server,
                Msg.prefix()
                    .append(Component.literal(racer.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(" venceu a corrida na pista ${race.track.nome} (${Msg.laps(race.laps)}) com ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(time).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)),
            )
            if (race.startersCount >= ConfigManager.config.minimoPilotosTrofeu) {
                TrophyService.give(player, race.track, totalMs, race.laps, race.startersCount)
            } else {
                Msg.send(player, "Troféu só em corridas com pelo menos ${ConfigManager.config.minimoPilotosTrofeu} pilotos.")
            }
        } else {
            Msg.title(player, Component.literal("${order}º LUGAR").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), Component.literal(time), 5, 40, 15)
        }

        val records = RaceStorage.records(race.track)
        val result = records.submit(race.laps, racer.uuid.toString(), racer.name, totalMs, Msg.today(), System.currentTimeMillis())
        RaceStorage.saveRecords(race.track)
        val rank = result.rank
        if (result.newRecord) {
            Msg.broadcast(
                server,
                Msg.prefix().append(
                    Component.literal("NOVO RECORDE na pista ${race.track.nome} (${Msg.laps(race.laps)}): ${racer.name} com $time!")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                ),
            )
        } else if (rank != null && rank <= ConfigManager.config.linhasPlacar) {
            raceAudience(race).forEach { Msg.send(it, "${racer.name} entrou no top ${ConfigManager.config.linhasPlacar} da pista em ${rank}º lugar!") }
        }
        RecordBoards.onRecordsChanged(race.track, race.laps)
    }

    // ------------------------------------------------------------------ saídas e fim

    /** Tira o piloto da corrida: o Porytronz some e ele volta para onde estava antes de entrar. */
    private fun dropOut(race: Race, racer: Racer, player: ServerPlayer?, reason: String?) {
        val server = server ?: return
        if (race.state == RaceState.OPEN) {
            removeBeforeStart(race, racer)
        } else {
            racer.out = true
            byPlayer.remove(racer.uuid)
            levelOf(server, race.track)?.let { Rentals.discard(rentalOf(it, racer)) }
        }
        if (player != null) {
            returnPlayer(player)
            ServerNetworking.send(player, RaceHudPayload.INACTIVE)
            ServerNetworking.send(player, RaceTrackPayload.NONE)
            if (reason != null) player.sendSystemMessage(Msg.error("Você ${reason}."))
        }
        if (reason != null) raceAudience(race).forEach { Msg.send(it, "${racer.name} $reason.") }
        if (race.state != RaceState.OPEN && race.allDone()) finishRace(server, race, null)
    }

    private fun removeBeforeStart(race: Race, racer: Racer) {
        race.racers.remove(racer.uuid)
        byPlayer.remove(racer.uuid)
        server?.let { srv -> levelOf(srv, race.track)?.let { Rentals.discard(rentalOf(it, racer)) } }
    }

    fun finishRace(server: MinecraftServer, race: Race, note: String?) {
        if (races[race.track.key] !== race) return
        races.remove(race.track.key)
        val level = levelOf(server, race.track)

        if (race.state != RaceState.OPEN) {
            val standings = standings(race)
            val rows = standings.map { (s, r) ->
                val p = r.progress
                ResultRow(
                    s.position, r.name,
                    if (r.finished) p?.finishedAtMs ?: -1 else -1,
                    p?.bestLapMs ?: -1,
                    race.fastestLapHolder == r.uuid,
                    false,
                )
            }
            val summary = Msg.prefix().append(Component.literal("Resultado · ${race.track.nome} (${Msg.laps(race.laps)})").withStyle(ChatFormatting.YELLOW))
            rows.forEach { row ->
                val time = if (row.totalMs >= 0) TimeFormat.race(row.totalMs) else "não terminou"
                summary.append(Component.literal("\n  ${row.position}º ${row.name} — $time").withStyle(if (row.position == 1 && row.totalMs >= 0) ChatFormatting.GOLD else ChatFormatting.WHITE))
            }
            for (racer in race.racers.values) {
                val player = server.playerList.getPlayer(racer.uuid) ?: continue
                ServerNetworking.send(
                    player,
                    RaceResultPayload(race.track.nome, race.laps, rows.map { it.copy(self = it.name == racer.name) }, note ?: ""),
                )
                player.sendSystemMessage(summary)
            }
        } else if (note != null) {
            race.racers.values.forEach { r -> server.playerList.getPlayer(r.uuid)?.let { Msg.send(it, note) } }
        }

        for (racer in race.racers.values) {
            level?.let { Rentals.discard(rentalOf(it, racer)) }
            if (byPlayer[racer.uuid] === race) byPlayer.remove(racer.uuid)
            val player = server.playerList.getPlayer(racer.uuid) ?: continue
            if (!racer.out) returnPlayer(player)
            ServerNetworking.send(player, RaceHudPayload.INACTIVE)
            ServerNetworking.send(player, RaceTrackPayload.NONE)
        }
    }

    fun shutdown(server: MinecraftServer) {
        races.values.toList().forEach { finishRace(server, it, "O servidor está reiniciando: corrida encerrada.") }
        races.clear()
        byPlayer.clear()
        this.server = null
    }

    fun onDisconnect(player: ServerPlayer) {
        val race = byPlayer[player.uuid] ?: return
        val racer = race.racers[player.uuid] ?: return
        // Não teleporta agora: o ponto de volta fica salvo e é usado quando ele entrar de novo
        val srv = server ?: return
        if (race.state == RaceState.OPEN) {
            removeBeforeStart(race, racer)
        } else {
            racer.out = true
            byPlayer.remove(racer.uuid)
            levelOf(srv, race.track)?.let { Rentals.discard(rentalOf(it, racer)) }
            raceAudience(race).forEach { Msg.send(it, "${racer.name} desconectou e saiu da corrida.") }
            if (race.allDone()) finishRace(srv, race, null)
        }
    }

    fun onJoin(player: ServerPlayer) {
        if (byPlayer.containsKey(player.uuid)) return
        if (RaceStorage.returns.containsKey(player.uuid.toString())) {
            returnPlayer(player)
            Msg.send(player, "Você foi levado de volta para onde estava antes da corrida.")
        }
    }

    fun onDeath(player: ServerPlayer) {
        val race = byPlayer[player.uuid] ?: return
        val racer = race.racers[player.uuid] ?: return
        dropOut(race, racer, null, "morreu e saiu da corrida")
        // Morto não dá para teleportar; o ponto de volta é usado quando ele renascer/entrar
    }

    fun onRespawn(player: ServerPlayer) {
        if (!byPlayer.containsKey(player.uuid) && RaceStorage.returns.containsKey(player.uuid.toString())) returnPlayer(player)
    }

    fun onChangeDimension(player: ServerPlayer) {
        val race = byPlayer[player.uuid] ?: return
        val racer = race.racers[player.uuid] ?: return
        if (race.state == RaceState.OPEN || !racer.finished) dropOut(race, racer, player, "saiu da dimensão da pista")
    }

    /** Dano no piloto ou no Porytronz: nunca acontece; lava, fogo, sufocar e vazio mandam para o checkpoint. */
    fun onHazard(uuid: UUID) {
        val race = byPlayer[uuid] ?: return
        if (race.state == RaceState.RUNNING) race.racers[uuid]?.pendingHazard = true
    }

    /** O piloto clicou no próprio Porytronz: monta (o Pokémon não tem dono, então o Cobblemon não deixaria sozinho). */
    fun tryMount(player: ServerPlayer, entity: PokemonEntity): Boolean {
        val (_, racer) = racerOfRental(entity) ?: return false
        if (racer.uuid != player.uuid) return false
        if (player.vehicle !== entity) player.startRiding(entity, true)
        return true
    }

    // ------------------------------------------------------------------ apoio

    private fun handleRemount(racer: Racer, player: ServerPlayer, rental: PokemonEntity) {
        if (racer.remountAt in 0..now) {
            racer.remountAt = -1
            if (player.vehicle !== rental && !player.startRiding(rental, true)) {
                ShifuRace.LOGGER.warn("Não consegui montar {} no Porytronz de corrida", racer.name)
            }
        }
    }

    /** Mantém o Porytronz sem piloto parado (ele é "selvagem" e sairia andando). */
    private fun holdStill(racer: Racer, rental: PokemonEntity, keepCurrent: Boolean) {
        val hold = racer.hold ?: if (keepCurrent) Vec(rental.x, rental.y, rental.z).also { racer.hold = it; racer.holdYaw = rental.yRot } else return
        if (Vec(rental.x, rental.y, rental.z).horizontalDistance(hold) > 0.75) {
            rental.teleportTo(hold.x, hold.y, hold.z)
            rental.deltaMovement = Vec3.ZERO
        }
        rental.navigation.stop()
    }

    private fun resetToCheckpoint(race: Race, racer: Racer, player: ServerPlayer, mounted: Boolean) {
        val srv = server ?: return
        val progress = racer.progress ?: return
        val level = levelOf(srv, race.track) ?: return
        val rental = ensureRental(race, racer, player, level) ?: return
        val lastRef = progress.lastGate
        val gate = race.track.gate(lastRef) ?: return
        val next = race.track.gate(race.track.after(lastRef)) ?: gate
        val nextCenter = next.shape().center
        val (x, z) = Placement.respawnPoint(gate.shape(), nextCenter.x, nextCenter.z)
        val y = groundY(level, x, z, gate)
        val yaw = Placement.yawToward(nextCenter.x - x, nextCenter.z - z)
        teleportPair(racer, player, rental, level, x, y, z, yaw, mounted)
        racer.pendingHazard = false
        racer.lastResetTick = now
        if (!mounted) {
            racer.waitingRemount = true
            racer.hold = Vec(x, y, z)
            racer.holdYaw = yaw
        } else {
            racer.waitingRemount = false
            racer.hold = null
        }
        racer.idle.reset(Vec(x, y, z), now)
    }

    /** Leva piloto e Porytronz juntos; se [mount], monta de novo 2 ticks depois (o cliente precisa ver os dois no lugar). */
    private fun teleportPair(
        racer: Racer, player: ServerPlayer, rental: PokemonEntity, level: ServerLevel,
        x: Double, y: Double, z: Double, yaw: Float, mount: Boolean,
    ) {
        racer.ignoreDismountUntil = now + 10
        player.stopRiding()
        rental.teleportTo(x, y, z)
        rental.yRot = yaw
        rental.yHeadRot = yaw
        rental.yBodyRot = yaw
        rental.deltaMovement = Vec3.ZERO
        rental.fallDistance = 0f
        rental.clearFire()
        player.teleportTo(level, x, y, z, yaw, 0f)
        player.deltaMovement = Vec3.ZERO
        player.fallDistance = 0f
        player.clearFire()
        racer.prevPos = Vec(x, y, z)
        racer.remountAt = if (mount) now + 2 else -1
    }

    /**
     * Porytronz do piloto; cria um novo onde o piloto está se ainda não existe (logo depois de entrar) ou sumiu.
     * Só cria com o chunk do piloto carregado: num chunk ainda carregando a entidade ficaria invisível,
     * o mod acharia que ela sumiu e criaria outra a cada tick.
     */
    private fun ensureRental(race: Race, racer: Racer, player: ServerPlayer, level: ServerLevel): PokemonEntity? {
        rentalOf(level, racer)?.let { return it }
        if (player.level() !== level) return null
        val pos = player.blockPosition()
        if (!level.isLoaded(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos))) return null
        val rental = Rentals.spawn(level, player.x, player.y, player.z, player.yRot) ?: return null
        if (racer.rentalUuid != null) ShifuRace.LOGGER.info("Porytronz de corrida de {} recriado na pista {}", racer.name, race.track.nome)
        racer.rentalUuid = rental.uuid
        racer.remountAt = now + 2
        racer.ignoreDismountUntil = now + 10
        return rental
    }

    private fun rentalOf(level: ServerLevel, racer: Racer): PokemonEntity? {
        val id = racer.rentalUuid ?: return null
        val entity = level.getEntity(id) as? PokemonEntity ?: return null
        return entity.takeIf { it.isAlive && !it.isRemoved }
    }

    private fun returnPlayer(player: ServerPlayer) {
        val srv = server ?: return
        val point = RaceStorage.returns.remove(player.uuid.toString())
        RaceStorage.saveReturns()
        player.stopRiding()
        if (point == null || player.isDeadOrDying) return
        val key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(point.dimensao))
        val level = srv.getLevel(key) ?: srv.overworld()
        player.teleportTo(level, point.x, point.y, point.z, point.yaw, point.pitch)
        player.fallDistance = 0f
        player.clearFire()
    }

    /** Jogadores online que estão (ou estavam, até o fim) nesta corrida. */
    private fun raceAudience(race: Race): List<ServerPlayer> {
        val srv = server ?: return emptyList()
        return race.racers.values.filter { !it.out }.mapNotNull { srv.playerList.getPlayer(it.uuid) }
    }

    fun levelOf(server: MinecraftServer, track: TrackData): ServerLevel? =
        server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(track.dimensao)))

    private fun forwardYaw(track: TrackData): Float {
        val (nx, nz) = track.linha!!.shape().normal()
        return Placement.yawToward(nx * track.sentidoLinha, nz * track.sentidoLinha)
    }

    /** Chão em (x, z) dentro da altura do portão: bloco sólido (ou água, onde o Porytronz anda por cima) com espaço livre acima. */
    fun groundY(level: Level, x: Double, z: Double, gate: GateData): Double {
        val shape = gate.shape()
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        var y = shape.maxY.toInt()
        val bottom = shape.minY.toInt() - 4
        while (y > bottom) {
            val pos = BlockPos(bx, y, bz)
            val below = pos.below()
            val belowState = level.getBlockState(below)
            val solidBelow = !belowState.getCollisionShape(level, below).isEmpty || level.getFluidState(below).`is`(FluidTags.WATER)
            val free = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty && level.getFluidState(pos).isEmpty &&
                level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty
            if (solidBelow && free && !level.getFluidState(below).`is`(FluidTags.LAVA)) {
                val top = belowState.getCollisionShape(level, below)
                return if (top.isEmpty) y.toDouble() else below.y + top.max(net.minecraft.core.Direction.Axis.Y)
            }
            y--
        }
        return shape.minY + 1
    }

    // ------------------------------------------------------------------ HUD

    private fun standings(race: Race): List<Pair<com.shifurace.core.Standing, Racer>> {
        val srv = server
        val level = srv?.let { levelOf(it, race.track) }
        val inputs = race.racers.values.mapNotNull { r ->
            val p = r.progress ?: return@mapNotNull null
            val rental = level?.let { rentalOf(it, r) }
            val nextCenter = race.track.gate(p.upcomingGate)?.shape()?.center
            val dist = if (rental != null && nextCenter != null) Vec(rental.x, rental.y, rental.z).horizontalDistance(nextCenter) else Double.MAX_VALUE
            StandingInput(r.uuid.toString(), p, r.finishOrder, dist, r.out)
        }
        val byId = race.racers.values.associateBy { it.uuid.toString() }
        return Standings.compute(inputs).map { it to byId.getValue(it.id) }
    }

    private fun sendHud(race: Race) {
        val srv = server ?: return
        val config = ConfigManager.config
        val standings = if (race.state == RaceState.RUNNING) standings(race) else emptyList()
        for (racer in race.racers.values) {
            if (racer.out) continue
            val player = srv.playerList.getPlayer(racer.uuid) ?: continue
            val p = racer.progress
            val views = if (standings.isNotEmpty()) {
                standings.map { (s, r) ->
                    val (kind, value) = when (val g = s.gap) {
                        Gap.Leader -> StandingView.GAP_LEADER to 0L
                        is Gap.Time -> StandingView.GAP_TIME to g.ms
                        is Gap.Laps -> StandingView.GAP_LAPS to g.laps.toLong()
                        Gap.Unknown -> StandingView.GAP_UNKNOWN to 0L
                        Gap.Out -> StandingView.GAP_OUT to 0L
                    }
                    StandingView(s.position, r.name, kind, value, r.uuid == racer.uuid, r.finished)
                }
            } else {
                race.racers.values.filter { !it.out }.mapIndexed { i, r ->
                    StandingView(i + 1, r.name, StandingView.GAP_UNKNOWN, 0, r.uuid == racer.uuid, false)
                }
            }
            val idleKickIn = if (race.state == RaceState.RUNNING && racer.active && racer.idleTicks >= config.segundosParadoAviso * 20L) {
                ((config.segundosParadoExpulsao * 20L - racer.idleTicks + 19) / 20).toInt().coerceAtLeast(1)
            } else 0
            val state = when {
                racer.finished -> RaceHudPayload.STATE_FINISHED
                race.state == RaceState.OPEN -> RaceHudPayload.STATE_OPEN
                race.state == RaceState.COUNTDOWN -> RaceHudPayload.STATE_COUNTDOWN
                else -> RaceHudPayload.STATE_RUNNING
            }
            val none = RaceHudPayload.NONE
            ServerNetworking.send(
                player,
                RaceHudPayload(
                    active = true,
                    state = state,
                    trackName = race.track.nome,
                    laps = race.laps,
                    currentLap = p?.currentLap ?: 1,
                    checkpointCount = race.track.checkpoints.size,
                    nextGate = p?.nextGate ?: 0,
                    raceMs = race.raceMs(now),
                    lapStartMs = p?.lapStartMs ?: 0,
                    lastLapMs = p?.lapTimes?.lastOrNull() ?: none,
                    bestLapMs = p?.bestLapMs ?: none,
                    fastestLapMs = race.fastestLapMs ?: none,
                    deltaMs = p?.lastDeltaMs ?: none,
                    finishMs = p?.finishedAtMs ?: none,
                    idleKickIn = idleKickIn,
                    countdownTicks = if (race.state == RaceState.COUNTDOWN) (race.goTick - now).toInt().coerceAtLeast(0) else 0,
                    standings = views,
                ),
            )
        }
    }

    fun gateViews(track: TrackData): List<GateView> {
        val list = mutableListOf<GateView>()
        track.linha?.let { list += view(-1, it) }
        track.checkpoints.forEachIndexed { i, g -> list += view(i, g) }
        return list
    }

    private fun view(index: Int, g: GateData): GateView {
        val s = g.shape()
        return GateView(index, s.ax, s.az, s.bx, s.bz, s.minY, s.maxY)
    }

    private fun trackPayload(track: TrackData) = RaceTrackPayload(track.nome, gateViews(track))

    /** Pista com corrida em andamento não pode ser editada (os portões mudariam no meio). */
    fun isTrackBusy(track: TrackData): Boolean = races.containsKey(track.key)

    /** UUIDs de todos os Porytronz de corrida ativos (para limpar os que sobraram de uma queda do servidor). */
    fun activeRentalIds(): Set<UUID> = races.values.flatMap { r -> r.racers.values.mapNotNull { if (it.out) null else it.rentalUuid } }.toSet()
}
