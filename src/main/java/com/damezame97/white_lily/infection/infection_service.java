package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.infection_state;
import com.damezame97.white_lily.capability.player_variables;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;

/** 所有玩法写入的服务端入口；直接设置/调试/治疗不再经过风险与抗性公式。 */
@SuppressWarnings("resource") // 世界由 Minecraft 管理，只借用。
public final class infection_service
{
    public record Conditions(boolean validHealth, double healthRatio, infection_math.Injury injury,
                             double stamina, double risk, double resistance) { }
    public enum Status { TRIGGERED, MISSED, DISABLED, INELIGIBLE, UNKNOWN_SOURCE, INVALID_HEALTH, NO_DATA }
    public record Result(Status status, int addedUnits, int afterUnits)
    {
        public boolean triggered() { return status == Status.TRIGGERED; }
        public double actualAdded() { return addedUnits / 10.0D; }
        public double after() { return afterUnits / 10.0D; }
    }

    public static double query(Player player)
    {
        return player_variables.infection(player).map(infection_state::value).orElse(0.0D);
    }

    /** 明确的感染耐力取值入口，不排除 SAN 或装备，只排除按感染所有权约定生成的修饰符。 */
    public static double stamina(Player player)
    {
        AttributeInstance instance = player.getAttribute(attributes.STAMINA.get());
        double value = instance == null ? 0 : infection_modifiers.valueWithoutInfection(instance);
        return Double.isFinite(value) ? value : 0;
    }

    public static Conditions conditions(Player player)
    {
        double health = player.getHealth();
        double max = player.getMaxHealth();
        boolean valid = Double.isFinite(health) && Double.isFinite(max) && health > 0 && max > 0;
        double ratio = valid ? Math.max(0, Math.min(1, health / max)) : 1.0D;
        double stamina = stamina(player);
        AttributeInstance resistanceAttribute = player.getAttribute(attributes.INFECTION_RESISTANCE.get());
        double resistance = infection_math.resistance(resistanceAttribute == null ? 0 : resistanceAttribute.getValue());
        return new Conditions(valid, ratio, infection_math.injury(ratio), stamina, infection_math.risk(stamina), resistance);
    }

    public static boolean excluded(Player player)
    {
        return player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable || player.isInvulnerable();
    }

    public static Result trySource(Player player, ResourceLocation id)
    {
        if (id == null) return result(player, Status.UNKNOWN_SOURCE, 0);
        var source = infection_sources.find(id);
        return source.map(value -> trySource(player, value, player.getRandom()))
                .orElseGet(() -> result(player, Status.UNKNOWN_SOURCE, 0));
    }

    /** 也供命令/独立测试的临时来源使用，不会把临时来源登记进正式表。 */
    public static Result trySource(Player player, infection_sources.Source source, RandomSource random)
    {
        if (source == null) return result(player, Status.UNKNOWN_SOURCE, 0);
        if (player.level().isClientSide || !player.isAlive() || player.deathTime > 0 || player.isRemoved() || excluded(player))
            return result(player, Status.INELIGIBLE, 0);
        if (!Config.infectionEnabled) return result(player, Status.DISABLED, 0);
        if (player_variables.infection(player).isEmpty()) return result(player, Status.NO_DATA, 0);
        if (source.probability() <= 0 || (source.probability() < 1 && random.nextDouble() >= source.probability()))
            return result(player, Status.MISSED, 0);
        Conditions conditions = conditions(player);
        if (!conditions.validHealth()) return result(player, Status.INVALID_HEALTH, 0);
        int delta = infection_math.external(source.base(), conditions.injury(), conditions.risk(), conditions.resistance());
        int before = player_variables.infection(player).orElseThrow().units();
        setUnits(player, Math.min(infection_state.MAX_UNITS, before + delta));
        int actual = player_variables.infection(player).orElseThrow().units() - before;
        infection_symptom_rules.onIncrease(player, actual, random);
        return result(player, Status.TRIGGERED, actual);
    }

    private static Result result(Player player, Status status, int added)
    {
        return new Result(status, added, player_variables.infection(player).map(infection_state::units).orElse(0));
    }

    public static double set(Player player, double value)
    {
        setUnits(player, infection_math.units(value));
        return query(player);
    }

    public static double addDirect(Player player, double value)
    {
        nonnegative(value);
        int before = player_variables.infection(player).map(infection_state::units).orElse(0);
        setUnits(player, Math.min(infection_state.MAX_UNITS, before + infection_math.units(value)));
        return (player_variables.infection(player).map(infection_state::units).orElse(0) - before) / 10.0D;
    }

    public static double reduce(Player player, double value)
    {
        nonnegative(value);
        int before = player_variables.infection(player).map(infection_state::units).orElse(0);
        setUnits(player, Math.max(0, before - infection_math.units(value)));
        return (before - player_variables.infection(player).map(infection_state::units).orElse(0)) / 10.0D;
    }

    public static void clear(Player player) { set(player, 0); }

    /** 明确的主动治疗入口；管理命令 reduce 不走这里。 */
    public static double treat(Player player, double amount)
    {
        double actual = reduce(player, amount);
        infection_symptom_rules.onTreatment(player, actual);
        return actual;
    }

    private static void nonnegative(double value)
    {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Amount must be finite and nonnegative");
    }

    private static void setUnits(Player player, int units)
    {
        if (player.level().isClientSide) return;
        player_variables.infection(player).ifPresent(data -> {
            int before = data.units();
            data.setUnits(units);
            Conditions conditions = conditions(player);
            if (conditions.validHealth() && conditions.healthRatio() > 0.70D) data.setPassiveTicks(0);
            if (before != data.units())
            {
                infection_network.sync(player, false);
                infection_symptoms.reevaluate(player);
            }
        });
    }

    /** 同一服务端全局 tick 最多推进一次；公开 clock 参数供 GameTest 在不等待现实一分钟的情况下验证。 */
    public static void tick(Player player, long clock)
    {
        if (player.level().isClientSide || player.isRemoved() || !player.isAlive() || player.deathTime > 0) return;
        player_variables.infection(player).ifPresent(data -> {
            if (data.lastTick == clock) return;
            data.lastTick = clock;
            Conditions conditions = conditions(player);
            // 清空条件优先于开关/创造模式暂停条件。
            if (data.units() == 0 || data.units() == infection_state.MAX_UNITS
                    || (conditions.validHealth() && conditions.healthRatio() > 0.70D))
            {
                data.setPassiveTicks(0);
                return;
            }
            if (!Config.infectionEnabled || excluded(player) || !conditions.validHealth()) return;
            int ticks = data.passiveTicks() + 1;
            if (ticks < infection_state.PERIOD)
            {
                data.setPassiveTicks(ticks);
                return;
            }
            data.setPassiveTicks(0);
            int delta = infection_math.passive(conditions.injury(), conditions.risk(), conditions.resistance());
            int before = data.units();
            setUnits(player, Math.min(infection_state.MAX_UNITS, data.units() + delta));
            infection_symptom_rules.onIncrease(player, data.units() - before, player.getRandom());
        });
    }

    private infection_service() { }
}
