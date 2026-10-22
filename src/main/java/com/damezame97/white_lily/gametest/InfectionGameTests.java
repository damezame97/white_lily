package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.infection_state;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.infection.*;
import com.damezame97.white_lily.procedures.san_procedures;
import com.damezame97.white_lily.white_lily;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 用独立临时来源，不登记生产来源或症状；假玩家不连接客户端。 */
@SuppressWarnings({"unused", "resource"})
@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
public class InfectionGameTests
{
    @GameTest(template = "empty")
    public static void injuryBoundariesAndContinuousRisk(GameTestHelper helper)
    {
        double[] health = {0.700001, 0.70, 0.400001, 0.40, 0.200001, 0.20, 0.01};
        double[] expectedM = {1, 1.2, 1.2, 1.8, 1.8, 2.8, 2.8};
        double[] expectedG = {0, 0.5, 0.5, 1, 1, 2, 2};
        for (int i = 0; i < health.length; i++)
        {
            near(helper, expectedM[i], infection_math.injury(health[i]).multiplier, "伤势边界M");
            near(helper, expectedG[i], infection_math.injury(health[i]).passive, "伤势边界G");
        }
        double[] stamina = {-100, -4, -3, -2, -1, -0.5, 0, 0.5, 1, 2, 3, 4, 5, 100};
        double[] risk = {2, 2, 1.75, 1.5, 1.25, 1.125, 1, 0.95, 0.9, 0.8, 0.7, 0.6, 0.5, 0.5};
        for (int i = 0; i < stamina.length; i++) near(helper, risk[i], infection_math.risk(stamina[i]), "耐力风险");
        near(helper, 1, infection_math.risk(Double.NaN), "异常耐力保护");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void requiredExampleAndExactlyTwelveHundredTicks(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(6);
        stamina(player).setBaseValue(-2);
        resistance(player).setBaseValue(0.2);
        var conditions = infection_service.conditions(player);
        near(helper, 0.3, conditions.healthRatio(), "生命6/20");
        near(helper, 1.8, conditions.injury().multiplier, "M");
        near(helper, 1, conditions.injury().passive, "G");
        near(helper, 1.5, conditions.risk(), "K");
        var result = infection_service.trySource(player, source(1, 10), new Rolls());
        helper.assertTrue(result.triggered(), "P=1必触发");
        near(helper, 17.6, result.actualAdded(), "指定外来例子");
        infection_service.set(player, 1);
        advance(player, 0, 1199);
        near(helper, 1, infection_service.query(player), "1199刻尚未结算");
        infection_service.tick(player, 1199);
        near(helper, 2.2, infection_service.query(player), "第1200刻被动恰好加1.2");
        helper.assertTrue(data(player).passiveTicks() == 0, "周期结算后归零");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roundingIsPerIncreaseWithoutHiddenRemainder(GameTestHelper helper)
    {
        Player player = player(helper);
        for (int i = 0; i < 100; i++) infection_service.addDirect(player, 0.049);
        helper.assertTrue(data(player).units() == 0, "不得积累小于0.1的隐藏余量");
        infection_service.addDirect(player, 0.05);
        infection_service.addDirect(player, 0.15);
        helper.assertTrue(data(player).units() == 3, "HALF_UP边界应为整数1+2");
        near(helper, 0.3, infection_service.query(player), "十分位存储");
        helper.assertTrue(infection_math.external(0.1, infection_math.Injury.HEALTHY, 2, 0) == 1,
                "健康时耐力不能降低或放大来源基础量");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resistanceReducesAmountNotProbability(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(19); // 避开体况良好的额外10个百分点抗性，单独核对公式。
        double[] raw = {0, 0.2, 0.8, 10};
        double[] expected = {10, 8, 2, 2};
        for (int i = 0; i < raw.length; i++)
        {
            infection_service.clear(player);
            resistance(player).setBaseValue(raw[i]);
            var result = infection_service.trySource(player, source(0.3, 10), new Rolls(0.29));
            helper.assertTrue(result.triggered(), "抗性不降低来源触发概率");
            near(helper, expected[i], result.actualAdded(), "抗性结果");
        }
        near(helper, 0, attributes.INFECTION_RESISTANCE.get().getDefaultValue(), "抗性默认零");
        helper.assertTrue(attributes.INFECTION_RESISTANCE.get().isClientSyncable(), "抗性属性应同步");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceProbabilityAndValidationHaveDeterministicBoundaries(GameTestHelper helper)
    {
        Player player = player(helper);
        helper.assertTrue(!infection_service.trySource(player, source(0, 10), new Rolls()).triggered(), "P=0不抽随机也不触发");
        helper.assertTrue(infection_service.trySource(player, source(1, 10), new Rolls()).triggered(), "P=1不依赖随机");
        helper.assertTrue(!infection_service.trySource(player, source(0.3, 10), new Rolls(0.3)).triggered(), "等于概率不触发");
        helper.assertTrue(infection_service.trySource(player, source(0.3, 10), new Rolls(0.299)).triggered(), "小于概率触发");
        helper.assertTrue(infection_sources.entries().isEmpty() && infection_symptoms.size() == 0, "生产来源和症状表必须为空");
        helper.assertTrue(infection_service.trySource(player, ResourceLocation.fromNamespaceAndPath("white_lily", "missing"))
                .status() == infection_service.Status.UNKNOWN_SOURCE, "未知来源不得生效");
        for (double probability : new double[]{-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
        {
            boolean rejected = false;
            try { source(probability, 10); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "应拒绝非法来源概率");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hardCapReportsActualIncreaseAndDiscardsCycles(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(4);
        infection_service.set(player, 299.9);
        data(player).setPassiveTicks(1199);
        var result = infection_service.trySource(player, source(1, Double.MAX_VALUE), new Rolls(0.99));
        near(helper, 0.1, result.actualAdded(), "实际只增加上限前剩余0.1");
        helper.assertTrue(data(player).units() == 3000 && data(player).passiveTicks() == 0, "满值不保存周期或溢出");
        advance(player, 1, 2400);
        infection_service.reduce(player, 0.1);
        helper.assertTrue(data(player).passiveTicks() == 0, "治疗后没有积压周期");
        infection_service.set(player, -10);
        helper.assertTrue(data(player).units() == 0, "负设置归零");
        infection_service.set(player, Double.MAX_VALUE);
        helper.assertTrue(data(player).units() == 3000, "超大数安全夹值");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void directManagementBypassesRiskAndRejectsInvalidAmounts(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(1);
        stamina(player).setBaseValue(-4);
        resistance(player).setBaseValue(0.8);
        infection_service.set(player, 5);
        near(helper, 10, infection_service.addDirect(player, 10), "直接增加不套伤势或抗性");
        near(helper, 2, infection_service.reduce(player, 2), "治疗不套抗性");
        near(helper, 13, infection_service.query(player), "管理结果");
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
        {
            boolean rejected = false;
            try { infection_service.addDirect(player, invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "增加量应拒绝负数和非有限数");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroInfectionAndHealingResetTimer(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(4);
        advance(player, 0, 2400);
        helper.assertTrue(data(player).units() == 0 && data(player).passiveTicks() == 0, "零感染不凭空产生感染");
        infection_service.set(player, 10);
        advance(player, 2400, 600);
        helper.assertTrue(data(player).passiveTicks() == 600, "低生命累计");
        player.setHealth(14.01F);
        infection_service.tick(player, 3000);
        helper.assertTrue(data(player).passiveTicks() == 0, "超过70%立即清空计时");
        player.setHealth(14);
        infection_service.tick(player, 3001);
        helper.assertTrue(data(player).passiveTicks() == 1, "恰好70%开始计时");
        infection_service.reduce(player, 10);
        helper.assertTrue(data(player).units() == 0 && data(player).passiveTicks() == 0, "治疗到零清计时");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tierChangesKeepTimerAndUseSettlementTier(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(14);
        infection_service.set(player, 10);
        advance(player, 0, 600);
        player.setHealth(8);
        advance(player, 600, 599);
        helper.assertTrue(data(player).passiveTicks() == 1199, "档位变化不能重置计时");
        player.setHealth(4);
        infection_service.tick(player, 1199);
        near(helper, 12, infection_service.query(player), "结算时G=2，不再乘M");
        infection_service.tick(player, 1199);
        helper.assertTrue(data(player).passiveTicks() == 0, "同一全局tick不重复处理");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disabledFrameworkPausesButManagementAndClearingStillWork(GameTestHelper helper)
    {
        boolean previous = Config.infectionEnabled;
        try
        {
            Player player = player(helper);
            player.setHealth(4);
            infection_service.set(player, 10);
            advance(player, 0, 600);
            Config.infectionEnabled = false;
            advance(player, 600, 1200);
            helper.assertTrue(data(player).passiveTicks() == 600, "关闭时冻结周期");
            helper.assertTrue(infection_service.trySource(player, source(1, 10), new Rolls()).status()
                    == infection_service.Status.DISABLED, "关闭时来源冻结");
            infection_service.addDirect(player, 2);
            infection_service.reduce(player, 1);
            near(helper, 11, infection_service.query(player), "关闭时管理和治疗仍可使用");
            player.setHealth(20);
            infection_service.tick(player, 1800);
            helper.assertTrue(data(player).passiveTicks() == 0, "暂停时也应执行康复清计时");
            player.setHealth(4);
            Config.infectionEnabled = true;
            advance(player, 1801, 1199);
            near(helper, 11, infection_service.query(player), "重新开启无离线补算");
        }
        finally { Config.infectionEnabled = previous; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invulnerabilityCreativeSpectatorAndDeathDoNotAccumulate(GameTestHelper helper)
    {
        Player player = player(helper);
        player.setHealth(6);
        infection_service.set(player, 10);
        data(player).setPassiveTicks(100);
        player.getAbilities().invulnerable = true;
        advance(player, 0, 2000);
        helper.assertTrue(data(player).passiveTicks() == 100, "无敌时暂停");
        player.getAbilities().invulnerable = false;
        infection_service.tick(player, 2000);
        helper.assertTrue(data(player).passiveTicks() == 101, "退出无敌继续原进度");
        player.deathTime = 1;
        infection_service.tick(player, 2001);
        helper.assertTrue(data(player).passiveTicks() == 101
                && !infection_service.trySource(player, source(1, 10), new Rolls()).triggered(), "正在死亡不得增长");
        player.deathTime = 0;
        Player creative = helper.makeMockPlayer();
        Player spectator = new Player(helper.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "infection-spectator"))
        {
            @Override public boolean isSpectator() { return true; }
            @Override public boolean isCreative() { return false; }
        };
        for (Player excluded : List.of(creative, spectator))
        {
            excluded.setHealth(6);
            infection_service.set(excluded, 10);
            data(excluded).setPassiveTicks(100);
            advance(excluded, 0, 1200);
            helper.assertTrue(data(excluded).passiveTicks() == 100, "创造/旁观暂停且不清数据");
            helper.assertTrue(!infection_service.trySource(excluded, source(1, 10), new Rolls()).triggered(), "无敌模式不接受来源");
        }
        player.setHealth(0);
        advance(player, 2001, 1200);
        near(helper, 10, infection_service.query(player), "死亡不增长");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void usesEffectiveMaxHealthAndIgnoresAbsorption(GameTestHelper helper)
    {
        Player player = player(helper);
        Objects.requireNonNull(player.getAttribute(Attributes.MAX_HEALTH)).addTransientModifier(
                new AttributeModifier(UUID.randomUUID(), "test_max_health", 20, AttributeModifier.Operation.ADDITION));
        player.setHealth(12);
        player.setAbsorptionAmount(100);
        near(helper, 0.3, infection_service.conditions(player).healthRatio(), "有效上限40且不包含黄心");
        near(helper, 1.8, infection_service.conditions(player).injury().multiplier, "按真实生命分档");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void excludesOnlyOwnedModifiersAndPreservesAllThreeOperations(GameTestHelper helper)
    {
        Player player = player(helper);
        AttributeInstance instance = stamina(player);
        instance.setBaseValue(2);
        instance.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "equipment_add", 3, AttributeModifier.Operation.ADDITION));
        instance.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "equipment_base", 0.2, AttributeModifier.Operation.MULTIPLY_BASE));
        instance.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "equipment_total", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
        ResourceLocation rule = ResourceLocation.fromNamespaceAndPath("white_lily", "test_symptom");
        instance.addTransientModifier(infection_modifiers.create(rule, attributes.STAMINA.get(), -10, AttributeModifier.Operation.ADDITION));
        // 各症状/操作使用独立规则ID；同一规则同一属性只拥有一个修饰符，便于更新和解除。
        instance.addTransientModifier(infection_modifiers.create(ResourceLocation.fromNamespaceAndPath("white_lily", "test_base"),
                attributes.STAMINA.get(), -0.3, AttributeModifier.Operation.MULTIPLY_BASE));
        instance.addTransientModifier(infection_modifiers.create(ResourceLocation.fromNamespaceAndPath("white_lily", "test_total"),
                attributes.STAMINA.get(), -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
        double actual = instance.getValue();
        var before = List.copyOf(instance.getModifiers());
        near(helper, 9, infection_service.stamina(player), "(2+3)*(1+0.2)*(1+0.5)，排除全部感染操作");
        helper.assertTrue(before.equals(List.copyOf(instance.getModifiers())), "取值不得拆装真实修饰符");
        near(helper, actual, instance.getValue(), "实际耐力不被读取操作改变");
        infection_modifiers.remove(player, rule, attributes.STAMINA.get());
        helper.assertTrue(instance.getModifiers().size() == before.size() - 1, "只解除所属规则的修正");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sanCollapseStaminaStillParticipates(GameTestHelper helper)
    {
        Player player = player(helper);
        san_procedures.syncStateModifiers(player, san_procedures.State.COLLAPSE, player_variables.san(player).orElseThrow());
        near(helper, -10, infection_service.stamina(player), "不能排除SAN的耐力惩罚");
        near(helper, 2, infection_service.conditions(player).risk(), "SAN造成的-10按2倍封顶");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveLoadValidationAndOfflinePause(GameTestHelper helper)
    {
        infection_state original = new infection_state();
        original.setUnits(176);
        original.setPassiveTicks(999);
        infection_state loaded = new infection_state();
        loaded.deserializeNBT(original.serializeNBT());
        helper.assertTrue(loaded.units() == 176 && loaded.passiveTicks() == 999, "金额及剩余进度往返，不读取现实时间");
        loaded.deserializeNBT(new CompoundTag());
        helper.assertTrue(loaded.units() == 0 && loaded.passiveTicks() == 0, "旧档安全默认");
        CompoundTag bad = new CompoundTag();
        bad.putLong("units", Long.MAX_VALUE);
        bad.putInt("passive_ticks", 50000);
        loaded.deserializeNBT(bad);
        helper.assertTrue(loaded.units() == 3000 && loaded.passiveTicks() == 0, "超大整数不能溢出并且满值清计时");
        bad.putInt("units", 1);
        loaded.deserializeNBT(bad);
        helper.assertTrue(loaded.units() == 1 && loaded.passiveTicks() == 1199, "异常周期校验");
        bad.putDouble("units", Double.NaN);
        loaded.deserializeNBT(bad);
        helper.assertTrue(loaded.units() == 0 && loaded.passiveTicks() == 0, "NaN安全归零");
        bad.putDouble("units", Double.POSITIVE_INFINITY);
        loaded.deserializeNBT(bad);
        helper.assertTrue(loaded.units() == 0, "无限值安全归零");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cloneUsesNewDataAndRespectsBothDeathConfigurations(GameTestHelper helper)
    {
        boolean keep = Config.infectionKeepOnDeath;
        try
        {
            Player original = player(helper);
            original.setHealth(6);
            infection_service.set(original, 17.6);
            data(original).setPassiveTicks(900);
            Player dimension = player(helper);
            original.invalidateCaps();
            infection_events.clonePlayer(new PlayerEvent.Clone(dimension, original, false));
            helper.assertTrue(data(dimension).units() == 176 && data(dimension).passiveTicks() == 900, "换维度保留全部进度");
            helper.assertTrue(data(dimension) != data(originalAfterRevive(original)), "不保留旧玩家数据对象引用");
            for (boolean value : new boolean[]{true, false})
            {
                Config.infectionKeepOnDeath = value;
                Player respawn = player(helper);
                infection_events.clonePlayer(new PlayerEvent.Clone(respawn, dimension, true));
                helper.assertTrue(data(respawn).units() == (value ? 176 : 0)
                        && data(respawn).passiveTicks() == 0, "死亡配置和清计时");
            }
        }
        finally { Config.infectionKeepOnDeath = keep; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotCodecAndSyncDeduplication(GameTestHelper helper)
    {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            var packet = new infection_network.Snapshot(176, 999);
            packet.write(buffer);
            helper.assertTrue(packet.equals(infection_network.Snapshot.read(buffer)), "快照编码解码");
        }
        finally { buffer.release(); }
        infection_state data = new infection_state();
        data.setUnits(10);
        helper.assertTrue(infection_network.needsSync(data, false), "首次需要同步");
        data.lastSyncedUnits = 10;
        data.setPassiveTicks(500);
        helper.assertTrue(!infection_network.needsSync(data, false), "纯计时不能每刻发包");
        helper.assertTrue(infection_network.needsSync(data, true), "生命周期强制同步");
        data.setUnits(11);
        helper.assertTrue(infection_network.needsSync(data, false), "数值变化需要同步");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandRequiresPermissionTwo(GameTestHelper helper)
    {
        var server = helper.getLevel().getServer();
        var command = server.getCommands().getDispatcher().getRoot().getChild("white_lily").getChild("infection");
        helper.assertTrue(command != null, "感染命令应注册");
        helper.assertTrue(!command.canUse(server.createCommandSourceStack().withPermission(1)), "权限1不得使用");
        helper.assertTrue(command.canUse(server.createCommandSourceStack().withPermission(2)), "权限2可使用");
        helper.succeed();
    }

    private static Player originalAfterRevive(Player player) { player.reviveCaps(); return player; }
    private static Player player(GameTestHelper helper)
    {
        Player player = helper.makeMockSurvivalPlayer();
        player.getAbilities().invulnerable = false;
        player.setHealth(20);
        return player;
    }
    private static AttributeInstance stamina(Player player) { return Objects.requireNonNull(player.getAttribute(attributes.STAMINA.get())); }
    private static AttributeInstance resistance(Player player) { return Objects.requireNonNull(player.getAttribute(attributes.INFECTION_RESISTANCE.get())); }
    private static infection_state data(Player player) { return player_variables.infection(player).orElseThrow(); }
    private static infection_sources.Source source(double probability, double base)
    {
        return new infection_sources.Source(ResourceLocation.fromNamespaceAndPath("white_lily", "gametest_only"), probability, base);
    }
    private static void advance(Player player, long start, int ticks)
    {
        for (int i = 0; i < ticks; i++) infection_service.tick(player, start + i);
    }
    private static void near(GameTestHelper helper, double expected, double actual, String message)
    {
        helper.assertTrue(Math.abs(expected - actual) < 0.000001D, message + ": " + expected + " != " + actual);
    }
    private static final class Rolls extends LegacyRandomSource
    {
        private final double[] values;
        private int index;
        Rolls(double... values) { super(0); this.values = values; }
        @Override public double nextDouble() { return values[index++]; }
    }
}
