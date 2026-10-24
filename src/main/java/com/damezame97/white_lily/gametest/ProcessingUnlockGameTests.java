package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.processing.*;
import com.damezame97.white_lily.research.research_capability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

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
    private static void fill(processing_block_entity table, ItemStack raw)
    {
        table.getInventory().setStackInSlot(0, new ItemStack(items.STONE_MORTAR.get()));
        table.getInventory().setStackInSlot(1, raw);
    }

    @GameTest(template = "empty")
    public static void registeredMachineIsSharedAcrossPlayersAndSurvivesRegistrantReset(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer owner = ProcessingTestPlayers.create(helper), other = ProcessingTestPlayers.create(helper);
            research_capability.get(owner).orElseThrow().complete(id("bitterleaf_paste"));
            yes(helper, table.registerLearned(owner) == 1, "learned owner registers the research recipe");
            fill(table, new ItemStack(items.BITTERLEAF.get(), 6));
            tick(table, 200);
            yes(helper, table.getInventory().getStackInSlot(5).getCount() == 4
                    && table.getInventory().getStackInSlot(1).isEmpty(), "registered machine runs without an operator or open menu");

            processing_menu otherMenu = menu(other, table);
            otherMenu.clicked(5, 0, ClickType.QUICK_MOVE, other);
            yes(helper, other.getInventory().countItem(items.BITTERLEAF_PASTE.get()) == 4
                    && table.getInventory().getStackInSlot(5).isEmpty(), "another player with no research can take shared output");
            yes(helper, !research_capability.get(other).orElseThrow().has(id("bitterleaf_paste")),
                    "taking output never grants personal research");
            yes(helper, table.registerLearned(other) == 0 && table.registeredRecipes().size() == 1,
                    "unresearched player cannot copy knowledge or remove the machine record");

            research_capability.get(owner).orElseThrow().reset(id("bitterleaf_paste"), -1);
            fill(table, new ItemStack(items.BITTERLEAF.get(), 3));
            tick(table, 100);
            yes(helper, table.getInventory().getStackInSlot(5).getCount() == 2,
                    "personal reset does not revoke the machine record");
            otherMenu.clicked(5, 0, ClickType.QUICK_MOVE, other);
            yes(helper, other.getInventory().countItem(items.BITTERLEAF_PASTE.get()) == 6,
                    "shared output stays usable after the registrant resets");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void baseProcessingNeedsNoRegistrationNoResearchAndNoPlayer(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            fill(table, new ItemStack(items.HEMP_STALK.get(), 1));
            tick(table, 40);
            yes(helper, table.getInventory().getStackInSlot(5).is(items.PLANT_FIBER.get())
                    && table.getInventory().getStackInSlot(5).getCount() == 2, "base recipe runs with no player, menu or registration");
            yes(helper, table.registeredRecipes().isEmpty(), "base recipe did not add a machine record");
            yes(helper, table.contextRecipes().contains(id("processing/hemp_fiber")), "base recipe is part of machine capability");

            ServerPlayer player = ProcessingTestPlayers.create(helper);
            processing_menu menu = menu(player, table);
            table.getInventory().setStackInSlot(5, ItemStack.EMPTY);
            player.getInventory().setItem(0, new ItemStack(items.STONE_MORTAR.get()));
            player.getInventory().setItem(1, new ItemStack(items.HEMP_STALK.get(), 1));
            yes(helper, menu.fillRecipe(player, id("processing/hemp_fiber"), false), "base recipe fills without personal permission");
            tick(table, 40);
            yes(helper, table.getInventory().getStackInSlot(5).is(items.PLANT_FIBER.get()), "filled base recipe produces");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void oldOperatorAndOutputOwnershipTagsAreIgnoredAndNotRewritten(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer owner = ProcessingTestPlayers.create(helper), other = ProcessingTestPlayers.create(helper);
            research_capability.get(owner).orElseThrow().complete(id("bitterleaf_paste"));
            table.registerLearned(owner);
            fill(table, new ItemStack(items.BITTERLEAF.get(), 3));
            tick(table, 100);
            yes(helper, table.getInventory().getStackInSlot(5).getCount() == 2, "fixture batch completed");

            CompoundTag saved = table.saveWithFullMetadata();
            saved.putUUID("Operator", UUID.randomUUID());
            saved.putUUID("OutputOwner", UUID.randomUUID());
            saved.putString("OutputRecipe", id("processing/bitterleaf_paste").toString());
            table.load(saved);
            table.setLevel(helper.getLevel());
            yes(helper, table.getInventory().getStackInSlot(5).getCount() == 2, "old fields do not erase existing output");
            processing_menu otherMenu = menu(other, table);
            otherMenu.clicked(5, 0, ClickType.QUICK_MOVE, other);
            yes(helper, other.getInventory().countItem(items.BITTERLEAF_PASTE.get()) == 2, "legacy ownership tags no longer block another player");

            CompoundTag rewritten = table.saveWithFullMetadata();
            yes(helper, !rewritten.contains("Operator") && !rewritten.contains("OutputOwner") && !rewritten.contains("OutputRecipe"),
                    "obsolete ownership fields are not written again");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void registrationDoesNotResetSharedProgressOrBindAnOperator(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer owner = ProcessingTestPlayers.create(helper), other = ProcessingTestPlayers.create(helper);
            fill(table, new ItemStack(items.HEMP_STALK.get(), 2));
            tick(table, 17);
            yes(helper, table.getData().get(0) == 17, "base progress accumulated");
            research_capability.get(owner).orElseThrow().complete(id("bitterleaf_paste"));
            yes(helper, table.registerLearned(owner) == 1, "registration succeeds while a base batch is running");
            yes(helper, table.getData().get(0) == 17, "registration does not reset compatible progress");
            table.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 3));
            tick(table, 1);
            yes(helper, table.getData().get(0) == 18, "another player supplementing the same material preserves progress");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void machineContextContainsOnlyExactBaseOrRegisteredRecipeIds(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            processing_block_entity table = table(helper);
            ServerPlayer owner = ProcessingTestPlayers.create(helper), other = ProcessingTestPlayers.create(helper);
            research_capability.get(owner).orElseThrow().complete(id("bitterleaf_paste"));
            table.registerLearned(owner);
            Set<ResourceLocation> context = table.contextRecipes();
            yes(helper, context.contains(id("processing/hemp_fiber")) && context.contains(id("processing/bitterleaf_paste")),
                    "base plus registered research recipe are exposed");
            yes(helper, !context.contains(id("processing/yarrow_dressing")), "unregistered research recipe is not exposed");
            processing_menu opened = menu(other, table);
            yes(helper, opened.machineRecipes().equals(context), "menu context matches the live machine capability set");
            yes(helper, opened.authorizeRecipeTransfer(other, id("processing/bitterleaf_paste")), "machine authorization accepts a registered research recipe");
            yes(helper, !opened.authorizeRecipeTransfer(other, id("processing/yarrow_dressing")), "machine authorization rejects an unregistered research recipe");
            helper.succeed();
        }
    }
}
