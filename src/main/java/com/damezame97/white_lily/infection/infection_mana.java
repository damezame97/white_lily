package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.Config;
import net.minecraft.world.entity.player.Player;

/** 精神涣散带来的施法成本：每级 +5%。 */
public final class infection_mana
{
    /** ISS 成本为整数，额外费用向上取整；预检查、界面和扣费共用此入口。 */
    public static int cost(Player player, int original)
    {
        if (player == null || original <= 0 || !Config.infectionEnabled || infection_service.excluded(player))
            return original;
        double multiplier = infection_symptom_rules.mentalCostMultiplier(
                infection_symptom_rules.mentalDistractionLevel(player));
        if (multiplier <= 1.0D) return original;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(original * multiplier - 1.0E-9));
    }

    private infection_mana() { }
}
