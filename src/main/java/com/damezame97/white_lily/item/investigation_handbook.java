package com.damezame97.white_lily.item;

import com.damezame97.white_lily.research.research_service;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** 手册只是入口；权威研究进度属于玩家能力，不写入物品NBT。 */
public final class investigation_handbook extends basic_material_item
{
    public investigation_handbook()
    {
        super(new Properties().stacksTo(1), 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        if (level.isClientSide)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.damezame97.white_lily.client.research_client.prepareItemOpen());
        }
        else if (player instanceof ServerPlayer serverPlayer)
        {
            research_service.refresh(serverPlayer, true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
