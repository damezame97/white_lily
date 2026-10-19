package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilydamage.nightmare;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「箭术精通」（{@code expert_in_archery}）、「箭矢暴击」（{@code arrow_strike}）与
 * 「箭矢暴击伤害」（{@code arrow_crit_damage}）的实际效果。
 *
 * <h2>需求（照 zh_cn.json 的 desc）</h2>
 * <ul>
 *     <li><b>箭术精通</b>：固定值加减，作用于「原版箭矢伤害类型的最终伤害」。原例：属性 1 → 最终伤害 +1。
 *         可为负（负向衰减），并要求钳 0 以免出现负数伤害。措辞：比箭矢暴击伤害<b>先</b>算。</li>
 *     <li><b>箭矢暴击</b>：触发概率 = 属性值（每 0.01 → 1%）。小数部分按 desc 的原例截掉：
 *         {@code 0.0018 → 0.1%}（见 {@link #ARROW_STRIKE_SCALE} 的说明）。不可为负（注册下限是 0）。</li>
 *     <li><b>箭矢暴击伤害</b>：<b>触发时</b>额外造成 `属性值 × 已结算箭矢伤害` 的<b>梦魇伤害</b>
 *         （每 0.01 → 1%，即值 1.50 → 150%）。措辞：比箭术精通<b>后</b>算。不可为负。</li>
 * </ul>
 *
 * <pre>
 * base  = max(0, 箭矢伤害 + 箭术精通)
 * 触发   = random[0,1) &lt; 截断3位(箭矢暴击)      // 且 base &gt; 0
 * extra = 触发 ? base × 箭矢暴击伤害 : 0        // 以 white_lily:nightmare 单独结算
 * </pre>
 *
 * <h2>{@link #ARROW_STRIKE_SCALE} 为什么是 3 而不是 2</h2>
 * <p>desc 的正文写「小数后<b>两</b>位后部分不会被计算」，但它自己举的例子是
 * 「属性为 0.0018 则只提供 <b>0.1%</b> 概率」：0.0018 截到 2 位是 0.00 → 0%，对不上例子；
 * 截到 3 位才是 0.001 → 0.1%，与例子一致。以<b>例子</b>为准，取 3。要按正文改成 2，
 * 改这一个常量即可。</p>
 *
 * <h2>挂在哪 / 怎么认箭矢伤害</h2>
 * <p>用 {@link LivingHurtEvent}（优先级 {@link EventPriority#HIGHEST}）。判定条件是
 * {@code source.is(DamageTypes.ARROW)}——原版弓/弩的箭（{@code AbstractArrow}）走的正是
 * {@code minecraft:arrow} 这个伤害类型；三叉戟是 {@code minecraft:trident}，不在范围内。</p>
 * <p>取射手用 {@code DamageSource#getEntity()}（原版 {@code AbstractArrow} 把弓的主人填成造成实体）。
 * 本模组属性只挂在玩家身上，拿不到属性实例时直接不管。</p>
 *
 * <h2>梦魇伤害怎么结算</h2>
 * <p>desc 说额外造成的是「梦魇伤害」，而本模组对「梦魇伤害」的定义就是
 * {@link com.damezame97.white_lily.white_lilydamage.shadow_damage} 那个标签（无视盔甲 /
 * 无敌帧 / 状态效果 / 附魔 / 盾牌）。所以这一份<b>不能</b>并进箭矢伤害里（那样会被盔甲削掉），
 * 而是用 {@link nightmare} 这个伤害类型<b>单独造成一次</b>。</p>
 * <p>就地在 {@code LivingHurtEvent} 里调 {@code target.hurt(...)} 是安全的：那个伤害类型带着
 * {@code bypasses_cooldown}，不会被无敌帧吃掉；而且它自己再触发一次 {@code LivingHurtEvent} 时，
 * 伤害类型不是 {@code minecraft:arrow}，本处理器会直接返回，不会递归。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class archery_procedures {

    /**
     * 「箭矢暴击」截断到小数点后几位。
     *
     * <p>取 3：desc 原例 0.0018 → 0.1%（0.0018 截到 3 位 = 0.001）。详见类注释。</p>
     */
    public static final int ARROW_STRIKE_SCALE = 3;

    private archery_procedures() {
    }

    /**
     * 在「受击」阶段结算三条箭矢属性。
     *
     * <p>伤害源不是 {@code minecraft:arrow}、或造成伤害的实体不是活体（拿不到属性）时直接返回。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null || !source.is(DamageTypes.ARROW)) {
            return;
        }

        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity shooter)) {
            return;
        }

        // 1) 箭术精通：固定值加在最终箭矢伤害上（先算）。
        double expertise = attributeValue(shooter, attributes.EXPERT_IN_ARCHERY.get());
        double base = expertInArcheryDamage(event.getAmount(), expertise);
        event.setAmount((float) base);

        // 2) 箭矢暴击：按概率触发。
        double strike = attributeValue(shooter, attributes.ARROW_STRIKE.get());
        double chance = arrowStrikeChance(strike);
        if (chance <= 0.0D || base <= 0.0D) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        // 3) 触发时额外造成梦魇伤害（后算；基数是已加过箭术精通的伤害）。
        double critDamage = attributeValue(shooter, attributes.ARROW_CRIT_DAMAGE.get());
        float extra = (float) nightmareAmount(base, critDamage);
        if (extra <= 0.0F) {
            return;
        }

        LivingEntity victim = event.getEntity();
        victim.hurt(nightmare.source(victim.level(), shooter), extra);
    }

    /**
     * 箭矢暴击的触发概率：属性值截断到 {@link #ARROW_STRIKE_SCALE} 位小数后钳到 [0, 1]。
     *
     * <p>公开是为了让 GameTest 能直接验证（desc 原例 0.0018 → 0.001）。</p>
     */
    public static double arrowStrikeChance(double arrowStrike) {
        double truncated = truncate(arrowStrike, ARROW_STRIKE_SCALE);
        return Math.max(0.0D, Math.min(1.0D, truncated));
    }

    /**
     * 箭术精通：固定值加减，并钳 0（需求明确要求不出现负数伤害）。
     *
     * @param damage           结算前的箭矢伤害
     * @param expertInArchery  箭术精通值（正数增伤，负数衰减）
     */
    public static double expertInArcheryDamage(double damage, double expertInArchery) {
        return Math.max(0.0D, damage + expertInArchery);
    }

    /**
     * 箭矢暴击伤害：触发时要额外造成的梦魇伤害量 = 箭矢伤害 × 属性值。
     *
     * @param baseDamage      已加过箭术精通的箭矢伤害
     * @param arrowCritDamage 箭矢暴击伤害属性（每 0.01 = 1%）
     */
    public static double nightmareAmount(double baseDamage, double arrowCritDamage) {
        return Math.max(0.0D, baseDamage * Math.max(0.0D, arrowCritDamage));
    }

    /** 向零截断到指定小数位（与 damage_mitigation_procedures 的既有约定一致）。 */
    private static double truncate(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.DOWN).doubleValue();
    }

    /** 取实体身上某条属性的值；实体没挂这条属性时返回 0（不介入）。 */
    private static double attributeValue(LivingEntity entity, Attribute attribute) {
        if (entity.getAttribute(attribute) == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute);
    }
}
