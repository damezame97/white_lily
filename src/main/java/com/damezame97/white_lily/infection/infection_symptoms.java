package com.damezame97.white_lily.infection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** 固定症状及额外 Java 规则共同使用的重评入口。 */
public final class infection_symptoms
{
    private static final Map<ResourceLocation, BiConsumer<Player, infection_service.Conditions>> RULES = new LinkedHashMap<>();

    public static synchronized void register(ResourceLocation id, BiConsumer<Player, infection_service.Conditions> rule)
    {
        Objects.requireNonNull(id);
        Objects.requireNonNull(rule);
        if (RULES.putIfAbsent(id, rule) != null) throw new IllegalArgumentException("Duplicate symptom rule: " + id);
    }

    /** 感染变化及将来生命/SAN/意志变化共用的重评入口。规则通过 service.query 读取感染值。 */
    public static void reevaluate(Player player)
    {
        if (player.level().isClientSide) return;
        infection_symptom_rules.reevaluate(player);
        List<BiConsumer<Player, infection_service.Conditions>> rules;
        synchronized (infection_symptoms.class) { rules = List.copyOf(RULES.values()); }
        if (rules.isEmpty()) return;
        infection_service.Conditions conditions = infection_service.conditions(player);
        for (var rule : rules) rule.accept(player, conditions);
    }

    public static synchronized int size() { return RULES.size(); }
    private infection_symptoms() { }
}
