package com.damezame97.white_lily.item;

import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A recipe book grants its fixed server-side recipe list, never a recipe ID supplied by a client. */
public final class recipe_unlock_book extends Item
{
    private final List<ResourceLocation> recipes;

    public recipe_unlock_book(ResourceLocation... recipes)
    {
        super(new Item.Properties().stacksTo(16));
        this.recipes = List.of(recipes);
        if (this.recipes.isEmpty()) throw new IllegalArgumentException("A recipe book needs a recipe");
    }

    public List<ResourceLocation> recipes() { return recipes; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.pass(stack);
        // A removed datapack recipe must never consume a book or create an invalid saved unlock.
        if (recipes.stream().anyMatch(id -> level.getRecipeManager().byKey(id).isEmpty()))
        {
            if (server.connection != null) server.displayClientMessage(
                    Component.translatable("message.white_lily.recipe_book.unavailable").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        int learned = 0;
        for (ResourceLocation recipe : recipes) if (recipe_unlocks.unlock(server, recipe)) learned++;
        if (learned == 0)
        {
            if (server.connection != null) server.displayClientMessage(
                    Component.translatable("message.white_lily.recipe_book.known").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (server.connection != null) server.displayClientMessage(
                Component.translatable("message.white_lily.recipe_book.learned", learned).withStyle(ChatFormatting.GREEN), true);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.white_lily.recipe_book.use").withStyle(ChatFormatting.AQUA));
        if (level != null)
            for (ResourceLocation id : recipes)
                level.getRecipeManager().byKey(id).ifPresent(recipe -> {
                    ItemStack result = recipe.getResultItem(level.registryAccess());
                    if (!result.isEmpty()) tooltip.add(Component.translatable("tooltip.white_lily.recipe_book.recipe", result.getHoverName())
                            .withStyle(ChatFormatting.GRAY));
                });
    }
}
