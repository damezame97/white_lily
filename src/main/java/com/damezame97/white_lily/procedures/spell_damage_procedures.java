package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.white_lily;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「法术增幅」（{@code spell_enhancement}）与「法术暴击」（{@code spell_critical_strike}）的实际效果。
 *
 * <h2>需求（照 zh_cn.json 的 desc）</h2>
 * <ul>
 *     <li><b>法术增幅</b>：固定值加减，作用于「法术类型伤害的最终伤害」。desc 原例：属性 1 → 最终伤害 +1；
 *         「小数点后一位后部分不会被计算」，例 1.22 → 只算 1.2。措辞：和法术暴击比<b>先</b>算。</li>
 *     <li><b>法术暴击</b>：每 1 点提供 <b>4% 伤害区间</b>；「小数部分不会被计算」，例 1.83 → 只算 1 点
 *         → 4% 区间。区间含义：本次法术伤害在该区间里取<b>随机</b>百分比增幅。措辞：比法术增幅<b>后</b>算。</li>
 *     <li>两条都可以为负 → 负向衰减。</li>
 *     <li>额外要求：钳 0，绝不让伤害变成负数。</li>
 * </ul>
 *
 * <pre>
 * final = max(0, (原伤害 + 截断1位(增幅)) × (1 + 截断取整(暴击) × 4% × random[0,1]))
 * </pre>
 *
 * <p>两个「截断」都是<b>向零截断</b>（{@link RoundingMode#DOWN}），与本项目
 * {@code damage_mitigation_procedures#truncateToTwoDecimals} 的既有约定一致：
 * 1.22 → 1.2、1.83 → 1；−1.22 → −1.2、−1.83 → −1。</p>
 *
 * <h2>挂在哪</h2>
 * <p>用 ISS 自己的 {@link SpellDamageEvent}。看过 ISS 3.16.3 的
 * {@code io.redspace.ironsspellbooks.damage.DamageSources#applyDamage} 字节码：它构造
 * {@code SpellDamageEvent} 投到 Forge 总线，<b>随后就取 {@code event.getAmount()}</b> 作为
 * 实际伤害（之后才乘目标学派的法术抗性）。所以在这里 {@code setAmount} 既生效、又正好落在
 * 「射手造成的法术伤害」这一层，与铳械那两条属性（先算射手加成、再算目标抗性）保持一致。</p>
 * <p>施法者取 {@code SpellDamageSource#getEntity()}：ISS 建伤害源是
 * {@code new SpellDamageSource(直接实体, 造成实体, 位置, 法术)}，造成实体就是施法者。</p>
 * <p>本模组这两条属性只挂在玩家身上（见 {@code attributes.addAttributesToPlayers}），
 * 拿不到属性实例时直接不管。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class spell_damage_procedures {

    /** 法术暴击每 1 点提供的伤害区间（4%）。 */
    public static final double CRIT_RANGE_PER_POINT = 0.04D;

    /** 法术增幅保留的小数位数：小数点后一位之后不计算。 */
    public static final int ENHANCEMENT_SCALE = 1;

    private spell_damage_procedures() {
    }

    /**
     * 在 ISS 的「法术伤害」事件里改写最终伤害。
     *
     * <p>施法者拿不到本模组属性、或两条属性都是 0 时直接返回，不介入。</p>
     */
    @SubscribeEvent
    public static void onSpellDamage(SpellDamageEvent event) {
        SpellDamageSource source = event.getSpellDamageSource();
        Entity caster = source == null ? null : source.getEntity();
        if (!(caster instanceof LivingEntity living)) {
            return;
        }

        double enhancement = attributeValue(living, attributes.SPELL_ENHANCEMENT.get());
        double criticalStrike = attributeValue(living, attributes.SPELL_CRITICAL_STRIKE.get());
        if (enhancement == 0.0D && criticalStrike == 0.0D) {
            return;
        }

        event.setAmount((float) applyToSpellDamage(
                event.getAmount(), enhancement, criticalStrike,
                ThreadLocalRandom.current().nextDouble()));
    }

    /**
     * 结算公式（暴击取一次随机 roll）。
     *
     * @param damage         结算前的法术伤害
     * @param enhancement    法术增幅：固定值，截断到小数点后 1 位（先算）
     * @param criticalStrike 法术暴击：每 1 点提供 4% 随机伤害区间，小数部分不计算（后算）
     * @return 结算后的伤害，钳在 0 以上
     */
    public static double applyToSpellDamage(double damage, double enhancement, double criticalStrike) {
        return applyToSpellDamage(damage, enhancement, criticalStrike,
                ThreadLocalRandom.current().nextDouble());
    }

    /**
     * 结算公式（自带 roll，方便测试复现）。
     *
     * @param roll 本次暴击区间里取的随机比例，会先钳到 [0, 1]；0 = 不出暴击、1 = 吃满区间
     */
    // 公开是为了让 GameTest（com.damezame97.white_lily.gametest.WhiteLilyGameTests）能直接验证公式。
    public static double applyToSpellDamage(double damage, double enhancement, double criticalStrike, double roll) {
        double flat = damage + truncate(enhancement, ENHANCEMENT_SCALE);
        int points = (int) truncate(criticalStrike, 0);
        double bonusRate = points * CRIT_RANGE_PER_POINT * clampRoll(roll);
        return Math.max(0.0D, flat * (1.0D + bonusRate));
    }

    /** 向零截断到指定小数位（与 damage_mitigation_procedures 的既有约定一致）。 */
    private static double truncate(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.DOWN).doubleValue();
    }

    /** roll 钳到 [0, 1]：区间只提供「增幅」，不该被外部的越界值放大。 */
    private static double clampRoll(double roll) {
        return Math.max(0.0D, Math.min(1.0D, roll));
    }

    /** 取实体身上某条属性的值；实体没挂这条属性时返回 0（不介入）。 */
    private static double attributeValue(LivingEntity entity, Attribute attribute) {
        if (entity.getAttribute(attribute) == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute);
    }
}
