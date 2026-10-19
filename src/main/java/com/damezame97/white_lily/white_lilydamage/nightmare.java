package com.damezame97.white_lily.white_lilydamage;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 「梦魇伤害」——本模组的第二个伤害类型（见 {@code white_lily:nightmare}）。
 *
 * <h2>它属于「梦魇伤害」这一族</h2>
 * <p>本类定义的伤害类型被登记进 {@link shadow_damage#TAG} 与它所蕴含的五个原版 bypasses_*
 * 标签（见 {@code data/white_lily/tags/damage_type/shadow_damage.json} 与
 * {@code data/minecraft/tags/damage_type/*.json}），因此它自动<b>无视盔甲、无敌帧、
 * 状态效果、附魔保护与盾牌</b>。</p>
 *
 * <h2>谁在用</h2>
 * <p>「箭矢暴击伤害」（{@code white_lily.arrow_crit_damage}）触发时，用它额外结算那一份
 * 梦魇伤害，见 {@link com.damezame97.white_lily.procedures.archery_procedures}。</p>
 *
 * <h2>为什么没有「注册」代码</h2>
 * <p>与 {@link abnormal_mana} 同理：1.20.1 的伤害类型是数据包注册表，定义在
 * {@code src/main/resources/data/white_lily/damage_type/nightmare.json}，本类只做代码侧入口。</p>
 *
 * <h2>死亡提示</h2>
 * <p>{@code message_id} 是 {@code nightmare}，所以死亡提示查
 * {@code death.attack.nightmare}（有凶手时是 {@code death.attack.nightmare.player}）。</p>
 */
public final class nightmare
{
    /** 伤害类型 ID（即 {@code white_lily:nightmare} 的路径部分）。 */
    public static final String NAME = "nightmare";

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
     * 造一个<b>带凶手</b>的梦魇伤害源。
     *
     * <p>直接实体与凶手实体都填 {@code attacker}，于是死亡提示会走
     * {@link #DEATH_MESSAGE_PLAYER_KEY} 那一版（能带上凶手名字），也会正常计入仇恨。</p>
     */
    public static DamageSource source(Level level, Entity attacker)
    {
        return new DamageSource(holder(level), attacker, attacker);
    }

    private nightmare()
    {
    }
}
