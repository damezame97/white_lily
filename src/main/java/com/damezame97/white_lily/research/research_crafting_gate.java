package com.damezame97.white_lily.research;

import com.damezame97.white_lily.crafting.research_crafting_recipe;
import com.damezame97.white_lily.mixin.research.ResearchCraftingContainerAccess;
import com.damezame97.white_lily.mixin.research.ResearchResultSlotAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/** 不使用全局“当前合成玩家”；从真正拥有网格的结果槽取得玩家，避免多人权限串用。 */
public final class research_crafting_gate
{
    @Nullable public static Player owner(CraftingContainer grid)
    {
        if (!(grid instanceof ResearchCraftingContainerAccess access)) return null;
        AbstractContainerMenu menu = access.whiteLily$researchMenu();
        return !menu.slots.isEmpty() && menu.getSlot(0) instanceof ResearchResultSlotAccess result
                && result.whiteLily$researchGrid() == grid ? result.whiteLily$researchPlayer() : null;
    }

    public static boolean allowed(CraftingContainer grid, ResourceLocation recipe)
    {
        Player player = owner(grid);
        if (player == null) return false;
        return permitted(player, recipe);
    }

    /** 研究型配方必须绑定有效节点；配置加载失败时不能退化为普通免费配方。 */
    public static boolean permitted(Player player, ResourceLocation recipe)
    {
        if (player == null || recipe == null) return false;
        if (!player.level().isClientSide)
            return research_catalog.recipeOwners().containsKey(recipe) && research_service.canCraft(player, recipe);
        AtomicBoolean allowed = new AtomicBoolean(false);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                allowed.set(com.damezame97.white_lily.client.research_client.canCraft(recipe)));
        return allowed.get();
    }

    /** 取结果前再次校验，覆盖过期结果和直接Shift转移；不等到已经转移后的onTake。 */
    public static boolean canTake(Slot slot, Player player)
    {
        // Client-side prediction must not desync from the authoritative server check.
        if (player != null && player.level().isClientSide) {
            if(slot instanceof ResearchResultSlotAccess a&&a.whiteLily$researchGrid() instanceof ResearchCraftingContainerAccess g
                    &&com.damezame97.white_lily.recipebook.crafting_choice.supports(g.whiteLily$researchMenu()))
                return com.damezame97.white_lily.recipebook.crafting_choice.canTake(g.whiteLily$researchMenu(),player);
            return true;
        }
        if (!(slot instanceof ResearchResultSlotAccess access)) return true;
        if(access.whiteLily$researchGrid() instanceof ResearchCraftingContainerAccess gridAccess
                &&com.damezame97.white_lily.recipebook.crafting_choice.supports(gridAccess.whiteLily$researchMenu()))
            return access.whiteLily$researchPlayer()==player&&com.damezame97.white_lily.recipebook.crafting_choice.canTake(gridAccess.whiteLily$researchMenu(),player);
        if (!(slot.container instanceof RecipeHolder holder)) return true;
        if (access.whiteLily$researchPlayer() != player) return false;
        Recipe<?> used = holder.getRecipeUsed();
        if (used == null) return true;
        if (!com.damezame97.white_lily.recipebook.recipe_unlocks.isUnlocked(player, used.getId())) return false;
        // Research recipes additionally re-check the live grid; normal recipes are identified by the recorded recipe.
        if (used instanceof research_crafting_recipe recipe)
        {
            CraftingContainer grid = access.whiteLily$researchGrid();
            return grid != null && recipe.matches(grid, player.level());
        }
        return true;
    }

    /** 完成研究后即刻刷新已摆好的网格，避免必须重新放料或重新登录。 */
    public static void refresh(Player player)
    {
        if (player.level().isClientSide || player instanceof ServerPlayer server && server.connection == null) return;
        player.inventoryMenu.slotsChanged(player.inventoryMenu.getCraftSlots());
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != player.inventoryMenu && menu instanceof CraftingMenu && menu.slots.size() > 1
                && menu.getSlot(1).container instanceof CraftingContainer grid)
            menu.slotsChanged(grid);
        menu.broadcastChanges();
    }

    private research_crafting_gate() { }
}
