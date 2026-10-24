package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.recipe_machine_owner;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** 熔炉属于解锁范围外：冶炼与取货都与原版一致，不再绑定操作者。 */
@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class FurnaceTakePermissionGameTests
{
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("minecraft", path); }

    @GameTest(template = "empty")
    public static void anyViewerCanTakeFinishedFurnaceResultWithoutOwnerOrRecipe(GameTestHelper helper)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(helper))
        {
            BlockPos relative = new BlockPos(1, 2, 1);
            helper.setBlock(relative, Blocks.FURNACE);
            FurnaceBlockEntity furnace = (FurnaceBlockEntity) helper.getBlockEntity(relative);
            ServerPlayer owner = ProcessingTestPlayers.create(helper);
            ServerPlayer viewer = ProcessingTestPlayers.create(helper);
            recipe_machine_owner.bind(furnace, owner);

            furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 2));
            viewer.setPos(helper.absolutePos(relative).getX() + .5, helper.absolutePos(relative).getY(),
                    helper.absolutePos(relative).getZ() + .5);
            FurnaceMenu menu = new FurnaceMenu(7, viewer.getInventory(), furnace, new SimpleContainerData(4));
            viewer.containerMenu = menu;

            yes(helper, !recipe_unlocks.isUnlocked(viewer, id("cooked_beef")), "viewer does not personally know the cooking recipe");
            yes(helper, !recipe_unlocks.requiresUnlock(viewer, id("cooked_beef")), "熔炉配方不在解锁范围内");
            yes(helper, recipe_machine_owner.allowed(furnace, id("cooked_beef")), "冶炼不再检查任何操作者或解锁权限");
            yes(helper, !furnace.getPersistentData().hasUUID("white_lily_recipe_operator"), "不再给熔炉写入操作者绑定");
            menu.clicked(2, 0, ClickType.PICKUP, viewer);
            yes(helper, menu.getCarried().is(Items.COOKED_BEEF) && menu.getCarried().getCount() == 2,
                    "normal click takes the finished result without owner or recipe permission");

            menu.setCarried(ItemStack.EMPTY);
            furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 2));
            menu.clicked(2, 0, ClickType.QUICK_MOVE, viewer);
            yes(helper, viewer.getInventory().countItem(Items.COOKED_BEEF) == 2 && furnace.getItem(2).isEmpty(),
                    "shift-click also takes the finished result");

            helper.getLevel().players().remove(owner);
            furnace.setItem(2, new ItemStack(Items.COOKED_BEEF, 2));
            menu.clicked(2, 0, ClickType.QUICK_MOVE, viewer);
            yes(helper, viewer.getInventory().countItem(Items.COOKED_BEEF) == 4,
                    "offline owner does not block taking an already finished result");
            helper.succeed();
        }
    }
}
