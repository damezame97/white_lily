package com.damezame97.white_lily.white_lilybuff;

import com.damezame97.white_lily.white_lilydamage.abnormal_mana;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 「不洁魔力」——本模组第一个自定义状态效果。
 *
 * <h2>它是什么</h2>
 * <ul>
 *     <li><b>中立</b>类别（{@link MobEffectCategory#NEUTRAL}）——不算增益也不算减益，
 *         所以它不会因为「有益/有害」的判定被别的机制顺手清掉；</li>
 *     <li><b>牛奶消不掉、蜂蜜也消不掉</b>（见下面的 {@code getCurativeItems()}）。</li>
 * </ul>
 *
 * <h2>效果流程</h2>
 * <p>效果挂在实体上期间，<b>每 10 游戏刻</b>对宿主造成一次
 * {@code 0 + 效果等级} 点、<b>无来源</b>、类型为 {@code white_lily:abnormal_mana} 的伤害。</p>
 * <ul>
 *     <li>「每 10 刻」用 {@link #isDurationEffectTick(int, int)} 实现——这是原版给效果自带的
 *         节流钩子，比在 {@code applyEffectTick} 里数 {@code tickCount} 更干净，
 *         也不用改实体状态；</li>
 *     <li>「0 + 效果等级」里的等级取的是 {@code amplifier}：等级 I 时 amplifier=0 → 0 点伤害，
 *         等级 II → 1 点，以此类推；</li>
 *     <li>「无来源」= {@code new DamageSource(holder)}，不挂任何实体，
 *         所以它的死亡提示只会走 {@code death.attack.abnormal_mana}（没有 {@code .player} 变体）。</li>
 * </ul>
 *
 * <h2>牛奶/蜂蜜消不掉是怎么做到的</h2>
 * <p>1.20.1 里喝牛奶走的是 {@code LivingEntity#curePotionEffects(ItemStack)}：它逐个效果地问
 * {@code MobEffectInstance#isCurativeItem(stack)}，而那个方法的清单来自
 * <b>{@code MobEffect#getCurativeItems()}</b>（Forge 的 {@code IForgeMobEffect} 扩展接口）。</p>
 * <p>Forge 的默认实现返回 {@code [MILK_BUCKET]}；这里覆写成<b>空表</b>，
 * 于是牛奶（以及任何走同一套「可治愈物品」机制的物品，例如蜂蜜）都拿它没办法。
 * 蜂蜜本身在原版只清除中毒，所以它天然动不了中立效果——这条覆写主要是为了牛奶。</p>
 */
public class impure_magic extends MobEffect
{
    /** 注册名（即 {@code white_lily:impure_magic} 的路径部分、贴图文件名与语言键尾段）。 */
    public static final String NAME = "impure_magic";

    /** 结算间隔：每 10 游戏刻一次。 */
    public static final int TICK_INTERVAL = 10;

    /**
     * 效果颜色（HUD 里图标边框与药水粒子用）。
     *
     * <p>不在类里挑颜色的话，原版会拿构造参数当颜色用；这里给的是偏紫的一档，配合「魔力」的意象。</p>
     */
    public static final int COLOR = 0x7A2FBF;

    public impure_magic()
    {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }

    // ==================================================================
    // 每 10 刻结算一次
    // ==================================================================

    /**
     * 原版的「这一刻要不要跑 {@link #applyEffectTick}」开关。
     *
     * <p>{@code duration} 从总时长往下数，所以 {@code duration % 10 == 0} 表示每 10 刻命中一次。
     * 用时长取模而不是读 {@code entity.tickCount}：时长是效果自己的状态，实体挂上效果之前的历史
     * 不会影响节奏，而且客户端/服务端算出来一致。</p>
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier)
    {
        return duration % TICK_INTERVAL == 0;
    }

    /**
     * 对宿主结算一次伤害。
     *
     * <p>只在服务端真正扣血；客户端本地预测会跑同一套代码，不拦住就会看到血量来回跳。</p>
     */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier)
    {
        if (entity.level().isClientSide)
        {
            return;
        }

        // 伤害 = 0 + 效果等级（amplifier）。等级 I 时是 0 点，等于这一跳只是「空跳」。
        entity.hurt(abnormal_mana.source(entity.level()), (float) amplifier);
    }

    // ==================================================================
    // 牛奶/蜂蜜消不掉
    // ==================================================================

    /**
     * 「哪些物品能治愈这个效果」的清单。
     *
     * <p>Forge 默认给的是 {@code [牛奶桶]}；返回空表 = 谁都治不了。
     * 注意 {@code MobEffectInstance} 会把这个清单缓存到自己身上，
     * 所以这里的返回值在效果实例创建时就被取用了——改这里就会立刻生效，不需要额外注册。</p>
     */
    @Override
    public List<ItemStack> getCurativeItems()
    {
        return List.of();
    }
}
