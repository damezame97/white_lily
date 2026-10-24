package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.recipebook.browser_cheat_network;
import com.damezame97.white_lily.recipebook.browser_item_catalog;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class BrowserCheatGameTests
{
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("minecraft", path); }

    private static ServerPlayer open(ProcessingTestPlayers scope, GameTestHelper helper, boolean permissions)
    {
        ServerPlayer player = ProcessingTestPlayers.create(helper, permissions);
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
        return player;
    }

    private static browser_cheat_network.Result give(ServerPlayer player, ItemStack stack, boolean full)
    {
        return browser_cheat_network.give(player, stack, full, 1, player.containerMenu.containerId);
    }

    @GameTest(template = "empty")
    public static void permissionRequiresCreativeAndCommandLevelTwo(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            ServerPlayer allowed = open(scope, helper, true);
            allowed.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, browser_cheat_network.canUse(allowed), "creative plus permission level 2 can use cheat mode");

            allowed.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            yes(helper, !browser_cheat_network.canUse(allowed), "survival is rejected even with command permission");

            ServerPlayer noPermission = open(scope, helper, false);
            noPermission.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, !browser_cheat_network.canUse(noPermission), "creative without command permission is rejected");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void giveUsesRealPlayerCommandAndRealStackSize(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            ServerPlayer single = open(scope, helper, true);
            single.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var singleResult=give(single,new ItemStack(Items.STONE),false);
            yes(helper, singleResult==browser_cheat_network.Result.OK && single.getInventory().countItem(Items.STONE)==1,
                    "single give produces exactly one item: result="+singleResult+" count="+single.getInventory().countItem(Items.STONE));

            ServerPlayer stack = open(scope, helper, true);
            stack.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, give(stack, new ItemStack(Items.STONE), true) == browser_cheat_network.Result.OK
                    && stack.getInventory().countItem(Items.STONE) == 64, "full-stack give reads the real max stack size");

            ServerPlayer unstackable = open(scope, helper, true);
            unstackable.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, give(unstackable, new ItemStack(Items.POTION), true) == browser_cheat_network.Result.OK
                    && unstackable.getInventory().countItem(Items.POTION) == 1, "unstackable give is exactly one");

            ItemStack tea = new ItemStack(items.CALMING_FLOWER_TEA.get());
            yes(helper, tea.getMaxStackSize() == 16, "tea real max stack is 16");
            ServerPlayer teaPlayer = open(scope, helper, true);
            teaPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, give(teaPlayer, tea, true) == browser_cheat_network.Result.OK
                    && teaPlayer.getInventory().countItem(items.CALMING_FLOWER_TEA.get()) == 16,
                    "full-stack cheat give uses tea's new 16 limit");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void givePreservesLegalPotionAndEnchantedBookVariants(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            ServerPlayer potionPlayer = open(scope, helper, true);
            potionPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            ItemStack potion = new ItemStack(Items.POTION);
            PotionUtils.setPotion(potion, Potions.HEALING);
            var potionResult=give(potionPlayer,potion,false); yes(helper, potionResult==browser_cheat_network.Result.OK, "potion give succeeds: "+potionResult);
            ItemStack receivedPotion = potionPlayer.getInventory().items.stream()
                    .filter(s -> s.is(Items.POTION)).findFirst().orElse(ItemStack.EMPTY);
            yes(helper, !receivedPotion.isEmpty() && receivedPotion.hasTag()
                    && receivedPotion.getTag().contains("Potion", 8)
                    && receivedPotion.getTag().getString("Potion").equals("minecraft:healing"),
                    "potion NBT is preserved");

            ServerPlayer bookPlayer = open(scope, helper, true);
            bookPlayer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 1));
            var bookResult=give(bookPlayer,book,false); yes(helper, bookResult==browser_cheat_network.Result.OK, "enchanted book give succeeds: "+bookResult);
            ItemStack receivedBook = bookPlayer.getInventory().items.stream()
                    .filter(s -> s.is(Items.ENCHANTED_BOOK)).findFirst().orElse(ItemStack.EMPTY);
            yes(helper, !receivedBook.isEmpty() && receivedBook.hasTag()
                    && receivedBook.getTag().contains("StoredEnchantments", 9)
                    && !receivedBook.getTag().getList("StoredEnchantments", 10).isEmpty(),
                    "enchanted book NBT is preserved");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void invalidAndUnauthorizedGiveRequestsAreRejected(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            ServerPlayer survival = open(scope, helper, true);
            yes(helper, give(survival, new ItemStack(Items.STONE), false) == browser_cheat_network.Result.DENIED,
                    "survival cannot use give even with permission");

            ServerPlayer noPermission = open(scope, helper, false);
            noPermission.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, give(noPermission, new ItemStack(Items.STONE), false) == browser_cheat_network.Result.DENIED,
                    "creative without permission cannot use give");

            ServerPlayer allowed = open(scope, helper, true);
            allowed.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            yes(helper, give(allowed, ItemStack.EMPTY, false) == browser_cheat_network.Result.INVALID,
                    "empty item request is rejected");
            yes(helper, browser_cheat_network.give(allowed, new ItemStack(Items.STONE), false, 2,
                    allowed.containerMenu.containerId + 99) == browser_cheat_network.Result.INVALID,
                    "stale container id is rejected");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void repeatedGiveIsThrottled(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            ServerPlayer player = open(scope, helper, true);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var first = give(player, new ItemStack(Items.STONE), false);
            var second = give(player, new ItemStack(Items.STONE), false);
            yes(helper, first == browser_cheat_network.Result.OK && second == browser_cheat_network.Result.COOLDOWN
                    && player.getInventory().countItem(Items.STONE) == 1, "rapid duplicate give is throttled");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void giveDoesNotDirectlyUnlockRecipeOrResearch(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            ServerPlayer player = open(scope, helper, true);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            ResourceLocation stoneRecipe = id("stone");
            yes(helper, !recipe_unlocks.isUnlocked(player, stoneRecipe), "fixture starts without the stone recipe");
            var giveResult=give(player,new ItemStack(Items.STONE),false); yes(helper, giveResult==browser_cheat_network.Result.OK
                    && player.getInventory().countItem(Items.STONE)==1, "give succeeds: "+giveResult);
            yes(helper, !recipe_unlocks.isUnlocked(player, stoneRecipe), "give alone does not grant the recipe");
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void allItemCatalogIsLegalAndIncludesVariants(GameTestHelper helper)
    {
        var all = browser_item_catalog.all();
        yes(helper, all.stream().anyMatch(s -> s.is(Items.DIRT)), "item with no crafting recipe is present");
        yes(helper, all.stream().noneMatch(ItemStack::isEmpty) && all.stream().noneMatch(s -> s.is(Items.AIR)),
                "catalog excludes air and empty stacks");
        yes(helper, all.stream().anyMatch(s -> s.is(Items.POTION) && s.hasTag()
                && s.getTag().contains("Potion", 8)), "potion variants are present");
        yes(helper, all.stream().anyMatch(s -> s.is(Items.ENCHANTED_BOOK) && s.hasTag()
                && s.getTag().contains("StoredEnchantments", 9)), "enchanted book variants are present");
        Set<String> keys = new HashSet<>();
        for (ItemStack stack : all)
            if (!keys.add(browser_item_catalog.key(stack)))
            {
                helper.fail("duplicate catalog entry: " + browser_item_catalog.key(stack));
                return;
            }
        helper.succeed();
    }
}

