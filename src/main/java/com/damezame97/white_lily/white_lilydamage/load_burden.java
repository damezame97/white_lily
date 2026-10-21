package com.damezame97.white_lily.white_lilydamage;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/**
 * 「载重反噬伤害」——{@code white_lily:load_burden}。
 *
 * <h2>它属于「梦魇伤害」这一族</h2>
 * <p>与 {@link nightmare} 一样，本类型登记进了 {@link shadow_damage#TAG} 与它蕴含的五个原版
 * bypasses_* 标签，因此<b>无视盔甲、无敌帧、状态效果、附魔保护与盾牌</b>。</p>
 *
 * <h2>谁在用</h2>
 * <p>「载重值」（{@code white_lily.load_capacity}）低到 90 以下时，玩家每次受到伤害都会额外挨一次
 * 这个伤害（数值 = 玩家生命上限 × 百分比）。见
 * {@link com.damezame97.white_lily.procedures.load_capacity_procedures}。</p>
 *
 * <h2>死亡提示</h2>
 * <p>{@code message_id} 是 {@code load_burden}，所以死亡提示查 {@code death.attack.load_burden}
 * （有凶手时是 {@code death.attack.load_burden.player}）。按需求这两个键的内容<b>留空</b>，
 * 由作者自行填写。</p>
 */
public final class load_burden
{
    /** 伤害类型 ID（即 {@code white_lily:load_burden} 的路径部分）。 */
    public static final String NAME = "load_burden";

    /** 死亡提示的 message_id —— 必须和 damage_type JSON 里的 {@code message_id} 逐字一致。 */
    public static final String MESSAGE_ID = NAME;

    /** 死亡提示的基础语言键（无凶手时用）。 */
    public static final String DEATH_MESSAGE_KEY = "death.attack." + MESSAGE_ID;

    /** 死亡提示的「有凶手」变体语言键。 */
    public static final String DEATH_MESSAGE_PLAYER_KEY = DEATH_MESSAGE_KEY + ".player";

    /** 伤害类型在数据包注册表里的键。 */
    public static final ResourceKey<DamageType> KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(white_lily.MODID, NAME));

    /** 从关卡拿到伤害类型的注册表句柄（和 {@link abnormal_mana#holder} 一样必须走 registryAccess）。 */
    public static Holder<DamageType> holder(Level level)
    {
        return level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KEY);
    }

    /**
     * 造一个<b>无来源</b>的反噬伤害源。
     *
     * <p>没有直接实体、没有凶手实体：这是玩家自己的「载重」压出来的伤害，不该给任何人记仇，
     * 也不该被「玩家造成的伤害」那套加成再算一遍。</p>
     */
    public static DamageSource source(Level level)
    {
        return new DamageSource(holder(level));
    }

    private load_burden()
    {
    }
}
