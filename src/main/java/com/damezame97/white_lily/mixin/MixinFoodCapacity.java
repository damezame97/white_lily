package com.damezame97.white_lily.mixin;

import com.damezame97.white_lily.capability.food_capacity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class MixinFoodCapacity implements food_capacity {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Unique private int whiteLily$capacity = 20;

    public int whiteLily$getCapacity() { return whiteLily$capacity; }
    public void whiteLily$setCapacity(int value) {
        whiteLily$capacity = Math.max(1, Math.min(1000000, value));
        foodLevel = Math.max(0, Math.min(foodLevel, whiteLily$capacity));
    }

    @ModifyConstant(method = {"eat(IF)V", "needsFood"}, constant = @Constant(intValue = 20))
    private int whiteLily$foodLimit(int original) { return whiteLily$capacity; }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 20))
    private int whiteLily$fullFood(int original) { return whiteLily$capacity; }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 18))
    private int whiteLily$highFood(int original) { return (int) Math.ceil(whiteLily$capacity * 0.9); }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void whiteLily$read(CompoundTag tag, CallbackInfo ci) {
        whiteLily$setCapacity(tag.contains("white_lily_food_capacity") ? tag.getInt("white_lily_food_capacity") : 20);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void whiteLily$write(CompoundTag tag, CallbackInfo ci) {
        tag.putInt("white_lily_food_capacity", whiteLily$capacity);
    }

    @Inject(method = "setFoodLevel", at = @At("TAIL"))
    private void whiteLily$clamp(int value, CallbackInfo ci) {
        foodLevel = Math.max(0, Math.min(foodLevel, whiteLily$capacity));
    }
}
