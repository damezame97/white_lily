package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.suffering_capability;
import com.damezame97.white_lily.capability.suffering_data;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.procedures.stored_suffering_events;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilycurio.stored_suffering;
import com.damezame97.white_lily.white_lilydamage.abnormal_mana;
import com.damezame97.white_lily.white_lilydamage.depletion;
import com.damezame97.white_lily.white_lilydamage.nightmare;
import com.damezame97.white_lily.white_lilydamage.stored_suffering_damage;
import com.tacz.guns.init.ModDamageTypes;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// Forge 通过注解发现测试。
@SuppressWarnings("unused")
@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
public class StoredSufferingGameTests
{
    @GameTest(template = "empty")
    @SuppressWarnings("ConstantValue") // CuriosApi 的桩方法在运行时由 Curios 替换，必须实际验证栏位规则。
    public static void ringUsesRingSlotAndRealDurability(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        helper.assertTrue(ring.getMaxStackSize() == 1 && ring.getMaxDamage() == 512, "戒指必须单件堆叠、512 耐久");
        helper.assertTrue(CuriosApi.isStackValid(new SlotContext("ring", player, 0, false, true), ring), "ring 槽应接受戒指");
        helper.assertTrue(!CuriosApi.isStackValid(new SlotContext("improvement", player, 0, false, true), ring), "不能占改良槽");
        ring.setDamageValue(1);
        helper.assertTrue(ring.isBarVisible(), "损耗后应显示真实耐久条");
        helper.assertTrue(!ring.isEnchantable() && !ring.getItem().isRepairable(ring), "本次不允许附魔或维修");
        ring.enchant(Enchantments.MENDING, 1);
        helper.assertTrue(ring.getEnchantmentLevel(Enchantments.MENDING) == 0, "即使命令加入经验修补也不应生效");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thresholdBelowThreeDoesNotTrigger(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 2.999F);
        near(helper, 20.0F - 2.999F, player.getHealth(), "低于 3 点应全额承受");
        near(helper, 0, data(player).total(), "不能产生延期伤害");
        helper.assertTrue(ring.getDamageValue() == 0, "不能扣耐久");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactlyThreeDefersThirtyPercent(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 3.0F);
        near(helper, 17.9F, player.getHealth(), "应立即承受 2.1");
        near(helper, 0.9F, data(player).total(), "应延期 0.9");
        helper.assertTrue(ring.getDamageValue() == 1, "恰好扣一点耐久");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualDamageTenSplitsSevenAndThreeForHundredTicks(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 10);
        near(helper, 13, player.getHealth(), "立即承受 7");
        long start = now(player);
        near(helper, 0, data(player).advance(start), "产生当刻不能提前倒计时");
        for (int tick = 1; tick < 100; tick++)
        {
            near(helper, 0, data(player).advance(start + tick), "不足 100 刻不应到账");
        }
        float due = data(player).advance(start + 100);
        near(helper, 3, due, "100 刻到账 3");
        stored_suffering_events.settle(player, due);
        near(helper, 10, player.getHealth(), "合计承受原始 10 点伤害");
        helper.assertTrue(ring.getDamageValue() == 1, "到账不能再扣耐久");
        near(helper, 0, data(player).advance(start + 100), "同一刻不能重复结算");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void consecutiveDamageHasIndependentDeadlines(GameTestHelper helper)
    {
        suffering_data ledger = new suffering_data();
        ledger.add(3, 0);
        for (int tick = 1; tick <= 30; tick++) ledger.advance(tick);
        ledger.add(6, 30);
        for (int tick = 31; tick < 100; tick++) near(helper, 0, ledger.advance(tick), "不能提前到账");
        near(helper, 3, ledger.advance(100), "第一笔应按自己的期限到账");
        near(helper, 6, ledger.total(), "新伤害不能刷新旧伤害期限");
        for (int tick = 101; tick < 130; tick++) near(helper, 0, ledger.advance(tick), "第二笔不能提前");
        near(helper, 6, ledger.advance(130), "第二笔按独立期限到账");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absorptionIsDeductedBeforeThreshold(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        player.setAbsorptionAmount(8);
        hit(player, player.damageSources().generic(), 10);
        near(helper, 18, player.getHealth(), "8 点黄心后实际伤害只有 2，不应触发");
        near(helper, 0, player.getAbsorptionAmount(), "黄心应只扣除一次");
        near(helper, 0, data(player).total(), "不能按原始 10 点触发");
        helper.assertTrue(ring.getDamageValue() == 0, "不能扣耐久");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorResistanceAndProjectMitigationPrecedeRing(GameTestHelper helper)
    {
        Player baseline = survivor(helper);
        Player wearer = survivor(helper);
        for (Player player : List.of(baseline, wearer))
        {
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            player.setAbsorptionAmount(1);
            Objects.requireNonNull(player.getAttribute(attributes.DAMAGE_MITIGATION.get())).setBaseValue(1);
            Objects.requireNonNull(player.getAttribute(attributes.DAMAGE_REDUCTION.get())).setBaseValue(0.1);
        }
        ItemStack ring = equip(wearer);
        hit(baseline, baseline.damageSources().generic(), 12);
        float actual = 20 - baseline.getHealth();
        helper.assertTrue(actual >= 3, "测试基线必须达到触发阈值");
        hit(wearer, wearer.damageSources().generic(), 12);
        near(helper, 20 - actual * 0.7F, wearer.getHealth(), "减伤及黄心之后再分摊");
        near(helper, actual * 0.3F, data(wearer).total(), "延期只取实际生命伤害的 30%");
        near(helper, baseline.getAbsorptionAmount(), wearer.getAbsorptionAmount(), "不能重复处理黄心");
        helper.assertTrue(ring.getDamageValue() == 1, "只能触发一次");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectMitigationCanPutRawDamageBelowThreshold(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        Objects.requireNonNull(player.getAttribute(attributes.DAMAGE_MITIGATION.get())).setBaseValue(8);
        hit(player, player.damageSources().generic(), 10);
        near(helper, 18, player.getHealth(), "项目固定减伤先结算");
        near(helper, 0, data(player).total(), "实际只有 2 点时不能触发");
        helper.assertTrue(ring.getDamageValue() == 0, "不能扣耐久");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void settlementBypassesMitigationAbsorptionAndInvulnerabilityFrames(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 10);
        Objects.requireNonNull(player.getAttribute(attributes.DAMAGE_MITIGATION.get())).setBaseValue(100);
        Objects.requireNonNull(player.getAttribute(attributes.DAMAGE_REDUCTION.get())).setBaseValue(1);
        player.setAbsorptionAmount(50);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        player.invulnerableTime = 20;
        Vec3 movement = new Vec3(0.1, 0.2, 0.3);
        player.setDeltaMovement(movement);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items.LOW_QUALITY_POTION.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        stored_suffering_events.settle(player, data(player).drain());
        near(helper, 10, player.getHealth(), "延期应直接承受 3，不再次减伤");
        near(helper, 50, player.getAbsorptionAmount(), "不再消耗黄心");
        helper.assertTrue(player.getDeltaMovement().equals(movement) && player.isUsingItem(), "不能击退或打断使用物品");
        helper.assertTrue(ring.getDamageValue() == 1 && data(player).total() == 0, "不能循环延期或再次耗耐久");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removingRingImmediatelySettlesAllDebt(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 10);
        ringItem().onUnequip(context(player), ItemStack.EMPTY, ring);
        near(helper, 10, player.getHealth(), "卸下时应立即还清 3");
        near(helper, 0, data(player).total(), "欠款应先清除以免重入重复结算");
        ringItem().onUnequip(context(player), ItemStack.EMPTY, ring);
        near(helper, 10, player.getHealth(), "重复卸下回调不能重复结算");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void durabilityUpdatesAreNotUnequip(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        stored_suffering.identity(ring);
        ItemStack before = ring.copy();
        hit(player, player.damageSources().generic(), 10);
        ringItem().onUnequip(context(player), ring, before);
        near(helper, 13, player.getHealth(), "同一实例耐久变化不能误判卸下");
        near(helper, 3, data(player).total(), "欠款应继续计时");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lastDurabilitySettlesIncludingNewThirtyPercent(GameTestHelper helper)
    {
        Player player = survivor(helper);
        Objects.requireNonNull(player.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(100);
        player.setHealth(100);
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 10);
        ring.setDamageValue(511);
        hit(player, player.damageSources().generic(), 10);
        helper.assertTrue(ring.isEmpty(), "最后一点耐久应破碎");
        near(helper, 80, player.getHealth(), "本次 7 + 新欠款 3 + 旧欠款 3 都应结清");
        near(helper, 0, data(player).total(), "破碎后不能遗漏欠款");
        stored_suffering_events.onRingRemoved(player);
        near(helper, 80, player.getHealth(), "破碎后的卸下回调不能重复扣血");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactlyFiveHundredTwelveTriggersBreakRing(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        ring.enchant(Enchantments.UNBREAKING, 10);
        for (int trigger = 1; trigger <= 512; trigger++)
        {
            player.setHealth(20);
            hit(player, player.damageSources().generic(), 3);
            if (trigger < 512)
            {
                helper.assertTrue(!ring.isEmpty() && ring.getDamageValue() == trigger, "每次固定损耗 1 点");
            }
            data(player).drain(); // 仅隔离耐久测试，避免累计欠款使样本死亡。
        }
        helper.assertTrue(ring.isEmpty(), "恰好第 512 次触发破碎，耐久附魔不能延长");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleRingsDoNotStackAndReplacementCannotEraseDebt(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ringSlots(player).addTransientModifier(new AttributeModifier(UUID.randomUUID(),
                "stored_suffering_test_second_ring", 1, AttributeModifier.Operation.ADDITION));
        ringSlots(player).update();
        ItemStack first = equip(player);
        ItemStack second = new ItemStack(items.STORED_SUFFERING.get());
        ringSlots(player).getStacks().setStackInSlot(1, second);
        hit(player, player.damageSources().generic(), 10);
        near(helper, 13, player.getHealth(), "佩戴两枚仍只延期 30%");
        helper.assertTrue(first.getDamageValue() + second.getDamageValue() == 1, "只耗一枚的 1 点耐久");
        ringSlots(player).getStacks().setStackInSlot(0, new ItemStack(items.STORED_SUFFERING.get()));
        stored_suffering_events.tick(player);
        near(helper, 10, player.getHealth(), "替换实例时即使仍有戒指也必须结清");
        near(helper, 0, data(player).total(), "不能靠更换装备清掉欠款");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingRingToAnotherSlotSettlesDebt(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ringSlots(player).addTransientModifier(new AttributeModifier(UUID.randomUUID(),
                "stored_suffering_test_second_ring", 1, AttributeModifier.Operation.ADDITION));
        ringSlots(player).update();
        ItemStack ring = equip(player);
        hit(player, player.damageSources().generic(), 10);
        ringSlots(player).getStacks().setStackInSlot(0, ItemStack.EMPTY);
        ringSlots(player).getStacks().setStackInSlot(1, ring);
        stored_suffering_events.tick(player);
        near(helper, 10, player.getHealth(), "移动槽位也必须结清");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedLedgerPausesOfflineAndRestoresRemainingTicks(GameTestHelper helper)
    {
        suffering_data original = new suffering_data();
        original.add(3, 0);
        for (int tick = 1; tick <= 40; tick++) original.advance(tick);
        CompoundTag saved = original.serializeNBT();
        suffering_data restored = new suffering_data();
        restored.deserializeNBT(saved);
        for (int tick = 10000; tick < 10059; tick++) near(helper, 0, restored.advance(tick), "离线时间不能算入倒计时");
        near(helper, 3, restored.advance(10059), "剩余 60 个在线刻后应到账");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loginEquipmentLoadDoesNotCountAsRemoval(GameTestHelper helper)
    {
        Player player = survivor(helper);
        equip(player);
        data(player).add(3, now(player));
        CompoundTag saved = data(player).serializeNBT();
        data(player).deserializeNBT(saved);
        stored_suffering_events.login(new PlayerEvent.PlayerLoggedInEvent(player));
        stored_suffering_events.tick(player);
        near(helper, 20, player.getHealth(), "正常读入装备不能立即结算");
        near(helper, 3, data(player).total(), "登录应保留未到期欠款");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dimensionClonePreservesDebtAndDeathCloneDoesNot(GameTestHelper helper)
    {
        Player original = survivor(helper);
        data(original).add(3, now(original));
        original.invalidateCaps();
        Player dimensionCopy = survivor(helper);
        suffering_capability.clonePlayer(new PlayerEvent.Clone(dimensionCopy, original, false));
        near(helper, 3, data(dimensionCopy).total(), "非死亡克隆应保留账本");
        Player respawn = survivor(helper);
        suffering_capability.clonePlayer(new PlayerEvent.Clone(respawn, dimensionCopy, true));
        near(helper, 0, data(respawn).total(), "死亡重生不能继承欠款");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lethalSettlementRunsDeathEventAndClearsDebt(GameTestHelper helper)
    {
        // ServerPlayer 覆写 die 并执行真实死亡界面/掉落流程；普通模拟 Player 会重复调用父类死亡事件。
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "suffering-test"));
        // 不向 PlayerList 注册无真实 Channel 的测试连接；只省略发包，保留 ServerPlayer.die 本身。
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player)
        {
            @Override
            public void send(@javax.annotation.Nonnull Packet<?> packet) {}

            @Override
            public void send(@javax.annotation.Nonnull Packet<?> packet, @javax.annotation.Nullable PacketSendListener listener) {}
        };
        player.getAbilities().invulnerable = false;
        equip(player);
        data(player).add(3, now(player));
        data(player).equipmentReady = true;
        player.setHealth(2);
        AtomicInteger deaths = new AtomicInteger();
        Consumer<LivingDeathEvent> listener = event -> { if (event.getEntity() == player) deaths.incrementAndGet(); };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try
        {
            stored_suffering_events.onRingRemoved(player);
            helper.assertTrue(player.isDeadOrDying() && deaths.get() == 1, "必须执行一次正常死亡事件");
            near(helper, 0, data(player).total(), "死亡后账本必须清空");
        }
        finally
        {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeAndInvulnerableStatesAreRespected(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        player.getAbilities().invulnerable = true;
        hit(player, player.damageSources().generic(), 10);
        stored_suffering_events.settle(player, 100);
        near(helper, 20, player.getHealth(), "无敌状态不能被延期伤害杀死");
        helper.assertTrue(ring.getDamageValue() == 0, "无敌时不能触发耐久损耗");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customForcedDamageCannotBeDeferred(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        List<DamageSource> sources = List.of(
                player.damageSources().fellOutOfWorld(), player.damageSources().genericKill(),
                depletion.source(player.level()), abnormal_mana.source(player.level()),
                nightmare.source(player.level(), null), stored_suffering_damage.source(player.level()));
        for (DamageSource source : sources)
        {
            player.setHealth(20);
            hit(player, source, 4);
            near(helper, 0, data(player).total(), "强制结算类型不能延期: " + source.getMsgId());
        }
        helper.assertTrue(ring.getDamageValue() == 0, "强制伤害不能消耗耐久");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrowGunSpellAndEnvironmentReachFinalDamageStage(GameTestHelper helper)
    {
        Player player = survivor(helper);
        ItemStack ring = equip(player);
        Player attacker = survivor(helper);
        Objects.requireNonNull(attacker.getAttribute(attributes.EXPERT_IN_ARCHERY.get())).setBaseValue(0);
        Objects.requireNonNull(attacker.getAttribute(attributes.ARROW_STRIKE.get())).setBaseValue(0);
        DamageSource arrow = new DamageSource(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW), attacker, attacker);
        DamageSource bullet = new DamageSource(helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getTag(ModDamageTypes.BULLETS_TAG).orElseThrow().iterator().next(), attacker, attacker);
        SpellDamageSource spell = SpellDamageSource.source(attacker, attacker, SpellRegistry.FIREBALL_SPELL.get());
        List<DamageSource> sources = List.of(arrow, bullet, spell, player.damageSources().cactus());
        for (DamageSource source : sources)
        {
            player.setHealth(20);
            data(player).drain();
            hit(player, source, 10);
            helper.assertTrue(data(player).total() >= 0.9F, "该类型应进入最终伤害延期: " + source.getMsgId());
        }
        helper.assertTrue(ring.getDamageValue() == 4, "每种伤害恰好触发一次");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spellDamageEventStillFeedsRingThroughActualHurt(GameTestHelper helper)
    {
        Player victim = survivor(helper);
        equip(victim);
        Player caster = survivor(helper);
        Objects.requireNonNull(caster.getAttribute(attributes.SPELL_CRITICAL_STRIKE.get())).setBaseValue(0);
        Objects.requireNonNull(caster.getAttribute(attributes.SPELL_ENHANCEMENT.get())).setBaseValue(2);
        SpellDamageSource source = SpellDamageSource.source(caster, caster, SpellRegistry.FIREBALL_SPELL.get());
        SpellDamageEvent spell = new SpellDamageEvent(victim, 8, source);
        MinecraftForge.EVENT_BUS.post(spell);
        hit(victim, source, spell.getAmount());
        near(helper, 13, victim.getHealth(), "法术增幅后的 10 点应分成 7+3");
        near(helper, 3, data(victim).total(), "法术伤害应保留 30%");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nameTooltipAndActionBarUseIndependentStyles(GameTestHelper helper)
    {
        ItemStack ring = new ItemStack(items.STORED_SUFFERING.get());
        helper.assertTrue(Objects.equals(ring.getHoverName().getStyle().getColor(), TextColor.fromRgb(0xAA0000)), "名称应暗红");
        List<Component> tooltip = new ArrayList<>();
        ring.getItem().appendHoverText(ring, helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
        helper.assertTrue(tooltip.size() == 2, "未按 Shift 时只显示风味和展开提示");
        tooltip.clear();
        ((stored_suffering) ring.getItem()).appendDescription(tooltip, true);
        helper.assertTrue(Objects.equals(tooltip.get(0).getStyle().getColor(), TextColor.fromRgb(0xAA00AA)), "风味应暗紫");
        for (int line = 1; line < tooltip.size(); line++)
        {
            helper.assertTrue(Objects.equals(tooltip.get(line).getStyle().getColor(), TextColor.fromRgb(0xAAAAAA)), "普通说明不继承暗紫");
        }
        Component message = stored_suffering_events.pendingMessage(3.04F);
        helper.assertTrue(Objects.equals(message.getStyle().getColor(), TextColor.fromRgb(0xAA0000))
                && message.getString().contains("3.0"), "提示应暗红并保留一位小数");
        helper.succeed();
    }

    private static Player survivor(GameTestHelper helper)
    {
        Player player = helper.makeMockSurvivalPlayer();
        player.getAbilities().invulnerable = false;
        // 固定伤害断言排除前置模组的随机暴击；不修改实际游戏的默认暴击属性。
        var criticalChance = Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("attributeslib", "crit_chance")));
        Objects.requireNonNull(player.getAttribute(criticalChance)).setBaseValue(0);
        return player;
    }

    private static ItemStack equip(Player player)
    {
        ItemStack ring = new ItemStack(items.STORED_SUFFERING.get());
        ringSlots(player).getStacks().setStackInSlot(0, ring);
        return ring;
    }

    private static ICurioStacksHandler ringSlots(Player player)
    {
        return CuriosApi.getCuriosInventory(player).orElseThrow(IllegalStateException::new)
                .getStacksHandler("ring").orElseThrow();
    }

    private static suffering_data data(Player player)
    {
        return suffering_capability.get(player).orElseThrow();
    }

    private static stored_suffering ringItem()
    {
        return (stored_suffering) items.STORED_SUFFERING.get();
    }

    private static SlotContext context(Player player)
    {
        return new SlotContext("ring", player, 0, false, true);
    }

    private static long now(Player player)
    {
        return Objects.requireNonNull(player.getServer(), "Server-side test requires a server").getTickCount();
    }

    private static void hit(Player player, DamageSource source, float amount)
    {
        player.invulnerableTime = 0;
        player.hurt(source, amount);
    }

    private static void near(GameTestHelper helper, float expected, float actual, String message)
    {
        helper.assertTrue(Math.abs(expected - actual) < 0.001F, message + ": expected=" + expected + ", actual=" + actual);
    }
}
