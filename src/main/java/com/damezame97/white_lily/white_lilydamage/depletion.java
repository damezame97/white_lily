package com.damezame97.white_lily.white_lilydamage;

import com.damezame97.white_lily.white_lily;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 「枯竭伤害」——{@code Happy_mode} 下玩家饿到饥饿值归零后，每 12 秒被扣一次的那种伤害。
 *
 * <p>结构照抄 {@link abnormal_mana}：定义在数据包里
 * （{@code data/white_lily/damage_type/depletion.json}），本类只做代码侧入口。</p>
 *
 * <h2>十七种死亡播报</h2>
 * <p>需求要求这种死法随机播报 17 条之一。这里没有做成 17 个伤害类型，而是<b>一个伤害类型 +
 * 一个随机挑语言的混入</b>（见 {@code com.damezame97.white_lily.mixin.MixinDamageSource}）：
 * 伤害源还是同一个 {@code white_lily:depletion}，只是死亡提示改成从下面 17 个键里随机取一个。
 * 这样服务端广播给客户端的还是同一个 {@link Component}（死亡界面显示的就是它），不会两边不一致。</p>
 *
 * <p>这 17 个键现在<b>故意留空</b>，等作者自己填：
 * {@code death.attack.depletion.1} ... {@code death.attack.depletion.17}
 * （另有不带序号的 {@code death.attack.depletion} 作为兜底）。</p>
 *
 * <p>这个伤害类型同时被加进了 {@code minecraft:bypasses_armor} 等 5 个绕过标签，
 * 所以它是「无视盔甲/附魔/效果/无敌帧/盾牌」的固定伤害——需求要的是「每 12 秒扣掉最大生命值的
 * 20%，直至死亡」，不能被盔甲打折；也正因为它绕过盔甲，它不会反过来触发
 * 「受到被盔甲值影响的伤害」那条计数器消耗（见 {@code hunger_activity}）。</p>
 */
public final class depletion
{
    /** 伤害类型 ID（即 {@code white_lily:depletion} 的路径部分）。 */
    public static final String NAME = "depletion";

    /** 死亡提示的 message_id —— 必须和 damage_type JSON 里的 {@code message_id} 逐字一致。 */
    public static final String MESSAGE_ID = NAME;

    /** 随机播报的条数。 */
    public static final int DEATH_MESSAGE_COUNT = 17;

    /** 带序号的死亡提示语言键前缀。 */
    private static final String DEATH_MESSAGE_PREFIX = "death.attack." + MESSAGE_ID + ".";

    /** 不带序号的兜底键（万一随机那条是空串，作者可以只填这一条）。 */
    public static final String DEATH_MESSAGE_FALLBACK_KEY = "death.attack." + MESSAGE_ID;

    /** 伤害类型在数据包注册表里的键。 */
    public static final ResourceKey<DamageType> KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(white_lily.MODID, NAME));

    /** 第 {@code index}（从 1 数到 17）条死亡提示的语言键。 */
    public static String deathMessageKey(int index)
    {
        return DEATH_MESSAGE_PREFIX + index;
    }

    /**
     * 从关卡拿到伤害类型的注册表句柄。
     *
     * <p>伤害类型是数据包注册表，必须走 {@code level.registryAccess()}，静态的
     * {@code BuiltInRegistries} 里没有这个注册表。</p>
     */
    public static Holder<DamageType> holder(Level level)
    {
        return level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KEY);
    }

    /** 造一个无来源的伤害源（不会触发仇恨，也没有凶手）。 */
    public static DamageSource source(Level level)
    {
        return new DamageSource(holder(level));
    }

    /**
     * 随机挑一条死亡播报。
     *
     * @param victim 死掉的那个实体（用来填 {@code %1$s}）
     */
    public static Component randomDeathMessage(LivingEntity victim)
    {
        int index = victim.getRandom().nextInt(DEATH_MESSAGE_COUNT) + 1;
        return Component.translatable(deathMessageKey(index), victim.getDisplayName());
    }

    private depletion()
    {
    }
}
