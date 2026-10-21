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
 * 「融毁」致死伤害 —— {@code white_lily:meltdown}。
 *
 * <p>理智值（{@code white_lily.san}）掉进<b>融毁</b>状态（&lt; 1000）后，玩家每次受伤都会把
 * 「损失的血量」等量削掉生命上限；生命上限被削到 0 时，用这个伤害类型执行死亡，
 * 于是致死播报查 {@code death.attack.meltdown} / {@code .player}。</p>
 *
 * <p>按需求这两个语言键<b>留空</b>，由作者自行填写（见 {@code zh_cn.json} / {@code en_us.json}）。</p>
 *
 * <p>本类型同样属于 {@link shadow_damage#TAG}（梦魇伤害一族），因此无视盔甲、无敌帧、
 * 状态效果、附魔与盾牌——生命上限都归零了，不该再被任何减免拖住。</p>
 */
public final class meltdown
{
    /** 伤害类型 ID（即 {@code white_lily:meltdown} 的路径部分）。 */
    public static final String NAME = "meltdown";

    /** 死亡提示的 message_id —— 必须和 damage_type JSON 里的 {@code message_id} 逐字一致。 */
    public static final String MESSAGE_ID = NAME;

    /** 死亡提示的基础语言键（无凶手时用）。 */
    public static final String DEATH_MESSAGE_KEY = "death.attack." + MESSAGE_ID;

    /** 死亡提示的「有凶手」变体语言键。 */
    public static final String DEATH_MESSAGE_PLAYER_KEY = DEATH_MESSAGE_KEY + ".player";

    /** 伤害类型在数据包注册表里的键。 */
    public static final ResourceKey<DamageType> KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(white_lily.MODID, NAME));

    /** 从关卡拿到伤害类型的注册表句柄。 */
    public static Holder<DamageType> holder(Level level)
    {
        return level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KEY);
    }

    /** 无来源的伤害源（生命上限归零不是「谁打的」，不该给任何人记仇）。 */
    public static DamageSource source(Level level)
    {
        return new DamageSource(holder(level));
    }

    private meltdown()
    {
    }
}
