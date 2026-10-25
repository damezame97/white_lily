package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.recipe_unlock_client;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** Device view rights are permanent history, not personal crafting unlocks. */
@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class MachineViewRightsGameTests
{
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("minecraft", path); }
    private static boolean viewable(ServerPlayer player, String path) { return recipe_unlocks.viewable(player).contains(id(path)); }
    private static void acquire(ServerPlayer player, net.minecraft.world.item.Item item)
    { recipe_unlocks.acquired(player, new ItemStack(item)); }

    @GameTest(template = "empty")
    public static void incompleteDiscoverySurvivesLogoutAndDeathCopy(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer original = ProcessingTestPlayers.create(h);
            acquire(original, Items.PAPER);
            yes(h, !recipe_unlocks.isUnlocked(original, id("book")), "Paper alone does not unlock books");
            CompoundTag saved = original.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound(recipe_unlocks.DATA_KEY).copy();
            yes(h, saved.getList("seen_items", net.minecraft.nbt.Tag.TAG_STRING)
                    .contains(StringTag.valueOf("minecraft:paper")), "Incomplete discovery is saved immediately");

            recipe_unlocks.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(original));
            recipe_unlocks.initialize(original);
            acquire(original, Items.LEATHER);
            yes(h, recipe_unlocks.isUnlocked(original, id("book")), "Logout cache eviction preserves earlier paper discovery");

            ServerPlayer restored = ProcessingTestPlayers.createWithoutInitialize(h);
            // A fresh player object and copied persisted NBT also cover the death/respawn path.
            CompoundTag persisted = new CompoundTag();
            persisted.put(recipe_unlocks.DATA_KEY, saved);
            restored.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            recipe_unlocks.initialize(restored);
            acquire(restored, Items.LEATHER);
            yes(h, recipe_unlocks.isUnlocked(restored, id("book")), "Fresh player restores incomplete discovery without holding paper");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void eachDeviceRevealsOnlyItsPolicyFilteredCookingTable(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.create(h);
            yes(h, recipe_unlocks.viewable(p).isEmpty(), "无设备历史时浏览器没有烹饪查看权");
            acquire(p, Items.FURNACE);
            yes(h, viewable(p, "cooked_beef") && viewable(p, "charcoal"), "获得熔炉揭示熔炉白名单");
            yes(h, !viewable(p, "cooked_beef_from_smoking") && !viewable(p, "iron_ingot_from_blasting_iron_ore"),
                    "熔炉不揭示烟熏炉或高炉配方");
            yes(h, !recipe_unlocks.isUnlocked(p, id("cooked_beef")), "查看权不等于个人合成解锁");

            acquire(p, Items.SMOKER);
            yes(h, viewable(p, "cooked_beef_from_smoking"), "获得烟熏炉揭示烟熏配方");
            yes(h, !viewable(p, "iron_ingot_from_blasting_iron_ore"), "烟熏炉不揭示高炉配方");

            acquire(p, Items.BLAST_FURNACE);
            yes(h, viewable(p, "iron_ingot_from_blasting_iron_ore"), "获得高炉揭示高炉白名单");
            yes(h, !recipe_unlocks.isUnlocked(p, id("iron_ingot_from_blasting_iron_ore")), "高炉查看权不授予个人合成权限");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void existingDeviceHistoryIsReconciledOnLoginAndClientViewIsSeparate(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.createWithoutInitialize(h);
            CompoundTag root = p.getPersistentData();
            CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag data = new CompoundTag();
            ListTag seen = new ListTag();
            seen.add(StringTag.valueOf("minecraft:furnace"));
            seen.add(StringTag.valueOf("minecraft:smoker"));
            data.put("seen_items", seen);
            data.putLong("generation", 1L);
            persisted.put(recipe_unlocks.DATA_KEY, data);
            root.put(Player.PERSISTED_NBT_TAG, persisted);
            recipe_unlocks.initialize(p);
            yes(h, viewable(p, "cooked_beef") && viewable(p, "cooked_beef_from_smoking")
                            && !viewable(p, "iron_ingot_from_blasting_iron_ore"),
                    "旧玩家已有设备历史在登录初始化时补齐查看权");

            recipe_unlock_client.accept(java.util.Set.of(), java.util.Set.of(id("cooked_beef")), 7L);
            yes(h, recipe_unlock_client.isViewable(id("cooked_beef"))
                            && !recipe_unlock_client.isUnlocked(id("cooked_beef")),
                    "客户端查看集合与个人解锁集合分离");
            recipe_unlock_client.clear();
        }
        h.succeed();
    }
}
