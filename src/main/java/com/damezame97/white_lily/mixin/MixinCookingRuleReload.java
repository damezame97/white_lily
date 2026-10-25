package com.damezame97.white_lily.mixin;

import com.damezame97.white_lily.machine.machine_rules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinCookingRuleReload {
    @Shadow int cookingProgress;
    @Shadow int cookingTotalTime;
    @Shadow private static int getTotalCookTime(Level level, AbstractFurnaceBlockEntity furnace) { throw new AssertionError(); }
    @Unique private long whiteLily$rules = Long.MIN_VALUE;
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void whiteLily$refreshCooking(Level level, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        var self = (MixinCookingRuleReload) (Object) furnace;
        if (machine_rules.device(furnace) == null) return;
        long revision = machine_rules.state(level.getRecipeManager()).revision();
        if (self.whiteLily$rules != revision) {
            boolean alreadyTicked = self.whiteLily$rules != Long.MIN_VALUE;
            self.whiteLily$rules = revision;
            int total = getTotalCookTime(level, furnace);
            // A new block entity also means a chunk/save was loaded, not necessarily a rule reload.
            // Keep valid saved progress; live reloads or incompatible saved work still start over.
            if (alreadyTicked || total != self.cookingTotalTime || machine_rules.findCooking(furnace, level).isEmpty())
                self.cookingProgress = 0;
            else self.cookingProgress = Math.max(0, Math.min(self.cookingProgress, Math.max(0, total - 1)));
            self.cookingTotalTime = total;
            furnace.setChanged();
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @Redirect(method = {"serverTick", "getTotalCookTime"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private static java.util.Optional<?> whiteLily$deviceRecipe(net.minecraft.world.item.crafting.RecipeManager.CachedCheck check,
            net.minecraft.world.Container input, Level level) {
        if (input instanceof AbstractFurnaceBlockEntity furnace && machine_rules.device(furnace) != null)
            return machine_rules.findCooking(furnace,level);
        return check.getRecipeFor(input,level);
    }
    @Redirect(method = "getRecipesToAwardAndPopExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;byKey(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private java.util.Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> whiteLily$privateRecipeExperience(
            net.minecraft.world.item.crafting.RecipeManager manager, net.minecraft.resources.ResourceLocation id) {
        var original=manager.byKey(id);
        if(original.isPresent()) return original;
        var self=(AbstractFurnaceBlockEntity)(Object)this;
        var device=machine_rules.device(self);
        return device==null?java.util.Optional.empty():machine_rules.recipes(manager,device).stream().filter(r->r.getId().equals(id)).findFirst();
    }
    @Redirect(method = "awardUsedRecipesAndPopExperience", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;awardRecipes(Ljava/util/Collection;)I"))
    private int whiteLily$awardGlobalRecipesOnly(net.minecraft.server.level.ServerPlayer player,
            java.util.Collection<net.minecraft.world.item.crafting.Recipe<?>> recipes) {
        return player.awardRecipes(recipes.stream().filter(r->player.level().getRecipeManager().byKey(r.getId()).isPresent()).toList());
    }
}
