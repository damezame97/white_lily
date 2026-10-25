package com.damezame97.white_lily.mixin;

import com.damezame97.white_lily.machine.*;
import com.google.gson.JsonElement;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class MixinMachineRecipeManager implements recipe_epoch, machine_state_access {
    @Unique private long whiteLily$epoch;
    public long whiteLily$recipeEpoch() { return whiteLily$epoch; }
    @Unique private machine_rules.State whiteLily$machines = machine_rules.empty();
    public machine_rules.State whiteLily$machines() { return whiteLily$machines; }
    public void whiteLily$machines(machine_rules.State state) { whiteLily$machines = state; whiteLily$epoch++; }
    @Inject(method = "apply", at = @At("TAIL"))
    private void whiteLily$applyRules(Map<ResourceLocation, JsonElement> files, ResourceManager resources,
            ProfilerFiller profiler, CallbackInfo ci) {
        var self = (RecipeManager) (Object) this;
        whiteLily$machines(machine_rules.reload(resources, self.getRecipes()));
    }
    @Inject(method = "replaceRecipes", at = @At("TAIL"))
    private void whiteLily$newEpoch(Iterable<Recipe<?>> recipes, CallbackInfo ci) {
        whiteLily$machines = machine_rules.rebuild(((RecipeManager)(Object)this).getRecipes(),whiteLily$machines);
        whiteLily$epoch++;
    }
}
