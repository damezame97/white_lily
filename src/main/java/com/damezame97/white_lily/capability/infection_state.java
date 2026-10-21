package com.damezame97.white_lily.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/** 整数十分位感染值及已完成的被动周期刻数；不保存小数余量或离线时钟。 */
@AutoRegisterCapability
public final class infection_state
{
    public static final int MAX_UNITS = 3000;
    public static final int PERIOD = 1200;
    private int units;
    private int passiveTicks;
    public final com.damezame97.white_lily.infection.infection_symptom_state symptoms =
            new com.damezame97.white_lily.infection.infection_symptom_state();
    public transient long lastTick = Long.MIN_VALUE;
    public transient int lastSyncedUnits = -1;

    public int units() { return units; }
    public double value() { return units / 10.0D; }
    public int passiveTicks() { return passiveTicks; }

    /** 内部存储入口；玩法侧统一使用 infection_service，保证服务端校验和通知。 */
    public void setUnits(int value)
    {
        units = Math.max(0, Math.min(MAX_UNITS, value));
        if (units == 0 || units == MAX_UNITS) passiveTicks = 0;
    }

    public void setPassiveTicks(int value)
    {
        passiveTicks = units == 0 || units == MAX_UNITS ? 0 : Math.max(0, Math.min(PERIOD - 1, value));
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("units", units);
        tag.putInt("passive_ticks", passiveTicks);
        tag.put("symptoms", symptoms.save());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        // 缺失/错误类型/非有限数归零；先夹值再转整数，避免大整数溢出。
        setUnits(readBounded(tag, "units", MAX_UNITS));
        setPassiveTicks(readBounded(tag, "passive_ticks", PERIOD - 1));
        symptoms.load(tag.getCompound("symptoms"));
        lastTick = Long.MIN_VALUE;
        lastSyncedUnits = -1;
    }

    private static int readBounded(CompoundTag tag, String key, int max)
    {
        double raw = tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : 0;
        return Double.isFinite(raw) ? (int) Math.max(0, Math.min(max, raw)) : 0;
    }

    public void copyFrom(infection_state other, boolean died, boolean keep)
    {
        setUnits(died && !keep ? 0 : other.units);
        setPassiveTicks(died ? 0 : other.passiveTicks);
        if (died) symptoms.clear();
        else
        {
            symptoms.load(other.symptoms.save());
            symptoms.lastTick = other.symptoms.lastTick;
        }
        lastTick = died ? Long.MIN_VALUE : other.lastTick;
        lastSyncedUnits = -1;
    }
}
