package com.damezame97.white_lily.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

/**
 * 挂在玩家身上的「独立变量类」——{@code progressive_tense}。
 *
 * <h2>它跟「往 NBT 里塞键值」有什么不同</h2>
 * <p>以前那种写法是 {@code player.getPersistentData().putInt("xxx", 1)}——名字是字符串，
 * 取的时候要记住每一个键、忘了就静默拿到 0。这里换成<b>一个带类型字段的类</b>：</p>
 * <ol>
 *     <li>字段是真正的 Java 字段（{@code int} / {@code boolean} / 以后想加的任意类型），
 *         编译期就能查错，不需要记字符串键；</li>
 *     <li>怎么存、怎么读由类自己负责（{@link #serializeNBT()} / {@link #deserializeNBT(CompoundTag)}），
 *         外面只碰字段，不碰 NBT；</li>
 *     <li>它只挂在<b>玩家</b>实体上（见 {@link player_variables}），怪物身上根本不存在这个能力。</li>
 * </ol>
 * <p><b>一句话说清「不同于 nbt」</b>：类本身是类型化变量，不是一堆 NBT 键；
 * 但 Minecraft 的存档格式就是 NBT，Forge 最后仍然会把它序列化成 NBT 存进玩家档案
 * （{@code ForgeCaps} 那一块），这是框架层的事，我们不用管，也不用自己找地方存。</p>
 *
 * <p>以后怎么加变量</p>
 * <p>加字段 → 在 {@link #serializeNBT()} 与 {@link #deserializeNBT(CompoundTag)} 里各写一行 →
 * 在 {@link #copyFrom(progressive_tense)} 里补一行（重生时拷值用）。就这三步。</p>
 *
 * <h2>注意：类上那个注解不能挪</h2>
 * <p>{@code @AutoRegisterCapability} 的 {@code @Target} 是 {@code TYPE}，Forge 是拿<b>类名</b>去
 * 创建能力的（扫到注解 → {@code CapabilityManager.get(类名, true)}），所以它必须打在这个类（能力的数据类）上，
 * 不能打在 {@code player_variables} 里那个字段上。两者最后靠同一套名字对起来。</p>
 */
@AutoRegisterCapability
public class progressive_tense
{
    /** 序列化用的键名——只在类内部使用，外面看不到。 */
    private static final String KEY_STAGE = "stage";
    private static final String KEY_TENSE = "tense";

    /** 示例字段 1：阶段/进度，随便你后面拿它当什么。 */
    private int stage;

    /** 示例字段 2：一个开关。 */
    private boolean tense;

    public int getStage()
    {
        return stage;
    }

    public progressive_tense setStage(int stage)
    {
        this.stage = stage;
        return this;
    }

    public boolean isTense()
    {
        return tense;
    }

    public progressive_tense setTense(boolean tense)
    {
        this.tense = tense;
        return this;
    }

    /** 写进玩家档案。 */
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_STAGE, stage);
        tag.putBoolean(KEY_TENSE, tense);
        return tag;
    }

    /** 从玩家档案读回来。 */
    public void deserializeNBT(CompoundTag tag)
    {
        this.stage = tag.getInt(KEY_STAGE);
        this.tense = tag.getBoolean(KEY_TENSE);
    }

    /** 重生/换维度时把旧玩家身上的值拷到新玩家身上。 */
    public void copyFrom(progressive_tense other)
    {
        this.stage = other.stage;
        this.tense = other.tense;
    }

    @Override
    public String toString()
    {
        return "progressive_tense{stage=" + stage + ", tense=" + tense + "}";
    }
}
