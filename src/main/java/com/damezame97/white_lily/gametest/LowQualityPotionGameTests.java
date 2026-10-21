package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.item.low_quality_potion;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.damezame97.white_lily.white_lily;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

// Forge 通过注解发现测试；Level 生命周期由游戏引擎管理。
@SuppressWarnings({"unused", "resource"})
@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
public class LowQualityPotionGameTests
{
    @GameTest(template = "empty")
    public static void lastPotionReturnsBottleAndRestoresMana(GameTestHelper helper)
    {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setHealth(10.0F);
        player.getFoodData().setFoodLevel(12);
        MagicData mana = MagicData.getPlayerMagicData(player);
        mana.setMana(0.0F);
        ItemStack potion = new ItemStack(items.LOW_QUALITY_POTION.get());

        helper.assertTrue(potion.getUseAnimation() == UseAnim.DRINK, "应使用饮用动画");
        ItemStack result = potion.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(result.is(Items.GLASS_BOTTLE) && result.getCount() == 1,
                "最后一瓶喝完应返回一个玻璃瓶");
        helper.assertTrue(player.getHealth() == 10.0F && player.getFoodData().getFoodLevel() == 12,
                "药剂不应恢复生命或饥饿值");
        helper.assertTrue(mana.getMana() == 15.0F || mana.getMana() == 45.0F,
                "药剂应恢复15点魔力，额外触发时共45点");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stackedPotionKeepsRemainderAndAddsBottle(GameTestHelper helper)
    {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        ItemStack potion = new ItemStack(items.LOW_QUALITY_POTION.get(), 2);
        ItemStack result = potion.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(result.is(items.LOW_QUALITY_POTION.get()) && result.getCount() == 1,
                "应只消耗一瓶药剂");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                "空瓶应进入背包");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeDrinkingDoesNotConsumeOrCreateBottles(GameTestHelper helper)
    {
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = true;
        MagicData.getPlayerMagicData(player).setMana(0);
        ItemStack potion = new ItemStack(items.LOW_QUALITY_POTION.get(), 2);
        ItemStack result = potion.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(result.is(items.LOW_QUALITY_POTION.get()) && result.getCount() == 2,
                "创造模式不应消耗药剂");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0,
                "创造模式不应生成额外空瓶");
        helper.assertTrue(MagicData.getPlayerMagicData(player).getMana() >= 15, "创造模式仍应获得药效");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void conditionalProbabilitiesLevelsDurationsAndHiddenParticles(GameTestHelper helper)
    {
        float[][] rolls = {{0.30F}, {0.29F, 0.08F}, {0.29F, 0.079F}, {0.99F}};
        for (int scenario = 0; scenario < rolls.length; scenario++)
        {
            Player player = helper.makeMockSurvivalPlayer();
            MagicData mana = MagicData.getPlayerMagicData(player);
            mana.setMana(0);
            FixedRolls random = new FixedRolls(rolls[scenario]);
            low_quality_potion.applyOnConsume(player, random);
            boolean impure = scenario == 1 || scenario == 2;
            boolean bonus = scenario == 2;
            helper.assertTrue(mana.getMana() == (bonus ? 45 : 15), "魔力恢复量应符合分支");
            helper.assertTrue(random.index == (impure ? 2 : 1), "只有肮脏魔力触发后才掷第二次概率");
            helper.assertTrue(player.hasEffect(mob_effects.IMPURE_MAGIC.get()) == impure, "30%概率边界错误");
            helper.assertTrue(player.hasEffect(MobEffects.HUNGER) == bonus, "条件8%概率边界错误");
            if (impure)
            {
                var effect = Objects.requireNonNull(player.getEffect(mob_effects.IMPURE_MAGIC.get()));
                helper.assertTrue(effect.getAmplifier() == 1 && effect.getDuration() == 300 && !effect.isVisible(),
                        "肮脏魔力应为II、300刻、隐藏粒子");
            }
            if (bonus)
            {
                var effect = Objects.requireNonNull(player.getEffect(MobEffects.HUNGER));
                helper.assertTrue(effect.getAmplifier() == 2 && effect.getDuration() == 600 && !effect.isVisible(),
                        "原版饥饿应为III、600刻、隐藏粒子");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fourApplesAndOnlyWaterCraftOnePotionWithoutExtraBottle(GameTestHelper helper)
    {
        Player player = helper.makeMockSurvivalPlayer();
        TestMenu menu = new TestMenu(player);
        CraftingRecipe recipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(white_lily.MODID, low_quality_potion.NAME)).orElseThrow();
        for (int slot : new int[]{1, 3, 7, 9}) menu.getSlot(slot).set(new ItemStack(Items.APPLE));
        ItemStack[] invalid = {
                PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING),
                PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), Potions.WATER),
                PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), Potions.WATER),
                new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.WATER_BUCKET)
        };
        for (ItemStack input : invalid)
        {
            menu.getSlot(5).set(input);
            helper.assertTrue(!recipe.matches(menu.grid(), helper.getLevel()), "应只接受普通水瓶");
        }
        menu.getSlot(5).set(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        menu.getSlot(9).set(ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(menu.grid(), helper.getLevel()), "三枚苹果不能合成");
        menu.getSlot(9).set(new ItemStack(items.MAGIC_APPLE.get()));
        helper.assertTrue(!recipe.matches(menu.grid(), helper.getLevel()), "不能用魔力苹果替代原版苹果");
        menu.getSlot(9).set(new ItemStack(Items.APPLE));
        helper.assertTrue(recipe.matches(menu.grid(), helper.getLevel()), "四个苹果加水瓶应无序合成");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(items.LOW_QUALITY_POTION.get()) && menu.getCarried().getCount() == 1,
                "单次应获得一瓶低质量药剂");
        for (int slot = 1; slot <= 9; slot++) helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "原料应完全消耗");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0,
                "水瓶容器成为药剂瓶，合成时不能额外返还空瓶");
        // 覆盖其他模组提供堆叠水瓶时的 Shift 批量合成。
        menu.setCarried(ItemStack.EMPTY);
        for (int slot : new int[]{1, 3, 7, 9}) menu.getSlot(slot).set(new ItemStack(Items.APPLE, 3));
        menu.getSlot(5).set(PotionUtils.setPotion(new ItemStack(Items.POTION, 3), Potions.WATER));
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().countItem(items.LOW_QUALITY_POTION.get()) == 3,
                "批量应产出三瓶药剂");
        for (int slot = 1; slot <= 9; slot++) helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(), "批量应消耗所有原料");
        helper.succeed();
    }

    private static class FixedRolls extends LegacyRandomSource
    {
        private final float[] rolls;
        private int index;

        FixedRolls(float... rolls)
        {
            super(0L);
            this.rolls = rolls;
        }

        @Override
        public float nextFloat()
        {
            return rolls[index++];
        }
    }

    private static class TestMenu extends CraftingMenu
    {
        private final Player player;

        TestMenu(Player player)
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
                getSlot(0).set(player.level().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, grid(), player.level())
                        .map(recipe -> recipe.assemble(grid(), player.level().registryAccess()))
                        .orElse(ItemStack.EMPTY));
            }
        }
    }
}
