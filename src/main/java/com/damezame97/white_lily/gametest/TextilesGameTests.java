package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
public final class TextilesGameTests {
    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
    private static void got(net.minecraft.server.level.ServerPlayer p, Item item) {
        recipe_unlocks.acquired(p, new ItemStack(item));
    }
    private static boolean knows(net.minecraft.server.level.ServerPlayer p, String name) {
        return recipe_unlocks.isUnlocked(p, id(name));
    }

    @GameTest(template = "empty")
    public static void leatherPaperAndSugarCane(GameTestHelper h) {
        try (var scope = new ProcessingTestPlayers(h)) {
            var p = ProcessingTestPlayers.create(h);
            got(p, Items.LEATHER);
            h.assertTrue(knows(p, "minecraft:leather_chestplate") && !knows(p, "minecraft:book"), "Leather armor only");
            got(p, Items.PAPER);
            h.assertTrue(knows(p, "minecraft:book"), "Leather plus paper unlocks book");
            var reverse = ProcessingTestPlayers.create(h);
            got(reverse, Items.PAPER);
            h.assertTrue(!knows(reverse, "minecraft:book"), "Paper alone cannot unlock book");
            got(reverse, Items.LEATHER);
            h.assertTrue(knows(reverse, "minecraft:book"), "Order-independent history");
            var cane = ProcessingTestPlayers.create(h); got(cane, Items.SUGAR_CANE);
            h.assertTrue(knows(cane, "minecraft:paper") && knows(cane, "minecraft:sugar_from_sugar_cane")
                    && !knows(cane, "minecraft:book"), "Cane unlocks processing, not book");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woolStringAndDyeBoundaries(GameTestHelper h) {
        try (var scope = new ProcessingTestPlayers(h)) {
            var p = ProcessingTestPlayers.create(h);
            got(p, Items.STRING);
            h.assertTrue(knows(p, "minecraft:white_wool_from_string") && !knows(p, "white_lily:white_sleeping_bag"), "String entry only");
            got(p, Items.RED_WOOL);
            h.assertTrue(knows(p, "white_lily:white_sleeping_bag") && knows(p, "minecraft:blue_bed")
                    && knows(p, "minecraft:green_carpet"), "Any wool unlocks bag, all beds and carpets");
            var dye = ProcessingTestPlayers.create(h); got(dye, Items.RED_DYE);
            h.assertTrue(knows(dye, "minecraft:white_dye") && knows(dye, "minecraft:green_dye")
                    && !knows(dye, "minecraft:dye_blue_wool") && !knows(dye, "minecraft:red_bed")
                    && !knows(dye, "minecraft:white_banner"), "Dye does not bypass item knowledge");
            got(dye, Items.BLACK_WOOL);
            h.assertTrue(knows(dye, "minecraft:dye_blue_wool"), "Earlier dye history is applied when wool opens");
            for (Item source : new Item[]{Items.DANDELION, Items.INK_SAC, Items.COCOA_BEANS,
                    Items.LAPIS_LAZULI, Items.BONE_MEAL, Items.CACTUS, Items.SEA_PICKLE}) {
                var fresh = ProcessingTestPlayers.create(h); got(fresh, source);
                h.assertTrue(knows(fresh, "minecraft:green_dye") && knows(fresh, "minecraft:white_dye"), "Dye source entry " + source);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedWoolSleepingBagRecipe(GameTestHelper h) {
        var menu = new CraftingMenu(1, h.makeMockPlayer().getInventory());
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        grid.setItem(3, new ItemStack(Items.RED_WOOL));
        grid.setItem(4, new ItemStack(Items.BLACK_WOOL));
        grid.setItem(5, new ItemStack(Items.BLUE_WOOL));
        var recipe = (CraftingRecipe) h.getLevel().getRecipeManager().byKey(id("white_lily:white_sleeping_bag")).orElseThrow();
        h.assertTrue(recipe.matches(grid, h.getLevel()) && recipe.assemble(grid, h.getLevel().registryAccess()).is(items.WHITE_SLEEPING_BAG.get()), "Mixed wool horizontal recipe");
        grid.clearContent();
        for (int i : new int[]{0, 3, 6}) grid.setItem(i, new ItemStack(Items.WHITE_WOOL));
        h.assertTrue(!recipe.matches(grid, h.getLevel()), "Vertical arrangement is not accepted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepingBagSleepsWithoutReplacingSpawn(GameTestHelper h) {
        var level = h.getLevel();
        long beforeTime = level.getDayTime();
        try (var scope = new ProcessingTestPlayers(h)) {
            var p = ProcessingTestPlayers.create(h);
            p.setGameMode(GameType.CREATIVE);
            BlockPos foot = h.absolutePos(new BlockPos(1, 2, 1));
            BlockPos head = foot.south();
            var state = medical_blocks.WHITE_SLEEPING_BAG.get().defaultBlockState()
                    .setValue(BedBlock.FACING, Direction.SOUTH);
            level.setBlock(foot, state, 2);
            state.getBlock().setPlacedBy(level, foot, state, p, new ItemStack(items.WHITE_SLEEPING_BAG.get()));
            h.assertTrue(level.getBlockState(head).is(medical_blocks.WHITE_SLEEPING_BAG.get())
                    && level.getBlockState(head).getValue(BedBlock.PART) == BedPart.HEAD, "Two-block placement");
            BlockPos previous = h.absolutePos(new BlockPos(3, 2, 3));
            p.setRespawnPosition(level.dimension(), previous, 17, true, false);
            level.setDayTime(13000);
            level.updateSkyBrightness();
            p.setPos(head.getX() + .5, head.getY(), head.getZ() + .5);
            var result = p.startSleepInBed(head);
            h.assertTrue(result.right().isPresent() && p.isSleeping(), "Normal sleep succeeds: " + result);
            h.assertTrue(previous.equals(p.getRespawnPosition()) && p.getRespawnAngle() == 17
                    && p.isRespawnForced(), "Existing respawn point is untouched");
            p.stopSleepInBed(true, false);
            var fresh = ProcessingTestPlayers.create(h);
            fresh.setRespawnPosition(level.dimension(), head, 0, false, false);
            h.assertTrue(fresh.getRespawnPosition() == null, "Bag cannot establish a first respawn point");
        } finally { level.setDayTime(beforeTime); level.updateSkyBrightness(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepingBagBreaksAsOneItem(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos foot = h.absolutePos(new BlockPos(1, 2, 1));
        var state = medical_blocks.WHITE_SLEEPING_BAG.get().defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH);
        var area = new net.minecraft.world.phys.AABB(foot).inflate(3);
        for (int pass = 0; pass < 2; pass++) {
            level.setBlock(foot, state, 2);
            state.getBlock().setPlacedBy(level, foot, state, null, new ItemStack(items.WHITE_SLEEPING_BAG.get()));
            level.destroyBlock(pass == 0 ? foot : foot.south(), true);
            h.assertTrue(level.getBlockState(foot).isAir() && level.getBlockState(foot.south()).isAir(), "Both halves removed");
            int count = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).stream()
                    .filter(e -> e.getItem().is(items.WHITE_SLEEPING_BAG.get()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(count == pass + 1, "Breaking either half drops exactly one bag: " + count);
        }
        h.succeed();
    }
}
