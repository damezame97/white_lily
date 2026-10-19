package com.damezame97.white_lily.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让玩家背包的槽位在界面上也能显示/拖动到 64 以上。
 *
 * <h2>为什么需要动这里</h2>
 * <p>{@code Slot#getMaxStackSize()} 返回的就是 {@code this.container.getMaxStackSize()}，
 * 而 {@code Container#getMaxStackSize()} 的默认值是 64；{@code Slot#getMaxStackSize(ItemStack)}
 * 再取 {@code min(容器上限, 物品上限)}。于是界面上一格最多 64，128 会显示成两格。</p>
 *
 * <h2>做法</h2>
 * <p>把 {@code Slot#getMaxStackSize()} 里那次 {@code container.getMaxStackSize()} 调用
 * （字节码 owner 是接口 {@code Container}，有 SRG 映射 {@code m_6893_}）重定向：
 * 若这个槽挂的是玩家背包（{@link Inventory}），就把上限抬到 128；其它容器原样返回。
 * 普通物品自己的上限 ≤ 64，{@code min(128, 64)} 仍是原来的值，不受影响。</p>
 */
@Mixin(Slot.class)
public abstract class MixinSlot
{
    @Redirect(method = "getMaxStackSize()I",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/Container;getMaxStackSize()I"))
    private int whiteLily$raiseSlotCap(Container container)
    {
        int vanilla = container.getMaxStackSize();
        return (container instanceof Inventory) ? Math.max(vanilla, 128) : vanilla;
    }
}
