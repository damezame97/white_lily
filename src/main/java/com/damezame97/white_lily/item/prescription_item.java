package com.damezame97.white_lily.item;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** 普通处方材料；只有空白处方的掉落物会在连续接触水 40 刻后显字。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
// Level 由游戏引擎持有；这里只借用，不得关闭世界。
@SuppressWarnings("resource")
public class prescription_item extends Item
{
    public static final int SOAK_TICKS = 40;
    private static final String WATER_TICKS = "white_lily:prescription_water_ticks";

    public prescription_item()
    {
        super(new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity)
    {
        if (entity.level().isClientSide || !stack.is(items.BLANK_PRESCRIPTION.get()))
        {
            return false;
        }

        CompoundTag data = entity.getPersistentData();
        if (!touchesWater(entity))
        {
            data.remove(WATER_TICKS);
            return false;
        }

        int ticks = data.getInt(WATER_TICKS) + 1;
        if (ticks >= SOAK_TICKS)
        {
            ItemStack revealed = new ItemStack(items.REVEALED_PRESCRIPTION.get(), stack.getCount());
            CompoundTag originalTag = stack.getTag();
            if (originalTag != null)
            {
                revealed.setTag(originalTag.copy());
            }
            // 原地替换栈，保留同一个实体的坐标、速度、拾取延迟、寿命和归属。
            entity.setItem(revealed);
            data.remove(WATER_TICKS);
        }
        else
        {
            data.putInt(WATER_TICKS, ticks);
        }
        // 继续执行原版掉落物更新，让移动、合并、玩家与漏斗拾取正常工作。
        return false;
    }

    /** 检查实体包围盒实际接触的流体，不依赖上一个 tick 的 isInWater 缓存。 */
    private static boolean touchesWater(ItemEntity entity)
    {
        AABB bounds = entity.getBoundingBox().deflate(0.001D);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
                Mth.floor(bounds.maxX), Mth.floor(bounds.maxY), Mth.floor(bounds.maxZ)))
        {
            FluidState fluid = entity.level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER)
                    && bounds.minY < pos.getY() + fluid.getHeight(entity.level(), pos))
            {
                return true;
            }
        }
        return false;
    }
}
