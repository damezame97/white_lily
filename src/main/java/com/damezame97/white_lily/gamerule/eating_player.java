package com.damezame97.white_lily.gamerule;

import net.minecraft.world.entity.player.Player;

/**
 * 「此刻是谁在吃东西」的一次性传递。
 *
 * <h2>为什么需要它</h2>
 * <p>{@code Building_endurance} 要把「储存饱和度」的上限从饥饿值抬到
 * {@code 饥饿值 + 该属性}，而这件事唯一的落点是原版
 * {@code FoodData#eat(int, float)} 里那句
 * {@code this.saturationLevel = Math.min(this.saturationLevel + 食物增益, (float)this.foodLevel);}
 * —— 那里<b>只有 FoodData 自己，没有玩家</b>（属性也就读不到）。</p>
 *
 * <p>所以在 {@code Player#eat(Level, ItemStack)} 的 HEAD/TAIL 各挂一刀（<b>不捕获任何参数</b>，
 * 只用 {@code this}）：进方法时把玩家放进来，出方法时清掉。中间那次
 * {@code FoodData.eat(int, float)} 就能顺手读到是谁在吃。</p>
 *
 * <p>用 {@link ThreadLocal} 是因为服务端的实体 tick 在同一个线程里跑，而这样也顺带保证
 * 别的线程（比如区块生成工作线程）不会读到。</p>
 */
public final class eating_player
{
    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    /** 进入 {@code Player#eat} 时登记。 */
    public static void begin(Player player)
    {
        CURRENT.set(player);
    }

    /** 离开 {@code Player#eat} 时清掉。 */
    public static void end()
    {
        CURRENT.remove();
    }

    /** 当前正在吃东西的玩家；不在吃东西时为 {@code null}。 */
    public static Player current()
    {
        return CURRENT.get();
    }

    private eating_player()
    {
    }
}
