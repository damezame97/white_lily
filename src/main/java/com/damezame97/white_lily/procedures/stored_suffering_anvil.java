package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.white_lily;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 明确区分单件重命名与维修/合并/附魔；不开放512次触发限制的维修旁路。 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class stored_suffering_anvil
{
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void update(AnvilUpdateEvent event)
    {
        if (!event.getLeft().is(items.STORED_SUFFERING.get())) return;
        if (!event.getRight().isEmpty()) { reject(event); return; }
        var original = event.getLeft();
        String name = event.getName();
        if ((name == null || Util.isBlank(name)) ? !original.hasCustomHoverName()
                : name.equals(original.getHoverName().getString()))
        { reject(event); return; }
        var renamed = original.copy();
        if (name == null || Util.isBlank(name)) renamed.resetHoverName();
        else renamed.setHoverName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
        event.setOutput(renamed);
        event.setCost((int)Math.min(39L, Math.max(0L, original.getBaseRepairCost()) + 1));
        event.setMaterialCost(0);
    }
    private static void reject(AnvilUpdateEvent event)
    {
        // Forge 的取消事件会直接离开 createResult，并不清除上一次的输出。
        // 对当前实际铁砧显式清空，防止先改名再放第二件物品留下可取的旧结果。
        if (event.getPlayer().containerMenu instanceof AnvilMenu menu
                && menu.getSlot(0).getItem() == event.getLeft())
        {
            menu.getSlot(2).set(ItemStack.EMPTY);
            menu.setMaximumCost(0);
            menu.repairItemCountCost = 0;
        }
        event.setCanceled(true);
    }
    private stored_suffering_anvil() { }
}
