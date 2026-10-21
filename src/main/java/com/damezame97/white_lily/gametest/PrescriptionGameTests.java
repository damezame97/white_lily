package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.white_lily;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

// Forge 通过注解发现测试；Level 生命周期由游戏引擎管理。
@SuppressWarnings({"unused", "resource"})
@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
public class PrescriptionGameTests
{
    private static final ResourceLocation RECIPE_ID = ResourceLocation.fromNamespaceAndPath(white_lily.MODID, "revealed_prescription");
    private static final BlockPos WATER_POS = new BlockPos(1, 2, 1);

    @GameTest(template = "empty")
    public static void waterConvertsOnlyAtFortyTicks(GameTestHelper helper)
    {
        helper.setBlock(WATER_POS, Blocks.WATER);
        ItemEntity entity = blank(helper, 23);
        tickAt(helper, entity, WATER_POS, 39);
        helper.assertTrue(entity.getItem().is(items.BLANK_PRESCRIPTION.get()), "不足 40 刻不能显字");
        tickAt(helper, entity, WATER_POS, 1);
        helper.assertTrue(entity.getItem().is(items.REVEALED_PRESCRIPTION.get()), "连续 40 刻应显字");
        helper.assertTrue(entity.getItem().getCount() == 23 && !entity.isRemoved(), "应整组原地转化，数量不变");
        tickAt(helper, entity, WATER_POS, 40);
        helper.assertTrue(entity.getItem().getCount() == 23, "显字后不能再次转换或复制");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leavingWaterResetsTimer(GameTestHelper helper)
    {
        helper.setBlock(WATER_POS, Blocks.WATER);
        ItemEntity entity = blank(helper, 1);
        tickAt(helper, entity, WATER_POS, 30);
        tickAt(helper, entity, WATER_POS.above(2), 1);
        tickAt(helper, entity, WATER_POS, 39);
        helper.assertTrue(entity.getItem().is(items.BLANK_PRESCRIPTION.get()), "离水应清零，不能累加两次浸泡");
        tickAt(helper, entity, WATER_POS, 1);
        helper.assertTrue(entity.getItem().is(items.REVEALED_PRESCRIPTION.get()), "重新连续入水 40 刻应显字");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entitiesHaveSeparateTimersAndFlowingWaterWorks(GameTestHelper helper)
    {
        helper.setBlock(WATER_POS, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        ItemEntity first = blank(helper, 2);
        ItemEntity second = blank(helper, 3);
        tickAt(helper, first, WATER_POS, 39);
        tickAt(helper, second, WATER_POS, 1);
        tickAt(helper, first, WATER_POS, 1);
        helper.assertTrue(first.getItem().is(items.REVEALED_PRESCRIPTION.get()), "流动水也应显字");
        helper.assertTrue(second.getItem().is(items.BLANK_PRESCRIPTION.get()), "每个实体应独立计时");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dryItemsAndLavaDoNotReveal(GameTestHelper helper)
    {
        ItemEntity dry = blank(helper, 4);
        tickAt(helper, dry, WATER_POS.above(2), 50);
        helper.assertTrue(dry.getItem().is(items.BLANK_PRESCRIPTION.get()), "没有水流体就不能显字，雨水也不例外");
        helper.setBlock(WATER_POS, Blocks.LAVA);
        ItemEntity lava = blank(helper, 4);
        // 只运行物品钩子，避免原版熔岩烧毁物品干扰流体判定测试。
        lava.setPos(Vec3.atCenterOf(helper.absolutePos(WATER_POS)));
        for (int i = 0; i < 50; i++)
        {
            lava.getItem().onEntityItemUpdate(lava);
        }
        helper.assertTrue(lava.getItem().is(items.BLANK_PRESCRIPTION.get()), "其他液体不能显字");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void conversionPreservesEntityMotionAndTags(GameTestHelper helper)
    {
        helper.setBlock(WATER_POS, Blocks.WATER);
        ItemEntity entity = blank(helper, 64);
        entity.getItem().getOrCreateTag().putString("test_marker", "preserved");
        entity.setPos(Vec3.atCenterOf(helper.absolutePos(WATER_POS)));
        Vec3 position = entity.position();
        Vec3 velocity = new Vec3(0.02D, 0.01D, -0.03D);
        entity.setDeltaMovement(velocity);
        for (int i = 0; i < 40; i++)
        {
            entity.getItem().onEntityItemUpdate(entity);
        }
        helper.assertTrue(entity.position().equals(position) && entity.getDeltaMovement().equals(velocity),
                "显字钩子不能改坐标或速度");
        helper.assertTrue(entity.getItem().is(items.REVEALED_PRESCRIPTION.get())
                && entity.getItem().getCount() == 64, "整组转化数量应保留");
        helper.assertTrue("preserved".equals(Objects.requireNonNull(entity.getItem().getTag()).getString("test_marker")), "应保留自定义数据");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void convertedItemsCanBePickedUpByPlayer(GameTestHelper helper)
    {
        helper.setBlock(WATER_POS, Blocks.WATER);
        ItemEntity entity = blank(helper, 8);
        tickAt(helper, entity, WATER_POS, 40);
        entity.setNoPickUpDelay();
        Player player = helper.makeMockSurvivalPlayer();
        entity.playerTouch(player);
        helper.assertTrue(player.getInventory().countItem(items.REVEALED_PRESCRIPTION.get()) == 8
                && entity.isRemoved(), "转化后玩家应能正常拾取，数量不变");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void convertedItemsCanBePickedUpByHopper(GameTestHelper helper)
    {
        helper.setBlock(WATER_POS, Blocks.WATER);
        ItemEntity entity = blank(helper, 8);
        tickAt(helper, entity, WATER_POS, 40);
        BlockPos hopperPos = WATER_POS.below();
        helper.setBlock(hopperPos, Blocks.HOPPER);
        entity.setPos(Vec3.atCenterOf(helper.absolutePos(WATER_POS)));
        helper.getLevel().addFreshEntity(entity);
        HopperBlockEntity hopper = (HopperBlockEntity) Objects.requireNonNull(helper.getBlockEntity(hopperPos));
        HopperBlockEntity.suckInItems(helper.getLevel(), hopper);
        helper.assertTrue(hopper.countItem(items.REVEALED_PRESCRIPTION.get()) == 8
                && entity.isRemoved(), "转化后漏斗应能正常吸入，数量不变");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeAcceptsOnlyOrdinaryWaterInAnySlotOrder(GameTestHelper helper)
    {
        TestCraftingMenu menu = menu(helper);
        menu.getSlot(9).set(new ItemStack(items.BLANK_PRESCRIPTION.get()));
        menu.getSlot(3).set(water(1));
        helper.assertTrue(recipe(helper).matches(menu.grid(), helper.getLevel()), "无序放置的普通水瓶应匹配");
        ItemStack[] rejected = {
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING),
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD),
                PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.WATER),
                PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.WATER),
                new ItemStack(Items.POTION), new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.WATER_BUCKET)
        };
        for (ItemStack input : rejected)
        {
            menu.getSlot(3).set(input);
            helper.assertTrue(!recipe(helper).matches(menu.grid(), helper.getLevel()), "不应接受 " + input);
        }
        ItemStack customWater = water(1);
        customWater.getOrCreateTag().put("CustomPotionEffects", new net.minecraft.nbt.ListTag());
        menu.getSlot(3).set(customWater);
        helper.assertTrue(!recipe(helper).matches(menu.grid(), helper.getLevel()), "严格只接受普通水瓶 NBT");
        menu.getSlot(3).set(water(1));
        menu.getSlot(1).set(new ItemStack(Items.DIRT));
        helper.assertTrue(!recipe(helper).matches(menu.grid(), helper.getLevel()), "额外材料必须使配方失效");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCraftReturnsExactlyOneBottle(GameTestHelper helper)
    {
        TestCraftingMenu menu = menu(helper);
        menu.getSlot(1).set(new ItemStack(items.BLANK_PRESCRIPTION.get()));
        menu.getSlot(2).set(water(1));
        menu.clicked(0, 0, ClickType.PICKUP, menu.player);
        helper.assertTrue(menu.getCarried().is(items.REVEALED_PRESCRIPTION.get())
                && menu.getCarried().getCount() == 1, "单次合成应取出一张显字处方");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().is(Items.GLASS_BOTTLE)
                && menu.getSlot(2).getItem().getCount() == 1, "原料应消耗，水瓶格应留下一个空瓶");
        helper.assertTrue(menu.player.getInventory().countItem(Items.GLASS_BOTTLE) == 0, "不能多返还一个瓶子");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedShiftCraftsReturnOneBottlePerWaterBottle(GameTestHelper helper)
    {
        TestCraftingMenu menu = menu(helper);
        menu.getSlot(1).set(new ItemStack(items.BLANK_PRESCRIPTION.get(), 4));
        for (int i = 0; i < 4; i++)
        {
            menu.getSlot(2).set(water(1));
            menu.clicked(0, 0, ClickType.QUICK_MOVE, menu.player);
            helper.assertTrue(menu.getSlot(2).getItem().is(Items.GLASS_BOTTLE), "每次 Shift 合成后水瓶格应留下空瓶");
            menu.clicked(2, 0, ClickType.QUICK_MOVE, menu.player);
        }
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "四张空白处方应全部消耗");
        helper.assertTrue(menu.player.getInventory().countItem(items.REVEALED_PRESCRIPTION.get()) == 4
                && menu.player.getInventory().countItem(Items.GLASS_BOTTLE) == 4, "连续合成数量应严格守恒");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftBatchWithStackedInputReturnsEveryBottle(GameTestHelper helper)
    {
        TestCraftingMenu menu = menu(helper);
        menu.getSlot(1).set(new ItemStack(items.BLANK_PRESCRIPTION.get(), 5));
        // 原版水瓶不可堆叠；额外覆盖命令/其他模组提供堆叠水瓶时的真正批量分支。
        menu.getSlot(2).set(water(5));
        menu.clicked(0, 0, ClickType.QUICK_MOVE, menu.player);
        helper.assertTrue(menu.player.getInventory().countItem(items.REVEALED_PRESCRIPTION.get()) == 5,
                "一次 Shift 批量应得到五张显字处方");
        helper.assertTrue(menu.player.getInventory().countItem(Items.GLASS_BOTTLE) == 4
                && menu.getSlot(2).getItem().is(Items.GLASS_BOTTLE)
                && menu.getSlot(2).getItem().getCount() == 1, "五瓶水应恰好返还五个空瓶");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "不能复制原料");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryDoesNotConsumeShiftCraftInputs(GameTestHelper helper)
    {
        TestCraftingMenu menu = menu(helper);
        for (int slot = 0; slot < 36; slot++)
        {
            menu.player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        menu.getSlot(1).set(new ItemStack(items.BLANK_PRESCRIPTION.get(), 3));
        menu.getSlot(2).set(water(1));
        menu.clicked(0, 0, ClickType.QUICK_MOVE, menu.player);
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 3
                && menu.getSlot(2).getItem().is(Items.POTION), "背包无处放成品时不能消耗原料");

        // 普通取出仍允许将成品放在鼠标上，空瓶必须留在原水瓶格，不依赖背包空位。
        menu.clicked(0, 0, ClickType.PICKUP, menu.player);
        helper.assertTrue(menu.getCarried().is(items.REVEALED_PRESCRIPTION.get())
                && menu.getCarried().getCount() == 1, "背包满时仍应允许普通取出一次成品");
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 2
                && menu.getSlot(2).getItem().is(Items.GLASS_BOTTLE)
                && menu.getSlot(2).getItem().getCount() == 1, "背包满时空瓶也不能丢失或复制");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeNetworkRoundTripPreservesStrictWaterIngredient(GameTestHelper helper)
    {
        CraftingRecipe original = recipe(helper);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            writeRecipe(buffer, original);
            CraftingRecipe copy = (CraftingRecipe) Objects.requireNonNull(original.getSerializer().fromNetwork(RECIPE_ID, buffer));
            TestCraftingMenu menu = menu(helper);
            menu.getSlot(1).set(new ItemStack(items.BLANK_PRESCRIPTION.get()));
            menu.getSlot(2).set(water(1));
            helper.assertTrue(copy.matches(menu.grid(), helper.getLevel()), "联网同步后普通水瓶仍应匹配");
            menu.getSlot(2).set(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING));
            helper.assertTrue(!copy.matches(menu.grid(), helper.getLevel()), "联网同步后不能接受其他药水");
        }
        finally
        {
            buffer.release();
        }
        helper.succeed();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeRecipe(FriendlyByteBuf buffer, CraftingRecipe recipe)
    {
        ((RecipeSerializer) recipe.getSerializer()).toNetwork(buffer, recipe);
    }

    private static CraftingRecipe recipe(GameTestHelper helper)
    {
        return (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(RECIPE_ID).orElseThrow();
    }

    private static ItemStack water(int count)
    {
        return PotionUtils.setPotion(new ItemStack(Items.POTION, count), Potions.WATER);
    }

    private static ItemEntity blank(GameTestHelper helper, int count)
    {
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(WATER_POS));
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.x, pos.y, pos.z,
                new ItemStack(items.BLANK_PRESCRIPTION.get(), count));
        entity.setNoGravity(true);
        return entity;
    }

    private static void tickAt(GameTestHelper helper, ItemEntity entity, BlockPos relativePos, int ticks)
    {
        for (int tick = 0; tick < ticks; tick++)
        {
            // 固定样本位置，避免浮力把测试物品推出这一个水方块；执行完整 ItemEntity.tick。
            entity.setPos(Vec3.atCenterOf(helper.absolutePos(relativePos)));
            entity.setDeltaMovement(Vec3.ZERO);
            entity.tick();
        }
    }

    private static TestCraftingMenu menu(GameTestHelper helper)
    {
        return new TestCraftingMenu(helper.makeMockSurvivalPlayer());
    }

    /** 使用真正的点击、ResultSlot 与 Shift 流程；仅省去假玩家的网络发包。 */
    private static class TestCraftingMenu extends CraftingMenu
    {
        private final Player player;

        TestCraftingMenu(Player player)
        {
            super(1, player.getInventory());
            this.player = player;
        }

        CraftingContainer grid()
        {
            return (CraftingContainer) getSlot(1).container;
        }

        @Override
        public void slotsChanged(@javax.annotation.Nonnull Container container)
        {
            if (player != null)
            {
                ItemStack result = player.level().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, grid(), player.level())
                        .map(recipe -> recipe.assemble(grid(), player.level().registryAccess()))
                        .orElse(ItemStack.EMPTY);
                getSlot(0).set(result);
            }
        }
    }
}
