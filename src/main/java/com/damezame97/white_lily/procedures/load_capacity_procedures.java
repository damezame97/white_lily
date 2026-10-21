package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.debugging.debug_file;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilycurio.improvement_loadout;
import com.damezame97.white_lily.white_lilydamage.load_burden;
import com.damezame97.white_lily.white_lilydamage.remit_loadcapacity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「载重值」（{@code load_capacity}）的默认值与全部机制。
 *
 * <h2>一、默认值按世界档位给</h2>
 * <p>与世界级调试文件 {@code Debugging_whitelilymod.json} 的 {@code difficulty_level} 挂钩
 * （与「改良」饰品栏同一套档位）：</p>
 * <table border="1">
 *     <caption>档位 → 载重值默认值</caption>
 *     <tr><th>档位</th><th>难度</th><th>载重值</th></tr>
 *     <tr><td>1</td><td>和平 PEACEFUL</td><td>150</td></tr>
 *     <tr><td>2</td><td>简单 EASY</td><td>140</td></tr>
 *     <tr><td>3</td><td>普通 NORMAL</td><td>120</td></tr>
 *     <tr><td>4</td><td>困难 HARD</td><td>100</td></tr>
 * </table>
 * <p>属性注册是全局的、拿不到世界信息，所以真正的「默认值」落在<b>玩家每次登录</b>时写
 * {@code setBaseValue}（与 {@code improvement} 每次进世界重设同一思路）。档位一律读
 * {@link debug_file#loadOrCreateLevel}，也就是<b>以 json 里的参数为准</b>，不是当前难度。</p>
 *
 * <h2>二、机制（属性低于 100 才生效）</h2>
 * <p>设 {@code v} = 载重值，{@code n = clamp(floor(100 − v), 0, 10)}（「低于 100 的整数个数」）：</p>
 * <ol>
 *     <li><b>受到的最终伤害</b> × {@code (1 + 0.20 × n)}：n=10（v ≤ 90）时 +200%。</li>
 *     <li><b>造成的最终伤害</b> × {@code (1 + 0.10 × n)}：n=10 时 +100%。</li>
 *     <li><b>载重反噬</b>：v &lt; 90 时，玩家每次受到伤害都会额外挨一次
 *         {@code white_lily:load_burden} 伤害，数值 = 生命上限 × {@code m × 100%}，
 *         其中 {@code m = clamp(floor(90 − v), 0, 50)}（即最高 5000%）。
 *         例：v=89 → 100%；v=1 → 8900% 但被夹到 5000%。</li>
 * </ol>
 * <p>两项加成与反噬的百分比彼此独立：v ≤ 90 之后，1/2 两项都已经到顶（+200% / +100%），
 * 再往下只增加反噬的百分比。</p>
 *
 * <h2>三、防递归 / 防溢出</h2>
 * <ul>
 *     <li><b>5 游戏刻冷却</b>：同一个玩家两次反噬之间至少隔 5 刻。</li>
 *     <li><b>反噬伤害不再触发反噬</b>：伤害类型是 {@code white_lily:load_burden} 时，整个处理器直接返回
 *         （既不套加成，也不再追加反噬）。</li>
 *     <li><b>硬上限</b>：反噬伤害最终被夹到 {@link #MAX_LOAD_BURDEN_DAMAGE}（1e9），
 *         避免生命上限过大时把 float 撑成 Infinity/NaN 反而归零。</li>
 * </ul>
 *
 * <h2>四、挂在哪</h2>
 * <p>{@link LivingDamageEvent}（Forge 的「最终伤害」阶段，在护甲/附魔/伤害吸收结算之后），
 * 用 {@link EventPriority#LOWEST} 保证是最后一个改数值的人。</p>
 * <p>反噬伤害在这里就地 {@code player.hurt(...)} 嵌套结算：这个伤害类型带着
 * {@code bypasses_cooldown}，不会被无敌帧吃掉；而外层的 {@code actuallyHurt} 在事件之后才
 * {@code setHealth(getHealth() - 伤害)}，读的是「已经被反噬扣过」的血量，所以两份伤害会正确叠加。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class load_capacity_procedures {

    // ==================================================================
    // 一、默认值
    // ==================================================================

    /** 档位 1（和平）的默认载重值。 */
    public static final int CAPACITY_PEACEFUL = 150;

    /** 档位 2（简单）的默认载重值。 */
    public static final int CAPACITY_EASY = 140;

    /** 档位 3（普通）的默认载重值。 */
    public static final int CAPACITY_NORMAL = 120;

    /** 档位 4（困难）的默认载重值。 */
    public static final int CAPACITY_HARD = 100;

    // ==================================================================
    // 二、机制参数
    // ==================================================================

    /** 「低于 100 的整数个数」的上限：v ≤ 90 之后不再增加。 */
    public static final int STEPS_CAP = 10;

    /** 每 1 个「低于 100 的整数」→ 受到的最终伤害 +20%。 */
    public static final double RECEIVED_PER_STEP = 0.20D;

    /** 每 1 个「低于 100 的整数」→ 造成的最终伤害 +10%。 */
    public static final double DEALT_PER_STEP = 0.10D;

    /** 反噬的起算线：载重值低于它才开始挨反噬伤害。 */
    public static final double BURDEN_START_BELOW = 90.0D;

    /** 每 1 个「低于 90 的整数」→ 反噬伤害 = 生命上限 × 100%。 */
    public static final double BURDEN_FRACTION_PER_STEP = 1.0D;

    /** 反噬百分比的上限：5000%。 */
    public static final double MAX_BURDEN_FRACTION = 50.0D;

    /** 反噬的内置冷却：5 游戏刻。 */
    public static final int BURDEN_COOLDOWN_TICKS = 5;

    /**
     * 反噬伤害的硬上限。
     *
     * <p>1e9：远高于任何正常生命上限能撑出来的数值，又远低于 float 的溢出线
     * （约 3.4e38），不会被算成 Infinity/NaN。</p>
     */
    public static final float MAX_LOAD_BURDEN_DAMAGE = 1.0E9F;

    /** 每个玩家上一次触发反噬的游戏刻（弱引用键，玩家离线后自动回收）。 */
    private static final Map<Player, Long> LAST_BURDEN_TICK =
            Collections.synchronizedMap(new WeakHashMap<>());

    private load_capacity_procedures() {
    }

    // ==================================================================
    // 三、默认值：读调试文件 → 写进玩家属性
    // ==================================================================

    /** 档位 → 默认载重值（和平 150 / 简单 140 / 普通 120 / 困难 100）。 */
    public static int loadCapacityForLevel(int level) {
        return switch (improvement_loadout.clampLevel(level)) {
            case 1 -> CAPACITY_PEACEFUL;
            case 2 -> CAPACITY_EASY;
            case 3 -> CAPACITY_NORMAL;
            default -> CAPACITY_HARD;
        };
    }

    /** 世界难度 → 默认载重值（内部换算成档位）。 */
    public static int loadCapacityForDifficulty(net.minecraft.world.Difficulty difficulty) {
        return loadCapacityForLevel(improvement_loadout.levelForDifficulty(difficulty));
    }

    /**
     * 玩家每次登录时调用：按世界调试文件的档位把载重值的基础值重设成对应默认值。
     *
     * @return 本次用的档位（1~4）
     */
    public static int syncDefaultFromWorld(Player player) {
        MinecraftServer server = player.getServer();
        int level = server != null
                ? debug_file.loadOrCreateLevel(server)
                : improvement_loadout.levelForDifficulty(player.level().getLevelData().getDifficulty());

        applyDefaultLoadCapacity(player, level);
        return level;
    }

    /** 把载重值的基础值设成该档位的默认值（不动修饰符）。 */
    public static void applyDefaultLoadCapacity(Player player, int level) {
        AttributeInstance instance = player.getAttribute(attributes.LOAD_CAPACITY.get());
        if (instance == null) {
            return;
        }
        instance.setBaseValue(loadCapacityForLevel(level));
    }

    /** 玩家登录：按世界档位重设载重值默认值。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncDefaultFromWorld(player);
    }

    // ==================================================================
    // 四、机制：最终伤害加成 + 载重反噬
    // ==================================================================

    /**
     * 在「最终伤害」阶段套用载重值的两条加成，并按需追加一次载重反噬伤害。
     *
     * <p>整段对 {@code white_lily:load_burden} 伤害直接放行（不套加成、不再追加反噬），
     * 这是防递归的关键一条。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(remit_loadcapacity.TAG)) {
            // 载重反噬、精神损伤这类「系统追罚」不再套加成、也不再追一次（防递归）。
            return;
        }

        // 1) 造成伤害的一方：载重值低 → 自己造成的最终伤害提高。
        Entity attacker = source.getEntity();
        if (attacker instanceof Player attackerPlayer) {
            int steps = stepsBelowHundred(attributeValue(attackerPlayer, attributes.LOAD_CAPACITY.get()));
            if (steps > 0) {
                event.setAmount((float) (event.getAmount() * (1.0D + DEALT_PER_STEP * steps)));
            }
        }

        // 2) / 3) 受到伤害的一方：受到的最终伤害提高，并在 v < 90 时追加反噬。
        if (event.getEntity() instanceof Player victim) {
            double value = attributeValue(victim, attributes.LOAD_CAPACITY.get());
            int steps = stepsBelowHundred(value);
            if (steps > 0) {
                event.setAmount((float) (event.getAmount() * (1.0D + RECEIVED_PER_STEP * steps)));
            }
            tryApplyLoadBurden(victim);
        }
    }

    /**
     * 给玩家追加一次载重反噬伤害（带冷却与硬上限）。
     *
     * @return 本次是否真的造成了反噬
     */
    public static boolean tryApplyLoadBurden(Player player) {
        double value = attributeValue(player, attributes.LOAD_CAPACITY.get());
        double fraction = burdenFraction(value);
        if (fraction <= 0.0D) {
            return false;
        }
        if (isOnBurdenCooldown(player)) {
            return false;
        }

        float damage = (float) burdenDamage(player.getMaxHealth(), value);
        if (damage <= 0.0F) {
            return false;
        }

        // 先记冷却再打伤害：即使伤害本身又触发了事件，也不会二次追加。
        markBurdenTriggered(player);
        player.hurt(load_burden.source(player.level()), damage);
        return true;
    }

    /** 这条伤害源是不是载重反噬。 */
    public static boolean isLoadBurden(DamageSource source) {
        return source != null && source.is(load_burden.KEY);
    }

    /** 该玩家是否还在反噬冷却里。 */
    public static boolean isOnBurdenCooldown(Player player) {
        Long last = LAST_BURDEN_TICK.get(player);
        if (last == null) {
            return false;
        }
        return player.level().getGameTime() - last < BURDEN_COOLDOWN_TICKS;
    }

    /** 清掉冷却（GameTest 用来模拟「冷却已过」）。 */
    public static void clearBurdenCooldown(Player player) {
        LAST_BURDEN_TICK.remove(player);
    }

    private static void markBurdenTriggered(Player player) {
        LAST_BURDEN_TICK.put(player, player.level().getGameTime());
    }

    // ==================================================================
    // 五、纯计算（公开给 GameTest 与后续复用）
    // ==================================================================

    /**
     * 「低于 100 的整数个数」，夹在 [0, {@link #STEPS_CAP}]。
     *
     * <p>例：100 → 0；94 → 6；90 → 10；89.9 → 10；1 → 10。</p>
     */
    public static int stepsBelowHundred(double loadCapacity) {
        double raw = Math.floor(100.0D - loadCapacity);
        return (int) Math.max(0.0D, Math.min(STEPS_CAP, raw));
    }

    /** 受到的最终伤害倍率：{@code 1 + 0.20 × n}。 */
    public static double receivedMultiplier(double loadCapacity) {
        return 1.0D + RECEIVED_PER_STEP * stepsBelowHundred(loadCapacity);
    }

    /** 造成的最终伤害倍率：{@code 1 + 0.10 × n}。 */
    public static double dealtMultiplier(double loadCapacity) {
        return 1.0D + DEALT_PER_STEP * stepsBelowHundred(loadCapacity);
    }

    /**
     * 反噬百分比（1.0 = 100%）。
     *
     * <p>载重值 ≥ 90 时是 0；之后每低 1 个整数 +100%，最高 {@link #MAX_BURDEN_FRACTION}（5000%）。</p>
     */
    public static double burdenFraction(double loadCapacity) {
        if (loadCapacity >= BURDEN_START_BELOW) {
            return 0.0D;
        }
        double steps = Math.floor(BURDEN_START_BELOW - loadCapacity);
        double raw = steps * BURDEN_FRACTION_PER_STEP;
        return Math.max(0.0D, Math.min(MAX_BURDEN_FRACTION, raw));
    }

    /** 反噬伤害量 = 生命上限 × 反噬百分比，夹到 {@link #MAX_LOAD_BURDEN_DAMAGE}。 */
    public static double burdenDamage(double maxHealth, double loadCapacity) {
        double raw = maxHealth * burdenFraction(loadCapacity);
        return Math.max(0.0D, Math.min(MAX_LOAD_BURDEN_DAMAGE, raw));
    }

    /** 取实体身上某条属性的值；实体没挂这条属性时返回 0。 */
    private static double attributeValue(LivingEntity entity, Attribute attribute) {
        if (entity.getAttribute(attribute) == null) {
            return 0.0D;
        }
        return entity.getAttributeValue(attribute);
    }
}
