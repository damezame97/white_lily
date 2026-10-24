package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.capability.food_capacity;
import com.damezame97.white_lily.item.capacity_test_item;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.procedures.capacity_test_events;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
public class CapacityTestGameTests {
    @GameTest(template = "empty")
    public static void foodCapacityEatingAndPersistence(GameTestHelper h) {
        FoodData food = new FoodData();
        food_capacity.set(food, 30);
        h.assertTrue(food.getFoodLevel() == 20 && food.needsFood(), "Increasing capacity must not refill");
        food.eat(15, 0);
        h.assertTrue(food.getFoodLevel() == 30 && !food.needsFood(), "Eating must use the real new cap");
        CompoundTag nbt = new CompoundTag(); food.addAdditionalSaveData(nbt);
        FoodData loaded = new FoodData(); loaded.readAdditionalSaveData(nbt);
        h.assertTrue(food_capacity.get(loaded) == 30 && loaded.getFoodLevel() == 30, "Save round trip");
        food_capacity.set(loaded, -100);
        h.assertTrue(food_capacity.get(loaded) == 1 && loaded.getFoodLevel() == 1, "Lower bound and clamp");
        loaded.eat(20, 0);
        h.assertTrue(loaded.getFoodLevel() == 1, "Eating below vanilla cap must clamp");
        loaded.readAdditionalSaveData(new CompoundTag());
        h.assertTrue(food_capacity.get(loaded) == 20, "Old saves default to 20");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void itemsWorkInSurvivalAndDoNotConsume(GameTestHelper h) {
        Player p = player(h);
        var stack = new ItemStack(items.HEALTH_CAPACITY_PLUS_TEN.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMaxHealth() == 30 && p.getHealth() == 20 && stack.getCount() == 1, "No refill or consumption");
        var foodStack = new ItemStack(items.FOOD_CAPACITY_PLUS_TEN.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, foodStack);
        foodStack.getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(food_capacity.get(p.getFoodData()) == 30 && p.getFoodData().getFoodLevel() == 20
                && foodStack.getCount() == 1, "Survival hunger tool works without refill or consumption");
        capacity_test_item.changeHealth(p, -100);
        h.assertTrue(p.getMaxHealth() == 1 && p.getHealth() == 1, "Health minimum and clamp");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void healthModifiersAndRespawn(GameTestHelper h) {
        Player p = player(h);
        var attr = p.getAttribute(Attributes.MAX_HEALTH);
        attr.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "test scaling", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
        attr.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "test total", 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
        capacity_test_item.changeHealth(p, 1);
        h.assertTrue(Math.abs(p.getMaxHealth() - 61) < 0.0001, "Effective health must increase by exactly one");
        Player old = player(h); capacity_test_item.changeHealth(old, 10);
        food_capacity.set(old.getFoodData(), 31);
        Player next = player(h);
        capacity_test_events.clone(new PlayerEvent.Clone(next, old, true));
        h.assertTrue(next.getMaxHealth() == 30 && food_capacity.get(next.getFoodData()) == 31, "Caps survive respawn");
        h.succeed();
    }

    private static Player player(GameTestHelper h) {
        return new Player(h.getLevel(), h.absolutePos(net.minecraft.core.BlockPos.ZERO), 0,
                new GameProfile(UUID.randomUUID(), "CapacityTest")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return getAbilities().instabuild; }
        };
    }
}
