package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.white_lily;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 「伤害抵抗」（{@code damage_mitigation}）与「伤害减免」（{@code damage_reduction}）
 * 两个属性的结算逻辑。
 *
 * <p>结算顺序（与 zh_cn.json 的描述一致）：</p>
 * <ol>
 *     <li><b>伤害抵抗</b>：固定值加减。最终伤害 = 伤害 − 抵抗值。
 *         例：伤害 1、抵抗 −3 → 1 − (−3) = 4。</li>
 *     <li><b>伤害减免</b>：百分比减免，每 0.01 点提供 1%，小数点后两位之后的部分直接截断。
 *         例：减免 0.1 → 10% 减伤；0.012 → 只按 0.01（1%）计算。</li>
 * </ol>
 *
 * <p>硬性下限：无论怎么减免，最终伤害都不会低于 {@link #MIN_FINAL_DAMAGE}（0.05），
 * 即这两个属性不可能把伤害变成 0、负数。</p>
 *
 * <p>挂载点：{@link LivingDamageEvent}。Forge 在 {@code LivingEntity#actuallyHurt} 中
 * 先触发 {@code LivingHurtEvent}（护甲/附魔结算之前），再经过护甲吸收、魔法吸收、
 * 伤害吸收（黄心）结算，最后才以“最终伤害”触发 {@link LivingDamageEvent}——
 * 正是描述里说的「最终伤害」。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class damage_mitigation_procedures {

    /** 最终伤害硬性下限：两个属性最多把伤害压到这个值。 */
    public static final double MIN_FINAL_DAMAGE = 0.05D;

    private damage_mitigation_procedures() {
    }

    /**
     * 在「最终伤害」阶段套用伤害抵抗与伤害减免。
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();

        AttributeInstance mitigation = entity.getAttribute(attributes.DAMAGE_MITIGATION.get());
        AttributeInstance reduction = entity.getAttribute(attributes.DAMAGE_REDUCTION.get());

        // 两个属性都不存在时完全不介入，让原版逻辑照常走
        if (mitigation == null && reduction == null) {
            return;
        }

        double damage = event.getAmount();
        // 原版已经把伤害算成 0 了（例如被黄心完全吸收），不要凭空制造伤害
        if (damage <= 0.0D) {
            return;
        }

        double result = applyMitigationAndReduction(damage,
                mitigation == null ? 0.0D : mitigation.getValue(),
                reduction == null ? 0.0D : reduction.getValue());

        event.setAmount((float) result);
    }

    /**
     * 先算固定值「伤害抵抗」，再算百分比「伤害减免」，最后取硬性下限。
     *
     * @param damage     结算前的最终伤害
     * @param mitigation 伤害抵抗值（正数减伤，负数增伤）
     * @param reduction  伤害减免值（每 0.01 = 1%，正数减伤，负数增伤；≥1.0 时按 100% 封顶）
     * @return 结算后的最终伤害，不低于 {@link #MIN_FINAL_DAMAGE}
     */
    // 公开是为了让 GameTest（com.damezame97.white_lily.gametest.WhiteLilyGameTests）能直接验证公式。
    public static double applyMitigationAndReduction(double damage, double mitigation, double reduction) {
        double afterMitigation = damage - mitigation;
        // 减免最多按 100% 计算：属性超过 1.0 时不能把系数变成负数（那会反过来增伤）
        double rate = Math.min(1.0D, truncateToTwoDecimals(reduction));
        double afterReduction = afterMitigation * (1.0D - rate);
        return Math.max(MIN_FINAL_DAMAGE, afterReduction);
    }

    /**
     * 截断到小数点后两位（向下截断，不四舍五入）。
     *
     * <p>用 {@link BigDecimal} 而不是 {@code value * 100} 取整，是为了避开浮点误差：
     * {@code 0.29 * 100} 在 double 下会得到 {@code 28.999999999999996}，
     * 直接取整会把 0.29 错算成 0.28。</p>
     */
    static double truncateToTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN).doubleValue();
    }
}
