package com.shifurace.registry

import com.shifurace.ShifuRace
import com.shifurace.tool.EditToolItem
import com.shifurace.trophy.TrophyBlock
import com.shifurace.trophy.TrophyBlockEntity
import com.shifurace.trophy.TrophyInfo
import com.shifurace.trophy.TrophyItem
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

object ModComponents {
    lateinit var TROPHY_INFO: DataComponentType<TrophyInfo>
        private set

    fun register() {
        TROPHY_INFO = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ShifuRace.id("trophy_info"),
            DataComponentType.builder<TrophyInfo>()
                .persistent(TrophyInfo.CODEC)
                .networkSynchronized(TrophyInfo.STREAM_CODEC)
                .build(),
        )
    }
}

object ModBlocks {
    lateinit var TROPHY: TrophyBlock
        private set

    fun register() {
        TROPHY = Registry.register(
            BuiltInRegistries.BLOCK,
            ShifuRace.id("trofeu"),
            TrophyBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(2.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .lightLevel { 5 }
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK),
            ),
        )
    }
}

object ModBlockEntities {
    lateinit var TROPHY: BlockEntityType<TrophyBlockEntity>
        private set

    fun register() {
        TROPHY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ShifuRace.id("trofeu"),
            BlockEntityType.Builder.of(::TrophyBlockEntity, ModBlocks.TROPHY).build(null),
        )
    }
}

object ModItems {
    lateinit var TROPHY: TrophyItem
        private set
    lateinit var TOOL: EditToolItem
        private set

    fun register() {
        TROPHY = Registry.register(
            BuiltInRegistries.ITEM,
            ShifuRace.id("trofeu"),
            TrophyItem(ModBlocks.TROPHY, Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()),
        )
        TOOL = Registry.register(
            BuiltInRegistries.ITEM,
            ShifuRace.id("ferramenta"),
            EditToolItem(Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
        )

        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ShifuRace.id("aba"),
            FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.shifurace"))
                .icon { ItemStack(TROPHY) }
                .displayItems { _, output ->
                    output.accept(TROPHY)
                    output.accept(TOOL)
                }
                .build(),
        )
    }
}
