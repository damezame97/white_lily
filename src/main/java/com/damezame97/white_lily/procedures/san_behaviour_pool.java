package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 「理智值行为池」—— 消耗 / 增加 {@code white_lily.san} 的行为登记处。
 *
 * <h2>现在是空的</h2>
 * <p>按需求，这一轮只把池子搭起来，<b>一条行为都不登记</b>。以后要加行为，
 * 实现 {@link Behaviour} 然后 {@link #register} 即可，不必改本类其它地方。</p>
 *
 * <h2>一个行为长什么样</h2>
 * <pre>{@code
 * san_behaviour_pool.register(new san_behaviour_pool.Behaviour()
 * {
 *     public ResourceLocation id() { return new ResourceLocation("white_lily", "drink_lily_tea"); }
 *     public double sanDelta() { return -500.0D; }               // 负数消耗、正数增加
 *     public void run(ServerPlayer player) { ... }
 * });
 *
 * // 触发（会先检查理智值够不够扣）
 * san_behaviour_pool.run(player, new ResourceLocation("white_lily", "drink_lily_tea"));
 * }</pre>
 *
 * <h2>三条规矩</h2>
 * <ol>
 *     <li><b>池子里的行为用 {@link #run} 触发，不要自己改属性</b>：这样「够不够扣」的判断只有一处。</li>
 *     <li><b>理智值永远不会低于 0</b>（属性 min 是 0）：{@link #tryConsume} 扣不起就整体失败，
 *         不会出现「扣一半」。</li>
 *     <li>行为池里的改动跟 {@link san_procedures} 的状态机完全独立：状态只看属性最终值，
 *         怎么变的不重要。</li>
 * </ol>
 */
public final class san_behaviour_pool
{
    /** 一个理智值行为。 */
    public interface Behaviour
    {
        /** 唯一标识（建议 {@code white_lily:xxx}）。 */
        ResourceLocation id();

        /** 对理智值的改变量：负数=消耗，正数=增加。 */
        double sanDelta();

        /** 理智值足够时的实际效果。 */
        void run(ServerPlayer player);
    }

    /** 登记表：保持插入顺序，方便命令/调试按稳定顺序列出。 */
    private static final Map<ResourceLocation, Behaviour> POOL = new LinkedHashMap<>();

    private san_behaviour_pool()
    {
    }

    /**
     * 登记一个行为。
     *
     * @return 登记成功；id 已被占用时返回 false（不覆盖已有行为）
     */
    public static boolean register(Behaviour behaviour)
    {
        if (behaviour == null || behaviour.id() == null)
        {
            return false;
        }
        if (POOL.containsKey(behaviour.id()))
        {
            return false;
        }
        POOL.put(behaviour.id(), behaviour);
        return true;
    }

    /** 已登记的全部行为 id（只读）。 */
    public static Set<ResourceLocation> ids()
    {
        return Collections.unmodifiableSet(POOL.keySet());
    }

    /** 按 id 找行为。 */
    public static Optional<Behaviour> find(ResourceLocation id)
    {
        return Optional.ofNullable(POOL.get(id));
    }

    /** 池子现在是不是空的（本轮就是）。 */
    public static boolean isEmpty()
    {
        return POOL.isEmpty();
    }

    /** 当前理智值。 */
    public static double currentSan(Player player)
    {
        AttributeInstance instance = player.getAttribute(attributes.SAN.get());
        return instance == null ? 0.0D : instance.getValue();
    }

    /** 直接改动理智值（会被属性上下限自然夹住，不会低于 0）。 */
    public static void addSan(Player player, double delta)
    {
        AttributeInstance instance = player.getAttribute(attributes.SAN.get());
        if (instance == null)
        {
            return;
        }
        instance.setBaseValue(Math.max(0.0D, instance.getBaseValue() + delta));
    }

    /**
     * 试着扣理智值：不够就一点都不扣。
     *
     * @return 是否扣成功
     */
    public static boolean tryConsume(Player player, double amount)
    {
        if (amount <= 0.0D)
        {
            return true;
        }
        if (currentSan(player) < amount)
        {
            return false;
        }
        addSan(player, -amount);
        return true;
    }

    /**
     * 触发一个行为：先校验理智值够不够，再执行效果。
     *
     * @return 是否真的执行了（行为不存在 / 理智值不够 → false）
     */
    public static boolean run(ServerPlayer player, ResourceLocation id)
    {
        Behaviour behaviour = POOL.get(id);
        if (behaviour == null)
        {
            return false;
        }

        double delta = behaviour.sanDelta();
        if (delta < 0.0D && !tryConsume(player, -delta))
        {
            return false;
        }
        behaviour.run(player);
        if (delta > 0.0D)
        {
            addSan(player, delta);
        }
        return true;
    }
}
