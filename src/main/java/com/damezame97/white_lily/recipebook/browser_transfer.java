package com.damezame97.white_lily.recipebook;

import com.damezame97.white_lily.processing.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Browser feedback wraps the existing machine planner and vanilla recipe placement, not new crafting. */
public final class browser_transfer
{
    @SuppressWarnings({"unchecked","rawtypes"})
    public static String fingerprint(Recipe<?> recipe)
    {
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try
        {
            ((RecipeSerializer)recipe.getSerializer()).toNetwork(buffer,recipe);
            byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(0,bytes);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch(Exception exception){return "";}
        finally{buffer.release();}
    }
    public static transfer_reason preview(Player player,Recipe<?> recipe)
    {
        if(player==null || !player.isAlive() || !player.containerMenu.stillValid(player))return transfer_reason.MENU_INVALID;
        if(recipe==null)return transfer_reason.DATA_CHANGED;
        var menu=player.containerMenu;
        if(recipe instanceof processing_recipe)
            return menu instanceof processing_menu table?table.previewRecipe(player,recipe.getId()):transfer_reason.WRONG_DEVICE;
        if(!(recipe instanceof CraftingRecipe) || recipe.isSpecial())return transfer_reason.UNSUPPORTED;
        if(!(menu instanceof CraftingMenu) && !(menu instanceof InventoryMenu))return transfer_reason.WRONG_DEVICE;
        if(!recipe_unlocks.isUnlocked(player,recipe.getId()))return transfer_reason.NOT_AUTHORIZED;
        RecipeBookMenu<?> book=(RecipeBookMenu<?>)menu;
        if(!recipe.canCraftInDimensions(book.getGridWidth(),book.getGridHeight()))return transfer_reason.WRONG_DEVICE;
        List<ItemStack> recovered=new ArrayList<>();for(int i=0;i<36;i++)recovered.add(player.getInventory().getItem(i).copy());
        for(int i=0;i<book.getSize();i++)if(book.shouldMoveToInventory(i)&&!recover(recovered,book.getSlot(i).getItem()))return transfer_reason.INVENTORY_FULL;
        var contents=new net.minecraft.world.entity.player.StackedContents();
        player.getInventory().fillStackedContents(contents);book.fillCraftSlotsStackedContents(contents);
        return contents.canCraft(recipe,null)?transfer_reason.OK:transfer_reason.MISSING_MATERIALS;
    }
    private static boolean recover(List<ItemStack> inventory,ItemStack original)
    {
        var rest=original.copy();
        for(var stack:inventory)if(!stack.isEmpty()&&ItemStack.isSameItemSameTags(stack,rest))
        {int n=Math.min(rest.getCount(),Math.max(0,stack.getMaxStackSize()-stack.getCount()));stack.grow(n);rest.shrink(n);}
        for(int i=0;i<inventory.size()&&!rest.isEmpty();i++)if(inventory.get(i).isEmpty())
        {int n=Math.min(rest.getCount(),rest.getMaxStackSize());inventory.set(i,rest.copyWithCount(n));rest.shrink(n);}
        return rest.isEmpty();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static transfer_reason apply(ServerPlayer player,int container,ResourceLocation id,String fingerprint,boolean maximum)
    {
        if(player.containerMenu.containerId!=container || !player.isAlive() || !player.containerMenu.stillValid(player))return transfer_reason.MENU_INVALID;
        Recipe<?> recipe=player.level().getRecipeManager().byKey(id).orElse(null);
        if(recipe==null || !fingerprint(recipe).equals(fingerprint))return transfer_reason.DATA_CHANGED;
        transfer_reason reason=preview(player,recipe);if(reason!=transfer_reason.OK)return reason;
        if(player.containerMenu instanceof processing_menu table)return table.transferRecipe(player,id,maximum,true);
        player.awardRecipes(List.of(recipe));
        new net.minecraft.recipebook.ServerPlaceRecipe((RecipeBookMenu)player.containerMenu).recipeClicked(player,recipe,maximum);
        if(!crafting_choice.selectFilled(player,player.containerMenu,id))return transfer_reason.DATA_CHANGED;
        player.containerMenu.broadcastChanges();return transfer_reason.OK;
    }
    private browser_transfer() { }
}
