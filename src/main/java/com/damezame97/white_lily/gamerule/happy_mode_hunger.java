package com.damezame97.white_lily.gamerule;

/**
 * {@code Happy_mode} 开启时那套「消耗计数器」的数值规则。
 *
 * <h2>它替换了原版的什么</h2>
 * <p>原版 {@code FoodData} 里有一个「疲劳值」累加器：跑、跳、挖、攻击、受伤都往里加，
 * 攒过 4 就扣 1 点饱和度（没饱和度扣 1 点饥饿值）。开启 {@code Happy_mode} 后，
 * 那个累加器被本模组<b>弃用</b>，换成这里的「消耗计数器」：</p>
 * <ul>
 *     <li>上限默认 {@value #DEFAULT_COUNTER_MAX}，受 {@code stamina} 属性影响
 *         （每 +1 上限 +{@value #STAMINA_BONUS_PER_POINT}，每 −1 上限
 *         −{@value #STAMINA_PENALTY_PER_POINT}，但不会低于 {@value #MIN_COUNTER_MAX}）；</li>
 *     <li>玩家做「消耗饥饿度的行为」时，计数器<b>往下减</b>；</li>
 *     <li>减到 0 时扣<b>固定 1 点饱和度</b>（没有饱和度就扣 1 点饥饿值），随后计数器回满。</li>
 * </ul>
 * <p>真正的「谁在什么时候扣多少」在 {@code com.damezame97.white_lily.gamerule.hunger_activity}
 * 里，本类只放<b>纯数值</b>，方便单独核对（GameTest 也直接测它）。</p>
 */
public final class happy_mode_hunger
{
    // ==================================================================
    // 计数器本身
    // ==================================================================

    /** 计数器上限的默认值。 */
    public static final int DEFAULT_COUNTER_MAX = 40;

    /** 计数器下限：到 0 就结算并回满。 */
    public static final int COUNTER_MIN = 0;

    /** 每 +1 点 {@code stamina} 给计数器上限的加成。 */
    public static final int STAMINA_BONUS_PER_POINT = 30;

    /** 每 −1 点 {@code stamina} 给计数器上限的削减。 */
    public static final int STAMINA_PENALTY_PER_POINT = 10;

    /** 不管 {@code stamina} 多低，上限都不会低于这个数。 */
    public static final int MIN_COUNTER_MAX = 10;

    // ==================================================================
    // 各种行为消耗多少
    // ==================================================================

    /** 游泳：每米 −3。 */
    public static final float SWIM_COST_PER_METER = 3.0F;

    /** 疾跑：每米 −1。 */
    public static final float SPRINT_COST_PER_METER = 1.0F;

    /** 普通移动：每 4 米 −0.5（也就是每米 −0.125）。 */
    public static final float WALK_COST_PER_4_METERS = 0.5F;

    /** 普通移动：每米消耗。 */
    public static final float WALK_COST_PER_METER = WALK_COST_PER_4_METERS / 4.0F;

    /** 摧毁方块：每个 −5。 */
    public static final float BREAK_BLOCK_COST = 5.0F;

    /** 跳跃：每次 −2。 */
    public static final float JUMP_COST = 2.0F;

    /** 疾跑时跳跃：每次 −4（<b>替代</b>普通跳跃的 2 点，不叠加）。 */
    public static final float SPRINT_JUMP_COST = 4.0F;

    /** 攻击：每一击 −8。 */
    public static final float ATTACK_COST = 8.0F;

    /** 受到「会被盔甲值影响」的伤害：每次 −4。 */
    public static final float ARMORED_DAMAGE_COST = 4.0F;

    /** 原版饥饿效果：每等级每秒 −2。 */
    public static final float HUNGER_EFFECT_COST_PER_LEVEL_PER_SECOND = 2.0F;

    /** 原版饱和效果：每等级每秒 +4（这是<b>加</b>，不是减）。 */
    public static final float SATURATION_EFFECT_GAIN_PER_LEVEL_PER_SECOND = 4.0F;

    // ==================================================================
    // 饥饿值归零之后的状态
    // ==================================================================

    /** 每 2 秒掉 1 点 {@code san}。 */
    public static final int SPIRIT_LOSS_INTERVAL_SECONDS = 2;

    /** 每 12 秒按最大生命值的一定比例扣血。 */
    public static final int HEALTH_DRAIN_INTERVAL_SECONDS = 12;

    /** 那次扣血是「最大生命值的 20%」。 */
    public static final float HEALTH_DRAIN_FRACTION_OF_MAX = 0.2F;

    /** 移动速度 −60%（乘算）。 */
    public static final double STARVING_SPEED_MODIFIER = -0.6D;

    /** 挖掘速度 ×0.2（也就是 −80%）。 */
    public static final float STARVING_BREAK_SPEED_MULTIPLIER = 0.2F;

    /** 台阶高度 −0.3（加算）。 */
    public static final double STARVING_STEP_HEIGHT_ADDITION = -0.3D;

    /** 游泳速度 −70%（乘算）。 */
    public static final double STARVING_SWIM_SPEED_MODIFIER = -0.7D;

    private static final int TICKS_PER_SECOND = 20;

    /** 计数器上限：默认 {@value #DEFAULT_COUNTER_MAX}，按 {@code stamina} 增减，不低于 {@value #MIN_COUNTER_MAX}。 */
    public static int counterMax(double stamina)
    {
        double delta = stamina >= 0.0D
                ? stamina * STAMINA_BONUS_PER_POINT
                : stamina * STAMINA_PENALTY_PER_POINT;
        return Math.max(MIN_COUNTER_MAX, (int) Math.round(DEFAULT_COUNTER_MAX + delta));
    }

    /** 跳一次扣多少：疾跑中的跳跃是 {@value #SPRINT_JUMP_COST}，否则 {@value #JUMP_COST}。 */
    public static float jumpCost(boolean sprinting)
    {
        return sprinting ? SPRINT_JUMP_COST : JUMP_COST;
    }

    /**
     * 移动每米的消耗。
     *
     * <p>游泳、疾跑、普通移动<b>按状态取其一</b>（游泳优先，其次疾跑，最后才是普通移动），
     * 不叠加：需求把它们列成三条并列的行为。</p>
     */
    public static float movementCostPerMeter(boolean swimming, boolean sprinting)
    {
        if (swimming)
        {
            return SWIM_COST_PER_METER;
        }
        if (sprinting)
        {
            return SPRINT_COST_PER_METER;
        }
        return WALK_COST_PER_METER;
    }

    /** 原版饥饿效果每刻扣多少（等级 = 放大器 + 1）。 */
    public static float hungerEffectCostPerTick(int amplifier)
    {
        return HUNGER_EFFECT_COST_PER_LEVEL_PER_SECOND * (amplifier + 1) / TICKS_PER_SECOND;
    }

    /** 原版饱和效果每刻回多少（等级 = 放大器 + 1）。 */
    public static float saturationEffectGainPerTick(int amplifier)
    {
        return SATURATION_EFFECT_GAIN_PER_LEVEL_PER_SECOND * (amplifier + 1) / TICKS_PER_SECOND;
    }

    /** {@code san} 每 2 秒掉 1 点，换算成刻。 */
    public static int spiritLossIntervalTicks()
    {
        return SPIRIT_LOSS_INTERVAL_SECONDS * TICKS_PER_SECOND;
    }

    /** 每 12 秒那次扣血，换算成刻。 */
    public static int healthDrainIntervalTicks()
    {
        return HEALTH_DRAIN_INTERVAL_SECONDS * TICKS_PER_SECOND;
    }

    /** 每 12 秒那次该扣多少血：最大生命值的 {@value #HEALTH_DRAIN_FRACTION_OF_MAX}。 */
    public static float healthDrainAmount(float maxHealth)
    {
        return maxHealth * HEALTH_DRAIN_FRACTION_OF_MAX;
    }

    private happy_mode_hunger()
    {
    }
}
