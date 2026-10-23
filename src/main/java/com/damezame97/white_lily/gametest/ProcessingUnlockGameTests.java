package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.crafting.prescription_recipes;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.processing.*;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import com.damezame97.white_lily.research.research_capability;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class ProcessingUnlockGameTests
{
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("white_lily", path); }
    private static processing_block_entity table(GameTestHelper helper)
    {
        helper.setBlock(new BlockPos(1, 2, 1), medical_blocks.SIMPLE_PROCESSING_TABLE.get());
        return (processing_block_entity) helper.getBlockEntity(new BlockPos(1, 2, 1));
    }
    private static void tick(processing_block_entity table, int count)
    { for (int n = 0; n < count; n++) processing_block_entity.serverTick(table.getLevel(), table.getBlockPos(), table.getBlockState(), table); }
    private static processing_menu menu(ServerPlayer player, processing_block_entity table)
    {
        player.setPos(table.getBlockPos().getX() + .5, table.getBlockPos().getY(), table.getBlockPos().getZ() + .5);
        processing_menu menu = new processing_menu(31, player.getInventory(), table);
        player.containerMenu = menu;
        return menu;
    }

    @GameTest(template = "empty")
    public static void machineRecordsNeverGrantAnotherPlayerRecipeOrOutputOwnership(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer owner = ProcessingTestPlayers.create(helper), other = ProcessingTestPlayers.create(helper);
            research_capability.get(owner).orElseThrow().complete(id("bitterleaf_paste"));
            yes(helper, table.registerLearned(owner) == 1, "learned owner can register one processing recipe");
            table.authorizeInput(other);
            table.getInventory().setStackInSlot(0, new ItemStack(items.STONE_MORTAR.get()));
            table.getInventory().setStackInSlot(1, new ItemStack(items.BITTERLEAF.get(), 6));
            tick(table, 100);
            yes(helper, table.getData().get(2) == processing_block_entity.LOCKED && table.getInventory().getStackInSlot(5).isEmpty()
                    && table.getInventory().getStackInSlot(1).getCount() == 6, "registered machine cannot produce for another locked player");
            table.authorizeInput(owner); tick(table, 100);
            yes(helper, table.getInventory().getStackInSlot(5).getCount() == 2, "unlocked live owner produces one batch");
            processing_menu foreignMenu = menu(other, table);
            for (ClickType click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.CLONE, ClickType.THROW, ClickType.PICKUP_ALL))
                foreignMenu.clicked(5, 0, click, other);
            yes(helper, foreignMenu.quickMoveStack(other, 5).isEmpty() && foreignMenu.getCarried().isEmpty()
                    && table.getInventory().getStackInSlot(5).getCount() == 2 && other.getInventory().countItem(items.BITTERLEAF_PASTE.get()) == 0,
                    "ordinary, shift, hotbar, clone, throw and collect-all cannot claim foreign output");
            yes(helper, !table.authorizeInput(other), "foreign pending output also protects the machine input operation");
            research_capability.get(owner).orElseThrow().reset(id("bitterleaf_paste"), -1);
            processing_menu ownerMenu = menu(owner, table);
            ownerMenu.clicked(5, 0, ClickType.QUICK_MOVE, owner);
            yes(helper, table.getInventory().getStackInSlot(5).getCount() == 2, "personal research reset immediately blocks existing result claims");
            research_capability.get(owner).orElseThrow().complete(id("bitterleaf_paste"));
            ownerMenu.clicked(5, 0, ClickType.QUICK_MOVE, owner);
            yes(helper, owner.getInventory().countItem(items.BITTERLEAF_PASTE.get()) == 2 && table.getInventory().getStackInSlot(5).isEmpty(),
                    "only the unlocked owner receives the batch once");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void ownerIdentityPersistsButOfflineDeathAndLegacyUnboundInputsPause(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer original = ProcessingTestPlayers.create(helper);
            table.getInventory().setStackInSlot(0, new ItemStack(items.STONE_MORTAR.get()));
            table.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 2));
            tick(table, 40);
            yes(helper, table.getData().get(2) == processing_block_entity.OWNER_OFFLINE && table.getInventory().getStackInSlot(5).isEmpty(),
                    "unbound old-world inputs cannot run even an initially unlocked recipe");
            table.authorizeInput(original); tick(table, 17);
            CompoundTag saved = table.saveWithFullMetadata();
            processing_block_entity restored = new processing_block_entity(table.getBlockPos(), table.getBlockState());
            restored.load(saved); restored.setLevel(helper.getLevel());
            helper.getLevel().players().remove(original); tick(restored, 100);
            yes(helper, restored.getData().get(0) == 17 && restored.getInventory().getStackInSlot(5).isEmpty(), "logout pauses saved progress without offline production");
            ServerPlayer reconnected = ProcessingTestPlayers.create(helper, original.getUUID());
            reconnected.setHealth(0); tick(restored, 100);
            yes(helper, restored.getData().get(0) == 17, "dead same-UUID player does not authorize processing");
            reconnected.setHealth(reconnected.getMaxHealth()); tick(restored, 23);
            yes(helper, restored.getInventory().getStackInSlot(5).getCount() == 2 && restored.canTakeOutputs(reconnected)
                    && !restored.canTakeOutputs(ProcessingTestPlayers.create(helper)), "fresh live same-UUID player resumes and owns exactly one completed batch");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void recipeTransferUsesRealMaterialsAndRollsBackWhenRecoveryCannotFit(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer player = ProcessingTestPlayers.create(helper);
            processing_menu menu = menu(player, table);
            ItemStack tool = new ItemStack(items.STONE_MORTAR.get()); tool.setHoverName(Component.literal("Preserved tool"));
            player.getInventory().setItem(0, tool);
            player.getInventory().setItem(9, new ItemStack(items.HEMP_STALK.get(), 64));
            yes(helper, menu.fillRecipe(player, id("processing/hemp_fiber"), true), "maximum transfer allocates real player inventory into processing inputs");
            yes(helper, table.getInventory().getStackInSlot(1).getCount() == 64 && table.getInventory().getStackInSlot(0).getHoverName().getString().equals("Preserved tool")
                    && player.getInventory().countItem(items.HEMP_STALK.get()) == 0, "maximum transfer preserves tool NBT and moves exactly 64 stalks");
            CompoundTag before = table.saveWithFullMetadata();
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            menu = menu(player, table);
            yes(helper, !menu.fillRecipe(player, id("processing/hemp_fiber"), false) && before.equals(table.saveWithFullMetadata())
                    && player.getInventory().countItem(Items.DIRT) == 2304, "full inventory recovery fails atomically without lost inputs, progress or inventory");
            player.setPos(table.getBlockPos().getX() + 100, table.getBlockPos().getY(), table.getBlockPos().getZ());
            yes(helper, !menu.fillRecipe(player, id("processing/hemp_fiber"), false), "remote recipe transfer is rejected");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void recipeTransferSolvesOverlappingIngredientsWithoutInventingItems(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            var manager = helper.getLevel().getRecipeManager(); List<Recipe<?>> original = new ArrayList<>(manager.getRecipes());
            var json = JsonParser.parseString("{\"tool\":{\"item\":\"white_lily:stone_mortar\"},\"ingredients\":[{\"ingredient\":[{\"item\":\"minecraft:oak_log\"},{\"item\":\"minecraft:birch_log\"}],\"count\":1},{\"ingredient\":{\"item\":\"minecraft:oak_log\"},\"count\":1}],\"result\":{\"item\":\"white_lily:plant_fiber\"},\"processing_time\":1}").getAsJsonObject();
            processing_recipe recipe = prescription_recipes.PROCESSING.get().fromJson(id("transfer_overlap_fixture"), json);
            try
            {
                List<Recipe<?>> changed = new ArrayList<>(original); changed.add(recipe); manager.replaceRecipes(changed);
                processing_block_entity table = table(helper); ServerPlayer player = ProcessingTestPlayers.create(helper);
                processing_menu menu = menu(player, table);
                player.getInventory().setItem(0, new ItemStack(items.STONE_MORTAR.get()));
                player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG)); player.getInventory().setItem(2, new ItemStack(Items.BIRCH_LOG));
                yes(helper, !menu.fillRecipe(player, recipe.getId(), false), "locked custom processing recipe cannot transfer materials");
                recipe_unlocks.unlock(player, recipe.getId());
                yes(helper, menu.fillRecipe(player, recipe.getId(), false), "overlapping broad/narrow ingredient requirements receive a complete feasible allocation");
                tick(table, 1);
                yes(helper, table.getInventory().getStackInSlot(5).getCount() == 1 && player.getInventory().countItem(Items.OAK_LOG) == 0
                        && player.getInventory().countItem(Items.BIRCH_LOG) == 0 && table.getInventory().getStackInSlot(1).isEmpty()
                        && table.getInventory().getStackInSlot(2).isEmpty(), "one real oak plus one real birch produce one result without duplication");
            }
            finally { manager.replaceRecipes(original); }
            helper.succeed();
        }
    }
}
