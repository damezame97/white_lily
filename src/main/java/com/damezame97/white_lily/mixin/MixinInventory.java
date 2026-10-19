package com.damezame97.white_lily.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 让玩家背包（{@code Inventory}）报告的槽位上限从原版的 64 变成 128。
 *
 * <h2>为什么需要动这里</h2>
 * <p>原版 {@code Container#getMaxStackSize()} 默认返回 64，玩家背包<b>没有覆写</b>它。
 * 于是 {@code Slot#getMaxStackSize(ItemStack)} = {@code min(64, 物品上限)} 最多 64；
 * {@code Inventory#addResource} 里那句 {@code this.getMaxStackSize()} 也把每格压到 64。
 * 128 会被拆成 64+64。</p>
 *
 * <h2>为什么写两个同名行为的方法（一个开发名、一个生产名）</h2>
 * <p>原版成员名分两套：开发环境是官方名 {@code getMaxStackSize}，生产环境是 SRG 名
 * {@code m_6893_}。Mixin <b>给 Inventory 新增</b>的方法<b>不会被重映射</b>（实测不写进 refmap），
 * 所以只写 {@code getMaxStackSize} 时：开发环境能覆写默认方法、生产环境覆写不到——
 * 就是「开发能跑、打包就废」。因此这里两个名字各写一份：</p>
 * <ul>
 *     <li>{@code getMaxStackSize()}：开发环境生效；</li>
 *     <li>{@code m_6893_()}：生产环境生效（{@code Container#getMaxStackSize} 的 SRG 名）。</li>
 * </ul>
 * <p>两个方法行为完全一致（都返回 128），多出来的那个在对应环境里只是没人调用的死方法，
 * 无害。改的是玩家背包这一个类，箱子/熔炉等容器不受影响。</p>
 */
@Mixin(Inventory.class)
public abstract class MixinInventory
{
    /** 开发环境（官方名）生效的那份。 */
    public int getMaxStackSize()
    {
        return 128;
    }

    /** 生产环境（SRG 名 {@code m_6893_}）生效的那份；见类注释。 */
    public int m_6893_()
    {
        return 128;
    }
}
