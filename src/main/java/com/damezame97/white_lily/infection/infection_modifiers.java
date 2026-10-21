package com.damezame97.white_lily.infection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 未来感染症状修饰符的所有权约定：固定名称前缀和按名称+属性派生的 UUID 必须同时匹配。 */
public final class infection_modifiers
{
    private static final String PREFIX = "white_lily:infection/";

    public static AttributeModifier create(ResourceLocation rule, Attribute attribute, double amount,
                                           AttributeModifier.Operation operation)
    {
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Modifier must be finite");
        String name = PREFIX + rule;
        return new AttributeModifier(id(name, attribute), name, amount, operation);
    }

    private static UUID id(String name, Attribute attribute)
    {
        return UUID.nameUUIDFromBytes((name + "|" + ForgeRegistries.ATTRIBUTES.getKey(attribute))
                .getBytes(StandardCharsets.UTF_8));
    }

    public static boolean owned(AttributeModifier modifier, Attribute attribute)
    {
        return modifier.getName().startsWith(PREFIX) && modifier.getId().equals(id(modifier.getName(), attribute));
    }

    /** 不拆装真实属性，完整重算三种 Operation；SAN 的耐力修正不属于感染，因此照常参与。 */
    public static double valueWithoutInfection(AttributeInstance instance)
    {
        Attribute attribute = instance.getAttribute();
        if (instance.getModifiers().stream().noneMatch(modifier -> owned(modifier, attribute))) return instance.getValue();
        double added = instance.getBaseValue();
        for (AttributeModifier modifier : instance.getModifiers(AttributeModifier.Operation.ADDITION))
            if (!owned(modifier, attribute)) added += modifier.getAmount();
        double value = added;
        for (AttributeModifier modifier : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))
            if (!owned(modifier, attribute)) value += added * modifier.getAmount();
        for (AttributeModifier modifier : instance.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))
            if (!owned(modifier, attribute)) value *= 1.0D + modifier.getAmount();
        return attribute.sanitizeValue(value);
    }

    public static void put(Player player, ResourceLocation rule, Attribute attribute, double amount,
                           AttributeModifier.Operation operation)
    {
        if (player.level().isClientSide) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier desired = create(rule, attribute, amount, operation);
        AttributeModifier current = instance.getModifier(desired.getId());
        if (current != null && !owned(current, attribute))
            throw new IllegalStateException("Infection modifier UUID collides with another system");
        if (current != null && current.getAmount() == amount && current.getOperation() == operation) return;
        instance.removeModifier(desired.getId());
        instance.addTransientModifier(desired);
    }

    public static void remove(Player player, ResourceLocation rule, Attribute attribute)
    {
        if (player.level().isClientSide) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        UUID key = id(PREFIX + rule, attribute);
        AttributeModifier modifier = instance.getModifier(key);
        if (modifier != null && owned(modifier, attribute)) instance.removeModifier(key);
    }

    private infection_modifiers() { }
}
