package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.capability.san_state;
import com.damezame97.white_lily.debugging.debug_file;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilycurio.improvement_loadout;
import com.damezame97.white_lily.white_lilydamage.meltdown;
import com.damezame97.white_lily.white_lilydamage.remit_loadcapacity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 理智值（{@code white_lily.san}）的默认值与四种状态的全部机制。
 *
 * <h2>一、默认值按世界档位给</h2>
 * <table border="1">
 *     <caption>档位 → 理智值默认值</caption>
 *     <tr><th>档位</th><th>难度</th><th>理智值</th></tr>
 *     <tr><td>1</td><td>和平 PEACEFUL</td><td>50000</td></tr>
 *     <tr><td>2</td><td>简单 EASY</td><td>22000</td></tr>
 *     <tr><td>3</td><td>普通 NORMAL</td><td>10000</td></tr>
 *     <tr><td>4</td><td>困难 HARD</td><td>2000</td></tr>
 * </table>
 * <p>档位读世界级调试文件 {@code Debugging_whitelilymod.json} 的 {@code difficulty_level}
 * （与「改良」饰品栏、载重值同一套）。注意这里<b>只初始化一次</b>
 * （{@code san_state.initialized}）：理智值会被游戏过程持续消耗（例如 Happy_mode 下饿到归零
 * 每 2 秒掉 1 点），每次登录都重设会把玩家的进度全部抹掉。</p>
 *
 * <h2>二、四种状态</h2>
 * <table border="1">
 *     <caption>状态划分</caption>
 *     <tr><th>状态</th><th>区间</th><th>效果</th></tr>
 *     <tr><td>过盛 OVERFLOW</td><td>&gt; 100000</td><td>无</td></tr>
 *     <tr><td>正常 NORMAL</td><td>[5000, 100000]</td><td>无</td></tr>
 *     <tr><td>崩溃 COLLAPSE</td><td>[1000, 5000)</td><td>见下</td></tr>
 *     <tr><td>融毁 MELTDOWN</td><td>&lt; 1000</td><td>见下</td></tr>
 * </table>
 *
 * <h3>崩溃</h3>
 * <ul>
 *     <li>{@code stamina} −10、{@code recovery_interval} −200（加法修饰符）；</li>
 *     <li>{@code restore_health} −1，但<b>不会扣到负数以下</b>：
 *         只扣「当前值到 0 的那一段」（{@link #clampRestoreHealthReduction}）；</li>
 *     <li><b>受伤计时器</b>：上限 50 秒；第一次挨打给 10 秒，计时器还在时每再挨一次 +5 秒；</li>
 *     <li><b>精神损伤</b>：计时器每 1 秒 → 玩家受到的伤害里有 1% 改按
 *         {@code white_lily:mental_injury}（梦魇伤害族，无视减免）结算。
 *         计时器 50 秒时就是 50%。</li>
 * </ul>
 *
 * <h3>融毁</h3>
 * <ul>
 *     <li>玩家每损失 1 点血量，就被等量削掉 1 点<b>生命上限</b>（累计，负向修饰符）；</li>
 *     <li>生命上限归 0 时立刻死亡，致死播报用 {@code white_lily:meltdown}
 *         （{@code death.attack.meltdown} 留空待填）；</li>
 *     <li>死亡重生会清掉这份罚值（否则会变成必死循环）。</li>
 * </ul>
 *
 * <h2>三、精神损伤为什么要「合并」</h2>
 * <p>需求要求它<b>只有一次伤害、一个伤害数字、一条主伤害类型的致死播报</b>，所以
 * {@code mental_injury} 从不单独 {@code hurt()}。具体做法是在 {@code LivingHurtEvent}
 * （护甲结算之前）把「那一份该无视减免的伤害」补进同一次伤害的数值里：
 * 设主伤害 {@code D}、转换比例 {@code p}、护甲系数 {@code k}（0&lt;k≤1），
 * 目标是 {@code k·D·(1−p) + D·p}，于是把事件数值改成
 * {@code D·(1−p) + D·p/k}——过完护甲刚好落回目标值。见 {@link #mentalInjuryBoostedAmount}。</p>
 * <p>因为始终是同一个 {@link DamageSource}，致死播报永远是主伤害类型的那条，
 * {@code mental_injury} 的 lang 理论上永远不会被看到（按需求留空）。</p>
 *
 * <h2>四、{@code remit_loadcapacity} 标签</h2>
 * <p>{@code mental_injury} 与 {@code load_burden} 都挂在这个标签上：它们不参与本类的精神损伤换算，
 * 也不触发 {@link load_capacity_procedures} 的载重追罚——否则会无限套娃。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class san_procedures
{
    // ==================================================================
    // 一、默认值
    // ==================================================================

    /** 档位 1（和平）的默认理智值。 */
    public static final int SAN_PEACEFUL = 50000;

    /** 档位 2（简单）的默认理智值。 */
    public static final int SAN_EASY = 22000;

    /** 档位 3（普通）的默认理智值。 */
    public static final int SAN_NORMAL = 10000;

    /** 档位 4（困难）的默认理智值。 */
    public static final int SAN_HARD = 2000;

    // ==================================================================
    // 二、状态阈值
    // ==================================================================

    /** 过盛：严格大于它。 */
    public static final double OVERFLOW_ABOVE = 100000.0D;

    /** 正常的下限（含）。 */
    public static final double NORMAL_MIN = 5000.0D;

    /** 崩溃的下限（含）。低于它就是融毁。 */
    public static final double COLLAPSE_MIN = 1000.0D;

    /** 理智值的四种状态。 */
    public enum State
    {
        /** 过盛：&gt; 100000。 */
        OVERFLOW("overflow"),
        /** 正常：[5000, 100000]。 */
        NORMAL("normal"),
        /** 崩溃：[1000, 5000)。 */
        COLLAPSE("collapse"),
        /** 融毁：&lt; 1000。 */
        MELTDOWN("meltdown");

        private final String key;

        State(String key)
        {
            this.key = key;
        }

        /** 写进日志/测试断言用的短名。 */
        public String key()
        {
            return key;
        }

        /** 按当前理智值判定状态。 */
        public static State of(double san)
        {
            if (san > OVERFLOW_ABOVE)
            {
                return OVERFLOW;
            }
            if (san >= NORMAL_MIN)
            {
                return NORMAL;
            }
            if (san >= COLLAPSE_MIN)
            {
                return COLLAPSE;
            }
            return MELTDOWN;
        }
    }

    // ==================================================================
    // 三、崩溃状态的参数
    // ==================================================================

    /** 崩溃时 {@code stamina} 的加法修正。 */
    public static final double COLLAPSE_STAMINA_DELTA = -10.0D;

    /** 崩溃时 {@code restore_health} 的请求扣除量（实际会按「不扣到负数以下」夹一次）。 */
    public static final double COLLAPSE_RESTORE_HEALTH_DELTA = -1.0D;

    /** 崩溃时 {@code recovery_interval} 的加法修正。 */
    public static final double COLLAPSE_RECOVERY_INTERVAL_DELTA = -200.0D;

    /** 一秒多少刻。 */
    public static final int TICKS_PER_SECOND = 20;

    /** 第一次挨打给的计时器：10 秒。 */
    public static final int INJURY_FIRST_TICKS = 10 * TICKS_PER_SECOND;

    /** 计时器还在时每再挨一次延长：5 秒。 */
    public static final int INJURY_EXTEND_TICKS = 5 * TICKS_PER_SECOND;

    /** 计时器上限：50 秒。 */
    public static final int INJURY_MAX_TICKS = 50 * TICKS_PER_SECOND;

    /** 计时器每满 1 秒 → 1% 的伤害改按精神损伤结算。 */
    public static final double MENTAL_INJURY_PER_SECOND = 0.01D;

    /** 崩溃状态三个修饰符的固定 UUID。 */
    private static final UUID STAMINA_MODIFIER_ID = UUID.fromString("b9d1c100-0000-4000-8000-000000000001");
    private static final UUID RESTORE_MODIFIER_ID = UUID.fromString("b9d1c100-0000-4000-8000-000000000002");
    private static final UUID RECOVERY_MODIFIER_ID = UUID.fromString("b9d1c100-0000-4000-8000-000000000003");

    /** 融毁「生命上限被削」的修饰符 UUID。 */
    private static final UUID MELTDOWN_MODIFIER_ID = UUID.fromString("b9d1c100-0000-4000-8000-000000000004");

    private static final String MODIFIER_NAMESPACE = white_lily.MODID + ":san_";

    /** 正在被本模组处决的玩家（防止融毁致死那一击自己套自己）。 */
    private static final Set<Player> MELTDOWN_KILLING =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** 诊断用：最后一个被 {@link #onLivingHurt} 处理的玩家走到了哪一步。 */
    public static String DEBUG_LAST_HURT = "never";

    private san_procedures()
    {
    }

    // ==================================================================
    // 四、默认值：读调试文件 → 写进玩家属性（只初始化一次）
    // ==================================================================

    /** 档位 → 默认理智值（和平 50000 / 简单 22000 / 普通 10000 / 困难 2000）。 */
    public static int sanForLevel(int level)
    {
        return switch (improvement_loadout.clampLevel(level))
        {
            case 1 -> SAN_PEACEFUL;
            case 2 -> SAN_EASY;
            case 3 -> SAN_NORMAL;
            default -> SAN_HARD;
        };
    }

    /** 世界难度 → 默认理智值。 */
    public static int sanForDifficulty(net.minecraft.world.Difficulty difficulty)
    {
        return sanForLevel(improvement_loadout.levelForDifficulty(difficulty));
    }

    /**
     * 玩家的理智值还没被初始化过时，按世界档位写一次默认值。
     *
     * @return 本次用的档位（1~4）；已经初始化过则返回 -1（什么都没改）
     */
    public static int syncDefaultFromWorld(Player player)
    {
        san_state state = player_variables.san(player).orElse(null);
        if (state == null || state.isInitialized())
        {
            return -1;
        }

        MinecraftServer server = player.getServer();
        int level = server != null
                ? debug_file.loadOrCreateLevel(server)
                : improvement_loadout.levelForDifficulty(player.level().getLevelData().getDifficulty());

        applyDefaultSan(player, level);
        state.setInitialized(true);
        return level;
    }

    /** 无条件把理智值的基础值设成该档位的默认值。 */
    public static void applyDefaultSan(Player player, int level)
    {
        AttributeInstance instance = player.getAttribute(attributes.SAN.get());
        if (instance != null)
        {
            instance.setBaseValue(sanForLevel(level));
        }
    }

    /** 玩家登录：首次进世界时按档位初始化理智值。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            syncDefaultFromWorld(player);
        }
    }

    // ==================================================================
    // 五、每刻：状态修饰符、受伤计时器、融毁罚值
    // ==================================================================

    /** 服务端每刻跑一次：状态修饰符、计时器倒数、融毁罚值同步。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        san_state state = player_variables.san(player).orElse(null);
        if (state == null)
        {
            return;
        }

        // 计时器每刻掉 1。
        if (state.getInjuryTicks() > 0)
        {
            state.setInjuryTicks(state.getInjuryTicks() - 1);
        }

        // 融毁罚值：每刻校正一次修饰符（临时修饰符不存档，靠能力值重建）。
        syncMeltdownModifier(player, state);

        // 状态类修饰符每 10 刻核对一次，够快又不会每刻重算属性。
        if (player.tickCount % 10 == 0)
        {
            syncStateModifiers(player, State.of(currentSan(player)), state);
        }
    }

    /** 按当前状态挂/摘崩溃的负面修饰符；离开崩溃时顺手清掉计时器。 */
    public static void syncStateModifiers(Player player, State state, san_state data)
    {
        boolean collapse = state == State.COLLAPSE;
        if (!collapse && data.getInjuryTicks() > 0)
        {
            // 离开崩溃状态：计时器作废（不是「暂停」，是整条机制不生效）。
            data.setInjuryTicks(0);
        }

        setModifier(player, attributes.STAMINA.get(), STAMINA_MODIFIER_ID, "stamina",
                collapse ? COLLAPSE_STAMINA_DELTA : 0.0D);
        setModifier(player, attributes.RECOVERY_INTERVAL.get(), RECOVERY_MODIFIER_ID, "recovery_interval",
                collapse ? COLLAPSE_RECOVERY_INTERVAL_DELTA : 0.0D);

        AttributeInstance restore = player.getAttribute(attributes.RESTORE_HEALTH.get());
        if (restore != null)
        {
            // restore_health 的扣除要「不扣到负数以下」，所以得先看当前值再决定扣多少。
            double current = valueWithout(restore, RESTORE_MODIFIER_ID);
            setModifier(player, attributes.RESTORE_HEALTH.get(), RESTORE_MODIFIER_ID, "restore_health",
                    clampRestoreHealthReduction(current, collapse ? COLLAPSE_RESTORE_HEALTH_DELTA : 0.0D));
        }
    }

    /** 融毁罚值 → 负向生命上限修饰符。 */
    public static void syncMeltdownModifier(Player player, san_state data)
    {
        setModifier(player, Attributes.MAX_HEALTH, MELTDOWN_MODIFIER_ID, "meltdown",
                -data.getMeltdownPenalty());
    }

    // ==================================================================
    // 六、受伤：精神损伤换算 + 计时器延长
    // ==================================================================

    /**
     * 崩溃状态下的伤害处理：先按「当前计时器」换算精神损伤比例，再延长计时器。
     *
     * <p>顺序是刻意的：第一次挨打时还没有计时器（比例 0），这一击按原样结算，
     * 打完之后才获得 10 秒计时器——对应需求里的「初始受伤一次后为 10 秒」。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (event.getAmount() <= 0.0F || MELTDOWN_KILLING.contains(event.getEntity()))
        {
            DEBUG_LAST_HURT = "guard-amount-or-killing";
            return;
        }
        if (!(event.getEntity() instanceof Player victim))
        {
            DEBUG_LAST_HURT = "not-player:" + event.getEntity().getClass().getSimpleName();
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(remit_loadcapacity.TAG))
        {
            // 精神损伤 / 载重反噬本身不再参与换算（防递归），也不刷新计时器。
            DEBUG_LAST_HURT = "remit-tag:" + source.getMsgId();
            return;
        }

        san_state data = player_variables.san(victim).orElse(null);
        if (data == null)
        {
            DEBUG_LAST_HURT = "no-cap";
            return;
        }
        State state = State.of(currentSan(victim));
        if (state != State.COLLAPSE)
        {
            DEBUG_LAST_HURT = "state=" + state.key() + " san=" + currentSan(victim);
            return;
        }

        // 1) 先按「此刻」的计时器换算。
        double percent = mentalInjuryPercent(data.getInjuryTicks());
        if (percent > 0.0D)
        {
            float armor = victim.getArmorValue();
            float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            boolean armorApplies = !source.is(DamageTypeTags.BYPASSES_ARMOR);
            double boosted = mentalInjuryBoostedAmount(event.getAmount(), percent,
                    armorApplies ? armor : 0.0F, armorApplies ? toughness : 0.0F);
            event.setAmount((float) boosted);
            DEBUG_LAST_HURT = "boosted pct=" + percent + " armor=" + armor + " tough=" + toughness
                    + " armorApplies=" + armorApplies + " -> " + event.getAmount();
        }
        else
        {
            DEBUG_LAST_HURT = "pct=0 ticks=" + data.getInjuryTicks();
        }

        // 2) 再把计时器建立/延长。
        data.setInjuryTicks(nextInjuryTicks(data.getInjuryTicks()));
    }

    // ==================================================================
    // 七、受伤：融毁的生命上限转换
    // ==================================================================

    /**
     * 融毁状态：把「损失的血量」等量削掉生命上限；上限归零则立刻处决。
     *
     * <p>用 {@link EventPriority#LOWEST}，拿到的是最终的、真正会从生命值里扣掉的数字。
     * 扣掉生命上限后原版会把当前生命夹到新上限，所以视觉上会一次少两截——
     * 这正是「血量换成上限」的字面效果。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event)
    {
        if (event.getAmount() <= 0.0F)
        {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || MELTDOWN_KILLING.contains(victim))
        {
            return;
        }
        if (event.getSource().is(remit_loadcapacity.TAG))
        {
            return;
        }

        san_state data = player_variables.san(victim).orElse(null);
        if (data == null || State.of(currentSan(victim)) != State.MELTDOWN)
        {
            return;
        }

        if (victim.getMaxHealth() <= 0.0F || remainingMaxHealth(victim, data) <= 0.0D)
        {
            // 已经是 0 上限，直接处决（不再累计，免得无限递归）。
            killByMeltdown(victim);
            return;
        }

        double lost = Math.min(event.getAmount(), victim.getHealth());
        if (lost <= 0.0D)
        {
            return;
        }

        data.addMeltdownPenalty(lost);
        syncMeltdownModifier(victim, data);

        if (remainingMaxHealth(victim, data) <= 0.0D)
        {
            killByMeltdown(victim);
        }
    }

    /**
     * 把融毁罚值扣完之后还剩多少生命上限。
     *
     * <p>不能直接看 {@code getMaxHealth()}：原版 {@code Attributes.MAX_HEALTH} 的最小值是
     * <b>1.0</b>，属性会自己夹住，永远到不了 0。所以这里用「自身基础上限（不含融毁修正）− 罚值」
     * 来算真正的手算值，≤ 0 就是「生命上限归零」。</p>
     */
    public static double remainingMaxHealth(Player player, san_state data)
    {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        double base = instance == null
                ? player.getMaxHealth()
                : valueWithout(instance, MELTDOWN_MODIFIER_ID);
        return base - data.getMeltdownPenalty();
    }

    /** 用 {@code white_lily:meltdown} 处决玩家（播报键 {@code death.attack.meltdown}，留空待填）。 */
    public static void killByMeltdown(Player player)
    {
        if (!MELTDOWN_KILLING.add(player))
        {
            return;
        }
        try
        {
            DamageSource source = meltdown.source(player.level());
            player.hurt(source, 1.0E9F);
            if (!player.isDeadOrDying())
            {
                player.die(source);
            }
        }
        finally
        {
            MELTDOWN_KILLING.remove(player);
        }
    }

    // ==================================================================
    // 八、纯计算（公开给 GameTest 与行为池复用）
    // ==================================================================

    /** 当前理智值（拿不到属性就当 0）。 */
    public static double currentSan(Player player)
    {
        AttributeInstance instance = player.getAttribute(attributes.SAN.get());
        return instance == null ? 0.0D : instance.getValue();
    }

    /** 计时器剩余刻数 → 精神损伤比例（0~0.50）。 */
    public static double mentalInjuryPercent(int injuryTicks)
    {
        int seconds = Math.max(0, injuryTicks) / TICKS_PER_SECOND;
        seconds = Math.min(seconds, INJURY_MAX_TICKS / TICKS_PER_SECOND);
        return seconds * MENTAL_INJURY_PER_SECOND;
    }

    /** 下一次挨打后的计时器：没有就给 10 秒，有就 +5 秒且不超过 50 秒。 */
    public static int nextInjuryTicks(int currentTicks)
    {
        if (currentTicks <= 0)
        {
            return INJURY_FIRST_TICKS;
        }
        return Math.min(INJURY_MAX_TICKS, currentTicks + INJURY_EXTEND_TICKS);
    }

    /**
     * 「不扣到负数以下」的那一段扣除量。
     *
     * @param currentValue 当前（不含本条修正的）属性值
     * @param requested    希望扣多少（负数表示扣）
     * @return 实际应施加的修正（负数，且 {@code currentValue + 结果 ≥ 0}）
     */
    public static double clampRestoreHealthReduction(double currentValue, double requested)
    {
        if (requested >= 0.0D)
        {
            return requested;
        }
        return -Math.min(-requested, Math.max(0.0D, currentValue));
    }

    /**
     * 把「按精神损伤结算的那一份」折进同一次伤害的事件数值里。
     *
     * <p>设事件伤害 {@code D}、转换比例 {@code p}、护甲系数 {@code k}：
     * 目标最终值 {@code k·D·(1−p) + D·p}，所以事件数值取
     * {@code D·(1−p) + D·p/k}（过完护甲刚好落回目标）。{@code p=0} 或 {@code k=1}
     * 时原样返回，不吃任何多余运算。</p>
     *
     * <p>护甲系数故意<b>不自己写公式</b>，而是直接问 {@code CombatRules.getDamageAfterAbsorb}：
     * 它才是真正会跑的那份减免逻辑（而且会被 Apothic Attributes 之类的模组改写，
     * 实测在本工作区里护甲 15 、伤害 5 时系数是 0.40 而不是原版算式的 0.50）。
     * 问它而不是猜它，才能保证「补回来的那一份」刚好等于真正被护甲吃掉的那一份。</p>
     *
     * @param amount    事件当前伤害（护甲结算之前）
     * @param percent   转换比例（0~1）
     * @param armor     受害者护甲值（不适用护甲时传 0）
     * @param toughness 受害者盔甲韧性（不适用护甲时传 0）
     */
    public static double mentalInjuryBoostedAmount(float amount, double percent,
                                                   float armor, float toughness)
    {
        if (amount <= 0.0F)
        {
            return amount;
        }
        double p = Math.max(0.0D, Math.min(1.0D, percent));
        if (p <= 0.0D)
        {
            return amount;
        }

        double portion = amount * p;
        double afterArmor = CombatRules.getDamageAfterAbsorb((float) portion, armor, toughness);
        if (afterArmor <= 1.0E-6D)
        {
            // 极端护甲下这份会被完全吃掉：那就整份按原样加上去（当成无视减免）。
            return amount + portion;
        }
        double k = afterArmor / portion;
        return amount - portion + portion / k;
    }

    // ==================================================================
    // 九、内部小工具
    // ==================================================================

    /** 挂/改/摘一个加法修饰符（值为 0 就摘掉）。 */
    private static void setModifier(Player player, Attribute attribute, UUID id, String name, double value)
    {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null)
        {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        boolean wantRemoved = Math.abs(value) < 1.0E-9D;
        if (wantRemoved)
        {
            if (existing != null)
            {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null)
        {
            if (Math.abs(existing.getAmount() - value) < 1.0E-9D)
            {
                return;
            }
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, MODIFIER_NAMESPACE + name, value,
                AttributeModifier.Operation.ADDITION));
    }

    /** 属性值，但先把自己那条修饰符摘掉（避免「读自己的结果再算自己」）。 */
    private static double valueWithout(AttributeInstance instance, UUID id)
    {
        AttributeModifier ours = instance.getModifier(id);
        if (ours == null)
        {
            return instance.getValue();
        }
        instance.removeModifier(id);
        double value = instance.getValue();
        instance.addTransientModifier(ours);
        return value;
    }
}
