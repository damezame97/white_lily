package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.infection_state;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.infection.*;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.procedures.san_procedures;
import com.damezame97.white_lily.procedures.stored_suffering_events;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.damezame97.white_lily.white_lilydamage.stored_suffering_damage;
import com.damezame97.white_lily.white_lilydamage.excessive_hemorrhage;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.Objects;
import java.util.UUID;

@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public class InfectionSymptomsGameTests
{
    @GameTest(template = "empty")
    public static void exactHealthInfectionAndSanBoundaries(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(20);
        infection_symptoms.reevaluate(p);
        yes(h, has(p, "healthy_condition"), "满血体况良好");
        near(h, 1, p.getAttributeValue(attributes.STAMINA.get()), "健康耐力");
        near(h, 0.1, p.getAttributeValue(attributes.INFECTION_RESISTANCE.get()), "健康抗性");
        attr(p, attributes.INFECTION_RESISTANCE.get()).setBaseValue(0.79);
        near(h, 0.8, p.getAttributeValue(attributes.INFECTION_RESISTANCE.get()), "抗性硬上限");
        p.setHealth(16);
        infection_service.set(p, 50);
        yes(h, !has(p, "healthy_condition"), "80%不体况良好");
        // 愈合迟滞不再是条件症状：门槛改成纯函数（感染 50+10×耐力夹 20~100，血量 <70%-5%×耐力夹 50%~90%）。
        near(h, 50, infection_symptom_rules.delayedInfectionThreshold(0), "无耐力感染门槛50");
        near(h, 0.70, infection_symptom_rules.delayedHealthThreshold(0), "无耐力血量门槛70%");
        near(h, 0.90, infection_symptom_rules.delayedHealthThreshold(-10), "负耐力血量门槛封顶90%");
        near(h, 0.50, infection_symptom_rules.delayedHealthThreshold(8), "高耐力血量门槛降到50%");
        near(h, 20, infection_symptom_rules.delayedInfectionThreshold(-10), "负耐力感染门槛降到20");
        near(h, 100, infection_symptom_rules.delayedInfectionThreshold(9), "高耐力感染门槛封顶100");
        p.setHealth(14);
        infection_service.set(p, 79.9);
        yes(h, !has(p, "rapid_breathing"), "79.9不急促");
        infection_service.set(p, 80);
        yes(h, has(p, "rapid_breathing"), "70%生命触发");
        p.setHealth(14.01F);
        infection_symptoms.reevaluate(p);
        yes(h, !has(p, "rapid_breathing"), "大于70%不触发");
        attr(p, attributes.SAN.get()).setBaseValue(5000);
        infection_service.set(p, 200);
        yes(h, !has(p, "mental_distraction") && !has(p, "infection_anxiety"), "SAN5000严格不触发");
        attr(p, attributes.SAN.get()).setBaseValue(4999);
        infection_symptoms.reevaluate(p);
        yes(h, has(p, "mental_distraction") && has(p, "infection_anxiety"), "SAN4999触发");
        attr(p, attributes.SAN.get()).setBaseValue(1000);
        infection_service.set(p, 150);
        yes(h, !has(p, "perception_disorder"), "SAN1000严格不触发");
        attr(p, attributes.SAN.get()).setBaseValue(999);
        infection_symptoms.reevaluate(p);
        yes(h, has(p, "perception_disorder"), "SAN999触发");
        infection_service.set(p, 100);
        yes(h, !has(p, "unstable_spellcasting"), "感染100不常驻施术失稳");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stackedIntervalsMovementAndRestoreFloor(GameTestHelper h)
    {
        Player p = player(h);
        attr(p, attributes.SAN.get()).setBaseValue(4000);
        infection_service.set(p, 100);
        infection_symptom_rules.onDirectHit(p, 3);
        // 焦虑(-10) + 0 级渗血(-30) 共 -40（愈合迟滞不再加间隔）。
        near(h, -40, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "三症状总间隔40秒");
        near(h, 1, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "0级渗血不扣回复量");
        infection_symptom_rules.start(p, "recovery_fatigue", 1200);
        near(h, 1.35, infection_symptom_rules.movementMultiplier(p, false, true), "加算35%");
        near(h, 1.35, infection_symptom_rules.movementMultiplier(p, true, false), "游泳同样加算");
        near(h, 1, infection_symptom_rules.movementMultiplier(p, false, false), "走路不变");
        // 逐级回复量：现在允许把最终回复量扣成负数（不再钳 0）。
        for (int level = 1; level <= 4; level++)
        {
            infection_symptom_rules.start(p, "wound_seepage", 1200, level);
            infection_symptom_rules.reevaluate(p);
            near(h, 1 + infection_symptom_rules.seepageRestorePenalty(level),
                    p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "等级 " + level + " 的回复量");
        }
        attr(p, attributes.RESTORE_HEALTH.get()).setBaseValue(-2);
        infection_symptom_rules.reevaluate(p);
        near(h, -22, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "外部负回复量直接叠加，不钳制");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woundThresholdDurationAndRingOrdering(GameTestHelper h)
    {
        Player p = player(h);
        infection_service.set(p, 30);
        infection_symptom_rules.onDirectHit(p, 2.999F);
        yes(h, state(p).remaining("wound_seepage") == 0, "小于3不触发");
        infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).remaining("wound_seepage") == 1200, "首次1200刻");
        yes(h, state(p).woundSeepageLevel == 0, "首次给0级");
        infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).remaining("wound_seepage") == 1600, "0级每次+400");
        infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).remaining("wound_seepage") == 2000, "0级可以到2000");
        infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).woundSeepageLevel == 1, "顶到2400就升1级");
        yes(h, state(p).remaining("wound_seepage") == 2400, "1级初始2400");
        yes(h, p.getEffect(mob_effects.WOUND_SEEPAGE.get()).getAmplifier() == 1, "显示为1级");
        infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).remaining("wound_seepage") == 3000, "1级每次+600");
        infection_service.set(p, 29.9);
        yes(h, !has(p, "wound_seepage"), "低于30提前结束");
        infection_service.set(p, 30);
        var slot = CuriosApi.getCuriosInventory(p).orElseThrow(IllegalStateException::new).getCurios().get("ring");
        slot.getStacks().setStackInSlot(0, new ItemStack(items.STORED_SUFFERING.get()));
        var event = new LivingDamageEvent(p, p.damageSources().mobAttack(h.makeMockSurvivalPlayer()), 3);
        stored_suffering_events.onDamage(event);
        near(h, 2.1, event.getAmount(), "戒指只立即扣70%");
        yes(h, state(p).remaining("wound_seepage") == 1200, "按拆分前3点触发渗血");
        state(p).set("wound_seepage", 100);
        stored_suffering_events.onDamage(new LivingDamageEvent(p, stored_suffering_damage.source(p.level()), 10));
        yes(h, state(p).remaining("wound_seepage") == 100, "延期到账不重复渗血");
        yes(h, !infection_damage.direct(p.damageSources().onFire()), "燃烧不属于直接伤害");
        yes(h, !infection_damage.direct(p.damageSources().magic()), "中毒魔法不属于直接伤害");
        var indirect = SpellDamageSource.source(h.makeMockSurvivalPlayer(), SpellRegistry.FIREBALL_SPELL.get());
        yes(h, !infection_damage.direct(indirect), "有攻击者和法术ID也不能冒充直接命中");
        var caster = h.makeMockSurvivalPlayer();
        var projectile = new io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball(h.getLevel(), caster);
        yes(h, infection_damage.direct(SpellDamageSource.source(projectile, caster, SpellRegistry.BLAZE_STORM_SPELL.get())),
                "已核实的烈焰风暴小火球直接命中可触发");
        var bulletType = h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getTag(com.tacz.guns.init.ModDamageTypes.BULLETS_TAG).orElseThrow().iterator().next();
        yes(h, infection_damage.direct(new net.minecraft.world.damagesource.DamageSource(bulletType, caster, caster)),
                "TaCZ子弹标签属于直接命中");
        p.setHealth(5);
        p.heal(4);
        near(h, 9, p.getHealth(), "渗血不会削减独立heal治疗来源");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blindnessPeriodLucidityAndExternalIsolation(GameTestHelper h)
    {
        Player p = player(h);
        attr(p, attributes.SAN.get()).setBaseValue(999);
        infection_service.set(p, 150);
        advance(p, 0, 1199);
        yes(h, !has(p, "infection_blindness"), "首次等待1199刻不失明");
        infection_symptom_rules.tick(p, 1199);
        yes(h, state(p).remaining("infection_blindness") == 60, "1200刻后发作60刻");
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300));
        advance(p, 1200, 60);
        yes(h, !has(p, "infection_blindness") && state(p).remaining("brief_lucidity") == 600, "失明结束给30秒清明");
        yes(h, p.getEffect(MobEffects.BLINDNESS).getDuration() == 300, "外部失明不被改动");
        yes(h, !infection_symptom_rules.blind(p), "清明防止本系统失明");
        state(p).perceptionTicks = 1199;
        infection_symptom_rules.tick(p, 1260);
        yes(h, state(p).perceptionTicks == 0 && !has(p, "infection_blindness"), "保护期间跳过并重新计周期");
        infection_service.set(p, 149.9);
        yes(h, state(p).perceptionTicks == 0 && p.hasEffect(MobEffects.BLINDNESS), "离开条件只清本系统");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualArmorAbsorptionAndMitigationPrecedeWound(GameTestHelper h)
    {
        Player p = player(h);
        Player attacker = player(h);
        var critical = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("attributeslib", "crit_chance"));
        if (critical != null) attr(attacker, critical).setBaseValue(0);
        infection_service.set(p, 30);
        p.setAbsorptionAmount(3);
        p.hurt(p.damageSources().mobAttack(attacker), 3);
        yes(h, !has(p, "wound_seepage"), "完全由黄心抵消不渗血");
        p.invulnerableTime = 0;
        p.setAbsorptionAmount(0);
        attr(p, attributes.DAMAGE_MITIGATION.get()).setBaseValue(1);
        p.hurt(p.damageSources().mobAttack(attacker), 3);
        yes(h, !has(p, "wound_seepage"), "自定义减伤后不足3不触发");
        p.invulnerableTime = 0;
        attr(p, attributes.DAMAGE_MITIGATION.get()).setBaseValue(0);
        attr(p, Attributes.ARMOR).setBaseValue(8);
        attr(p, Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        p.hurt(p.damageSources().mobAttack(attacker), 3);
        yes(h, !has(p, "wound_seepage"), "护甲之后不足3不触发");
        p.invulnerableTime = 0;
        attr(p, Attributes.ARMOR).setBaseValue(0);
        attr(p, Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        p.hurt(p.damageSources().mobAttack(attacker), 3);
        yes(h, has(p, "wound_seepage"), "最终恰好3触发");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void regenerationKeepsElapsedTicksAndHungerOnlyBranch(GameTestHelper h)
    {
        Player p = player(h);
        var rule = com.damezame97.white_lily.gamerule.happy_mode.RULE;
        boolean previous = p.level().getGameRules().getBoolean(rule);
        try
        {
            p.level().getGameRules().getRule(rule).set(true, null);
            var food = p.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(10);
            attr(p, attributes.RECOVERY_INTERVAL.get()).setBaseValue(100);
            for (int i = 0; i < 40; i++) food.tick(p);
            for (int i = 0; i < 1559; i++) food.tick(p);
            near(h, 10, p.getHealth(), "180-100=80秒，1599刻不回血");
            food.tick(p);
            near(h, 11, p.getHealth(), "保留之前40刻，累加1600刻回血");
            food.setSaturation(0);
            p.setHealth(10);
            for (int i = 0; i < 3599; i++) food.tick(p);
            near(h, 10, p.getHealth(), "无饱和仍固定180秒");
            food.tick(p);
            near(h, 11, p.getHealth(), "无饱和分支不套症状回复间隔");
        }
        finally { p.level().getGameRules().getRule(rule).set(previous, null); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blindnessConditionEndAndCommandRemovalDiffer(GameTestHelper h)
    {
        Player p = player(h);
        attr(p, attributes.SAN.get()).setBaseValue(999);
        infection_service.set(p, 150);
        infection_symptom_rules.blind(p);
        infection_service.set(p, 149);
        yes(h, has(p, "brief_lucidity") && !has(p, "infection_blindness"), "发作中条件解除可以进入清明");
        p.removeEffect(mob_effects.BRIEF_LUCIDITY.get());
        infection_service.set(p, 150);
        infection_symptom_rules.blind(p);
        p.removeEffect(mob_effects.INFECTION_BLINDNESS.get());
        infection_symptom_rules.tick(p, 0);
        yes(h, !has(p, "brief_lucidity"), "命令移除失明不得发清明");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deterministicRandomPoolCooldownAndDuplicate(GameTestHelper h)
    {
        Player p = player(h);
        infection_service.set(p, 80);
        infection_symptom_rules.onIncrease(p, 1, new Rolls(0.099, 0));
        yes(h, state(p).poolCooldown == 600 && has(p, "shivering"), "80点区间10%及冷却");
        near(h, -0.05, p.getAttributeValue(attributes.RIFLE_EXPERTISE.get()), "铳械减值");
        advance(p, 0, 600);
        int remaining = state(p).remaining("shivering");
        infection_symptom_rules.onIncrease(p, 1, new Rolls(0, 0));
        yes(h, state(p).remaining("shivering") == remaining, "重复抽中不刷新不重抽");
        state(p).poolCooldown = 0;
        infection_symptom_rules.onIncrease(p, 1, new Rolls(0, 1));
        yes(h, has(p, "shivering") && has(p, "unstable_spellcasting"), "两种随机症状共存");
        near(h, -0.5, p.getAttributeValue(attributes.SPELL_ENHANCEMENT.get()), "法术减值");
        infection_service.set(p, 79.9);
        yes(h, !has(p, "shivering") && !has(p, "unstable_spellcasting"), "低于80均提前解除");
        near(h, 0.1, infection_symptom_rules.poolChance(149.9), "150边界下方");
        near(h, 0.2, infection_symptom_rules.poolChance(150), "150概率20%");
        near(h, 0.2, infection_symptom_rules.poolChance(229.9), "230边界下方");
        near(h, 0.3, infection_symptom_rules.poolChance(230), "230概率30%");
        state(p).poolCooldown = 0;
        infection_service.set(p, 230);
        infection_symptom_rules.onIncrease(p, 1, new Rolls(0.3, 0));
        yes(h, state(p).poolCooldown == 600 && !has(p, "shivering"), "概率失败同样进入冷却");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyActualGameplayIncreaseRollsAndActiveTreatmentFatigues(GameTestHelper h)
    {
        Player p = player(h);
        infection_service.set(p, 300);
        infection_service.trySource(p, new infection_sources.Source(infection_symptom_rules.id("test"), 1, 10), new Rolls(0, 0));
        yes(h, state(p).poolCooldown == 0, "满300实际不增加不得抽取");
        infection_service.set(p, 20);
        near(h, 20, infection_service.treat(p, 50), "实际只能降低20");
        yes(h, !has(p, "recovery_fatigue"), "实际不足30不疲劳");
        p.setHealth(20);
        infection_service.set(p, 100);
        infection_service.reduce(p, 30);
        yes(h, !has(p, "recovery_fatigue") && state(p).poolCooldown == 0, "管理降低和设置均不触发");
        infection_service.treat(p, 30);
        yes(h, state(p).remaining("recovery_fatigue") == 1200, "满血主动治疗30也疲劳");
        advance(p, 0, 50);
        infection_service.set(p, 100);
        infection_service.treat(p, 30);
        yes(h, state(p).remaining("recovery_fatigue") == 1200, "只刷新至60秒");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gameplayRoutesRollOnceAndPausedBlindnessDoesNotGrantLucidity(GameTestHelper h)
    {
        Player p = player(h);
        infection_service.set(p, 79.9);
        Rolls rolls = new Rolls(0.99, 0);
        infection_service.trySource(p, new infection_sources.Source(infection_symptom_rules.id("test_only"), 1, 50), rolls);
        yes(h, state(p).poolCooldown == 600 && rolls.calls == 1, "一次外来增长只抽一次，不按0.1点抽");
        state(p).poolCooldown = 0;
        infection_service.set(p, 80);
        var data = player_variables.infection(p).orElseThrow();
        data.setPassiveTicks(1199);
        infection_service.tick(p, 0);
        yes(h, state(p).poolCooldown == 600, "实际被动增长同样接入随机池");
        attr(p, attributes.SAN.get()).setBaseValue(999);
        infection_service.set(p, 150);
        infection_symptom_rules.blind(p);
        boolean enabled = Config.infectionEnabled;
        try
        {
            Config.infectionEnabled = false;
            infection_symptom_rules.tick(p, 1);
            yes(h, !has(p, "infection_blindness") && !has(p, "brief_lucidity"), "关闭不凭空发放清明");
            yes(h, state(p).remaining("infection_blindness") == 60, "关闭冻结而非到期");
        }
        finally { Config.infectionEnabled = enabled; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allTwelveEffectsAreMilkImmuneWithoutChangingVanilla(GameTestHelper h)
    {
        Player p = player(h);
        p.addEffect(new MobEffectInstance(mob_effects.INFECTION_BLINDNESS.get(), 100));
        for (String name : infection_symptom_rules.ALL_IDS)
        {
            var effect = infection_symptom_rules.effect(name);
            yes(h, effect != null && effect.getCurativeItems().isEmpty(), "牛奶免疫：" + name);
            p.addEffect(new MobEffectInstance(effect, 100));
        }
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
        p.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        for (String name : infection_symptom_rules.ALL_IDS) yes(h, has(p, name), "喝奶后保留：" + name);
        yes(h, !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.BLINDNESS), "外部原版效果仍可清除");
        // 蜂蜜只清中毒，碰不到渗血（它的治愈列表是空的）。
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        new ItemStack(Items.HONEY_BOTTLE).finishUsingItem(h.getLevel(), p);
        yes(h, !p.hasEffect(MobEffects.POISON), "蜂蜜照常清掉中毒");
        for (String name : infection_symptom_rules.ALL_IDS) yes(h, has(p, name), "喝蜂蜜后保留：" + name);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pauseResumeSaveCloneAndDeath(GameTestHelper h)
    {
        Player p = player(h);
        infection_service.set(p, 100);
        infection_symptom_rules.start(p, "shivering", 900);
        state(p).poolCooldown = 400;
        state(p).perceptionTicks = 300;
        infection_state saved = new infection_state();
        saved.deserializeNBT(player_variables.infection(p).orElseThrow().serializeNBT());
        yes(h, saved.symptoms.remaining("shivering") == 900 && saved.symptoms.poolCooldown == 400, "存档保留计时");
        infection_state dimension = new infection_state();
        dimension.copyFrom(saved, false, true);
        yes(h, dimension.symptoms.remaining("shivering") == 900, "维度复制保留独立数据");
        dimension.symptoms.set("shivering", 1);
        yes(h, saved.symptoms.remaining("shivering") == 900, "不共享旧状态引用");
        dimension.copyFrom(saved, true, true);
        yes(h, dimension.units() == 1000 && dimension.symptoms.remaining.isEmpty(), "死亡保留感染但清理事件症状");
        dimension.copyFrom(saved, true, false);
        yes(h, dimension.units() == 0 && dimension.symptoms.poolCooldown == 0, "死亡清除配置");
        boolean enabled = Config.infectionEnabled;
        try
        {
            Config.infectionEnabled = false;
            infection_symptom_rules.tick(p, 0);
            yes(h, state(p).remaining("shivering") == 900 && state(p).poolCooldown == 400, "禁用暂停所有相关时钟");
            yes(h, !has(p, "shivering"), "禁用清理自动效果");
            near(h, 0, p.getAttributeValue(attributes.RIFLE_EXPERTISE.get()), "无属性残留");
            Config.infectionEnabled = true;
            infection_symptom_rules.tick(p, 1);
            yes(h, has(p, "shivering") && state(p).remaining("shivering") == 899, "恢复继续倒计时");
            p.getAbilities().invulnerable = true;
            infection_symptom_rules.tick(p, 2);
            yes(h, !has(p, "shivering") && state(p).remaining("shivering") == 899, "无敌模式暂停");
        }
        finally { Config.infectionEnabled = enabled; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void manaPrecheckAndActualIssPaymentAgree(GameTestHelper h)
    {
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "InfectionMana"));
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p)
        { @Override public void send(Packet<?> packet) { } };
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().invulnerable = false;
        p.setHealth(10);
        attr(p, attributes.SAN.get()).setBaseValue(4000);
        infection_service.set(p, 200);
        var spell = SpellRegistry.FIREBALL_SPELL.get();
        var magic = MagicData.getPlayerMagicData(p);
        int original = spell.getManaCost(1);
        int cost = infection_mana.cost(p, original);
        yes(h, cost == original, "0级精神涣散不改成本");
        magic.setMana(cost - 1);
        yes(h, !spell.canBeCastedBy(1, CastSource.SPELLBOOK, magic, p).isSuccess(), "预检查使用提高后的成本");
        magic.setMana(1000);
        float before = magic.getMana(); // ISS 本身按当前最大魔力限制 setMana。
        yes(h, spell.canBeCastedBy(1, CastSource.SPELLBOOK, magic, p).isSuccess(), "足够魔力允许施法");
        spell.castSpell(h.getLevel(), 1, p, CastSource.SPELLBOOK, false);
        near(h, before - cost, magic.getMana(), "实际只扣一次相同成本");
        near(h, 1, infection_symptom_rules.mentalDistractionLevel(p), "花魔力后升至1级");
        yes(h, infection_mana.cost(p, 100) == 105, "1级成本+5%");
        near(h, 1, infection_symptom_rules.strength(p), "意志系数固定1");
        infection_symptom_rules.start(p, "shivering", 900);
        p.setGameMode(GameType.CREATIVE);
        infection_symptom_rules.tick(p, 0);
        yes(h, !has(p, "shivering") && state(p).remaining("shivering") == 900, "真实创造模式暂停症状");
        p.setGameMode(GameType.SPECTATOR);
        infection_symptom_rules.tick(p, 1);
        yes(h, !has(p, "shivering") && state(p).remaining("shivering") == 900, "真实旁观模式暂停症状");
        p.setGameMode(GameType.SURVIVAL);
        infection_symptom_rules.tick(p, 2);
        yes(h, has(p, "shivering") && state(p).remaining("shivering") == 899, "回生存继续症状");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woundSeepageStaminaScalesThresholds(GameTestHelper h)
    {
        Player p = player(h);
        attr(p, attributes.STAMINA.get()).setBaseValue(2);
        near(h, 40, infection_symptom_rules.seepageInfectionThreshold(2), "感染阈值 30+5×2");
        near(h, 7, infection_symptom_rules.seepageDamageThreshold(20, 2), "伤害阈值 3+20×20%");
        infection_service.set(p, 39.9);
        infection_symptom_rules.onDirectHit(p, 20);
        yes(h, !has(p, "wound_seepage"), "感染不够不触发");
        infection_service.set(p, 40);
        infection_symptom_rules.onDirectHit(p, 6.999F);
        yes(h, !has(p, "wound_seepage"), "伤害不够不触发");
        infection_symptom_rules.onDirectHit(p, 7);
        yes(h, has(p, "wound_seepage"), "两条都过线才触发");
        near(h, 60, infection_symptom_rules.seepageInfectionThreshold(10), "感染阈值封顶60");
        near(h, 0, infection_symptom_rules.seepageInfectionThreshold(-10), "负耐力不降低到0以下");
        near(h, 0, infection_symptom_rules.seepageStaminaRatio(-3), "负耐力不计比例");
        near(h, 0.25, infection_symptom_rules.seepageStaminaRatio(9), "比例封顶 25%");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woundSeepageEscalatesAndCapsAtLevelFour(GameTestHelper h)
    {
        Player p = player(h);
        infection_service.set(p, 100);
        int[] durations = {1200, 1600, 2000, 2400};
        for (int i = 0; i < durations.length; i++)
        {
            infection_symptom_rules.onDirectHit(p, 3);
            yes(h, state(p).remaining("wound_seepage") == durations[i], "第" + (i + 1) + "次应当为" + durations[i]);
        }
        yes(h, state(p).woundSeepageLevel == 1, "顶到2400后应当是1级");
        for (int i = 0; i < 200; i++) infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).woundSeepageLevel == 4, "最高只能到4级");
        yes(h, state(p).remaining("wound_seepage") <= 12000, "4级上限12000");
        // 4级已经是顶，再受击只能顶在上限，不能重置回 6000。
        state(p).set("wound_seepage", 11900);
        // 用高伤害避开「耐力抬高伤害门槛」的干扰（满血时 healthy_condition 会给 +1 耐力）。
        infection_symptom_rules.onDirectHit(p, 20);
        yes(h, state(p).woundSeepageLevel == 4 && state(p).remaining("wound_seepage") == 12000,
                "满级顶到上限而不是重置，实测 " + state(p).remaining("wound_seepage"));
        yes(h, p.getEffect(mob_effects.WOUND_SEEPAGE.get()).getAmplifier() == 4, "显示等级同步到4");
        for (int level = 0; level <= 4; level++)
        {
            yes(h, infection_symptom_rules.seepageInitialDuration(level) == 1200 * (level + 1), "初始公式 " + level);
            yes(h, infection_symptom_rules.seepageMaximumDuration(level) == 2400 * (level + 1), "上限公式 " + level);
            yes(h, infection_symptom_rules.seepageExtension(level) == 400 + 200 * level, "延长公式 " + level);
        }
        infection_state saved = new infection_state();
        saved.deserializeNBT(player_variables.infection(p).orElseThrow().serializeNBT());
        yes(h, saved.symptoms.woundSeepageLevel == 4, "等级随存档保存");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woundSeepageLevelEffectsFollowTable(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(19);
        infection_service.set(p, 50);
        infection_symptom_rules.start(p, "wound_seepage", 1200, 0);
        infection_symptom_rules.reevaluate(p);
        near(h, -30, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "0级间隔+30");
        near(h, 1, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "0级不扣回复量");
        near(h, 20, p.getAttributeValue(Attributes.MAX_HEALTH), "0级不改生命上限");
        infection_symptom_rules.start(p, "wound_seepage", 1200, 1);
        infection_symptom_rules.reevaluate(p);
        near(h, -30, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "1级间隔+30");
        near(h, 0, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "1级回复量-1");
        infection_symptom_rules.start(p, "wound_seepage", 1200, 2);
        infection_symptom_rules.reevaluate(p);
        near(h, -0.5, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "2级回复量-1.5");
        infection_symptom_rules.start(p, "wound_seepage", 1200, 3);
        infection_symptom_rules.reevaluate(p);
        near(h, 0, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "3级不再动间隔");
        near(h, -4, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "3级回复量-5");
        near(h, 12, p.getAttributeValue(Attributes.MAX_HEALTH), "3级生命上限-40%");
        attr(p, attributes.STAMINA.get()).setBaseValue(3);
        infection_symptom_rules.reevaluate(p);
        near(h, 16, p.getAttributeValue(Attributes.MAX_HEALTH), "耐力3把3级惩罚减半");
        infection_symptom_rules.start(p, "wound_seepage", 1200, 4);
        infection_symptom_rules.reevaluate(p);
        near(h, -19, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "4级回复量-20");
        near(h, 12, p.getAttributeValue(Attributes.MAX_HEALTH), "耐力3时4级-40%");
        near(h, 0.07, p.getAttributeValue(Attributes.MOVEMENT_SPEED), "4级移速-30%");
        attr(p, attributes.STAMINA.get()).setBaseValue(0);
        infection_symptom_rules.reevaluate(p);
        near(h, 8, p.getAttributeValue(Attributes.MAX_HEALTH), "无耐力时4级-60%");
        infection_symptom_rules.start(p, "wound_seepage", 1200, 0);
        p.addEffect(new MobEffectInstance(mob_effects.WOUND_SUPPRESSION.get(), 100, 0, false, false, true));
        infection_symptom_rules.reevaluate(p);
        near(h, 0, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "0级抑制能盖住0级");
        infection_symptom_rules.start(p, "wound_seepage", 1200, 1);
        infection_symptom_rules.reevaluate(p);
        near(h, -30, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "0级抑制盖不住1级");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woundSeepageSanDrainHonoursWillpower(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(19);
        infection_service.set(p, 50);
        attr(p, attributes.SAN.get()).setBaseValue(10000);
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(0);
        infection_symptom_rules.start(p, "wound_seepage", 400, 3);
        for (int i = 0; i < 20; i++) infection_symptom_rules.tick(p, i);
        near(h, 9995, san_procedures.currentSan(p), "3级每秒-5理智");
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(2);
        for (int i = 20; i < 60; i++) infection_symptom_rules.tick(p, i);
        near(h, 9995, san_procedures.currentSan(p), "意志≥2免疫3级");
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(0.5);
        infection_symptom_rules.start(p, "wound_seepage", 400, 1);
        for (int i = 60; i < 220; i++) infection_symptom_rules.tick(p, i);
        near(h, 9993, san_procedures.currentSan(p), "1级每4秒-1，160刻应扣2");
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(1);
        for (int i = 220; i < 300; i++) infection_symptom_rules.tick(p, i);
        near(h, 9993, san_procedures.currentSan(p), "意志≥1免疫1级");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void woundSeepageBleedsOnMovement(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(20);
        p.setPos(0, 100, 0);
        infection_service.set(p, 50);
        // 不靠血量断言：3/4 级会改生命上限，血量会被夹，改成直接统计出血伤害总额。
        double[] bleed = {0};
        Object listener = new Object()
        {
            @net.minecraftforge.eventbus.api.SubscribeEvent
            public void onDamage(LivingDamageEvent event)
            {
                if (event.getEntity() == p && excessive_hemorrhage.NAME.equals(event.getSource().getMsgId()))
                    bleed[0] += event.getAmount();
            }
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener);
        try
        {
            infection_symptom_rules.start(p, "wound_seepage", 1200, 3);
            infection_symptom_rules.tick(p, 0);
            p.setSprinting(false);
            for (int i = 1; i <= 4; i++)
            {
                p.setPos(i * 1.5, 100, 0);
                infection_symptom_rules.tick(p, i);
            }
            near(h, 0, bleed[0], "3级不疾走不掉血");
            p.setSprinting(true);
            for (int i = 5; i <= 9; i++)
            {
                p.setPos(6 + (i - 4) * 1.5, 100, 0);
                infection_symptom_rules.tick(p, i);
            }
            near(h, 1, bleed[0], "3级疾走6格掉1点");
            // 4级：走路也算，每 3 格 生命上限 20% + 2。
            bleed[0] = 0;
            p.setSprinting(false);
            infection_symptom_rules.start(p, "wound_seepage", 1200, 4);
            infection_symptom_rules.tick(p, 10);
            double expected = p.getMaxHealth() * 0.20D + 2.0D;
            double x = p.getX();
            for (int i = 11; i <= 12; i++)
            {
                p.setPos(x + (i - 10) * 1.5, 100, 0);
                infection_symptom_rules.tick(p, i);
            }
            near(h, expected, bleed[0], "4级走路3格掉 20%上限+2");
        }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void delayedHealingMultiplierAndSanDrain(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(19);
        infection_service.set(p, 50);
        near(h, 1, infection_symptom_rules.healMultiplier(p), "没有迟滞时系数为1");
        for (int level = 0; level <= 3; level++)
        {
            infection_symptom_rules.start(p, "delayed_healing", 4800, level);
            infection_symptom_rules.reevaluate(p);
            near(h, infection_symptom_rules.delayedMultiplier(level),
                    infection_symptom_rules.healMultiplier(p), "等级" + level + "的系数");
            near(h, level, p.getEffect(mob_effects.DELAYED_HEALING.get()).getAmplifier(), "显示的等级" + level);
        }
        attr(p, attributes.SAN.get()).setBaseValue(10000);
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(0);
        infection_symptom_rules.start(p, "delayed_healing", 4800, 3);
        for (int i = 0; i < 40; i++) infection_symptom_rules.tick(p, i);
        near(h, 9996, san_procedures.currentSan(p), "3级每2秒-4理智");
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(2);
        for (int i = 40; i < 120; i++) infection_symptom_rules.tick(p, i);
        near(h, 9996, san_procedures.currentSan(p), "意志≥2免疫3级");
        attr(p, attributes.WILLPOWER_STRENGTH.get()).setBaseValue(0.5);
        infection_symptom_rules.start(p, "delayed_healing", 2000, 1);
        for (int i = 120; i < 280; i++) infection_symptom_rules.tick(p, i);
        near(h, 9994, san_procedures.currentSan(p), "1级每8秒-1，160刻扣2");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void delayedHealingRollsFollowThresholdsAndLevels(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(19);
        infection_service.set(p, 10);
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 0));
        yes(h, !has(p, "delayed_healing"), "感染不到门槛不掷中");
        infection_service.set(p, 50);
        p.setHealth(16);
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 0));
        yes(h, !has(p, "delayed_healing"), "血量不到门槛不掷中");
        p.setHealth(10);
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 0));
        yes(h, has(p, "delayed_healing") && state(p).delayedHealingLevel == 0, "满足门槛时得到0级");
        yes(h, state(p).remaining("delayed_healing") == 4800, "nextInt→0 时是4分钟");
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.5, 0));
        yes(h, state(p).delayedHealingLevel == 0, "3%骰没中不升级");
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 4));
        yes(h, state(p).delayedHealingLevel == 1, "0级升1级");
        yes(h, state(p).remaining("delayed_healing") == 9600, "升级后重掷为8分钟");
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 0));
        yes(h, state(p).delayedHealingLevel == 2, "1级升2级");
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 0));
        yes(h, state(p).delayedHealingLevel == 3, "2级升3级");
        infection_symptom_rules.onDelayedHealAction(p, new Rolls(0.0, 0));
        yes(h, state(p).delayedHealingLevel == 3 && state(p).remaining("delayed_healing") == 72000,
                "满级0.012%把时长变成60分钟");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mentalDistractionStacksCostsAndDecays(GameTestHelper h)
    {
        Player p = player(h);
        p.setHealth(10);
        attr(p, attributes.SAN.get()).setBaseValue(4000);
        infection_service.set(p, 200);
        infection_symptom_rules.reevaluate(p);
        yes(h, has(p, "mental_distraction") && state(p).mentalDistractionLevel == 0, "条件成立时获得0级");
        yes(h, p.getEffect(mob_effects.MENTAL_DISTRACTION.get()).isInfiniteDuration(), "显示为无限时长");
        for (int i = 0; i < 3; i++) infection_symptom_rules.onManaSpent(p);
        yes(h, state(p).mentalDistractionLevel == 3, "每消耗一次魔力升一级");
        near(h, 1.15, infection_symptom_rules.mentalCostMultiplier(3), "3级成本+15%");
        near(h, 1.30, infection_symptom_rules.mentalCastMultiplier(3), "3级施法时长+30%");
        for (int i = 0; i < 20; i++) infection_symptom_rules.onManaSpent(p);
        yes(h, state(p).mentalDistractionLevel == 10, "等级上限10");
        yes(h, p.getEffect(mob_effects.MENTAL_DISTRACTION.get()).getAmplifier() == 10, "显示等级同步到10");
        // 条件失效：保留效果、仍是无限时长，先等 10 秒再每 30 秒降一级。
        infection_service.set(p, 100);
        infection_symptom_rules.reevaluate(p);
        yes(h, has(p, "mental_distraction") && p.getEffect(mob_effects.MENTAL_DISTRACTION.get()).isInfiniteDuration(),
                "条件失效仍保留");
        for (int i = 0; i < 200; i++) infection_symptom_rules.tick(p, i);
        yes(h, state(p).mentalDistractionLevel == 10, "10秒内不降级");
        for (int i = 200; i < 800; i++) infection_symptom_rules.tick(p, i);
        yes(h, state(p).mentalDistractionLevel == 9, "满30秒降一级");
        infection_symptom_rules.onManaSpent(p);
        yes(h, state(p).mentalDistractionLevel == 10, "消耗魔力打断并升一级");
        state(p).mentalDistractionLevel = 1;
        for (int i = 800; i < 1600; i++) infection_symptom_rules.tick(p, i);
        yes(h, !has(p, "mental_distraction"), "降到0时清除");
        // 0 级 + 条件不满足 → 直接清除。
        infection_service.set(p, 200);
        infection_symptom_rules.reevaluate(p);
        yes(h, has(p, "mental_distraction"), "重新获得0级");
        infection_service.set(p, 10);
        infection_symptom_rules.reevaluate(p);
        yes(h, !has(p, "mental_distraction"), "0级且条件不满足立即清除");
        h.succeed();
    }

    private static Player player(GameTestHelper h)
    {
        Player p = h.makeMockSurvivalPlayer();
        p.getAbilities().invulnerable = false;
        p.setHealth(10);
        attr(p, attributes.SAN.get()).setBaseValue(10000);
        attr(p, attributes.LOAD_CAPACITY.get()).setBaseValue(100);
        return p;
    }
    private static AttributeInstance attr(Player p, net.minecraft.world.entity.ai.attributes.Attribute a)
    { return Objects.requireNonNull(p.getAttribute(a)); }
    private static infection_symptom_state state(Player p) { return infection_symptom_rules.state(p); }
    private static boolean has(Player p, String name) { return infection_symptom_rules.has(p, name); }
    private static void advance(Player p, int first, int count)
    { for (int i = 0; i < count; i++) infection_symptom_rules.tick(p, first + i); }
    private static void yes(GameTestHelper h, boolean result, String message) { h.assertTrue(result, message); }
    private static void near(GameTestHelper h, double expected, double actual, String message)
    { yes(h, Math.abs(expected - actual) < 1.0E-5, message + ": " + expected + " != " + actual); }
    private static final class Rolls extends LegacyRandomSource
    {
        int calls;
        private final double roll;
        private final int entry;
        Rolls(double roll, int entry) { super(0); this.roll = roll; this.entry = entry; }
        @Override public double nextDouble() { calls++; return roll; }
        @Override public int nextInt(int bound) { return Math.floorMod(entry, bound); }
    }
}
