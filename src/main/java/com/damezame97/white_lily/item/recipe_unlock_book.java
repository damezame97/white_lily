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
import java.util.ArrayList;
import java.util.List;

/**
 * A server-authoritative recipe book.
 *
 * <p>Fixed books grant their exact recipe/operation whitelist. Random books choose exactly one
 * still-unlearned, currently valid target from their server-side pool. No target or result is
 * stored on the item stack, so a copied random book keeps randomizing against its reader.</p>
 */
public class recipe_unlock_book extends Item
{
    private static final int USE_COOLDOWN_TICKS = 10;
    private final List<ResourceLocation> grants;
    private final boolean random;
    private final boolean copyable;
    @Nullable private final String randomPoolKey;

    public recipe_unlock_book(ResourceLocation... grants)
    {
        this(false, null, false, grants);
    }

    /** A fixed book whose detailed tooltip also explains its supported transcription recipe. */
    public static recipe_unlock_book copyable(ResourceLocation... grants)
    {
        return new recipe_unlock_book(false, null, true, grants);
    }

    private recipe_unlock_book(boolean random, @Nullable String randomPoolKey, boolean copyable,
                               ResourceLocation... grants)
    {
        super(new Item.Properties().stacksTo(16));
        this.grants = List.of(grants);
        this.random = random;
        this.copyable = copyable;
        this.randomPoolKey = randomPoolKey;
        if (this.grants.isEmpty()) throw new IllegalArgumentException("A recipe book needs a target");
    }

    /** Creates a book that uniformly chooses one not-yet-known valid target from its pool. */
    public static recipe_unlock_book random(String poolTranslationKey, ResourceLocation... pool)
    {
        return new recipe_unlock_book(true, poolTranslationKey, true, pool);
    }

    public List<ResourceLocation> recipes() { return grants; }

    public boolean isRandom() { return random; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer server)) return InteractionResultHolder.pass(stack);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);

        if (random)
        {
            List<ResourceLocation> candidates = new ArrayList<>();
            for (ResourceLocation target : grants)
                if (recipe_unlocks.canGrant(server, target) && !recipe_unlocks.isUnlocked(server, target))
                    candidates.add(target);
            if (candidates.isEmpty())
            {
                boolean anyValid = grants.stream().anyMatch(target -> recipe_unlocks.canGrant(server, target));
                message(server, anyValid ? "message.white_lily.recipe_book.known"
                        : "message.white_lily.recipe_book.unavailable", ChatFormatting.GRAY);
                return InteractionResultHolder.fail(stack);
            }
            // The client never supplies the random result; one request grants one target.
            ResourceLocation chosen = candidates.get(server.getRandom().nextInt(candidates.size()));
            if (!recipe_unlocks.unlock(server, chosen))
            {
                message(server, "message.white_lily.recipe_book.known", ChatFormatting.GRAY);
                return InteractionResultHolder.fail(stack);
            }
            return finish(server, player, stack, 1);
        }

        // A removed datapack recipe or a broken operation target must never consume the book.
        for (ResourceLocation target : grants)
            if (!recipe_unlocks.canGrant(server, target))
            {
                message(server, "message.white_lily.recipe_book.unavailable", ChatFormatting.RED);
                return InteractionResultHolder.fail(stack);
            }
        int learned = 0;
        for (ResourceLocation target : grants) if (recipe_unlocks.unlock(server, target)) learned++;
        if (learned == 0)
        {
            message(server, "message.white_lily.recipe_book.known", ChatFormatting.GRAY);
            return InteractionResultHolder.fail(stack);
        }
        return finish(server, player, stack, learned);
    }

    private InteractionResultHolder<ItemStack> finish(ServerPlayer server, Player player, ItemStack stack, int learned)
    {
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (server.connection != null) server.displayClientMessage(
                Component.translatable("message.white_lily.recipe_book.learned", learned).withStyle(ChatFormatting.GREEN), true);
        player.getCooldowns().addCooldown(this, USE_COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    private static void message(ServerPlayer server, String key, ChatFormatting color)
    {
        if (server.connection != null) server.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        if (copyable)
            detail_tooltip.appendDetails(tooltip, detail_tooltip.isExpanded(level),
                    lines -> appendContent(lines, level));
        else
            // The pre-existing golden sword book keeps its original always-visible tooltip.
            appendContent(tooltip, level);
    }

    /** Visible for the same tooltip checks used by the existing Shift-detail tests. */
    public void appendDetails(ItemStack stack, @Nullable Level level, List<Component> tooltip, boolean expanded)
    {
        detail_tooltip.appendDetails(tooltip, expanded, lines -> appendContent(lines, level));
    }

    private void appendContent(List<Component> lines, @Nullable Level level)
    {
        lines.add(Component.translatable("tooltip.white_lily.recipe_book.use").withStyle(ChatFormatting.AQUA));
        if (random)
        {
            lines.add(Component.translatable("tooltip.white_lily.recipe_book.random",
                    Component.translatable(randomPoolKey == null ? "" : randomPoolKey))
                    .withStyle(ChatFormatting.GRAY));
        }
        else if (level != null)
        {
            for (ResourceLocation target : grants)
            {
                if (recipe_unlocks.isOperation(target))
                {
                    lines.add(Component.translatable("tooltip.white_lily.recipe_book.operation",
                            operationName(target)).withStyle(ChatFormatting.GRAY));
                    continue;
                }
                level.getRecipeManager().byKey(target).ifPresent(recipe -> {
                    ItemStack result = recipe.getResultItem(level.registryAccess());
                    if (!result.isEmpty()) lines.add(Component.translatable("tooltip.white_lily.recipe_book.recipe",
                            result.getHoverName()).withStyle(ChatFormatting.GRAY));
                });
            }
        }
        if (copyable)
            lines.add(Component.translatable("tooltip.white_lily.recipe_book.copy").withStyle(ChatFormatting.GRAY));
    }

    private static Component operationName(ResourceLocation id)
    {
        String path = id.getPath();
        String name = path.startsWith("operation/") ? path.substring("operation/".length()) : path;
        return Component.translatable("tooltip.white_lily.operation." + name);
    }
}
