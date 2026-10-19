package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.white_lily;
import com.tacz.guns.init.ModDamageTypes;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「铳械精通」（{@code rifle_expertise}）与「铳械增幅」（{@code rifle_enhancement}）的实际效果。
 *
 * <h2>需求（照 zh_cn.json 的 desc）</h2>
 * <ul>
 *     <li><b>铳械增幅</b>：固定值加减。desc 原例：最终枪械伤害 5、该属性 −1 → 变成 4。
 *         且写明「和'铳械专精'处理时<b>先</b>计算该属性」。</li>
 *     <li><b>铳械精通</b>：百分比。每 0.01 点 → 最终枪械伤害 +1%（即 0.10 → +10%）。
 *         且写明「和'铳械共感'处理时<b>后</b>计算该属性」。</li>
 *     <li>两条都可以为负 → 负向衰减（−0.10 → 伤害 ×0.90）。</li>
 * </ul>
 *
 * <h2>结算顺序</h2>
 * <p>两条 desc 各拿「另一条暂未实现的属性」作参照（铳械专精 / 铳械共感，本项目里都不存在），
 * 所以彼此的先后只能按它们自己的「先 / 后」措辞来定：<b>先算固定值（增幅），再乘百分比（精通）</b>——
 * 与本项目「法术增幅 → 法术暴击」那一对的顺序一致：</p>
 * <pre>
 * final = max(0, (原伤害 + 增幅) × (1 + 精通))
 * </pre>
 * <p>最后钳到 0：负到把伤害压成负数时会变成「治疗」，原版 {@code LivingEntity#hurt} 在
 * 事件后还有一次 {@code if (amount <= 0) return false;}，钳掉最省事也最安全。</p>
 *
 * <h2>挂在哪 / 怎么认枪伤</h2>
 * <p>看 TaCZ 1.1.8 的字节码：子弹命中走 {@code EntityKineticBullet#tacAttackEntity} →
 * {@code Entity#hurt(source, amount)} → Forge 的 {@code LivingHurtEvent}；TaCZ 自己也在
 * {@code com.tacz.guns.event.EntityDamageEvent#onLivingHurt} 里监听同一个事件（把目标身上的
 * {@code tacz:bullet_resistance} 折算掉）。所以这里也挂 {@link LivingHurtEvent}。</p>
 * <p>「枪械类型伤害」的判据直接用 TaCZ 自己的标签 {@link ModDamageTypes#BULLETS_TAG}
 * （{@code tacz:bullets} = bullet / bullet_ignore_armor / bullet_void / bullet_void_ignore_armor），
 * 比一个个比 damage type 稳，TaCZ 以后加弹种也自动覆盖。</p>
 * <p>取「造成伤害的那一方」用 {@code DamageSource#getEntity()}：TaCZ 建伤害源时是
 * {@code new DamageSource(类型, 直接实体=子弹, 造成实体=枪的主人)}，所以 {@code getEntity()}
 * 就是开枪的人。属性只挂在玩家身上（见 {@code attributes.addAttributesToPlayers}），
 * 拿不到属性实例时直接不管，不会误伤其他模组的枪。</p>
 *
 * <h2>优先级</h2>
 * <p>用 {@link EventPriority#HIGHEST} 先跑：本模组改的是「射手造成的枪械伤害」，
 * 应该先算完再交给目标的子弹抗性（TaCZ 的默认优先级处理器）去减。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class rifle_procedures {

    private rifle_procedures() {
    }

    /**
     * 在「受击」阶段改写枪械伤害。
     *
     * <p>伤害源不在 {@code tacz:bullets} 里、或造成伤害的实体不是活体（拿不到属性）时直接返回。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source == null || !source.is(ModDamageTypes.BULLETS_TAG)) {
            return;
        }

        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity shooter)) {
            return;
        }

        double enhancement = attributeValue(shooter, attributes.RIFLE_ENHANCEMENT.get());
        double expertise = attributeValue(shooter, attributes.RIFLE_EXPERTISE.get());
        if (enhancement == 0.0D && expertise == 0.0D) {
            return;
        }

        event.setAmount((float) applyToGunDamage(event.getAmount(), enhancement, expertise));
    }

    /**
     * 铳械伤害的结算公式。
     *
     * <p>公开是为了让 GameTest 能直接验证公式（照 desc 的原例逐条对）。</p>
     *
     * @param damage      结算前的枪械伤害
     * @param enhancement 铳械增幅：固定值加减（先算）
     * @param expertise   铳械精通：每 0.01 = +1%，即 0.10 = +10%（后算）
     * @return 结算后的伤害，钳在 0 以上
     */
    public static double applyToGunDamage(double damage, double enhancement, double expertise) {
        double afterEnhancement = damage + enhancement;
        double afterExpertise = afterEnhancement * (1.0D + expertise);
        return Math.max(0.0D, afterExpertise);
    }

    /** 取实体身上某条属性的值；实体没挂这条属性时返回 0（不介入）。 */
    private static double attributeValue(LivingEntity entity, Attribute attribute) {
        if (entity.getAttribute(attribute) == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute);
    }
}
