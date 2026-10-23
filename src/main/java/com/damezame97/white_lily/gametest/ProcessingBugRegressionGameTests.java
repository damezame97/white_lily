package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.processing.processing_block_entity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** 仅覆盖真实操作边界：区块刚加载、首个加工刻尚未运行时的库存变化。 */
@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class ProcessingBugRegressionGameTests
{
    @GameTest(template = "empty")
    public static void restoredProgressCannotSurviveRemovingRequiredInputsBeforeFirstTick(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            BlockPos relative = new BlockPos(1, 2, 1);
            h.setBlock(relative, medical_blocks.SIMPLE_PROCESSING_TABLE.get());
            processing_block_entity original = (processing_block_entity) h.getBlockEntity(relative);
            original.authorizeInput(ProcessingTestPlayers.create(h));
            original.getInventory().setStackInSlot(0, new ItemStack(items.STONE_MORTAR.get()));
            original.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 2));
            for (int tick = 0; tick < 17; tick++) tick(original);
            CompoundTag saved = original.saveWithFullMetadata();

            processing_block_entity restored = restore(original, saved);
            ItemStack removed = restored.getInventory().extractItem(1, 2, false);
            yes(h, removed.getCount() == 2 && restored.getData().get(0) == 0,
                    "加载后首刻前取走必要原料，也必须立即清空17刻进度");
            restored.getInventory().setStackInSlot(1, removed);
            tick(restored);
            yes(h, restored.getData().get(0) == 1 && restored.getInventory().getStackInSlot(5).isEmpty(),
                    "同刻放回原料只能重新开始，不能恢复旧进度");

            processing_block_entity addition = restore(original, saved);
            addition.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 5));
            yes(h, addition.getData().get(0) == 17, "加载后增加同种有效原料仍保留原有进度");
            tick(addition);
            yes(h, addition.getData().get(0) == 18, "有效原料增加后从已保存的进度继续");
            h.succeed();
        }
    }

    private static processing_block_entity restore(processing_block_entity original, CompoundTag saved)
    {
        processing_block_entity restored = new processing_block_entity(original.getBlockPos(), original.getBlockState());
        restored.load(saved.copy());
        restored.setLevel(original.getLevel());
        return restored;
    }

    private static void tick(processing_block_entity table)
    {
        processing_block_entity.serverTick(table.getLevel(), table.getBlockPos(), table.getBlockState(), table);
    }
}
