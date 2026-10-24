package com.damezame97.white_lily.capability;

import net.minecraft.world.food.FoodData;

/** Implemented by FoodData; saved alongside vanilla food values. */
public interface food_capacity {
    int whiteLily$getCapacity();
    void whiteLily$setCapacity(int value);
    static int get(FoodData data) { return ((food_capacity) data).whiteLily$getCapacity(); }
    static void set(FoodData data, int value) { ((food_capacity) data).whiteLily$setCapacity(value); }
}
