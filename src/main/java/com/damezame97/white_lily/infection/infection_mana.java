package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.Config;
import net.minecraft.world.entity.player.Player;

public final class infection_mana
{
    /** ISS 成本为整数，额外费用向上取整；预检查、界面和扣费共用此入口。 */
    public static int cost(Player player, int original)
    {
        if (player == null || original <= 0 || !Config.infectionEnabled || infection_service.excluded(player)
                || !player.hasEffect(infection_symptom_rules.effect("mental_distraction"))) return original;
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(original * (1 + 0.15 * infection_symptom_rules.strength(player)) - 1.0E-9));
    }
    private infection_mana() { }
}
