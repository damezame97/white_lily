package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily") @PrefixGameTestTemplate(false)
public final class CraftingChoiceGameTests {
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("white_lily","choice_test/"+name);}
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final List<Recipe<?>> original;final ProcessingTestPlayers players;final CraftingRecipe a,b;
        Fixture(GameTestHelper h,boolean sameOutput){this.h=h;players=new ProcessingTestPlayers(h);original=List.copyOf(h.getLevel().getRecipeManager().getRecipes());
            a=recipe("a",Ingredient.of(Items.PAPER,Items.STICK),new ItemStack(Items.DIAMOND),sameOutput?Items.AIR:Items.GLASS_BOTTLE);
            ItemStack result=new ItemStack(sameOutput?Items.DIAMOND:Items.EMERALD,2);result.getOrCreateTag().putString("choice_test","B");
            b=recipe("b",Ingredient.of(Items.PAPER),result,sameOutput?Items.STICK:Items.BOWL);
            var all=new ArrayList<Recipe<?>>(original);all.add(a);all.add(b);h.getLevel().getRecipeManager().replaceRecipes(all);crafting_choice.invalidateRecipes();}
        ServerPlayer player(boolean both){var p=ProcessingTestPlayers.create(h);recipe_unlocks.grantForTesting(p,b.getId());if(both)recipe_unlocks.grantForTesting(p,a.getId());return p;}
        CraftingMenu table(ServerPlayer p){var pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);var menu=new CraftingMenu(61,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));p.containerMenu=menu;return menu;}
        @Override public void close(){h.getLevel().getRecipeManager().replaceRecipes(original);crafting_choice.invalidateRecipes();players.close();}
    }
    private static CraftingRecipe recipe(String name,Ingredient material,ItemStack result,Item remainder){
        return new ShapelessRecipe(id(name),"",CraftingBookCategory.MISC,result,NonNullList.of(Ingredient.EMPTY,material)){
            @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid){var list=NonNullList.withSize(grid.getContainerSize(),ItemStack.EMPTY);for(int i=0;i<grid.getContainerSize();i++)if(!grid.getItem(i).isEmpty()&&remainder!=Items.AIR){list.set(i,new ItemStack(remainder));break;}return list;}
        };
    }
    private static void select(GameTestHelper h,ServerPlayer p,ResourceLocation id){var v=crafting_choice.open(p,p.containerMenu.containerId);yes(h,crafting_choice.select(p,v.menu(),v.session(),v.version(),1,id)==transfer_reason.OK,"authorized current recipe selected");}
    @GameTest(template="empty") public static void selectedRecipeControlsNbtCountRemainderAndEvents(GameTestHelper h){try(var f=new Fixture(h,false)){
        var p=f.player(true);var menu=f.table(p);menu.getSlot(1).set(new ItemStack(Items.PAPER));var before=menu.getSlot(1).getItem().copy();var v=crafting_choice.open(p,menu.containerId);
        yes(h,v.candidates().size()==2&&v.selected().equals(f.a.getId()),"all actual matches, stable ID default");
        java.util.concurrent.atomic.AtomicInteger events=new java.util.concurrent.atomic.AtomicInteger();java.util.function.Consumer<net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent> listener=e->{if(e.getEntity()==p)events.incrementAndGet();};net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try{
            select(h,p,f.b.getId());yes(h,ItemStack.matches(before,menu.getSlot(1).getItem())&&events.get()==0,"preview/selection consumes nothing and awards no craft event");
            yes(h,menu.getSlot(0).getItem().is(Items.EMERALD)&&menu.getSlot(0).getItem().getCount()==2&&menu.getSlot(0).getItem().getTag().getString("choice_test").equals("B"),"accurate selected count and NBT preview");
            menu.clicked(0,0,ClickType.PICKUP,p);yes(h,menu.getCarried().is(Items.EMERALD)&&menu.getCarried().getCount()==2,"normal pickup gets selected output");
            yes(h,menu.getSlot(1).getItem().is(Items.BOWL)&&p.getInventory().countItem(Items.GLASS_BOTTLE)==0,"B remainder used, not first recipe A");
            yes(h,events.get()==1,"one native craft event, no duplicate selector rewards");
            menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(new ItemStack(Items.PAPER));select(h,p,f.a.getId());menu.clicked(0,0,ClickType.PICKUP,p);
            yes(h,menu.getCarried().is(Items.DIAMOND)&&menu.getCarried().getCount()==1&&menu.getSlot(1).getItem().is(Items.GLASS_BOTTLE),"explicit A selection also crafts A and returns A container");
            yes(h,events.get()==2,"two actual crafts have exactly two craft events");
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);}
    }h.succeed();}
    @GameTest(template="empty") public static void lockedFirstDoesNotMaskSecondAndPlayersAreIndependent(GameTestHelper h){try(var f=new Fixture(h,false)){
        var p=f.player(false);var menu=f.table(p);menu.getSlot(1).set(new ItemStack(Items.PAPER));var v=crafting_choice.open(p,menu.containerId);
        yes(h,v.candidates().size()==1&&v.selected().equals(f.b.getId())&&menu.getSlot(0).getItem().is(Items.EMERALD),"locked first recipe does not hide authorized second match");
        yes(h,crafting_choice.select(p,v.menu(),v.session(),v.version(),1,f.a.getId())==transfer_reason.NOT_AUTHORIZED,"forged locked recipe ID rejected");
        var other=f.player(true);var otherMenu=f.table(other);otherMenu.getSlot(1).set(new ItemStack(Items.PAPER));yes(h,otherMenu.getSlot(0).getItem().is(Items.DIAMOND),"another player's default/selection independent");
    }h.succeed();}
    @GameTest(template="empty") public static void inventoryTwoByTwoAndNumberSwapUseChosenRecipe(GameTestHelper h){try(var f=new Fixture(h,false)){
        var p=f.player(true);var menu=p.inventoryMenu;p.containerMenu=menu;menu.getSlot(1).set(new ItemStack(Items.PAPER));select(h,p,f.b.getId());
        menu.clicked(0,0,ClickType.SWAP,p);yes(h,p.getInventory().getItem(0).is(Items.EMERALD)&&p.getInventory().getItem(0).getCount()==2&&menu.getSlot(1).getItem().is(Items.BOWL),"2x2 numeric hotbar swap gets correct output and remainder");
        menu.getSlot(1).set(new ItemStack(Items.DIRT));yes(h,menu.getSlot(0).getItem().isEmpty(),"input invalidation clears selected result");
        var json=com.google.gson.JsonParser.parseString("{\"pattern\":[\"XXX\"],\"key\":{\"X\":{\"item\":\"minecraft:paper\"}},\"result\":{\"item\":\"minecraft:gold_ingot\"}}").getAsJsonObject();
        var big=RecipeSerializer.SHAPED_RECIPE.fromJson(id("big"),json);var all=new ArrayList<Recipe<?>>(h.getLevel().getRecipeManager().getRecipes());all.add(big);h.getLevel().getRecipeManager().replaceRecipes(all);crafting_choice.invalidateRecipes();recipe_unlocks.grantForTesting(p,big.getId());
        menu.getSlot(1).set(new ItemStack(Items.PAPER));yes(h,crafting_choice.open(p,menu.containerId).candidates().stream().noneMatch(c->c.id().equals(big.getId())),"3-wide recipe cannot appear in 2x2");
        var table=f.table(p);for(int i=1;i<=3;i++)table.getSlot(i).set(new ItemStack(Items.PAPER));yes(h,crafting_choice.open(p,table.containerId).candidates().stream().anyMatch(c->c.id().equals(big.getId())),"same recipe valid in actual 3x3 grid");
    }h.succeed();}
    @GameTest(template="empty") public static void shiftLocksRecipeIdEvenWhenFallbackOutputIsSameItem(GameTestHelper h){try(var f=new Fixture(h,true)){
        var p=f.player(true);var menu=f.table(p);menu.getSlot(1).set(new ItemStack(Items.PAPER,3));select(h,p,f.b.getId());menu.clicked(0,0,ClickType.QUICK_MOVE,p);
        yes(h,p.getInventory().countItem(Items.DIAMOND)==6&&menu.getSlot(1).getItem().is(Items.STICK),"Shift produces only three B batches and stops before matching A consumes final stick");
        yes(h,menu.getSlot(0).getItem().is(Items.DIAMOND)&&menu.getSlot(0).getItem().getCount()==1,"fallback A becomes available only after Shift action ends");
        p.getInventory().clearContent();for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));menu.getSlot(1).set(new ItemStack(Items.PAPER));select(h,p,f.b.getId());
        yes(h,menu.quickMoveStack(p,0).isEmpty()&&menu.getSlot(1).getItem().is(Items.PAPER),"full inventory stops before consumption");
    }h.succeed();}
    @GameTest(template="empty") public static void staleInputSessionMenuAndRecipeReloadAreRejected(GameTestHelper h){try(var f=new Fixture(h,false)){
        var p=f.player(true);var menu=f.table(p);menu.getSlot(1).set(new ItemStack(Items.PAPER));select(h,p,f.b.getId());var old=crafting_choice.open(p,menu.containerId);menu.getSlot(1).set(new ItemStack(Items.PAPER,2));
        yes(h,menu.getSlot(0).getItem().is(Items.EMERALD),"adding input preserves a still-legal explicit selection");
        yes(h,crafting_choice.select(p,old.menu(),old.session(),old.version(),1,f.b.getId())==transfer_reason.DATA_CHANGED,"input version mismatch rejects stale selection");
        var current=crafting_choice.open(p,menu.containerId);yes(h,crafting_choice.select(p,old.menu(),old.session(),current.version(),2,f.b.getId())==transfer_reason.DATA_CHANGED,"reopened session rejects old request");
        crafting_choice.close(menu);yes(h,crafting_choice.select(p,current.menu(),current.session(),current.version(),3,f.b.getId())==transfer_reason.DATA_CHANGED,"closed menu session cannot be resurrected");
        p.containerMenu=p.inventoryMenu;yes(h,crafting_choice.select(p,current.menu(),current.session(),current.version(),4,f.b.getId())==transfer_reason.MENU_INVALID,"wrong current menu rejected");
        p.containerMenu=menu;select(h,p,f.b.getId());var remaining=new ArrayList<Recipe<?>>(h.getLevel().getRecipeManager().getRecipes());remaining.removeIf(r->r.getId().equals(f.b.getId()));h.getLevel().getRecipeManager().replaceRecipes(remaining);crafting_choice.invalidateRecipes();crafting_choice.update(menu,p);
        yes(h,menu.getSlot(0).getItem().is(Items.DIAMOND),"reload invalidates removed selection and uses authorized default");
    }h.succeed();}
    @GameTest(template="empty") public static void browserFillSelectsExactRecipeAndPopupLayoutIsBounded(GameTestHelper h){try(var f=new Fixture(h,false)){
        var p=f.player(true);var menu=f.table(p);p.getInventory().setItem(0,new ItemStack(Items.PAPER,3));
        yes(h,browser_transfer.apply(p,menu.containerId,f.b.getId(),browser_transfer.fingerprint(f.b),false)==transfer_reason.OK,"existing atomic browser fill succeeds");
        yes(h,menu.getSlot(0).getItem().is(Items.EMERALD)&&menu.getSlot(0).getItem().getCount()==2,"browser B fill chooses actual B, not default A");
        for(int width:List.of(240,320,600))for(int height:List.of(180,240,400)){
            var anchor=crafting_choice_layout.button(width,height,width-25,height-45);var pop=crafting_choice_layout.popup(width,height,anchor,20);var r=pop.bounds();
            yes(h,r.x()>=0&&r.y()>=0&&r.x()+r.w()<=width&&r.y()+r.h()<=height,"modal bounds stay on screen");
            yes(h,pop.row(r.x()+10,r.y()+25,3)==3&&pop.row(r.x()-1,r.y()+25,3)==-1,"same geometry drives row hit and outside-click exclusion");
        }
    }h.succeed();}
}
