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
 * 「精神损伤」——{@code white_lily:mental_injury}。
 *
 * <h2>它不是一次独立的伤害</h2>
 * <p>按需求，它<b>必须合并在一个主伤害里</b>：玩家受到伤害时，其中「受伤计时器秒数 %」那一部分
 * 在账面上算作精神损伤，但游戏里<b>只有一次伤害、一个伤害数字、一条主伤害类型的致死播报</b>。
 * 所以本类<b>从不</b>自己造 {@code hurt()} 调用，只提供伤害类型定义与工具方法；
 * 真正的换算在 {@link com.damezame97.white_lily.procedures.san_procedures} 里做
 * （在 {@code LivingHurtEvent} 里把那一份「改用无视减免的结算方式」补进同一次伤害里）。</p>
 *
 * <h2>三条标签</h2>
 * <ul>
 *     <li>{@link shadow_damage#TAG} —— 属于梦魇伤害一族，连带进五个 {@code bypasses_*}；</li>
 *     <li>{@link remit_loadcapacity#TAG} —— 不触发「载重值低于 90 后」那套追加伤害；</li>
 *     <li>五个原版 {@code bypasses_*}（由 {@link shadow_damage#IMPLIED_BYPASSES} 约束）。</li>
 * </ul>
 *
 * <h2>死亡提示</h2>
 * <p>理论上它永远不会成为致死原因（因为合并进主伤害），所以 {@code death.attack.mental_injury}
 * 与 {@code .player} 两个键<b>留空</b>，由作者自行填写。</p>
 */
public final class mental_injury
{
    /** 伤害类型 ID（即 {@code white_lily:mental_injury} 的路径部分）。 */
    public static final String NAME = "mental_injury";

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
     * 造一个无来源的伤害源。
     *
     * <p>只给测试与「万一需要单独结算」时用；正常流程里精神损伤不单独 {@code hurt()}。</p>
     */
    public static DamageSource source(Level level)
    {
        return new DamageSource(holder(level));
    }

    private mental_injury()
    {
    }
}
