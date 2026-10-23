package com.damezame97.white_lily.recipebook;

import com.damezame97.white_lily.mixin.research.ResearchResultSlotAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.crafting.Recipe;
import javax.annotation.Nullable;

/** Permission checks for explicitly identified recipe-result slots only. Inventory/storage slots are untouched. */
public final class recipe_menu_gate {
    public static ResourceLocation operation(String name) { return ResourceLocation.fromNamespaceAndPath("white_lily","operation/"+name); }
    @Nullable public static Player owner(AbstractContainerMenu menu) {
        for(Slot slot:menu.slots) if(slot.container instanceof Inventory inventory) return inventory.player;
        return null;
    }
    public static boolean canTake(AbstractContainerMenu menu,int index,Player player) {
        return index<0 || index>=menu.slots.size() || canTake(menu,menu.getSlot(index),player);
    }
    public static boolean canTake(AbstractContainerMenu menu,Slot slot,Player player) {
        // Client clicks are predictions; the server re-checks every result slot. Never block locally from a stale snapshot.
        if(player==null || player.level().isClientSide) return true;
        if(slot.container instanceof Inventory || !slot.hasItem()) return true;
        if(slot instanceof ResearchResultSlotAccess)
            return com.damezame97.white_lily.research.research_crafting_gate.canTake(slot,player);
        if(menu instanceof AbstractFurnaceMenu && slot.index==2) {
            if(slot.container instanceof net.minecraft.world.level.block.entity.BlockEntity machine) {
                ResourceLocation last=ResourceLocation.tryParse(machine.getPersistentData().getString("white_lily_last_recipe"));
                if(last!=null) return recipe_unlocks.isUnlocked(player,last);
            }
            return player.level().getRecipeManager().getRecipes().stream()
                .filter(recipe->recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe)
                .filter(recipe->net.minecraft.world.item.ItemStack.isSameItemSameTags(recipe.getResultItem(player.level().registryAccess()),slot.getItem()))
                .anyMatch(recipe->recipe_unlocks.isUnlocked(player,recipe.getId()));
        }
        if(slot.container instanceof ResultContainer && slot.container instanceof RecipeHolder holder) {
            Recipe<?> recipe=holder.getRecipeUsed();
            if(recipe!=null) return recipe_unlocks.isUnlocked(player,recipe.getId());
        }
        String operation=operationFor(menu,slot);
        return operation==null || recipe_unlocks.isUnlocked(player,operation(operation));
    }
    @Nullable private static String operationFor(AbstractContainerMenu menu,Slot slot) {
        if(menu instanceof AnvilMenu && slot.index==2) return "anvil";
        if(menu instanceof GrindstoneMenu && slot.index==2) return "grindstone";
        if(menu instanceof LoomMenu loom && slot==loom.getResultSlot()) return "loom";
        if(menu instanceof CartographyTableMenu && slot.index==2) return "cartography";
        String name=menu.getClass().getName();
        if(name.equals("io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu") && slot.container instanceof ResultContainer) return "arcane_anvil";
        if(name.equals("io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu") && slot.container instanceof ResultContainer) return "inscription";
        if(name.equals("io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeMenu") && slot.index==39) return "scroll_forge";
        return null;
    }
    private recipe_menu_gate() { }
}
