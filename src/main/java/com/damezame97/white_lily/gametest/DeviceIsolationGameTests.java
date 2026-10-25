package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.machine.*;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("white_lily") @PrefixGameTestTemplate(false)
public final class DeviceIsolationGameTests {
    private static ResourceLocation id(String s){return ResourceLocation.parse(s.contains(":")?s:"minecraft:"+s);}
    private static final ResourceLocation SHARED=id("white_lily:qa_shared_type");
    static { machine_rules.register(new machine_adapter(){
        public ResourceLocation id(){return SHARED;}
        public RecipeType<?> type(ResourceLocation device){return RecipeType.SMELTING;}
        public Recipe<?> copy(ResourceLocation d,ResourceLocation key,Recipe<?> src,int ticks){return new vanilla_cooking_adapter().copy(DeviceIsolationGameTests.id("furnace"),key,src,ticks);}
    }); }
    @GameTest(template="empty")
    public static void sameTypeUnadaptedMachineStillProcessesOres(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(1,2,1));
        h.getLevel().setBlock(pos,Blocks.BLAST_FURNACE.defaultBlockState(),3);
        // Same base entity and SMELTING, but a different block identity: no enrollment.
        var other=new FurnaceBlockEntity(pos,Blocks.BLAST_FURNACE.defaultBlockState());other.setLevel(h.getLevel());
        h.assertTrue(machine_rules.device(other)==null,"Block identity plus entity type must both match");
        other.setItem(0,new ItemStack(Items.RAW_IRON));other.setItem(1,new ItemStack(Items.COAL));
        for(int i=0;i<200;i++)AbstractFurnaceBlockEntity.serverTick(h.getLevel(),pos,other.getBlockState(),other);
        h.assertTrue(other.getItem(2).is(Items.IRON_INGOT),"Unadapted same-type machine uses original global recipe");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id("iron_ingot_from_smelting_raw_iron")).isPresent(),"Global ore smelting recipe is untouched");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void sameRecipeTypeCanHaveIndependentDevicePolicies(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();
        var a=new machine_rules.Rule(id("white_lily:a"),id("example:device_a"),SHARED,true,Set.of(id("glass")),Set.of(),List.of());
        var b=new machine_rules.Rule(id("white_lily:b"),id("example:device_b"),SHARED,true,Set.of(id("cooked_beef")),Set.of(),List.of());
        var state=machine_rules.build(manager.getRecipes(),List.of(a,b));
        h.assertTrue(state.tables().get(a.device()).get(0).getId().equals(id("glass"))
                && state.tables().get(b.device()).get(0).getId().equals(id("cooked_beef")),"Shared type is not a shared whitelist");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void privateCopiesSyncAndCraftWithoutGlobalLeakage(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var before=machine_rules.state(manager);
        try(var scope=new ProcessingTestPlayers(h)){
            var key=id("white_lily:private_glass");
            var rule=new machine_rules.Rule(id("white_lily:private_rule"),id("furnace"),id("white_lily:vanilla_cooking"),true,
                    Set.of(),Set.of(),List.of(new machine_rules.Copy(id("glass"),key,20,true)));
            var state=machine_rules.build(manager.getRecipes(),List.of(rule));
            ((machine_state_access)manager).whiteLily$machines(state);
            h.assertTrue(manager.byKey(id("glass")).isPresent()&&manager.byKey(key).isEmpty(),"Migration leaves original global table untouched and copy private");
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{
                new machine_view_network.Snapshot(state.tables()).write(buffer);
                var decoded=machine_view_network.Snapshot.read(buffer);
                h.assertTrue(decoded.tables().get(id("furnace")).get(0).getId().equals(key),"Private recipe view survives network serialization");
            }finally{buffer.release();}
            var p=ProcessingTestPlayers.create(h);recipe_unlocks.acquired(p,new ItemStack(Items.FURNACE));
            h.assertTrue(recipe_unlocks.viewable(p).contains(key)&&!recipe_unlocks.viewable(p).contains(id("glass")),"History reveals device view, not all global smelting recipes");
            var pos=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);
            var f=(AbstractFurnaceBlockEntity)h.getLevel().getBlockEntity(pos);
            f.setItem(0,new ItemStack(Items.SAND));f.setItem(1,new ItemStack(Items.COAL));
            for(int i=0;i<20;i++)AbstractFurnaceBlockEntity.serverTick(h.getLevel(),pos,f.getBlockState(),f);
            h.assertTrue(f.getItem(2).is(Items.GLASS),"Private copy actually executes at its configured time");
            h.assertTrue(f.getRecipesToAwardAndPopExperience(h.getLevel(),p.position()).stream().anyMatch(r->r.getId().equals(key)),"Private recipes retain experience lookup");
            h.assertTrue(manager.getRecipeFor(RecipeType.SMELTING,new net.minecraft.world.SimpleContainer(new ItemStack(Items.SAND)),h.getLevel()).orElseThrow().getId().equals(id("glass")),"Unadapted machine cannot see private copy");
        }finally{((machine_state_access)manager).whiteLily$machines(before);}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void disallowedMatchingRecipeCannotShadowAllowedOne(GameTestHelper h){
        var manager=h.getLevel().getRecipeManager();var saved=List.copyOf(manager.getRecipes());var before=machine_rules.state(manager);
        try{
            List<Recipe<?>> all=new ArrayList<>(saved);
            all.add(new SmeltingRecipe(id("example:also_beef"),"",CookingBookCategory.FOOD,Ingredient.of(Items.BEEF),new ItemStack(Items.DIAMOND),0,1));
            manager.replaceRecipes(all);
            var pos=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);
            var f=(AbstractFurnaceBlockEntity)h.getLevel().getBlockEntity(pos);f.setItem(0,new ItemStack(Items.BEEF));
            h.assertTrue(machine_rules.findCooking(f,h.getLevel()).orElseThrow().getId().equals(id("cooked_beef")),"Selection searches the device view, not the first global match");
        }finally{manager.replaceRecipes(saved);((machine_state_access)manager).whiteLily$machines(before);}
        h.succeed();
    }
}
