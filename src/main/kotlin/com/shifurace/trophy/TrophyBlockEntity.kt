package com.shifurace.trophy

import com.shifurace.registry.ModBlockEntities
import com.shifurace.registry.ModComponents
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** Guarda as informações do troféu quando ele é colocado no mundo (e devolve ao quebrar). */
class TrophyBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlockEntities.TROPHY, pos, state) {

    var info: TrophyInfo? = null
        private set

    override fun applyImplicitComponents(input: DataComponentInput) {
        info = input.get(ModComponents.TROPHY_INFO)
    }

    override fun collectImplicitComponents(builder: DataComponentMap.Builder) {
        info?.let { builder.set(ModComponents.TROPHY_INFO, it) }
    }

    @Deprecated("Deprecated in Java")
    override fun removeComponentsFromTag(tag: CompoundTag) {
        tag.remove(TAG)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        info?.let { value ->
            TrophyInfo.CODEC.encodeStart(NbtOps.INSTANCE, value).result().ifPresent { tag.put(TAG, it) }
        }
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        val before = info?.color
        info = if (tag.contains(TAG)) {
            TrophyInfo.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG)).result().orElse(null)
        } else {
            null
        }
        // No cliente, a cor da taça vem do bloco: redesenha quando os dados chegam
        val lvl = level
        if (lvl != null && lvl.isClientSide && before != info?.color) lvl.sendBlockUpdated(blockPos, blockState, blockState, 8)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)

    companion object {
        private const val TAG = "trophy_info"
    }
}
