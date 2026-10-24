package com.damezame97.white_lily.recipebook;

import com.damezame97.white_lily.mixin.research.ResearchResultSlotAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import javax.annotation.Nullable;

/**
 * 只在明确属于解锁范围的结果槽上检查权限。
 *
 * <p>范围是原版工作台、原版四格合成与 {@code white_lily:simple_processing_table}；
 * 其余工作方块（熔炉系、切石机、铁砧、织布机、砂轮、制图台、酿造台、附魔台、
 * ISS 工作台、枪械台、葡萄酒机器……）与原版一致，一律放行。</p>
 */
public final class recipe_menu_gate {
    public static ResourceLocation operation(String name) { return ResourceLocation.fromNamespaceAndPath("white_lily","operation/"+name); }
    @Nullable public static Player owner(AbstractContainerMenu menu) {        for(Slot slot:menu.slots) if(slot.container instanceof Inventory inventory) return inventory.player;
        return null;
    }
    /**
     * 某条配方现在能不能用：范围外一律放行，范围内要求已解锁。
     *
     * <p>供没有菜单（例如方块实体）或只有操作 ID 的门控共用。</p>
     */
    public static boolean permitted(Player player,ResourceLocation id) {
        return player==null || !recipe_unlocks.requiresUnlock(player,id) || recipe_unlocks.isUnlocked(player,id);
    }
    public static boolean canTake(AbstractContainerMenu menu,int index,Player player) {
        return index<0 || index>=menu.slots.size() || canTake(menu,menu.getSlot(index),player);
    }
    public static boolean canTake(AbstractContainerMenu menu,Slot slot,Player player) {
        // 客户端点击只是预测，服务端会重算；本地永远不用陈旧快照拦。
        if(player==null || player.level().isClientSide) return true;
        if(slot.container instanceof Inventory || !slot.hasItem()) return true;
        if(!recipe_unlocks.gatedMenu(menu)) return true;
        if(slot instanceof ResearchResultSlotAccess)
            return com.damezame97.white_lily.research.research_crafting_gate.canTake(slot,player);
        return true;
    }
    private recipe_menu_gate() { }
}
