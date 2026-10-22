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
        yes(h, !has(p, "healthy_condition") && !has(p, "delayed_healing"), "80%不迟滞");
        p.setHealth(15.99F);
        infection_symptoms.reevaluate(p);
        yes(h, has(p, "delayed_healing"), "低于80%迟滞");
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
        near(h, -50, p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()), "三症状总间隔50秒");
        near(h, 0.5, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "渗血扣0.5");
        infection_symptom_rules.start(p, "recovery_fatigue", 1200);
        near(h, 1.35, infection_symptom_rules.movementMultiplier(p, false, true), "加算35%");
        near(h, 1.35, infection_symptom_rules.movementMultiplier(p, true, false), "游泳同样加算");
        near(h, 1, infection_symptom_rules.movementMultiplier(p, false, false), "走路不变");
        var data = player_variables.san(p).orElseThrow();
        san_procedures.syncStateModifiers(p, san_procedures.State.COLLAPSE, data);
        infection_symptom_rules.reevaluate(p);
        for (int i = 0; i < 10; i++)
        {
            san_procedures.syncStateModifiers(p, san_procedures.State.COLLAPSE, data);
            infection_symptom_rules.reevaluate(p);
        }
        near(h, 0, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "SAN和渗血不能反复振荡成负数");
        attr(p, attributes.RESTORE_HEALTH.get()).setBaseValue(-2);
        san_procedures.syncStateModifiers(p, san_procedures.State.COLLAPSE, data);
        infection_symptom_rules.reevaluate(p);
        near(h, -2, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "外部负回复量保留");
        attr(p, attributes.RESTORE_HEALTH.get()).setBaseValue(0.2);
        attr(p, attributes.RESTORE_HEALTH.get()).addTransientModifier(new AttributeModifier(UUID.randomUUID(), "test", 1,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        san_procedures.syncStateModifiers(p, san_procedures.State.COLLAPSE, data);
        infection_symptom_rules.reevaluate(p);
        near(h, 0, p.getAttributeValue(attributes.RESTORE_HEALTH.get()), "乘法装备下同样不扣成负数");
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
        yes(h, state(p).remaining("wound_seepage") == 600, "首次30秒");
        for (int i = 0; i < 5; i++) infection_symptom_rules.onDirectHit(p, 3);
        yes(h, state(p).remaining("wound_seepage") == 1200, "重复触发最高60秒");
        infection_service.set(p, 29.9);
        yes(h, !has(p, "wound_seepage"), "低于30提前结束");
        infection_service.set(p, 30);
        var slot = CuriosApi.getCuriosInventory(p).orElseThrow(IllegalStateException::new).getCurios().get("ring");
        slot.getStacks().setStackInSlot(0, new ItemStack(items.STORED_SUFFERING.get()));
        var event = new LivingDamageEvent(p, p.damageSources().mobAttack(h.makeMockSurvivalPlayer()), 3);
        stored_suffering_events.onDamage(event);
        near(h, 2.1, event.getAmount(), "戒指只立即扣70%");
        yes(h, state(p).remaining("wound_seepage") == 600, "按拆分前3点触发渗血");
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
            attr(p, attributes.RECOVERY_INTERVAL.get()).setBaseValue(175);
            for (int i = 0; i < 40; i++) food.tick(p);
            infection_service.set(p, 50);
            for (int i = 0; i < 459; i++) food.tick(p);
            near(h, 10, p.getHealth(), "间隔变长后499刻不回血");
            food.tick(p);
            near(h, 11, p.getHealth(), "保留之前40刻，累计500刻回血");
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
        yes(h, cost > original, "精神涣散提高整数成本");
        magic.setMana(cost - 1);
        yes(h, !spell.canBeCastedBy(1, CastSource.SPELLBOOK, magic, p).isSuccess(), "预检查使用提高后的成本");
        magic.setMana(1000);
        float before = magic.getMana(); // ISS 本身按当前最大魔力限制 setMana。
        yes(h, spell.canBeCastedBy(1, CastSource.SPELLBOOK, magic, p).isSuccess(), "足够魔力允许施法");
        spell.castSpell(h.getLevel(), 1, p, CastSource.SPELLBOOK, false);
        near(h, before - cost, magic.getMana(), "实际只扣一次相同成本");
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
