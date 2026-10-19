package com.damezame97.white_lily.white_lilycurio;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 「改良」系列饰品的共同基类：四个饰品（{@code flesh_ring} / {@code improve_body} /
 * {@code invalid_placeholder} / {@code fate_compass}）都继承它，所以它们天然共享同一套规则。
 *
 * <h2>三条统一特性</h2>
 * <ol>
 *     <li><b>拿不下来</b>：{@link #canUnequip} 返回 {@code false}。Curios 把饰品取出走的是
 *         {@code DynamicStackHandler#extractItem}，那里会问这个方法：拿到 false 就直接返回空栈，
 *         所以 GUI 里拖、Shift 快速移动、任何走容器逻辑的路径都拿不走。
 *         本模组自己的「每次进世界重设」用的是 {@code setEquippedCurio} → 直接写格子，
 *         <b>不</b>走 extractItem，所以不受这条影响。</li>
 *     <li><b>死了不掉</b>：{@link #getDropRule} 返回 {@link ICurio.DropRule#ALWAYS_KEEP}，
 *         Curios 处理玩家死亡掉落时会看这个值把饰品留下；{@link curio_events} 另外还兜了一层
 *         {@code LivingDropsEvent}，把掉落物里的这四件直接剔除。</li>
 *     <li><b>非创造玩家的物品栏里出现就删</b>：见 {@link curio_events} 的每 tick 清理。
 *         创造模式玩家不受影响。</li>
 * </ol>
 *
 * <h2>怎么被 Curios 认成饰品</h2>
 * <p>实现 {@link ICurioItem} 就够（Forge 侧由 Curios 的
 * {@code CuriosEventHandler#attachStackCapabilities} 兜底），再加上这四个物品都在
 * {@code data/curios/tags/items/improvement.json} 里，于是 {@code improvement} 栏的
 * {@code curios:tag} 校验器会接受它们。</p>
 */
public abstract class improvement_curio extends Item implements ICurioItem
{
    protected improvement_curio()
    {
        super(new Item.Properties().stacksTo(1));
    }

    /** 拿不下来。 */
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack)
    {
        return false;
    }

    /** 死了也留在身上。 */
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       int lootingLevel, boolean recentlyHit, ItemStack stack)
    {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    /** 这件物品是不是「改良」系列饰品（四个都继承本类，所以一句 {@code instanceof} 就够）。 */
    public static boolean isImprovementCurio(ItemStack stack)
    {
        return stack.getItem() instanceof improvement_curio;
    }
}
