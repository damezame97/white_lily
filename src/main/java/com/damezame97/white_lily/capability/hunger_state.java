package com.damezame97.white_lily.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/**
 * 挂在玩家身上的 {@code Happy_mode}「消耗计数器」状态。
 *
 * <h2>为什么单独开一个能力，而不是塞进 {@link progressive_tense}</h2>
 * <p>{@code progressive_tense} 是「作者以后自己用的通用变量包」，语义上跟饥饿机制无关。
 * 这个计数器是 {@code Happy_mode} 的运行时状态，独立成一个能力，字段各归各的。</p>
 *
 * <h2>存什么、不存什么</h2>
 * <ul>
 *     <li><b>计数器值</b>跟着玩家档案走（{@code serializeNBT} / {@code deserializeNBT}），
 *         退出重进不会重置；</li>
 *     <li>位置采样、两个计时器、以及「当前是不是饿到归零」这些是<b>每刻重算的临时状态</b>，
 *         不写档案（重进/重生后自然会重建）。</li>
 * </ul>
 *
 * <p>和 {@code progressive_tense} 一样，类上必须打 {@code @AutoRegisterCapability}
 * （它的 {@code @Target} 是 TYPE），注册靠类名。</p>
 */
@AutoRegisterCapability
public class hunger_state
{
    /** 序列化键名。 */
    private static final String KEY_COUNTER = "counter";

    /** 小于 0 表示「这份档案里还没有这个计数器」，首次使用时按属性算出来的上限填满。 */
    public static final float UNINITIALIZED = -1.0F;

    /** 计数器当前值。 */
    private float counter = UNINITIALIZED;

    // ---- 以下都不进档案（每刻重算的临时状态） ----

    /** 上一次采样时玩家所在的水平位置，用来算「这一米走了多远」。 */
    private double lastX;
    private double lastZ;

    /** 有没有采过样（刚登录那一帧还没法算位移）。 */
    private boolean hasLastPosition;

    /** 距离下一次掉意志坚定值还有几刻。 */
    private int spiritTimer;

    /** 距离下一次扣血还有几刻。 */
    private int healthTimer;

    /** 现在是不是「饿到归零」状态（对应的属性修正有没有加上去）。 */
    private boolean starving;

    public float getCounter()
    {
        return counter;
    }

    public void setCounter(float counter)
    {
        this.counter = counter;
    }

    /** 计数器还没初始化（档案里没有 / 刚挂上）。 */
    public boolean isUninitialized()
    {
        return counter < 0.0F;
    }

    /** 扣（负数就是加）计数器。 */
    public void add(float delta)
    {
        if (isUninitialized())
        {
            return;
        }
        this.counter += delta;
    }

    public double getLastX()
    {
        return lastX;
    }

    public double getLastZ()
    {
        return lastZ;
    }

    public void setLastPosition(double x, double z)
    {
        this.lastX = x;
        this.lastZ = z;
        this.hasLastPosition = true;
    }

    public boolean hasLastPosition()
    {
        return hasLastPosition;
    }

    public int getSpiritTimer()
    {
        return spiritTimer;
    }

    public void setSpiritTimer(int ticks)
    {
        this.spiritTimer = ticks;
    }

    public int getHealthTimer()
    {
        return healthTimer;
    }

    public void setHealthTimer(int ticks)
    {
        this.healthTimer = ticks;
    }

    public boolean isStarving()
    {
        return starving;
    }

    public void setStarving(boolean starving)
    {
        this.starving = starving;
    }

    /** 写进玩家档案。 */
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(KEY_COUNTER, counter);
        return tag;
    }

    /** 从玩家档案读回来；档案里没有这一项就保持「未初始化」。 */
    public void deserializeNBT(CompoundTag tag)
    {
        this.counter = tag.contains(KEY_COUNTER) ? tag.getFloat(KEY_COUNTER) : UNINITIALIZED;
    }

    /** 重生/换维度时把旧玩家身上的计数器拷到新玩家身上（临时状态不拷，重算即可）。 */
    public void copyFrom(hunger_state other)
    {
        this.counter = other.counter;
    }

    @Override
    public String toString()
    {
        return "hunger_state{counter=" + counter + ", starving=" + starving + '}';
    }
}
