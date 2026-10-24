package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.creative_tabs;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** Exercises real menus and the book item, including stale results and independent player saves. */
@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class RecipeUnlockGameTests
{
    private static final ResourceLocation GOLD = ResourceLocation.fromNamespaceAndPath("minecraft", "golden_sword");

    private static CraftingMenu table(GameTestHelper h, Fixture player)
    {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        player.containerMenu = menu;
        return menu;
    }

    private static void putGold(CraftingMenu menu, int count)
    {
        menu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT, count));
        menu.getSlot(4).set(new ItemStack(Items.GOLD_INGOT, count));
        menu.getSlot(7).set(new ItemStack(Items.STICK, count));
    }

    @GameTest(template = "empty")
    public static void goldRecipeIsLockedForManualCraftingStaleOutputAndRecipePlacement(GameTestHelper h)
    {
        Fixture player = new Fixture(h);
        recipe_unlocks.initialize(player);
        yes(h, !recipe_unlocks.isUnlocked(player, GOLD), "金剑不在初始解锁中");
        Recipe<?> recipe = h.getLevel().getRecipeManager().byKey(GOLD).orElseThrow();
        yes(h, player.awardRecipes(List.of(recipe)) == 0 && !player.getRecipeBook().contains(recipe),
                "原版配方奖励不能绕过个人白名单");
        CraftingMenu menu = table(h, player);
        putGold(menu, 2);
        yes(h, menu.getSlot(0).getItem().isEmpty(), "手动摆出金剑不预览锁定结果");
        yes(h, menu.quickMoveStack(player, 0).isEmpty(), "直接Shift不能制作锁定金剑");
        ((ResultContainer) menu.getSlot(0).container).setRecipeUsed(recipe);
        menu.getSlot(0).set(new ItemStack(Items.GOLDEN_SWORD));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        yes(h, menu.getCarried().isEmpty(), "伪造或过期的结果普通取出也被拒绝");
        yes(h, menu.quickMoveStack(player, 0).isEmpty() && player.getInventory().countItem(Items.GOLDEN_SWORD) == 0,
                "过期结果的直接Shift在转移前拒绝");
        yes(h, menu.getSlot(1).getItem().getCount() == 2 && menu.getSlot(4).getItem().getCount() == 2,
                "拒绝不消耗材料");
        menu.getSlot(0).set(ItemStack.EMPTY);
        for (int i : new int[]{1, 4, 7}) menu.getSlot(i).set(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        player.getInventory().setItem(1, new ItemStack(Items.STICK));
        player.getRecipeBook().add(recipe); // A legacy known-recipe entry must not authorize placement.
        @SuppressWarnings("unchecked") Recipe<CraftingContainer> typed = (Recipe<CraftingContainer>) recipe;
        new ServerPlaceRecipe<CraftingContainer>(menu).recipeClicked(player, typed, false);
        yes(h, ((CraftingContainer) menu.getSlot(1).container).isEmpty(), "旧配方书残留仍不能自动填充");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeBookUnlocksImmediatelyOnceAndOnlyForItsUser(GameTestHelper h)
    {
        Fixture owner = new Fixture(h), other = new Fixture(h);
        recipe_unlocks.initialize(owner);
        recipe_unlocks.initialize(other);
        CraftingMenu ownerMenu = table(h, owner), otherMenu = table(h, other);
        putGold(ownerMenu, 2);
        putGold(otherMenu, 2);
        ItemStack books = new ItemStack(items.GOLDEN_SWORD_RECIPE_BOOK.get(), 2);
        owner.setItemInHand(InteractionHand.MAIN_HAND, books);
        items.GOLDEN_SWORD_RECIPE_BOOK.get().use(h.getLevel(), owner, InteractionHand.MAIN_HAND);
        yes(h, recipe_unlocks.isUnlocked(owner, GOLD) && !recipe_unlocks.isUnlocked(other, GOLD), "书只授予使用者金剑权限");
        yes(h, books.getCount() == 1, "生存首次解锁消耗一本");
        yes(h, ownerMenu.getSlot(0).getItem().is(Items.GOLDEN_SWORD) && otherMenu.getSlot(0).getItem().isEmpty(),
                "解锁立即刷新已摆好的网格且不影响另一玩家");
        items.GOLDEN_SWORD_RECIPE_BOOK.get().use(h.getLevel(), owner, InteractionHand.MAIN_HAND);
        yes(h, books.getCount() == 1, "重复阅读不消耗书");
        ownerMenu.clicked(0, 0, ClickType.QUICK_MOVE, owner);
        yes(h, owner.getInventory().countItem(Items.GOLDEN_SWORD) == 2, "解锁后真实Shift制作两批");
        yes(h, ((CraftingContainer) ownerMenu.getSlot(1).container).isEmpty(), "制作正确消耗两批材料");
        Fixture creative = new Fixture(h);
        creative.getAbilities().instabuild = true;
        ItemStack creativeBook = new ItemStack(items.GOLDEN_SWORD_RECIPE_BOOK.get());
        creative.setItemInHand(InteractionHand.MAIN_HAND, creativeBook);
        items.GOLDEN_SWORD_RECIPE_BOOK.get().use(h.getLevel(), creative, InteractionHand.MAIN_HAND);
        yes(h, recipe_unlocks.isUnlocked(creative, GOLD) && creativeBook.getCount() == 1, "创造模式正常解锁且保留书");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeProgressSurvivesPlayerSaveDeathAndDimensionClone(GameTestHelper h)
    {
        Fixture original = new Fixture(h);
        recipe_unlocks.initialize(original);
        yes(h, recipe_unlocks.unlock(original, GOLD), "首次显式解锁成功");
        yes(h, !recipe_unlocks.unlock(original, GOLD), "重复解锁是幂等操作");
        CompoundTag saved = original.serializeNBT();
        Fixture loaded = new Fixture(h);
        loaded.deserializeNBT(saved);
        recipe_unlocks.initialize(loaded);
        yes(h, recipe_unlocks.isUnlocked(loaded, GOLD), "真实玩家NBT存读保留配方进度");
        Fixture respawn = new Fixture(h);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawn, original, true));
        recipe_unlocks.initialize(respawn);
        yes(h, recipe_unlocks.isUnlocked(respawn, GOLD), "死亡Clone保留个人配方");
        Fixture dimension = new Fixture(h);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(dimension, respawn, false));
        recipe_unlocks.initialize(dimension);
        yes(h, recipe_unlocks.isUnlocked(dimension, GOLD), "非死亡Clone保留个人配方");
        yes(h, !recipe_unlocks.isUnlocked(new Fixture(h), GOLD), "新玩家不继承其他玩家进度");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeBookCreativeTabAndTextureAreShipped(GameTestHelper h)
    {
        CreativeModeTab tab = creative_tabs.RECIPE_BOOKS.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        yes(h, tab.shouldDisplay() && tab.getIconItem().is(items.GOLDEN_SWORD_RECIPE_BOOK.get()), "配方书创造栏显示真实书图标");
        // 本轮明确把 7 本铁器知识书加入既有配方书栏；金剑书仍保留在首位。
        yes(h, tab.getDisplayItems().size() == 8 && tab.getDisplayItems().iterator().next().is(items.GOLDEN_SWORD_RECIPE_BOOK.get()),
                "金剑书与7本新知识书共用一个现有配方书栏，金剑书仍在首位");
        yes(h, tab.getDisplayItems().stream().anyMatch(stack -> stack.is(items.BLACKSMITH_TOOLS_NOTES.get()))
                        && tab.getDisplayItems().stream().anyMatch(stack -> stack.is(items.TRANSPORT_NOTES.get())),
                "新知识书进入既有配方书栏");
        yes(h, tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(items.GOLDEN_SWORD_RECIPE_BOOK.get())),
                "范例书也进入创造搜索");
        for (String file : List.of("models/item/golden_sword_recipe_book.json", "textures/item/golden_sword_recipe_book.png"))
            try (var input = RecipeUnlockGameTests.class.getResourceAsStream("/assets/white_lily/" + file))
            {
                yes(h, input != null && input.readAllBytes().length > 0, "包内资源存在：" + file);
            }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        h.succeed();
    }

    private static final class Fixture extends ServerPlayer
    {
        Fixture(GameTestHelper h)
        {
            super(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "RecipeQA"));
            connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), this)
            {
                @Override public void send(Packet<?> packet) { }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { }
            };
            setHealth(getMaxHealth());
            getAbilities().instabuild = false;
        }
    }
}
