package com.damezame97.white_lily.item;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.damezame97.white_lily.white_lilyfood.magic_apple;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** 低质量药剂：恢复魔力，可能附加肮脏魔力及额外魔力/饥饿，饮用后返还玻璃瓶。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
// Level 由游戏引擎持有；这里只借用，不得关闭世界。
@SuppressWarnings("resource")
public class low_quality_potion extends Item
{
    // 既有注册 ID，保留拼写以兼容已有存档。
    @SuppressWarnings("SpellCheckingInspection")
    public static final String NAME = "low_qualitypotion";
    public static final float MANA_GAIN = 15.0F;
    public static final float BONUS_MANA = 30.0F;
    public static final float IMPURE_CHANCE = 0.30F;
    public static final float BONUS_CHANCE = 0.08F;

    public low_quality_potion()
    {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!detail_tooltip.isExpanded(level))
        {
            tooltip.add(detail_tooltip.hint());
            return;
        }
        for (int line = 1; line <= 5; line++)
        {
            tooltip.add(Component.translatable(getDescriptionId() + ".effect." + line).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 第二次概率只在肮脏魔力触发后判定；服务端一次结算并复用现有 ISS 魔力同步。 */
    public static void applyOnConsume(LivingEntity entity, RandomSource random)
    {
        if (entity.level().isClientSide)
        {
            return;
        }
        float mana = MANA_GAIN;
        if (random.nextFloat() < IMPURE_CHANCE)
        {
            entity.addEffect(new MobEffectInstance(mob_effects.IMPURE_MAGIC.get(), 300, 1, false, false, true));
            if (random.nextFloat() < BONUS_CHANCE)
            {
                mana += BONUS_MANA;
                entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 2, false, false, true));
            }
        }
        magic_apple.grantMana(entity, mana);
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        // 动画可以在两端完成；物品、瓶子、统计和效果统一由服务端结算。
        if (level.isClientSide) return stack;
        applyOnConsume(entity, entity.getRandom());
        Player player = entity instanceof Player ? (Player) entity : null;
        if (player instanceof ServerPlayer serverPlayer)
        {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (player != null)
        {
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        // 创造模式不消耗药剂，也不额外生成瓶子。
        if (player != null && player.getAbilities().instabuild)
        {
            return stack;
        }

        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty())
        {
            return bottle;
        }

        // 剩余药剂留在手中，空瓶进背包；背包满时只由服务端掉落。
        if (player != null)
        {
            if (!player.getInventory().add(bottle))
            {
                player.drop(bottle, false);
            }
        }
        else
        {
            entity.spawnAtLocation(bottle);
        }
        return stack;
    }
}
