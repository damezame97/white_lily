package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.recipe_machine_owner;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Per-recipe whitelist check before vanilla starts or consumes cooking ingredients. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinFurnaceRecipeGate {
    @Inject(method="canBurn",at=@At("HEAD"),cancellable=true)
    private void whiteLily$lockedCooking(RegistryAccess registry,Recipe<?> recipe,NonNullList<ItemStack> stacks,int max,CallbackInfoReturnable<Boolean> callback) {
        if(recipe!=null && !recipe_machine_owner.allowed((AbstractFurnaceBlockEntity)(Object)this,recipe.getId())) callback.setReturnValue(false);
    }
    @Inject(method="setRecipeUsed",at=@At("HEAD"))
    private void whiteLily$rememberCooking(Recipe<?> recipe,CallbackInfo callback) {
        if(recipe!=null) ((AbstractFurnaceBlockEntity)(Object)this).getPersistentData().putString("white_lily_last_recipe",recipe.getId().toString());
    }
}
