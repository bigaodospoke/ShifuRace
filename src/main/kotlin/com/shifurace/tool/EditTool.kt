package com.shifurace.tool

import com.shifurace.config.ConfigManager
import com.shifurace.data.RaceStorage
import com.shifurace.network.EditViewPayload
import com.shifurace.network.ServerNetworking
import com.shifurace.race.Msg
import com.shifurace.race.RaceManager
import com.shifurace.registry.ModItems
import com.shifurace.track.GateData
import com.shifurace.track.TrackData
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import java.util.UUID

/** Item para montar a pista: clique esquerdo marca o ponto 1, direito o ponto 2; agachado + direito no ar troca o modo. */
class EditToolItem(properties: Properties) : Item(properties) {
    override fun isFoil(stack: ItemStack): Boolean = true

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player
        if (!context.level.isClientSide && player is ServerPlayer) EditTool.secondPoint(player, context.clickedPos)
        return InteractionResult.sidedSuccess(context.level.isClientSide)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!player.isShiftKeyDown) return InteractionResultHolder.pass(stack)
        if (!level.isClientSide && player is ServerPlayer) EditTool.toggleMode(player)
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("item.shifurace.ferramenta.desc1").withStyle(ChatFormatting.GRAY))
        tooltip.add(Component.translatable("item.shifurace.ferramenta.desc2").withStyle(ChatFormatting.GRAY))
        tooltip.add(Component.translatable("item.shifurace.ferramenta.desc3").withStyle(ChatFormatting.GRAY))
    }
}

object EditTool {
    enum class Mode(val label: String) { LINE("LINHA DE LARGADA/CHEGADA"), CHECKPOINT("CHECKPOINT") }

    class Session(var track: String, var mode: Mode, var first: BlockPos? = null, var insertAt: Int? = null, var lastClick: Long = 0)

    private val sessions = hashMapOf<UUID, Session>()

    /** Quem está vendo os portões de qual pista. */
    private val viewers = hashMapOf<UUID, String>()

    fun register() {
        // Clique esquerdo com a ferramenta: marca o ponto 1 (e não quebra o bloco)
        AttackBlockCallback.EVENT.register { player, world, hand, pos, _ ->
            if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).`is`(ModItems.TOOL)) return@register InteractionResult.PASS
            if (!world.isClientSide && player is ServerPlayer) firstPoint(player, pos)
            InteractionResult.SUCCESS
        }
    }

    fun start(player: ServerPlayer, track: TrackData) {
        val session = sessions.getOrPut(player.uuid) { Session(track.key, if (track.linha == null) Mode.LINE else Mode.CHECKPOINT) }
        session.track = track.key
        session.first = null
        session.insertAt = null
        session.mode = if (track.linha == null) Mode.LINE else Mode.CHECKPOINT
        if (!player.inventory.contains(ItemStack(ModItems.TOOL))) {
            val stack = ItemStack(ModItems.TOOL)
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
        setViewing(player, track, true)
        Msg.send(player, "Editando a pista ${track.nome}. Clique esquerdo num bloco de um lado da pista, direito no bloco do outro lado. Agachado + clique direito no ar troca entre linha e checkpoint.")
    }

    fun setInsert(player: ServerPlayer, track: TrackData, index: Int) {
        start(player, track)
        val session = sessions.getValue(player.uuid)
        session.mode = Mode.CHECKPOINT
        session.insertAt = index
        Msg.send(player, "O próximo checkpoint marcado entra como número ${index + 1}.")
    }

    fun toggleMode(player: ServerPlayer) {
        val session = sessions[player.uuid] ?: return noSession(player)
        session.mode = if (session.mode == Mode.LINE) Mode.CHECKPOINT else Mode.LINE
        session.first = null
        player.displayClientMessage(Component.literal("Modo: ${session.mode.label}").withStyle(ChatFormatting.YELLOW), true)
    }

    private fun firstPoint(player: ServerPlayer, pos: BlockPos) {
        val session = sessions[player.uuid] ?: return noSession(player)
        val now = player.server.tickCount.toLong()
        if (now - session.lastClick < 4 && session.first == pos) return
        session.lastClick = now
        session.first = pos.immutable()
        player.displayClientMessage(
            Component.literal("Ponto 1 marcado em ${pos.x} ${pos.y} ${pos.z}. Agora clique direito no outro lado da pista.").withStyle(ChatFormatting.GREEN),
            true,
        )
    }

    fun secondPoint(player: ServerPlayer, pos: BlockPos) {
        val session = sessions[player.uuid] ?: return noSession(player)
        val track = RaceStorage.track(session.track) ?: return player.sendSystemMessage(Msg.error("A pista não existe mais."))
        val first = session.first ?: return player.displayClientMessage(
            Component.literal("Primeiro marque o ponto 1 com o clique esquerdo.").withStyle(ChatFormatting.RED), true,
        )
        if (RaceManager.isTrackBusy(track)) return player.sendSystemMessage(Msg.error("Há uma corrida nessa pista; espere ela acabar para editar."))
        val dimension = player.level().dimension().location().toString()
        val hasGates = track.linha != null || track.checkpoints.isNotEmpty()
        if (hasGates && track.dimensao != dimension) return player.sendSystemMessage(Msg.error("A pista fica em ${track.dimensao}; edite lá."))
        if (first == pos) return player.displayClientMessage(Component.literal("Os dois pontos são o mesmo bloco.").withStyle(ChatFormatting.RED), true)
        if (first.distSqr(pos) > 64.0 * 64.0) return player.sendSystemMessage(Msg.error("Os dois pontos estão longe demais (máximo 64 blocos)."))

        track.dimensao = dimension
        val gate = GateData(first.x, first.y, first.z, pos.x, pos.y, pos.z, ConfigManager.config.alturaPortao)
        when (session.mode) {
            Mode.LINE -> {
                // "Para frente" é para onde o staff está olhando ao marcar o ponto 2
                val (nx, nz) = gate.shape().normal()
                val look = player.lookAngle
                track.sentidoLinha = if (look.x * nx + look.z * nz >= 0) 1 else -1
                track.linha = gate
                Msg.send(player, "Linha de largada/chegada marcada! A seta verde mostra o sentido da corrida (use /corrida linha ${track.nome} inverter se estiver ao contrário).")
                session.mode = Mode.CHECKPOINT
            }
            Mode.CHECKPOINT -> {
                val index = (session.insertAt ?: track.checkpoints.size).coerceIn(0, track.checkpoints.size)
                track.checkpoints.add(index, gate)
                session.insertAt = session.insertAt?.let { it + 1 }
                Msg.send(player, "Checkpoint ${index + 1} marcado (${track.checkpoints.size} no total).")
            }
        }
        session.first = null
        RaceStorage.saveTracks()
        refreshViewers(player.server, track)
    }

    private fun noSession(player: ServerPlayer) {
        player.displayClientMessage(Component.literal("Use /corrida ferramenta <pista> para escolher a pista.").withStyle(ChatFormatting.RED), true)
    }

    fun setViewing(player: ServerPlayer, track: TrackData, on: Boolean) {
        if (on) {
            viewers[player.uuid] = track.key
            ServerNetworking.send(player, EditViewPayload(track.nome, track.sentidoLinha, RaceManager.gateViews(track)))
        } else {
            viewers.remove(player.uuid)
            ServerNetworking.send(player, EditViewPayload.OFF)
        }
    }

    fun isViewing(player: ServerPlayer, track: TrackData): Boolean = viewers[player.uuid] == track.key

    fun refreshViewers(server: MinecraftServer, track: TrackData) {
        viewers.filterValues { it == track.key }.keys.forEach { id ->
            server.playerList.getPlayer(id)?.let { ServerNetworking.send(it, EditViewPayload(track.nome, track.sentidoLinha, RaceManager.gateViews(track))) }
        }
    }

    fun onTrackRemoved(server: MinecraftServer, key: String) {
        viewers.filterValues { it == key }.keys.toList().forEach { id ->
            viewers.remove(id)
            server.playerList.getPlayer(id)?.let { ServerNetworking.send(it, EditViewPayload.OFF) }
        }
        sessions.values.removeIf { it.track == key }
    }

    fun onDisconnect(player: ServerPlayer) {
        sessions.remove(player.uuid)
        viewers.remove(player.uuid)
    }

    /** Barra de ação com o modo atual para quem está segurando a ferramenta. */
    fun tick(server: MinecraftServer) {
        if (server.tickCount % 10 != 0) return
        for ((id, session) in sessions) {
            val player = server.playerList.getPlayer(id) ?: continue
            if (!player.mainHandItem.`is`(ModItems.TOOL)) continue
            val track = RaceStorage.track(session.track) ?: continue
            val step = if (session.first == null) "clique esquerdo: ponto 1" else "clique direito: ponto 2"
            val number = if (session.mode == Mode.CHECKPOINT) " #${(session.insertAt ?: track.checkpoints.size) + 1}" else ""
            player.displayClientMessage(
                Component.literal("${track.nome} · ${session.mode.label}$number · $step").withStyle(ChatFormatting.AQUA),
                true,
            )
        }
    }
}
