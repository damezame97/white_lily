package com.damezame97.white_lily.infection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;

/** 玩家所有的事件症状时钟；不使用现实时间，不保存玩家引用。 */
public final class infection_symptom_state
{
    /** 单个症状计时上限：1 小时（「造血障碍」要求 60 分钟）。 */
    public static final int MAX_TICKS = 60 * 60 * 20;

    public final Map<String, Integer> remaining = new HashMap<>();
    public int perceptionTicks;
    public int poolCooldown;
    /** 伤口渗血的当前等级（0~4）；只在渗血未结束时保留。 */
    public int woundSeepageLevel;
    /** 「愈合迟滞」的当前等级（0~3）。 */
    public int delayedHealingLevel;
    public transient int delayedSanTicks;
    /** 精神涣散的当前等级（0~10）；随存档保存。 */
    public int mentalDistractionLevel;
    public transient int mentalDistractionDecay;
    public transient int mentalDistractionRestart;
    public transient int seepageSanTicks;
    public transient double seepageBlocks;
    public transient boolean seepagePositionValid;
    public transient double seepageLastX, seepageLastY, seepageLastZ;
    public transient boolean changingEffects;
    public transient long lastTick = Long.MIN_VALUE;
    public transient long lastEvaluation = Long.MIN_VALUE;
    public transient boolean dirty = true;
    public transient boolean evaluated;
    public transient boolean active;
    public transient boolean healthLossPending;
    public transient float health, maxHealth;
    public transient double san;
    public transient int infection;

    public int remaining(String id) { return remaining.getOrDefault(id, 0); }
    public void set(String id, int ticks)
    {
        if (ticks == -1) remaining.put(id, -1);
        else if (ticks <= 0) remaining.remove(id);
        else remaining.put(id, Math.min(MAX_TICKS, ticks));
    }
    public boolean running(String id) { return remaining(id) != 0; }
    public void clear()
    {
        remaining.clear();
        perceptionTicks = 0;
        poolCooldown = 0;
        woundSeepageLevel = 0;
        delayedHealingLevel = 0;
        delayedSanTicks = 0;
        mentalDistractionLevel = 0;
        mentalDistractionDecay = 0;
        mentalDistractionRestart = 0;
        seepageSanTicks = 0;
        seepageBlocks = 0;
        seepagePositionValid = false;
        lastTick = Long.MIN_VALUE;
        lastEvaluation = Long.MIN_VALUE;
        dirty = true;
        evaluated = false;
        healthLossPending = false;
    }
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        CompoundTag durations = new CompoundTag();
        remaining.forEach(durations::putInt);
        tag.put("remaining", durations);
        tag.putInt("perception", perceptionTicks);
        tag.putInt("cooldown", poolCooldown);
        tag.putInt("seepage_level", woundSeepageLevel);
        tag.putInt("delayed_level", delayedHealingLevel);
        tag.putInt("mental_level", mentalDistractionLevel);
        return tag;
    }
    public void load(CompoundTag tag)
    {
        clear();
        CompoundTag durations = tag.getCompound("remaining");
        for (String key : infection_symptom_rules.timedIds())
            set(key, durations.contains(key, Tag.TAG_ANY_NUMERIC) && durations.getDouble(key) == -1
                    ? -1 : bounded(durations, key, MAX_TICKS));
        perceptionTicks = bounded(tag, "perception", 1199);
        poolCooldown = bounded(tag, "cooldown", 600);
        woundSeepageLevel = bounded(tag, "seepage_level", 4);
        delayedHealingLevel = bounded(tag, "delayed_level", 3);
        mentalDistractionLevel = bounded(tag, "mental_level", 10);
        if (!remaining.containsKey("delayed_healing")) delayedHealingLevel = 0;
        if (!remaining.containsKey("wound_seepage")) woundSeepageLevel = 0;
    }
    private static int bounded(CompoundTag tag, String key, int max)
    {
        double value = tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : 0;
        return Double.isFinite(value) ? (int) Math.max(0, Math.min(max, value)) : 0;
    }
}
