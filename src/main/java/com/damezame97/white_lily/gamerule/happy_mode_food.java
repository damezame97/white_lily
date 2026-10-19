package com.damezame97.white_lily.gamerule;

/**
 * 游戏规则 {@code Happy_mode} 开启时那套「按规则回复生命值」的数值规则。
 *
 * <h2>这套规则在原版里的位置</h2>
 * <p>原版是靠 {@code FoodData#tick(Player)} 里的自然恢复分支（饥饿值满 + 有饱和度，
 * 或饥饿值 ≥ 18）回血的。开启 {@code Happy_mode} 后，那条分支被整体替换成这里的规则
 * ——真正的替换动作在 {@code com.damezame97.white_lily.mixin.MixinFoodData} 里，
 * 本类只放<b>纯数值规则</b>，方便单独核对（GameTest 也直接测它）。</p>
 *
 * <h2>规则</h2>
 * <ol>
 *     <li><b>饥饿值满(20) 且有饱和度</b>：间隔 = {@value #BASE_INTERVAL_SECONDS} 秒
 *         − {@code Recovery_interval}（每 +1 少 1 秒、每 −1 多 1 秒），下限
 *         {@value #MIN_INTERVAL_SECONDS} 秒；每次恢复 {@code Restore_health} 点生命值
 *         （默认 {@value #DEFAULT_RESTORE_HEALTH}，可以是负数 → 掉血）。</li>
 *     <li><b>饥饿值不满但有饱和度</b>：饥饿值低于 {@value #LOW_FOOD_CUTOFF} 就不再恢复；
 *         否则在间隔上再叠加 {@value #NOT_FULL_HUNGER_INTERVAL_OFFSET}（＝把
 *         {@code Recovery_interval} 这个数 −120 之后再算），其余同上。</li>
 *     <li><b>完全没有饱和度、只剩饥饿值</b>：把饥饿值当成饱和度用 —— 固定
 *         {@value #BASE_INTERVAL_SECONDS} 秒一次、每次固定掉 1 点饥饿值、固定回
 *         {@value #DEFAULT_RESTORE_HEALTH} 点生命值，<b>不受</b>
 *         {@code Recovery_interval} / {@code Restore_health} / {@code stamina} 影响；
 *         饥饿值低于 {@value #LOW_FOOD_CUTOFF} 后停止。</li>
 *     <li><b>消耗饱和度</b>：默认每次 1 点。{@code stamina} ≥ 0 时改成「每
 *         {@code floor(stamina)+1} 次才扣 1 点」，{@code stamina} &lt; 0 时改成
 *         「每次扣 {@code 1 + floor(-stamina)} 点」（见 {@link #saturationCost}）。</li>
 * </ol>
 * <p>「满血时不跑」：只要玩家没掉血（{@code !player.isHurt()}），计时器就归零、
 * 什么都不扣，和原版自然恢复的判定一致。</p>
 */
public final class happy_mode_food
{
    /** 基础恢复间隔（秒）。 */
    public static final int BASE_INTERVAL_SECONDS = 180;

    /** 基础恢复间隔（刻）。 */
    public static final int BASE_INTERVAL_TICKS = BASE_INTERVAL_SECONDS * 20;

    /** 饥饿值低于这个数就不再拿饱和度/饥饿值换生命值（「低于 6 点」）。 */
    public static final int LOW_FOOD_CUTOFF = 6;

    /**
     * 饥饿值不满时的间隔修正量。
     *
     * <p>需求原话是「使 Recovery_interval 减少 120」，所以这里是加在
     * <b>Recovery_interval 这个数上</b>的：等价于间隔 {@code +120} 秒，即
     * {@code 180 - (Recovery_interval - 120)} 秒。</p>
     */
    public static final double NOT_FULL_HUNGER_INTERVAL_OFFSET = -120.0D;

    /** 间隔下限（秒）：怎么减都不会比它更短。 */
    public static final int MIN_INTERVAL_SECONDS = 5;

    /** 每次恢复的默认生命值（也是「只剩饥饿值」那条的固定值）。 */
    public static final double DEFAULT_RESTORE_HEALTH = 1.0D;

    private static final int TICKS_PER_SECOND = 20;

    /**
     * 恢复间隔（秒）。
     *
     * @param recoveryInterval {@code Recovery_interval} 属性的当前值
     * @param hungerNotFull    饥饿值是否不满（不满时会先叠上
     *                         {@link #NOT_FULL_HUNGER_INTERVAL_OFFSET}）
     */
    public static int intervalSeconds(double recoveryInterval, boolean hungerNotFull)
    {
        double effective = hungerNotFull
                ? recoveryInterval + NOT_FULL_HUNGER_INTERVAL_OFFSET
                : recoveryInterval;
        int seconds = BASE_INTERVAL_SECONDS - (int) Math.round(effective);
        return Math.max(seconds, MIN_INTERVAL_SECONDS);
    }

    /** 同 {@link #intervalSeconds}，换算成刻。 */
    public static int intervalTicks(double recoveryInterval, boolean hungerNotFull)
    {
        return intervalSeconds(recoveryInterval, hungerNotFull) * TICKS_PER_SECOND;
    }

    /**
     * 这一次恢复该扣多少饱和度。
     *
     * @param stamina              {@code stamina} 属性的当前值
     * @param healsSinceLastCost   这是「距离上次扣饱和度之后的第几次恢复」，从 1 开始数
     * @return 该扣的饱和度点数（0 表示这次不扣）
     */
    public static float saturationCost(double stamina, int healsSinceLastCost)
    {
        if (stamina < 0.0D)
        {
            // stamina = -1 → 每次扣 2 点；-2 → 每次扣 3 点；以此类推
            return 1.0F + (float) Math.floor(-stamina);
        }

        // stamina = 0 → 每次都扣；1 → 每 2 次扣 1 点；2 → 每 3 次扣 1 点；以此类推
        int everyHeals = (int) Math.floor(stamina) + 1;
        return healsSinceLastCost >= everyHeals ? 1.0F : 0.0F;
    }

    private happy_mode_food()
    {
    }
}
