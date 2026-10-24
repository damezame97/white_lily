package com.damezame97.white_lily.recipebook;

import com.damezame97.white_lily.mixin.research.ResearchResultSlotAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Selection belongs to a menu instance, never a recipe manager, output item, or global crafting player. */
@Mod.EventBusSubscriber(modid="white_lily")
public final class crafting_choice {
    public record Candidate(ResourceLocation id,ItemStack output,boolean shapeless,List<ItemStack> returned) { }
    public record View(int menu,long session,long version,int width,int height,List<ItemStack> inputs,
                       List<Candidate> candidates,ResourceLocation selected,int request,transfer_reason reason) { }
    private static final Map<AbstractContainerMenu,State> STATES=new WeakHashMap<>();
    private static long sequence,epoch;
    private static final class State {
        long session,version,epoch=-1;int lastRequest,consuming,actionDepth;boolean shift;
        ResourceLocation selected,locked,consumed;
        List<ItemStack> inputs=List.of();Set<ResourceLocation> rights=Set.of();List<Candidate> candidates=List.of();
        net.minecraft.world.item.crafting.RecipeManager manager;
    }
    // Special third-party menus may override result calculation/consumption; keep their existing gates untouched.
    public static boolean supports(AbstractContainerMenu menu){return menu!=null&&(menu.getClass()==CraftingMenu.class||menu.getClass()==InventoryMenu.class);}
    public static CraftingContainer grid(AbstractContainerMenu menu){return supports(menu)&&!menu.slots.isEmpty()&&menu.getSlot(0) instanceof ResearchResultSlotAccess a?a.whiteLily$researchGrid():null;}
    private static State state(AbstractContainerMenu menu){return STATES.computeIfAbsent(menu,m->new State());}
    private static List<ItemStack> copy(CraftingContainer grid){List<ItemStack> result=new ArrayList<>();for(int i=0;i<grid.getContainerSize();i++)result.add(grid.getItem(i).copy());return List.copyOf(result);}
    private static boolean same(List<ItemStack> a,List<ItemStack> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}
    private static boolean permitted(Player player,CraftingRecipe recipe,CraftingContainer grid){
        return recipe.canCraftInDimensions(grid.getWidth(),grid.getHeight())&&recipe_unlocks.isUnlocked(player,recipe.getId())
                && (recipe.isSpecial()||!player.level().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)||player instanceof ServerPlayer sp&&sp.getRecipeBook().contains(recipe))
                &&recipe.matches(grid,player.level());
    }
    private static CraftingRecipe valid(Player player,AbstractContainerMenu menu,ResourceLocation id){
        var grid=grid(menu);if(id==null||grid==null)return null;
        var recipe=player.level().getRecipeManager().byKey(id).orElse(null);
        return recipe instanceof CraftingRecipe craft&&permitted(player,craft,grid)?craft:null;
    }
    /** Called by the real vanilla slotsChanged path and permission/reload refreshes. */
    public static void update(AbstractContainerMenu menu,Player player){
        if(player==null||player.level().isClientSide||!supports(menu)||grid(menu)==null||player instanceof ServerPlayer sp&&sp.getRecipeBook()==null)return;
        State s=state(menu);if(s.consuming>0)return;
        CraftingContainer grid=grid(menu);var inputs=copy(grid);var rights=recipe_unlocks.unlocked(player);var manager=player.level().getRecipeManager();
        if(player.level().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)&&player instanceof ServerPlayer sp){Set<ResourceLocation> filtered=new HashSet<>();for(var id:rights){var recipe=manager.byKey(id).orElse(null);if(recipe!=null&&(recipe.isSpecial()||sp.getRecipeBook().contains(recipe)))filtered.add(id);}rights=Set.copyOf(filtered);}
        boolean changed=s.manager!=manager||s.epoch!=epoch||!same(s.inputs,inputs)||!s.rights.equals(rights);
        if(changed){
            List<Candidate> candidates=new ArrayList<>();
            // All effective recipe IDs are considered, including research grants. Sort once per input/rights change.
            for(ResourceLocation id:rights.stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()){
                var found=manager.byKey(id).orElse(null);if(!(found instanceof CraftingRecipe recipe))continue;
                if(!permitted(player,recipe,grid))continue;
                ItemStack output=recipe.assemble(grid,player.level().registryAccess());
                if(output.isEmpty()||!output.isItemEnabled(player.level().enabledFeatures()))continue;
                // These APIs are previews; consumption and achievements remain in vanilla ResultSlot.
                List<ItemStack> returned=recipe.getRemainingItems(grid).stream().map(ItemStack::copy).toList();
                candidates.add(new Candidate(recipe.getId(),output.copy(),!(recipe instanceof net.minecraftforge.common.crafting.IShapedRecipe<?>),returned));
            }
            s.candidates=List.copyOf(candidates);s.inputs=inputs;s.rights=rights;s.manager=manager;s.epoch=epoch;s.version=++sequence;
        }
        ResourceLocation old=s.selected;
        if(s.shift)s.selected=s.candidates.stream().anyMatch(c->c.id().equals(s.locked))?s.locked:null;
        else if(s.candidates.stream().noneMatch(c->c.id().equals(s.selected)))s.selected=s.candidates.isEmpty()?null:s.candidates.get(0).id();
        var result=(ResultContainer)menu.getSlot(0).container;
        CraftingRecipe chosen=valid(player,menu,s.selected);ItemStack output=chosen==null?ItemStack.EMPTY:chosen.assemble(grid,player.level().registryAccess());
        boolean resultChanged=!ItemStack.matches(result.getItem(0),output);
        result.setRecipeUsed(chosen);result.setItem(0,output);menu.setRemoteSlot(0,output);
        if(player instanceof ServerPlayer sp&&sp.connection!=null&&player.containerMenu==menu){
            if(changed||resultChanged||!Objects.equals(old,s.selected))sp.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId,menu.incrementStateId(),0,output));
            if(s.session!=0&&(changed||!Objects.equals(old,s.selected)))crafting_choice_network.send(sp,view(menu,player,0,transfer_reason.OK));
        }
    }
    public static View view(AbstractContainerMenu menu,Player player,int request,transfer_reason reason){
        update(menu,player);State s=state(menu);CraftingContainer grid=grid(menu);
        return new View(menu.containerId,s.session,s.version,grid.getWidth(),grid.getHeight(),s.inputs,s.candidates,s.selected,request,reason);
    }
    public static View open(ServerPlayer player,int menu){
        if(player.containerMenu.containerId!=menu||!supports(player.containerMenu)||!player.isAlive()||!player.containerMenu.stillValid(player))return null;
        State s=state(player.containerMenu);s.session=++sequence;s.lastRequest=0;
        s.epoch=-1;
        return view(player.containerMenu,player,0,transfer_reason.OK);
    }
    public static transfer_reason select(ServerPlayer player,int menu,long session,long version,int request,ResourceLocation id){
        var current=player.containerMenu;if(!supports(current)||current.containerId!=menu||!current.stillValid(player)||!player.isAlive())return transfer_reason.MENU_INVALID;
        update(current,player);State s=state(current);
        if(session==0||s.session!=session||s.version!=version||request<=s.lastRequest)return transfer_reason.DATA_CHANGED;
        s.lastRequest=request;
        if(s.candidates.stream().noneMatch(c->c.id().equals(id))||valid(player,current,id)==null)return transfer_reason.NOT_AUTHORIZED;
        s.selected=id;update(current,player);current.broadcastChanges();return transfer_reason.OK;
    }
    /** Browser fill is an explicit selection; no client-provided permissions are trusted. */
    public static boolean selectFilled(Player player,AbstractContainerMenu menu,ResourceLocation id){
        update(menu,player);if(valid(player,menu,id)==null)return false;state(menu).selected=id;update(menu,player);
        if(player instanceof ServerPlayer sp&&state(menu).session!=0)crafting_choice_network.send(sp,view(menu,player,0,transfer_reason.OK));return true;
    }
    public static boolean canTake(AbstractContainerMenu menu,Player player){
        if(!supports(menu))return true;
        if(player.level().isClientSide)return net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.damezame97.white_lily.client.crafting_choice_client.canPredict(menu));
        // Vanilla's network click handler checks active menu and distance before invoking slots.
        // Do not impose network-only lifecycle checks on direct vanilla menu API calls.
        if(!player.isAlive())return false;
        State s=state(menu);CraftingRecipe recipe=valid(player,menu,s.selected);if(recipe==null||s.shift&&!Objects.equals(s.locked,s.selected))return false;
        ItemStack expected=recipe.assemble(grid(menu),player.level().registryAccess());
        return !expected.isEmpty()&&ItemStack.matches(expected,menu.getSlot(0).getItem());
    }
    public static boolean canQuickMove(AbstractContainerMenu menu,Player player){
        if(player.level().isClientSide||!supports(menu))return true;if(!canTake(menu,player))return false;
        ItemStack rest=menu.getSlot(0).getItem().copy();
        for(int i=0;i<36&&!rest.isEmpty();i++){ItemStack slot=player.getInventory().getItem(i);if(slot.isEmpty())rest.shrink(Math.min(rest.getMaxStackSize(),player.getInventory().getMaxStackSize()));else if(ItemStack.isSameItemSameTags(slot,rest))rest.shrink(Math.max(0,Math.min(slot.getMaxStackSize(),player.getInventory().getMaxStackSize())-slot.getCount()));}
        return rest.isEmpty();
    }
    public static void beginAction(AbstractContainerMenu menu,Player player,int slot,ClickType action){
        if(player.level().isClientSide||!supports(menu))return;State s=state(menu);if(s.actionDepth++==0&&slot==0&&action==ClickType.QUICK_MOVE){s.shift=true;s.locked=s.selected;}
    }
    public static void endAction(AbstractContainerMenu menu,Player player){
        if(player.level().isClientSide||!supports(menu))return;State s=state(menu);if(--s.actionDepth<=0){s.actionDepth=0;s.shift=false;s.locked=null;s.consuming=0;update(menu,player);}
    }
    public static CraftingRecipe beginConsume(AbstractContainerMenu menu,Player player){
        if(player.level().isClientSide||!supports(menu))return null;State s=state(menu);var recipe=valid(player,menu,s.selected);s.consuming++;s.consumed=recipe==null?null:recipe.getId();return recipe;
    }
    public static void endConsume(AbstractContainerMenu menu,Player player){if(player.level().isClientSide||!supports(menu))return;State s=state(menu);s.consuming=Math.max(0,s.consuming-1);update(menu,player);}
    public static void close(AbstractContainerMenu menu){STATES.remove(menu);}
    @SubscribeEvent public static void closed(PlayerContainerEvent.Close event){close(event.getContainer());}
    public static void invalidateRecipes(){epoch++;}
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event){invalidateRecipes();}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer p&&p.tickCount%10==0&&supports(p.containerMenu))update(p.containerMenu,p);}
    private crafting_choice() { }
}
