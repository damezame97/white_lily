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
 * 「大出血伤害」——{@code white_lily:excessive_hemorrhage}。
 *
 * <h2>谁在用</h2>
 * <p>伤口渗血 3 级（疾跑每 6 格 1 点）与 4 级（疾跑或移动每 3 格 生命上限 20% + 2 点）。
 * 见 {@link com.damezame97.white_lily.infection.infection_symptom_rules}。</p>
 *
 * <h2>标签</h2>
 * <ul>
 *     <li>{@link shadow_damage#TAG} —— 梦魇伤害一族，连带五个 {@code bypasses_*}（无视盔甲/无敌帧/效果/附魔/盾牌）；</li>
 *     <li>{@link remit_loadcapacity#TAG} —— 不触发载重反噬，也不会被当成「直接命中」再延长渗血。</li>
 * </ul>
 *
 * <h2>死亡提示</h2>
 * <p>{@code message_id} 是 {@code excessive_hemorrhage}，所以查 {@code death.attack.excessive_hemorrhage}
 * 与 {@code death.attack.excessive_hemorrhage.player}。按需求这两个键的内容<b>留空</b>，由作者自行填写。</p>
 */
public final class excessive_hemorrhage
{
    /** 伤害类型 ID（即 {@code white_lily:excessive_hemorrhage} 的路径部分）。 */
    public static final String NAME = "excessive_hemorrhage";

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

    /**
     * 造一个无来源的出血伤害源。
     *
     * <p>是玩家自己的伤口在流血，不该给任何人记仇，也不该被「某生物造成的伤害」那套逻辑再算一遍。
     * 带着 {@code bypasses_cooldown}，所以连续结算不会被无敌帧吞掉。</p>
     */
    public static DamageSource source(Level level)
    {
        return new DamageSource(holder(level));
    }

    private excessive_hemorrhage()
    {
    }
}
