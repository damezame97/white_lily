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
 * 「异常魔力伤害」——本模组第一个自定义伤害类型。
 *
 * <h2>为什么这个类里没有「注册」代码</h2>
 * <p>1.20.1 的伤害类型是<b>数据包注册表</b>（{@code minecraft:damage_type}），
 * 只能在数据包里声明、由游戏加载数据包时读进来，走不了 {@code DeferredRegister}。
 * 所以真正的定义在：</p>
 * <pre>src/main/resources/data/white_lily/damage_type/abnormal_mana.json</pre>
 * <p>本类只负责代码侧的入口：把那个 ID 包成 {@link ResourceKey}，再提供构造
 * {@link DamageSource} 的工厂方法。以后本模组新增的伤害类型都放进
 * {@code white_lilydamage} 这个包，互相之间容易找。</p>
 *
 * <h2>死亡提示</h2>
 * <p>伤害类型的 {@code message_id} 决定死亡提示查哪个语言键：{@code death.attack.<message_id>}，
 * 有玩家凶手时是 {@code death.attack.<message_id>.player}。这两个键现在<b>故意留空</b>，
 * 等作者自己填（见 lang 文件）。</p>
 */
public final class abnormal_mana
{
    /** 伤害类型 ID（即 {@code white_lily:abnormal_mana} 的路径部分）。 */
    public static final String NAME = "abnormal_mana";

    /** 死亡提示的 message_id —— 必须和 damage_type JSON 里的 {@code message_id} 逐字一致。 */
    public static final String MESSAGE_ID = NAME;

    /** 死亡提示的基础语言键（无凶手时用）。 */
    public static final String DEATH_MESSAGE_KEY = "death.attack." + MESSAGE_ID;

    /** 死亡提示的「被玩家杀死」变体语言键。 */
    public static final String DEATH_MESSAGE_PLAYER_KEY = DEATH_MESSAGE_KEY + ".player";

    /** 伤害类型在数据包注册表里的键。 */
    public static final ResourceKey<DamageType> KEY =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(white_lily.MODID, NAME));

    /**
     * 从关卡拿到伤害类型的注册表句柄。
     *
     * <p>必须走 {@code level.registryAccess()}：伤害类型是随存档/数据包加载的，
     * 用静态的 {@code BuiltInRegistries} 拿不到（那里根本不存在这个注册表）。</p>
     */
    public static Holder<DamageType> holder(Level level)
    {
        return level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(KEY);
    }

    /**
     * 造一个<b>无来源</b>的伤害源。
     *
     * <p>走 {@code new DamageSource(holder)} 这个单参构造：没有直接实体、没有凶手实体、没有位置。
     * 于是它不会触发反击/仇恨，死亡提示也只查无凶手那一版语言键。</p>
     */
    public static DamageSource source(Level level)
    {
        return new DamageSource(holder(level));
    }

    private abnormal_mana()
    {
    }
}
