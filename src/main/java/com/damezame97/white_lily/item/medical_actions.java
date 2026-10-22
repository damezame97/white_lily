package com.damezame97.white_lily.item;

import com.damezame97.white_lily.infection.*;
import com.damezame97.white_lily.procedures.san_procedures;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class medical_actions
{
    public enum Kind { SIMPLE, FINE, BITTERLEAF, PASTE, YARROW, DRESSING, FLOWER, TEA }

    /** 两次独立抽取；第一组互斥，合并后只送一次感染来源。 */
    public static int bandageBase(RandomSource random)
    {
        double first = random.nextDouble();
        int amount = first < 0.15 ? 30 : first < 0.45 ? 15 : 0;
        return amount + (random.nextDouble() < 0.5 ? 5 : 0);
    }
    public static boolean canUse(Player player, Kind kind)
    {
        var bandage = player.getEffect(mob_effects.BANDAGED.get());
        return kind != Kind.SIMPLE || bandage == null || bandage.getAmplifier() == 0;
    }
    public static boolean apply(Player player, Kind kind, RandomSource random)
    {
        if (player.level().isClientSide || !player.isAlive() || !canUse(player, kind)) return false;
        switch (kind)
        {
            case SIMPLE, FINE -> {
                player.removeEffect(mob_effects.BANDAGED.get());
                player.addEffect(new MobEffectInstance(mob_effects.BANDAGED.get(), kind == Kind.SIMPLE ? 6000 : 12000,
                        kind == Kind.SIMPLE ? 0 : 1, false, false, true));
                if (kind == Kind.SIMPLE)
                {
                    int base = bandageBase(random);
                    if (base > 0) infection_service.trySource(player, new infection_sources.Source(
                            infection_symptom_rules.id("simple_bandage"), 1, base), random);
                }
            }
            case BITTERLEAF, PASTE -> {
                infection_service.treat(player, kind == Kind.BITTERLEAF ? 3 : 8);
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, kind == Kind.BITTERLEAF ? 900 : 200,
                        0, false, false, true));
            }
            case YARROW, DRESSING -> {
                var previous = player.getEffect(mob_effects.WOUND_SUPPRESSION.get());
                int duration = kind == Kind.YARROW ? 400 : 1200;
                if (previous == null || (!previous.isInfiniteDuration() && previous.getDuration() < duration))
                    player.addEffect(new MobEffectInstance(mob_effects.WOUND_SUPPRESSION.get(), duration, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, kind == Kind.YARROW ? 600 : 200,
                        0, false, false, true));
                infection_symptoms.reevaluate(player);
            }
            case FLOWER, TEA -> {
                san_procedures.restore(player, kind == Kind.FLOWER ? 5 : 15, kind == Kind.FLOWER ? 10 : 30);
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, kind == Kind.FLOWER ? 400 : 200,
                        0, false, false, true));
            }
        }
        return true;
    }
    public static ItemStack finish(ItemStack stack, Level level, LivingEntity entity, Kind kind)
    {
        // 客户端不改变库存或裁决效果，最终结果由服务端槽位同步。
        if (level.isClientSide || !(entity instanceof Player player) || !apply(player, kind, player.getRandom())) return stack;
        if (player instanceof ServerPlayer server) CriteriaTriggers.CONSUME_ITEM.trigger(server, stack);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (player.getAbilities().instabuild) return stack;
        stack.shrink(1);
        if (kind == Kind.TEA)
        {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) return bottle;
            if (!player.getInventory().add(bottle)) player.drop(bottle, false);
        }
        return stack;
    }
    private medical_actions() { }
}
