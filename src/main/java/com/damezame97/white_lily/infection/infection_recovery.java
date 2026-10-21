package com.damezame97.white_lily.infection;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import java.util.UUID;

/** 纯读取，排除 SAN 自身及感染回复量修正，不拆装真实修饰符。 */
public final class infection_recovery
{
    /** 将最终回复量减少换算为加法修饰量，考虑乘法装备修正，保留原本的负回复量。 */
    public static double safeReduction(AttributeInstance instance, double current, double requested)
    {
        if (requested >= 0 || current <= 0) return 0;
        double slope = 1;
        for (var m : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) slope += m.getAmount();
        for (var m : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL)) slope *= 1 + m.getAmount();
        if (!Double.isFinite(slope) || slope <= 0) return 0;
        return -Math.min(current, -requested) / slope;
    }
    public static double valueWithout(AttributeInstance instance, UUID san)
    {
        double added = instance.getBaseValue();
        for (var m : instance.getModifiers(AttributeModifier.Operation.ADDITION))
            if (keep(instance, m, san)) added += m.getAmount();
        double value = added;
        for (var m : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))
            if (keep(instance, m, san)) value += added * m.getAmount();
        for (var m : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))
            if (keep(instance, m, san)) value *= 1 + m.getAmount();
        return instance.getAttribute().sanitizeValue(value);
    }
    private static boolean keep(AttributeInstance instance, AttributeModifier modifier, UUID san)
    {
        return !modifier.getId().equals(san) && !infection_modifiers.owned(modifier, instance.getAttribute());
    }
    private infection_recovery() { }
}
