package com.damezame97.white_lily.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让物品数量挤得下 128：把「存档 NBT 里记录数量」的字段从 <b>byte</b> 升级成 <b>int</b>（仅当数量 &gt; 127）。
 *
 * <h2>为什么需要动这里</h2>
 * <p>原版 {@code ItemStack#save(CompoundTag)} 写的是 {@code putByte("Count", (byte)count)}，
 * 读回来是 {@code getByte("Count")}。byte 有符号，<b>128 存进去是 -128，读出来也是 -128</b>，
 * 于是存盘/读盘一轮之后 128 个物品就没了。</p>
 *
 * <h2>为什么只在 &gt; 127 时改格式</h2>
 * <p>数量 ≤ 127 时<b>保持原版的 ByteTag 一个字都不动</b>，这样普通物品的存档格式和别的模组完全兼容
 * （有些代码会用 {@code tag.contains("Count", 1)} 判断类型，不能乱改）。只有本模组那种
 * 超大堆叠才写 IntTag。读的时候统一用 {@code getInt}：它对 ByteTag 和 IntTag 都成立，
 * 所以老存档（ByteTag）和新的超大堆叠（IntTag）都能读回来。</p>
 *
 * <p>注意本类只是「两处补丁」，{@code getMaxStackSize} 本身没被改（原版就不夹 64，
 * 夹 64 的是容器槽位 {@code Container#getMaxStackSize}，见 {@link MixinInventory}）。</p>
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack
{
    @Shadow
    private int count;

    /** {@code save} 收尾时，若数量超过 byte 能装的范围，就把 "Count" 改写成 IntTag。 */
    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"))
    private void whiteLily$writeLargeCount(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir)
    {
        if (this.count > 127)
        {
            tag.putInt("Count", this.count);
        }
    }

    /** 从 NBT 构造完（原版已把 count 读成 byte）之后，用 getInt 重新读一遍，纠正 &gt; 127 的情况。 */
    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void whiteLily$readLargeCount(CompoundTag tag, CallbackInfo ci)
    {
        this.count = tag.getInt("Count");
    }
}
