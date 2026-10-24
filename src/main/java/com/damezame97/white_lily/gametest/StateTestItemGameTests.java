package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.food_capacity;
import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.item.capacity_test_item;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
public final class StateTestItemGameTests {
    @GameTest(template = "empty")
    public static void healthRestoreUsesModifiedMaximumWithoutPermission(GameTestHelper h) {
        var p = new MedicalLifecycleGameTests.ToolPlayer(h, false);
        p.setGameMode(GameType.SURVIVAL);
        capacity_test_item.changeHealth(p, 17);
        p.setHealth(3);
        use(p, items.HEALTH_RESTORE_TEST.get());
        h.assertTrue(p.getHealth() == 37 && p.getMaxHealth() == 37, "Restore to actual max, not vanilla 20");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "Restore tool must not be consumed");
        p.setHealth(5);
        p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getHealth() == 5, "Cooldown blocks duplicate use");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foodRestoreUsesCapacityAndConditionalSaturationBonus(GameTestHelper h) {
        var p = new MedicalLifecycleGameTests.ToolPlayer(h, false);
        p.setGameMode(GameType.SURVIVAL);
        food_capacity.set(p.getFoodData(), 37);
        p.getFoodData().setFoodLevel(2);
        p.getFoodData().setSaturation(0);
        p.getFoodData().setExhaustion(3.5F);
        var attr = p.getAttribute(attributes.BUILDING_ENDURANCE.get());
        attr.setBaseValue(7);
        boolean before = h.getLevel().getGameRules().getBoolean(happy_mode.RULE);
        try {
            // Test setup bypasses the intentional in-game rule lock; do not edit world config.
            h.getLevel().getGameRules().getRule(happy_mode.RULE).set(true, null);
            use(p, items.FOOD_RESTORE_TEST.get());
            h.assertTrue(p.getFoodData().getFoodLevel() == 37 && p.getFoodData().getSaturationLevel() == 44,
                    "Restore both actual limits, including saturation bonus");
            h.assertTrue(p.getFoodData().getExhaustionLevel() == 0 && p.getMainHandItem().getCount() == 1,
                    "Clear exhaustion without consuming tool");
            p.getCooldowns().removeCooldown(items.FOOD_RESTORE_TEST.get());
            food_capacity.set(p.getFoodData(), 9);
            h.getLevel().getGameRules().getRule(happy_mode.RULE).set(false, null);
            use(p, items.FOOD_RESTORE_TEST.get());
            h.assertTrue(p.getFoodData().getFoodLevel() == 9 && p.getFoodData().getSaturationLevel() == 9,
                    "Small limits and disabled bonus must also be respected");
        } finally {
            h.getLevel().getGameRules().getRule(happy_mode.RULE).set(before, null);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedDamageBypassesProtectionAndCanKill(GameTestHelper h) {
        var p = new MedicalLifecycleGameTests.ToolPlayer(h, false);
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(20);
        p.setAbsorptionAmount(8);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        p.getAttribute(Attributes.ARMOR).addTransientModifier(new AttributeModifier(UUID.randomUUID(),
                "test armor", 30, AttributeModifier.Operation.ADDITION));
        p.invulnerableTime = 20;
        use(p, items.INSTANT_DAMAGE_TEST.get());
        h.assertTrue(p.getHealth() == 10 && p.getAbsorptionAmount() == 8,
                "Exactly 10 health lost despite protection, without consuming absorption");
        p.getCooldowns().removeCooldown(items.INSTANT_DAMAGE_TEST.get());
        p.setGameMode(GameType.CREATIVE);
        p.setHealth(15);
        use(p, items.INSTANT_DAMAGE_TEST.get());
        h.assertTrue(p.getHealth() == 5, "Creative invulnerability must not prevent this test");
        h.assertTrue(p.getMainHandItem().getCount() == 1, "Damage tool must not be consumed");
        p.getCooldowns().removeCooldown(items.INSTANT_DAMAGE_TEST.get());
        p.setGameMode(GameType.SURVIVAL);
        use(p, items.INSTANT_DAMAGE_TEST.get());
        h.assertTrue(p.getHealth() == 0 && !p.isAlive(), "Insufficient health must cause death");
        h.succeed();
    }

    private static void use(MedicalLifecycleGameTests.ToolPlayer p, Item item) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        item.use(p.level(), p, InteractionHand.MAIN_HAND);
    }
}
