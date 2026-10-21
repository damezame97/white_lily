package com.damezame97.white_lily.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/**
 * 挂在玩家身上的「理智值」运行时状态（{@code white_lily.san} 的配套数据）。
 *
 * <h2>存什么</h2>
 * <ul>
 *     <li><b>初始化标记</b>（{@code initialized}）：理智值的默认值按世界档位写，
 *         但<b>只写一次</b>——它会被游戏过程持续消耗（Happy_mode 下饿到归零每 2 秒掉 1 点），
 *         每次登录都重设会把玩家进度全部抹掉。</li>
 *     <li><b>受伤计时器</b>（{@code injury_ticks}）：崩溃状态下每挨一次打就有的那个倒计时，
 *         上限 50 秒、首次 10 秒、之后每次 +5 秒。跟着档案走，退出重进不白送重置。</li>
 *     <li><b>融毁时被削掉的生命上限</b>（{@code meltdown_penalty}）：累计值，用负向
 *         {@code MAX_HEALTH} 修饰符体现；死亡重生时清零（否则会变成必死循环）。</li>
 * </ul>
 *
 * <p>和 {@link hunger_state} 一样，类上必须打 {@code @AutoRegisterCapability}
 * （它的 {@code @Target} 是 TYPE），注册靠类名，提供者在 {@link player_variables} 里挂。</p>
 */
@AutoRegisterCapability
public class san_state
{
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_INJURY_TICKS = "injury_ticks";
    private static final String KEY_MELTDOWN_PENALTY = "meltdown_penalty";

    /** 理智值的基础值是不是已经按世界档位写过默认值。 */
    private boolean initialized;

    /** 受伤计时器剩余刻数（0 = 没有计时器）。 */
    private int injuryTicks;

    /** 融毁状态下累计被削掉的生命上限（正数表示「少了这么多」）。 */
    private double meltdownPenalty;

    public boolean isInitialized()
    {
        return initialized;
    }

    public void setInitialized(boolean initialized)
    {
        this.initialized = initialized;
    }

    public int getInjuryTicks()
    {
        return injuryTicks;
    }

    public void setInjuryTicks(int ticks)
    {
        this.injuryTicks = Math.max(0, ticks);
    }

    public boolean hasInjuryTimer()
    {
        return injuryTicks > 0;
    }

    public double getMeltdownPenalty()
    {
        return meltdownPenalty;
    }

    public void setMeltdownPenalty(double penalty)
    {
        this.meltdownPenalty = Math.max(0.0D, penalty);
    }

    public void addMeltdownPenalty(double delta)
    {
        setMeltdownPenalty(this.meltdownPenalty + delta);
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_INITIALIZED, initialized);
        tag.putInt(KEY_INJURY_TICKS, injuryTicks);
        tag.putDouble(KEY_MELTDOWN_PENALTY, meltdownPenalty);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        this.initialized = tag.getBoolean(KEY_INITIALIZED);
        this.injuryTicks = Math.max(0, tag.getInt(KEY_INJURY_TICKS));
        this.meltdownPenalty = Math.max(0.0D, tag.getDouble(KEY_MELTDOWN_PENALTY));
    }

    /** 换维度时把旧玩家身上的值拷到新玩家身上。 */
    public void copyFrom(san_state other)
    {
        this.initialized = other.initialized;
        this.injuryTicks = other.injuryTicks;
        this.meltdownPenalty = other.meltdownPenalty;
    }

    /** 死亡重生：计时器与融毁罚值都清空（不然会无限循环死）。 */
    public void resetAfterDeath()
    {
        this.injuryTicks = 0;
        this.meltdownPenalty = 0.0D;
    }

    @Override
    public String toString()
    {
        return "san_state{initialized=" + initialized
                + ", injuryTicks=" + injuryTicks
                + ", meltdownPenalty=" + meltdownPenalty + '}';
    }
}
