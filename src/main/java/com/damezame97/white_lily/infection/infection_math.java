package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.capability.infection_state;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 感染计算的纯函数；十进制运算后每次独立 HALF_UP，不累计不足 0.1 的余量。 */
public final class infection_math
{
    public enum Injury
    {
        HEALTHY(1.0D, 0.0D), WOUNDED(1.2D, 0.5D), SEVERE(1.8D, 1.0D), CRITICAL(2.8D, 2.0D);
        public final double multiplier;
        public final double passive;
        Injury(double multiplier, double passive) { this.multiplier = multiplier; this.passive = passive; }
    }

    public static Injury injury(double ratio)
    {
        if (!Double.isFinite(ratio) || ratio > 0.70D) return Injury.HEALTHY;
        if (ratio > 0.40D) return Injury.WOUNDED;
        if (ratio > 0.20D) return Injury.SEVERE;
        return Injury.CRITICAL;
    }

    public static double risk(double stamina)
    {
        if (!Double.isFinite(stamina)) return 1.0D;
        BigDecimal s = BigDecimal.valueOf(stamina);
        BigDecimal value = BigDecimal.ONE.subtract(s.multiply(new BigDecimal(stamina < 0 ? "0.25" : "0.1")));
        return value.max(new BigDecimal("0.5")).min(new BigDecimal("2.0")).doubleValue();
    }

    public static double resistance(double raw)
    {
        return Double.isFinite(raw) ? Math.max(0, Math.min(0.8D, raw)) : 0.0D;
    }

    public static int units(double value)
    {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Infection amount must be finite");
        return roundedUnits(BigDecimal.valueOf(value));
    }

    public static int roundedUnits(BigDecimal value)
    {
        BigDecimal scaled = value.max(BigDecimal.ZERO).movePointRight(1).setScale(0, RoundingMode.HALF_UP);
        return scaled.min(BigDecimal.valueOf(infection_state.MAX_UNITS)).intValueExact();
    }

    public static int external(double base, Injury injury, double risk, double resistance)
    {
        BigDecimal extra = BigDecimal.valueOf(injury.multiplier).subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(risk));
        return roundedUnits(BigDecimal.valueOf(base).multiply(BigDecimal.ONE.add(extra))
                .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(resistance))));
    }

    public static int passive(Injury injury, double risk, double resistance)
    {
        return roundedUnits(BigDecimal.valueOf(injury.passive).multiply(BigDecimal.valueOf(risk))
                .multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(resistance))));
    }

    private infection_math() { }
}
