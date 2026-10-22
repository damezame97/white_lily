package com.damezame97.white_lily.mixin.research;

import com.damezame97.white_lily.crafting.research_crafting_recipe;
import com.damezame97.white_lily.research.research_crafting_gate;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;

@Mixin(ServerRecipeBook.class)
public abstract class MixinResearchRecipeBook
{
    /** 同时防止旧解锁进度、配方命令或其他解锁通知提前公开研究配方。 */
    @ModifyVariable(method = "addRecipes", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Collection<Recipe<?>> whiteLily$researchAwards(Collection<Recipe<?>> recipes, Collection<Recipe<?>> ignored,
                                                         ServerPlayer player)
    {
        return recipes.stream().filter(recipe -> !(recipe instanceof research_crafting_recipe)
                || research_crafting_gate.permitted(player, recipe.getId())).toList();
    }

    @Inject(method = "sendInitialRecipeBook", at = @At("HEAD"))
    private void whiteLily$researchInitial(ServerPlayer player, CallbackInfo callback)
    {
        ServerRecipeBook serverBook = (ServerRecipeBook)(Object)this;
        RecipeBook book = serverBook;
        // 只在初始同步前读取一次原生快照；不借用Mixin自身继承字段，正式包映射仍指向原版API。
        ListTag known = serverBook.toNbt().getList("recipes", 8);
        for (int index = 0; index < known.size(); index++)
        {
            ResourceLocation id = ResourceLocation.tryParse(known.getString(index));
            if (id == null) continue;
            player.getServer().getRecipeManager().byKey(id).ifPresent(recipe -> {
                if (recipe instanceof research_crafting_recipe && !research_crafting_gate.permitted(player, id)) book.remove(recipe);
            });
        }
    }
}
