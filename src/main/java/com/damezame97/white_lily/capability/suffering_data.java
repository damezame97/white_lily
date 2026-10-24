package com.damezame97.white_lily.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 玩家自己的延期账本。只保存金额和剩余刻数；离线不会推进计时。 */
@AutoRegisterCapability
public class suffering_data
{
    private final List<Entry> entries = new ArrayList<>();
    private long lastTick = Long.MIN_VALUE;
    public boolean equipmentReady;
    public Map<Integer, UUID> equipment = Map.of();
    public float lastDisplayed = Float.NaN;
    /** Transient HUD synchronization state; never part of the saved ledger. */
    public float lastHudSynced = Float.NaN;
    public float clientPending;
    public long nextDisplayTick;

    public void add(float amount, long createdTick)
    {
        if (Float.isFinite(amount) && amount > 0.0F)
        {
            entries.add(new Entry(amount, 100, createdTick));
        }
    }

    /** 同一服务端刻只推进一次，当刻新产生的欠款不提前少算一刻。 */
    public float advance(long tick)
    {
        if (lastTick == tick)
        {
            return 0.0F;
        }
        lastTick = tick;
        float due = 0.0F;
        for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext();)
        {
            Entry entry = iterator.next();
            if (entry.createdTick != tick && --entry.remaining <= 0)
            {
                due += entry.amount;
                iterator.remove();
            }
        }
        return due;
    }

    public float total()
    {
        float total = 0.0F;
        for (Entry entry : entries)
        {
            total += entry.amount;
        }
        return total;
    }

    public float drain()
    {
        float total = total();
        entries.clear();
        return total;
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        ListTag pending = new ListTag();
        for (Entry entry : entries)
        {
            CompoundTag record = new CompoundTag();
            record.putFloat("amount", entry.amount);
            record.putInt("remaining", entry.remaining);
            pending.add(record);
        }
        tag.put("pending", pending);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        entries.clear();
        for (Tag value : tag.getList("pending", Tag.TAG_COMPOUND))
        {
            CompoundTag record = (CompoundTag) value;
            float amount = record.getFloat("amount");
            if (Float.isFinite(amount) && amount > 0.0F)
            {
                entries.add(new Entry(amount, Math.max(1, Math.min(100, record.getInt("remaining"))), Long.MIN_VALUE));
            }
        }
        lastTick = Long.MIN_VALUE;
        equipmentReady = false;
        equipment = Map.of();
        lastDisplayed = Float.NaN;
        lastHudSynced = Float.NaN;
        nextDisplayTick = 0;
    }

    private static class Entry
    {
        final float amount;
        int remaining;
        final long createdTick;

        Entry(float amount, int remaining, long createdTick)
        {
            this.amount = amount;
            this.remaining = remaining;
            this.createdTick = createdTick;
        }
    }
}
