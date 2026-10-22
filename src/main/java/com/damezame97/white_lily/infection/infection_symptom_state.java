package com.damezame97.white_lily.infection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;

/** 玩家所有的事件症状时钟；不使用现实时间，不保存玩家引用。 */
public final class infection_symptom_state
{
    public final Map<String, Integer> remaining = new HashMap<>();
    public int perceptionTicks;
    public int poolCooldown;
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
        else remaining.put(id, Math.min(12000, ticks));
    }
    public boolean running(String id) { return remaining(id) != 0; }
    public void clear()
    {
        remaining.clear();
        perceptionTicks = 0;
        poolCooldown = 0;
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
        return tag;
    }
    public void load(CompoundTag tag)
    {
        clear();
        CompoundTag durations = tag.getCompound("remaining");
        for (String key : infection_symptom_rules.timedIds())
            set(key, durations.contains(key, Tag.TAG_ANY_NUMERIC) && durations.getDouble(key) == -1
                    ? -1 : bounded(durations, key, 12000));
        perceptionTicks = bounded(tag, "perception", 1199);
        poolCooldown = bounded(tag, "cooldown", 600);
    }
    private static int bounded(CompoundTag tag, String key, int max)
    {
        double value = tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : 0;
        return Double.isFinite(value) ? (int) Math.max(0, Math.min(max, value)) : 0;
    }
}
