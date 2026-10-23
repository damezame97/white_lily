package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.procedures.san_behaviour_pool;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.damezame97.white_lily.white_lilydamage.excessive_hemorrhage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** 固定条件、事件时钟和等权随机池。计时只由服务端玩家 tick 推进。 */
public final class infection_symptom_rules
{
    public static final List<String> TIMED_IDS = List.of("wound_seepage", "delayed_healing", "infection_blindness",
            "brief_lucidity", "recovery_fatigue", "shivering", "unstable_spellcasting");
    public static final List<String> ALL_IDS = List.of("healthy_condition", "delayed_healing", "rapid_breathing",
            "wound_seepage", "infection_anxiety", "perception_disorder", "mental_distraction", "brief_lucidity",
            "recovery_fatigue", "shivering", "unstable_spellcasting", "infection_blindness");
    private static final Map<ResourceLocation, Integer> POOL = new LinkedHashMap<>();
    private static volatile List<String> timed = TIMED_IDS;
    private static volatile List<String> all = ALL_IDS;
    private static final Map<String, MobEffect> EFFECTS = new java.util.concurrent.ConcurrentHashMap<>();
    static
    {
        registerRandom(id("shivering"), 1200);
        registerRandom(id("unstable_spellcasting"), 1200);
    }

    /** 意志尺度未定稿：不读取/假定 SAN 或 willpower_strength 的换算关系。 */
    public static double strength(Player player) { return 1.0D; }

    // ==================================================================
    // 伤口渗血（wound_seepage）分级
    // ==================================================================
    /** 渗血的效果 ID。 */
    public static final String SEEPAGE = "wound_seepage";
    /** 渗血能达到的最高等级（0~4）。 */
    public static final int SEEPAGE_MAX_LEVEL = 4;
    /** 每级初始时长的基数：初始 = 1200 × (等级 + 1)。 */
    public static final int SEEPAGE_INITIAL_STEP = 1200;
    /** 每级时长上限的基数：上限 = 2400 × (等级 + 1)。 */
    public static final int SEEPAGE_MAXIMUM_STEP = 2400;
    /** 每次延长的固定部分：延长 = 400 + 200 × 等级。 */
    public static final int SEEPAGE_EXTEND_BASE = 400;
    /** 每次延长的每级部分。 */
    public static final int SEEPAGE_EXTEND_STEP = 200;
    /** 各等级扣理智的周期（刻，0 = 不扣）。 */
    private static final int[] SEEPAGE_SAN_PERIOD = {0, 80, 40, 20, 20};
    /** 各等级每次扣多少理智。 */
    private static final double[] SEEPAGE_SAN_AMOUNT = {0, 1, 1, 5, 20};
    /** 各等级「意志坚定」免疫门槛：属性值 ≥ 该数则不受扣理智影响（0 = 无门槛）。 */
    private static final double[] SEEPAGE_SAN_WILLPOWER = {0, 1, 1, 2, 3};
    /** 各等级的回复量扣减（允许把最终回复量扣成负数）。 */
    private static final double[] SEEPAGE_RESTORE_PENALTY = {0, -1, -1.5, -5, -20};
    /** 疾走出血的判定间隔（格）。 */
    private static final double SEEPAGE_BLEED_BLOCKS_SLOW = 6;
    /** 3 级疾走出血伤害。 */
    private static final float SEEPAGE_BLEED_SLOW = 1.0F;
    /** 4 级疾走/移动出血的每几格间隔。 */
    private static final double SEEPAGE_BLEED_BLOCKS_FAST = 3;

    /** 「愈合迟滞」的效果 ID。 */
    public static final String DELAYED = "delayed_healing";
    /** 愈合迟滞的最高等级。 */
    public static final int DELAYED_MAX_LEVEL = 3;
    /** 各等级的最终回复量系数。 */
    private static final double[] DELAYED_MULTIPLIER = {0.75D, 0.5D, 0.25D, 0.0D};
    /** 各等级扣理智的周期（刻）：8/6/4/2 秒。 */
    private static final int[] DELAYED_SAN_PERIOD = {160, 120, 80, 40};
    /** 各等级每次扣多少理智。 */
    private static final double[] DELAYED_SAN_AMOUNT = {1, 2, 3, 4};
    /** 各等级的意志免疫门槛。 */
    private static final double[] DELAYED_SAN_WILLPOWER = {1, 1, 2, 2};
    /** 初得/升级时的持续时间范围：4~12 分钟（整分钟）。 */
    public static final int DELAYED_MIN_MINUTES = 4;
    public static final int DELAYED_MAX_MINUTES = 12;
    /** 「造血障碍」成就要求的持续时长：60 分钟。 */
    public static final int DELAYED_CHALLENGE_TICKS = 60 * 60 * 20;
    /** 各阶段触发概率。 */
    public static final double DELAYED_GAIN_CHANCE = 0.07D;
    public static final double[] DELAYED_LEVEL_CHANCE = {0.03D, 0.02D, 0.0005D};
    public static final double DELAYED_CHALLENGE_CHANCE = 0.00012D;

    /** 该等级的最终回复量系数（负数回复量不使用）。 */
    public static double delayedMultiplier(int level)
    {
        return DELAYED_MULTIPLIER[Math.max(0, Math.min(DELAYED_MAX_LEVEL, level))];
    }

    public static int delayedSanPeriod(int level)
    {
        return DELAYED_SAN_PERIOD[Math.max(0, Math.min(DELAYED_MAX_LEVEL, level))];
    }

    public static double delayedSanAmount(int level)
    {
        return DELAYED_SAN_AMOUNT[Math.max(0, Math.min(DELAYED_MAX_LEVEL, level))];
    }

    public static double delayedSanWillpower(int level)
    {
        return DELAYED_SAN_WILLPOWER[Math.max(0, Math.min(DELAYED_MAX_LEVEL, level))];
    }

    /** 初得/升级时的随机持续刻数：4~12 整分钟。 */
    public static int delayedRandomDuration(RandomSource random)
    {
        int minutes = DELAYED_MIN_MINUTES + random.nextInt(DELAYED_MAX_MINUTES - DELAYED_MIN_MINUTES + 1);
        return minutes * 60 * 20;
    }

    /** 「愈合迟滞」的触发感染阈值：50 + 10×耐力，夹在 [20, 100]。 */
    public static double delayedInfectionThreshold(double stamina)
    {
        if (!Double.isFinite(stamina)) return 50;
        return Math.max(20, Math.min(100, 50 + 10 * stamina));
    }

    /** 「愈合迟滞」的触发血量比例阈值：70% - 5%×耐力，夹在 [50%, 90%]。 */
    public static double delayedHealthThreshold(double stamina)
    {
        if (!Double.isFinite(stamina)) return 0.70D;
        return Math.max(0.50D, Math.min(0.90D, 0.70D - 0.05D * stamina));
    }

    /** 当前「愈合迟滞」等级（没有该效果时返回 -1）。 */
    public static int delayedLevel(Player player)
    {
        infection_symptom_state state = state(player);
        if (state == null || !has(player, DELAYED)) return -1;
        return Math.max(0, Math.min(DELAYED_MAX_LEVEL, state.delayedHealingLevel));
    }

    /** 这次回复该乘的系数：没有「愈合迟滞」时是 1。 */
    public static double healMultiplier(Player player)
    {
        int level = delayedLevel(player);
        return level < 0 ? 1.0D : delayedMultiplier(level);
    }

    /**
     * 「消耗饱食度恢复血量」每次结算前调用：先掷骰决定是否获得/升级，再让调用方乘系数。
     *
     * <p>获得 0 级需要同时满足感染与血量两条门槛；已经持有时的升级骰不再重复看门槛。</p>
     */
    public static void onDelayedHealAction(Player player, RandomSource random)
    {
        if (!active(player) || player.level().isClientSide) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        double stamina = stamina(player);
        if (!has(player, DELAYED))
        {
            if (infection_service.query(player) < delayedInfectionThreshold(stamina)) return;
            double max = player.getMaxHealth();
            double ratio = max > 0 ? player.getHealth() / max : 0;
            if (ratio >= delayedHealthThreshold(stamina)) return;
            if (random.nextDouble() >= DELAYED_GAIN_CHANCE) return;
            start(player, DELAYED, delayedRandomDuration(random), 0);
            reevaluate(player);
            return;
        }
        int level = delayedLevel(player);
        if (level < 0) return;
        if (level < DELAYED_MAX_LEVEL)
        {
            if (random.nextDouble() >= DELAYED_LEVEL_CHANCE[level]) return;
            start(player, DELAYED, delayedRandomDuration(random), level + 1);
            reevaluate(player);
            return;
        }
        if (random.nextDouble() >= DELAYED_CHALLENGE_CHANCE) return;
        start(player, DELAYED, DELAYED_CHALLENGE_TICKS, DELAYED_MAX_LEVEL);
        reevaluate(player);
        grantHematopoiesisDisorder(player);
    }

    /** 授予「造血障碍」成就（只在服务端且有对应进度时生效）。 */
    private static void grantHematopoiesisDisorder(Player player)
    {
        if (!(player instanceof ServerPlayer server)) return;
        var advancement = server.getServer().getAdvancements().getAdvancement(
                ResourceLocation.fromNamespaceAndPath("white_lily", "hematopoiesis_disorder"));
        if (advancement != null) server.getAdvancements().award(advancement, "granted");
    }

    /** 每刻结算「愈合迟滞」的扣理智。 */
    private static void delayedTick(Player player, infection_symptom_state state)
    {
        if (!state.running(DELAYED))
        {
            state.delayedSanTicks = 0;
            state.delayedHealingLevel = 0;
            return;
        }
        int level = Math.max(0, Math.min(DELAYED_MAX_LEVEL, state.delayedHealingLevel));
        if (willpower(player) >= delayedSanWillpower(level))
        {
            state.delayedSanTicks = 0;
            return;
        }
        if (++state.delayedSanTicks >= delayedSanPeriod(level))
        {
            state.delayedSanTicks = 0;
            san_behaviour_pool.addSan(player, -delayedSanAmount(level));
        }
    }

    // ==================================================================
    // 精神涣散（mental_distraction）：等级叠加与衰减
    // ==================================================================
    public static final String MENTAL = "mental_distraction";
    /** 等级上限。 */
    public static final int MENTAL_MAX_LEVEL = 10;
    /** 不满足条件后，每 30 秒降一级。 */
    public static final int MENTAL_DECAY_TICKS = 30 * 20;
    /** 消耗魔力后 10 秒内不重启衰减计时器。 */
    public static final int MENTAL_RESTART_TICKS = 10 * 20;
    /** 每级魔力成本增幅。 */
    public static final double MENTAL_COST_PER_LEVEL = 0.05D;
    /** 每级施法时长增幅。 */
    public static final double MENTAL_CAST_PER_LEVEL = 0.10D;

    /** 当前精神涣散等级（没有该效果时返回 0）。 */
    public static int mentalDistractionLevel(Player player)
    {
        infection_symptom_state state = state(player);
        if (state == null || !has(player, MENTAL)) return 0;
        return Math.max(0, Math.min(MENTAL_MAX_LEVEL, state.mentalDistractionLevel));
    }

    /** 获得该效果的感染/SAN 条件。 */
    public static boolean mentalCondition(Player player)
    {
        return infection_service.query(player) >= 200 && san(player) < 5000;
    }

    /** 该等级的魔力成本系数。 */
    public static double mentalCostMultiplier(int level)
    {
        return 1.0D + MENTAL_COST_PER_LEVEL * Math.max(0, Math.min(MENTAL_MAX_LEVEL, level));
    }

    /** 该等级的施法时长系数。 */
    public static double mentalCastMultiplier(int level)
    {
        return 1.0D + MENTAL_CAST_PER_LEVEL * Math.max(0, Math.min(MENTAL_MAX_LEVEL, level));
    }

    /** 实际花掉魔力时调用：等级 +1（封顶 10），并重新开始 10 秒的暂停。 */
    public static void onManaSpent(Player player)
    {
        if (player == null || player.level().isClientSide) return;
        infection_symptom_state state = state(player);
        if (state == null || !has(player, MENTAL)) return;
        state.mentalDistractionLevel = Math.min(MENTAL_MAX_LEVEL,
                Math.max(0, state.mentalDistractionLevel) + 1);
        state.mentalDistractionRestart = MENTAL_RESTART_TICKS;
        state.mentalDistractionDecay = 0;
        state.dirty = true;
        reevaluate(player);
    }

    /** 每刻推进衰减；条件满足时暂停并清空计时。 */
    private static void mentalTick(Player player, infection_symptom_state state)
    {
        if (!has(player, MENTAL))
        {
            state.mentalDistractionDecay = 0;
            state.mentalDistractionRestart = 0;
            return;
        }
        if (mentalCondition(player))
        {
            state.mentalDistractionDecay = 0;
            state.mentalDistractionRestart = 0;
            return;
        }
        if (state.mentalDistractionLevel <= 0)
        {
            stop(player, MENTAL);
            return;
        }
        if (state.mentalDistractionRestart > 0)
        {
            state.mentalDistractionRestart--;
            return;
        }
        if (++state.mentalDistractionDecay >= MENTAL_DECAY_TICKS)
        {
            state.mentalDistractionDecay = 0;
            state.mentalDistractionLevel--;
            state.dirty = true;
            if (state.mentalDistractionLevel <= 0) stop(player, MENTAL);
        }
    }

    // ==================================================================
    // 精神涣散结束
    // ==================================================================

    // ==================================================================
    // 包扎（bandaged）：一次性削减渗血
    // ==================================================================
    /** 同级包扎的扣除量（刻）。 */
    public static final int BANDAGED_BASE_DEDUCTION = 1000;
    /** 结算后给的 delayed_healing 时长：180 秒。 */
    public static final int BANDAGED_DELAYED_HEALING_TICKS = 180 * 20;
    /** 结算后给的 delayed_healing 放大等级：1 级。 */
    public static final int BANDAGED_DELAYED_HEALING_AMPLIFIER = 1;

    /**
     * 包扎能扣掉多少渗血时长：同级 1000，包扎每低一级除 2，每高一级乘 2。
     *
     * @param bandagedLevel 包扎的放大等级
     * @param seepageLevel  渗血的等级
     */
    public static int bandagedDeduction(int bandagedLevel, int seepageLevel)
    {
        int difference = seepageLevel(seepageLevel) - Math.max(0, bandagedLevel);
        double value = BANDAGED_BASE_DEDUCTION;
        value *= Math.pow(2.0D, -difference);
        if (!Double.isFinite(value)) return Integer.MAX_VALUE;
        return (int) Math.round(Math.min(Integer.MAX_VALUE, value));
    }

    /**
     * 包扎的结算入口（由 {@code bandaged#applyEffectTick} 每刻调用）。
     *
     * <p>先按等级算扣除量、改渗血时长，再摘掉包扎；只有真的对渗血生效过，
     * 才给 1 级 180 秒的 {@code delayed_healing}。</p>
     *
     * @return 实际扣除的刻数（没生效为 0）
     */
    public static int resolveBandaged(Player player, int bandagedLevel)
    {
        if (player.level().isClientSide) return 0;
        MobEffect bandaged = effect("bandaged");
        if (bandaged == null || !player.hasEffect(bandaged)) return 0;
        infection_symptom_state state = state(player);
        int deduction = 0;
        boolean applied = false;
        if (state != null && has(player, SEEPAGE))
        {
            int level = seepageLevel(state.woundSeepageLevel);
            deduction = bandagedDeduction(bandagedLevel, level);
            int remaining = state.remaining(SEEPAGE);
            applied = true;
            if (remaining == -1)
            {
                // 无限时长不参与扣除，但仍然算「生效过」。
                deduction = 0;
            }
            else
            {
                int left = remaining - deduction;
                // 剩余时长不足扣除量（含刚好扣完）：直接移除。
                if (left <= 0) stop(player, SEEPAGE);
                else state.set(SEEPAGE, left);
            }
        }
        removeDisplay(player, "bandaged");
        if (applied && state != null)
        {
            // 「愈合迟滞」现在是普通计时症状：1 级、180 秒。
            start(player, DELAYED, BANDAGED_DELAYED_HEALING_TICKS, BANDAGED_DELAYED_HEALING_AMPLIFIER);
        }
        reevaluate(player);
        return deduction;
    }

    /** 等级夹取到 0~4。 */
    public static int seepageLevel(int level) { return Math.max(0, Math.min(SEEPAGE_MAX_LEVEL, level)); }

    /** 该等级的初始时长：1200 × (等级 + 1)。 */
    public static int seepageInitialDuration(int level) { return SEEPAGE_INITIAL_STEP * (seepageLevel(level) + 1); }

    /** 该等级的时长上限：2400 × (等级 + 1)。 */
    public static int seepageMaximumDuration(int level) { return SEEPAGE_MAXIMUM_STEP * (seepageLevel(level) + 1); }

    /** 该等级每次延长的刻数：400 + 200 × 等级。 */
    public static int seepageExtension(int level) { return SEEPAGE_EXTEND_BASE + SEEPAGE_EXTEND_STEP * seepageLevel(level); }

    public static int seepageSanPeriod(int level) { return SEEPAGE_SAN_PERIOD[seepageLevel(level)]; }

    public static double seepageSanAmount(int level) { return SEEPAGE_SAN_AMOUNT[seepageLevel(level)]; }

    public static double seepageSanWillpower(int level) { return SEEPAGE_SAN_WILLPOWER[seepageLevel(level)]; }

    public static double seepageRestorePenalty(int level) { return SEEPAGE_RESTORE_PENALTY[seepageLevel(level)]; }

    /** 触发渗血的感染阈值：30 + 5 × 耐力，夹在 [0, 60]。 */
    public static double seepageInfectionThreshold(double stamina)
    {
        if (!Double.isFinite(stamina)) return 30;
        return Math.max(0, Math.min(60, 30 + 5 * stamina));
    }

    /** 耐力带来的额外伤害门槛比例：max(0, 10% × 耐力)，封顶 +25%。 */
    public static double seepageStaminaRatio(double stamina)
    {
        if (!Double.isFinite(stamina) || stamina <= 0) return 0;
        return Math.min(0.25, 0.10 * stamina);
    }

    /** 触发渗血的伤害阈值：3 + 生命上限 × 耐力比例。 */
    public static double seepageDamageThreshold(double maxHealth, double stamina)
    {
        if (!Double.isFinite(maxHealth) || maxHealth < 0) maxHealth = 0;
        return 3 + maxHealth * seepageStaminaRatio(stamina);
    }

    /** 当前渗血等级（0~4；没有该效果时按 0）。 */
    public static int seepageLevel(Player player)
    {
        infection_symptom_state state = state(player);
        return state == null ? 0 : seepageLevel(state.woundSeepageLevel);
    }

    /** 渗血抑制的等级：没有抑制时 -1，否则取其放大等级。 */
    public static int suppressionLevel(Player player)
    {
        MobEffectInstance instance = player.getEffect(mob_effects.WOUND_SUPPRESSION.get());
        return instance == null ? -1 : Math.max(0, instance.getAmplifier());
    }

    /**
     * 实际生效的渗血等级：被抑制时为 -1。
     *
     * <p>抑制只能盖住「等级不高于自己」的那一层：0 级抑制盖不住 1 级渗血的惩罚。</p>
     */
    public static int seepageEffectiveLevel(Player player)
    {
        infection_symptom_state state = state(player);
        if (state == null || !has(player, SEEPAGE)) return -1;
        int level = seepageLevel(state.woundSeepageLevel);
        return level <= suppressionLevel(player) ? -1 : level;
    }

    /** 当前耐力属性值。 */
    public static double stamina(Player player)
    {
        var attribute = player.getAttribute(attributes.STAMINA.get());
        return attribute == null ? 0 : attribute.getValue();
    }

    /** 当前意志坚定属性值。 */
    public static double willpower(Player player)
    {
        var attribute = player.getAttribute(attributes.WILLPOWER_STRENGTH.get());
        return attribute == null ? 0 : attribute.getValue();
    }

    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("white_lily", name); }
    @Nullable
    public static MobEffect effect(@Nullable String name)
    {
        if (name == null) return null;
        MobEffect cached = EFFECTS.get(name);
        if (cached != null) return cached;
        ResourceLocation key = ResourceLocation.tryParse("white_lily:" + name);
        MobEffect found = key == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(key);
        if (found != null) EFFECTS.put(name, found);
        return found;
    }
    @Nullable
    public static infection_symptom_state state(Player player)
    {
        return player_variables.infection(player).map(data -> data.symptoms).orElse(null);
    }
    public static boolean active(Player player)
    {
        return connected(player) && !player.level().isClientSide && player_variables.infection(player).isPresent() && player.isAlive() && player.deathTime == 0
                && Float.isFinite(player.getHealth()) && Float.isFinite(player.getMaxHealth()) && player.getMaxHealth() > 0
                && !player.isRemoved() && Config.infectionEnabled && !infection_service.excluded(player);
    }
    private static boolean connected(Player player)
    { return !(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection != null; }
    public static boolean has(Player player, String name)
    { MobEffect effect = effect(name); return effect != null && player.hasEffect(effect); }

    public static synchronized void registerRandom(ResourceLocation effect, int duration)
    {
        if (!"white_lily".equals(effect.getNamespace()) || duration <= 0 || duration > 12000 || POOL.putIfAbsent(effect, duration) != null)
            throw new IllegalArgumentException("Invalid or duplicate random symptom: " + effect);
        var names = new java.util.LinkedHashSet<>(TIMED_IDS);
        POOL.keySet().forEach(key -> names.add(key.getPath()));
        timed = List.copyOf(names);
        names.addAll(ALL_IDS);
        all = List.copyOf(names);
    }

    public static List<String> timedIds() { return timed; }

    public static List<String> allIds() { return all; }

    public static double poolChance(double infection)
    {
        return infection < 80 ? 0 : infection < 150 ? 0.1 : infection < 230 ? 0.2 : 0.3;
    }

    /** 只由成功产生实际增量的外来来源/被动增长调用；命令和读档不调用。 */
    public static void onIncrease(Player player, int actualUnits, RandomSource random)
    {
        if (!active(player) || actualUnits <= 0 || infection_service.query(player) < 80) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        if (state.poolCooldown > 0) return;
        state.poolCooldown = 600; // 包括失败与重复命中。
        if (random.nextDouble() >= poolChance(infection_service.query(player))) return;
        List<Map.Entry<ResourceLocation, Integer>> entries;
        synchronized (infection_symptom_rules.class) { entries = new ArrayList<>(POOL.entrySet()); }
        if (entries.isEmpty()) return;
        var entry = entries.get(random.nextInt(entries.size()));
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(entry.getKey());
        if (effect == null || player.hasEffect(effect) || state.running(entry.getKey().getPath())) return;
        start(player, entry.getKey().getPath(), entry.getValue());
        reevaluate(player);
    }

    public static void start(Player player, String name, int duration)
    {
        start(player, name, duration, 0);
    }

    /** 带放大等级的版本：只有渗血会用非 0 等级。 */
    public static void start(Player player, String name, int duration, int amplifier)
    {
        infection_symptom_state state = state(player);
        if (!active(player) || state == null || effect(name) == null || !timedIds().contains(name)) return;
        state.set(name, duration);
        state.dirty = true;
        if (SEEPAGE.equals(name)) state.woundSeepageLevel = seepageLevel(amplifier);
        if (DELAYED.equals(name)) state.delayedHealingLevel = Math.max(0, Math.min(DELAYED_MAX_LEVEL, amplifier));
        if (!display(player, name, duration, amplifier)) state.set(name, 0);
    }

    public static boolean blind(Player player)
    {
        var state = state(player);
        if (!active(player) || state == null || has(player, "brief_lucidity") || state.running("brief_lucidity")) return false;
        start(player, "infection_blindness", Math.max(1, (int) Math.round(60 * strength(player))));
        return state.running("infection_blindness") && has(player, "infection_blindness");
    }

    /**
     * 直接命中：感染与最终伤害都过线才给/延长渗血。
     *
     * <p>首次给 0 级、时长 = 该级初始值；之后每次 +（400 + 200 × 当前等级），
     * 一旦顶到该级上限就升一级、时长重置为新等级的初始值；最高 4 级。</p>
     */
    public static void onDirectHit(Player player, float originalHealthDamage)
    {
        if (!active(player) || !Float.isFinite(originalHealthDamage)) return;
        double stamina = stamina(player);
        if (infection_service.query(player) < seepageInfectionThreshold(stamina)) return;
        if (originalHealthDamage < seepageDamageThreshold(player.getMaxHealth(), stamina)) return;
        var state = state(player);
        if (state == null) return;
        int previous = state.remaining(SEEPAGE);
        int level;
        int duration;
        if (previous <= 0)
        {
            level = 0;
            duration = seepageInitialDuration(level);
        }
        else if (previous == -1)
        {
            // 外部给了无限时长：只保留原样，不擅自改写。
            level = seepageLevel(state.woundSeepageLevel);
            duration = -1;
        }
        else
        {
            level = seepageLevel(state.woundSeepageLevel);
            int maximum = seepageMaximumDuration(level);
            duration = previous + seepageExtension(level);
            if (duration >= maximum)
            {
                // 满是最高级时不再有「下一级」：直接顶在上限，不能重置成初始值。
                if (level < SEEPAGE_MAX_LEVEL)
                {
                    level = level + 1;
                    duration = seepageInitialDuration(level);
                }
                else
                {
                    duration = maximum;
                }
            }
        }
        start(player, SEEPAGE, duration, level);
        reevaluate(player);
    }

    public static void onTreatment(Player player, double actualReduction)
    {
        if (active(player) && actualReduction >= 30) { start(player, "recovery_fatigue", 1200); reevaluate(player); }
    }

    public static void onHealthLoss(Player player)
    {
        var state = state(player);
        if (!connected(player) || state == null) return;
        state.dirty = true;
        state.healthLossPending = true;
        removeDisplay(player, "healthy_condition");
        modifiers(player);
    }

    public static void tick(Player player, long clock)
    {
        if (!connected(player) || player.level().isClientSide || player_variables.infection(player).isEmpty()) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        if (state.lastTick == clock) return;
        state.lastTick = clock;
        if (state.healthLossPending) { state.healthLossPending = false; state.dirty = true; }
        if (!player.isAlive() || player.deathTime > 0)
        {
            if (!state.evaluated || state.active || !state.remaining.isEmpty())
            { state.clear(); reevaluate(player); }
            return;
        }
        boolean active = active(player);
        if (!active)
        {
            if (!state.evaluated || state.active || state.dirty) reevaluate(player);
            return;
        }
        // 只读缓存属性及生命值；状态变化立即重评，稳定时每10刻校验。
        double san = san(player);
        boolean changed = !state.evaluated || state.active != active || state.health != player.getHealth()
                || state.maxHealth != player.getMaxHealth() || state.san != san
                || state.infection != player_variables.infection(player).map(data -> data.units()).orElse(0);
        // 从上刻已有的事件计时开始扣减；本刻新发作拥有完整持续时间。
        if (state.poolCooldown > 0) state.poolCooldown--;
        boolean blindnessEnded = state.remaining("infection_blindness") == 1;
        var iterator = state.remaining.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            if (entry.getValue() == -1) continue;
            if (entry.getValue() <= 1) { iterator.remove(); state.dirty = true; }
            else entry.setValue(entry.getValue() - 1);
        }
        if (!state.running(SEEPAGE)) resetSeepageProgress(state);
        else seepageTick(player, state);
        delayedTick(player, state);
        mentalTick(player, state);
        if (blindnessEnded) start(player, "brief_lucidity", 600);
        if (changed || state.dirty || state.lastEvaluation == Long.MIN_VALUE || clock - state.lastEvaluation >= 10)
        {
            reevaluate(player);
            state.lastEvaluation = clock;
        }
        if (has(player, "perception_disorder"))
        {
            if (++state.perceptionTicks >= 1200) { state.perceptionTicks = 0; blind(player); }
        }
        else state.perceptionTicks = 0;
    }

    /** 条件改变立即重评；不会推进任何时钟，也不会抽取随机池。 */
    public static void reevaluate(Player player)
    {
        if (!connected(player) || player.level().isClientSide || player_variables.infection(player).isEmpty()) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        if (!active(player))
        {
            for (String name : allIds()) removeDisplay(player, name);
            modifiers(player);
            remember(player, state, false);
            return;
        }
        var health = infection_service.health(player);
        double infection = infection_service.query(player);
        double san = san(player);
        double h = health.ratio();
        boolean valid = health.valid();
        conditional(player, "healthy_condition", valid && !state.healthLossPending && player.getMaxHealth() - player.getHealth()
                <= Math.max(1.0E-5D, player.getMaxHealth() * 1.0E-6D));
        conditional(player, "rapid_breathing", valid && infection >= 80 && h <= 0.7);
        conditional(player, "infection_anxiety", infection >= 80 && san < 5000);
        boolean perception = infection >= 150 && san < 1000;
        conditional(player, "perception_disorder", perception);
        // 精神涣散：条件满足时发放/保留 0 级；不满足时若等级>0 则继续显示无限时长，靠计时器降级。
        boolean mental = infection >= 200 && san < 5000;
        if (mental && !has(player, MENTAL))
        {
            state.mentalDistractionLevel = 0;
            state.mentalDistractionDecay = 0;
            state.mentalDistractionRestart = 0;
        }
        int mentalLevel = Math.max(0, Math.min(MENTAL_MAX_LEVEL, state.mentalDistractionLevel));
        if (!mental && mentalLevel <= 0) removeDisplay(player, MENTAL);
        else if (!display(player, MENTAL, -1, mentalLevel)) state.mentalDistractionLevel = 0;
        if (infection < 30) stop(player, "wound_seepage");
        if (infection < 80)
        {
            List<ResourceLocation> randomIds;
            synchronized (infection_symptom_rules.class) { randomIds = List.copyOf(POOL.keySet()); }
            for (var key : randomIds) stop(player, key.getPath());
        }
        if (!perception)
        {
            state.perceptionTicks = 0;
            if (state.running("infection_blindness"))
            {
                stop(player, "infection_blindness");
                start(player, "brief_lucidity", 600);
            }
        }
        for (String name : timedIds())
        {
            if (state.running(name))
            {
                int amplifier = SEEPAGE.equals(name) ? seepageLevel(state.woundSeepageLevel)
                        : DELAYED.equals(name) ? Math.max(0, Math.min(DELAYED_MAX_LEVEL, state.delayedHealingLevel))
                        : 0;
                if (!display(player, name, state.remaining(name), amplifier)) state.set(name, 0);
            }
            else removeDisplay(player, name);
        }
        modifiers(player);
        remember(player, state, true);
    }

    private static double san(Player player)
    {
        var attribute = attributes.SAN.isPresent() ? player.getAttribute(attributes.SAN.get()) : null;
        return attribute == null ? 10000 : attribute.getValue();
    }
    private static void remember(Player player, infection_symptom_state state, boolean active)
    {
        state.active = active;
        state.health = player.getHealth();
        state.maxHealth = player.getMaxHealth();
        state.san = san(player);
        state.infection = player_variables.infection(player).map(data -> data.units()).orElse(0);
        state.evaluated = true;
        state.dirty = false;
        state.lastEvaluation = state.lastTick;
    }

    private static void conditional(Player player, String name, boolean enabled)
    {
        if (enabled) display(player, name, -1);
        else removeDisplay(player, name);
    }

    private static boolean display(Player player, String name, int ticks)
    {
        return display(player, name, ticks, 0);
    }

    private static boolean display(Player player, String name, int ticks, int amplifier)
    {
        MobEffect effect = effect(name);
        if (effect == null) return false;
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() == amplifier && (existing.getDuration() == ticks
                || (existing.getDuration() >= 0 && ticks >= 0 && Math.abs(existing.getDuration() - ticks) <= 2))) return true;
        infection_symptom_state state = state(player);
        if (state == null) return false;
        state.changingEffects = true;
        try
        {
            // 替换以允许缩短时长；不留下隐藏的高等级/无限效果。
            player.removeEffect(effect);
            return player.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, true));
        }
        finally { state.changingEffects = false; }
    }

    private static void removeDisplay(Player player, String name)
    {
        if (!has(player, name)) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        state.changingEffects = true;
        try { player.removeEffect(effect(name)); }
        finally { state.changingEffects = false; }
    }

    private static void stop(Player player, String name)
    { var state = state(player); if (state != null) state.set(name, 0); removeDisplay(player, name); }

    /** /effect clear 只清除当前实例；不会凭空触发清明或清除外部失明。 */
    public static void removed(Player player, MobEffect effect)
    {
        if (player.level().isClientSide || player_variables.infection(player).isEmpty()) return;
        var state = state(player);
        if (state == null || state.changingEffects) return;
        state.dirty = true;
        ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (key != null && key.getNamespace().equals("white_lily") && timedIds().contains(key.getPath()))
        {
            state.set(key.getPath(), 0);
            if (SEEPAGE.equals(key.getPath())) resetSeepageProgress(state);
            if (DELAYED.equals(key.getPath())) state.delayedHealingLevel = 0;
        }
        else if (key != null && key.getNamespace().equals("white_lily") && MENTAL.equals(key.getPath()))
        {
            // 外部（/effect clear 等）移除时，等级与计时一并归零。
            state.mentalDistractionLevel = 0;
            state.mentalDistractionDecay = 0;
            state.mentalDistractionRestart = 0;
        }
    }

    public static double movementMultiplier(Player player, boolean swimming, boolean sprinting)
    {
        if ((!swimming && !sprinting) || !active(player)) return 1;
        return 1 + strength(player) * ((has(player, "rapid_breathing") ? 0.25 : 0)
                + (has(player, "recovery_fatigue") ? 0.10 : 0));
    }

    private static void modifier(Player player, String name, Attribute attribute, double amount)
    {
        modifier(player, name, attribute, amount, AttributeModifier.Operation.ADDITION);
    }

    private static void modifier(Player player, String name, Attribute attribute, double amount,
                                 AttributeModifier.Operation operation)
    {
        if (amount == 0) infection_modifiers.remove(player, id(name), attribute);
        else infection_modifiers.put(player, id(name), attribute, amount, operation);
    }

    /** 渗血的累计进度归零（等级、扣理智计时、移动距离）。 */
    private static void resetSeepageProgress(infection_symptom_state state)
    {
        state.woundSeepageLevel = 0;
        state.seepageSanTicks = 0;
        state.seepageBlocks = 0;
        state.seepagePositionValid = false;
    }

    /**
     * 渗血每刻结算：扣理智、疾走/移动出血。被抑制时整层不生效。
     *
     * <p>这些效果互不叠加：只按当前等级那一层执行（等级 2 不会把等级 1 的也算进来）。</p>
     */
    private static void seepageTick(Player player, infection_symptom_state state)
    {
        int level = seepageEffectiveLevel(player);
        if (level < 0)
        {
            resetSeepageAccumulators(state);
            return;
        }
        // 理智：每级有固定周期，意志坚定达到该级门槛则完全不扣。
        int period = seepageSanPeriod(level);
        if (period <= 0 || willpower(player) >= seepageSanWillpower(level))
        {
            state.seepageSanTicks = 0;
        }
        else if (++state.seepageSanTicks >= period)
        {
            state.seepageSanTicks = 0;
            san_behaviour_pool.addSan(player, -seepageSanAmount(level));
        }
        // 出血：按水平位移累计，到阈值就结算一段并保留余数。
        double blocks = level == SEEPAGE_MAX_LEVEL ? SEEPAGE_BLEED_BLOCKS_FAST
                : level == 3 ? SEEPAGE_BLEED_BLOCKS_SLOW : 0;
        if (blocks <= 0 || (level == 3 && !player.isSprinting()))
        {
            state.seepageBlocks = 0;
            rememberSeepagePosition(player, state);
            return;
        }
        if (!state.seepagePositionValid)
        {
            rememberSeepagePosition(player, state);
            return;
        }
        double dx = player.getX() - state.seepageLastX;
        double dy = player.getY() - state.seepageLastY;
        double dz = player.getZ() - state.seepageLastZ;
        double moved = Math.sqrt(dx * dx + dz * dz);
        // 瞬移/换维度保护：单刻超过 32 格不计。
        if (Double.isFinite(moved) && moved < 32)
        {
            state.seepageBlocks += moved;
            while (state.seepageBlocks >= blocks)
            {
                state.seepageBlocks -= blocks;
                float damage = level == SEEPAGE_MAX_LEVEL
                        ? (float) (player.getMaxHealth() * 0.20D + 2.0D)
                        : SEEPAGE_BLEED_SLOW;
                hurtBySeepage(player, damage);
            }
        }
        rememberSeepagePosition(player, state);
    }

    private static void rememberSeepagePosition(Player player, infection_symptom_state state)
    {
        state.seepageLastX = player.getX();
        state.seepageLastY = player.getY();
        state.seepageLastZ = player.getZ();
        state.seepagePositionValid = true;
    }

    private static void resetSeepageAccumulators(infection_symptom_state state)
    {
        state.seepageSanTicks = 0;
        state.seepageBlocks = 0;
        state.seepagePositionValid = false;
    }

    private static void hurtBySeepage(Player player, float amount)
    {
        if (!Float.isFinite(amount) || amount <= 0) return;
        if (player.level().isClientSide || !player.isAlive()) return;
        player.hurt(excessive_hemorrhage.source(player.level()), amount);
    }

    public static void modifiers(Player player)
    {
        double scale = strength(player);
        modifier(player, "healthy_condition", attributes.STAMINA.get(), has(player, "healthy_condition") ? 1 : 0);
        modifier(player, "healthy_condition", attributes.INFECTION_RESISTANCE.get(), has(player, "healthy_condition") ? 0.10 : 0);
        // 不再给恢复间隔修正（旧的 -20 已移除）。
        // 渗血：只吃当前等级那一层，被抑制则一点不加。
        int seepage = seepageEffectiveLevel(player);
        double interval = seepage >= 0 && seepage <= 2 ? -30 : 0;
        modifier(player, "wound_seepage", attributes.RECOVERY_INTERVAL.get(), interval);
        modifier(player, "wound_seepage", attributes.RESTORE_HEALTH.get(),
                seepage < 0 ? 0 : seepageRestorePenalty(seepage));
        double stamina = stamina(player);
        double maxHealth = seepage == 3 ? (stamina >= 3 ? -0.2 : -0.4)
                : seepage == 4 ? (stamina >= 3 ? -0.4 : -0.6) : 0;
        modifier(player, "wound_seepage", Attributes.MAX_HEALTH, maxHealth,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(player, "wound_seepage", Attributes.MOVEMENT_SPEED,
                seepage == 4 ? -0.3 : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(player, "infection_anxiety", attributes.RECOVERY_INTERVAL.get(), has(player, "infection_anxiety") ? -10 * scale : 0);
        modifier(player, "shivering", attributes.RIFLE_EXPERTISE.get(), has(player, "shivering") ? -0.05 * scale : 0);
        modifier(player, "shivering", attributes.EXPERT_IN_ARCHERY.get(), has(player, "shivering") ? -0.5 * scale : 0);
        modifier(player, "unstable_spellcasting", attributes.SPELL_ENHANCEMENT.get(), has(player, "unstable_spellcasting") ? -0.5 * scale : 0);
    }

    private infection_symptom_rules() { }
}
