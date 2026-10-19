package com.damezame97.white_lily.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让物品数量挤得下 128：把「网络上传输物品数量」的字段从<b>有符号 byte</b> 换成 <b>VarInt</b>。
 *
 * <h2>为什么需要动这里</h2>
 * <p>原版 {@code FriendlyByteBuf#writeItemStack(ItemStack, boolean)} 用 {@code writeByte(getCount())}
 * 写数量，{@code readItem()} 用 {@code readByte()} 读回来。byte 有符号，0~127 无损，
 * <b>128 会翻成 -128</b>，客户端把它当空物品。所以想让上限超过 127，就必须换掉这两处。</p>
 *
 * <h2>为什么 {@code writeItemStack} 要写 {@code remap = false}</h2>
 * <p>{@code writeItemStack(ItemStack, boolean)} 是 <b>Forge 补丁加进去的方法</b>，原版没有，
 * 因此<b>不在 Forge 的 MCP→SRG 映射表里</b>（可查 {@code build/createMcpToSrg/output.tsrg}，
 * 只有 {@code writeItem} / {@code readItem} 有 SRG 名 {@code m_130055_} / {@code m_130267_}）。
 * 若按默认（remap=true）去查映射，注解处理器会直接报
 * {@code Unable to locate obfuscation mapping for @Overwrite method}，生成不出 refmap、编译失败——
 * 这正是本项目第八轮放弃 128 方案的原因。</p>
 * <p>Forge 补的方法在正式环境里<b>保留原名字</b>（不像原版成员会被改写成 {@code m_xxxx_}），
 * 所以这里写 {@code remap = false} 让 mixin 按字面名 {@code writeItemStack} 去找，开发/正式两边都能命中。</p>
 *
 * <p>{@code readItem()} 是原版方法（有 SRG 名 {@code m_130267_}），走 refmap 映射；
 * 但用 {@code @Inject} 而不是 {@code @Overwrite}——实测 {@code @Overwrite} 指向的原版方法
 * 不会被写进 refmap，生产环境会失效。</p>
 *
 * <p>覆盖 {@code writeItemStack} 一并接管了所有写入路径，包括直接调用它的两个创造模式/容器点击封包
 * （{@code ServerboundSetCreativeModeSlotPacket}、{@code ServerboundContainerClickPacket}），
 * 它们与 {@code writeItem} 用的是同一份逻辑，所以整条链路上写的都是 VarInt。</p>
 */
@Mixin(FriendlyByteBuf.class)
public abstract class MixinFriendlyByteBuf
{
    /**
     * 与 {@code writeItemStack} 原实现逐句一致，唯一差别是数量那一行改用 {@code writeVarInt}。
     *
     * @author 义眼
     * @reason 数量字段改用 VarInt，以支持超过 127 的堆叠（原版 writeByte 装不下 128）
     */
    @Overwrite(remap = false)
    public FriendlyByteBuf writeItemStack(ItemStack stack, boolean limitedTag)
    {
        FriendlyByteBuf self = (FriendlyByteBuf) (Object) this;
        if (stack.isEmpty())
        {
            self.writeBoolean(false);
        }
        else
        {
            self.writeBoolean(true);
            Item item = stack.getItem();
            self.writeId(BuiltInRegistries.ITEM, item);
            // 原版是 self.writeByte(stack.getCount())：byte 容不下 128。
            self.writeVarInt(stack.getCount());
            CompoundTag compoundtag = null;
            if (item.isDamageable(stack) || item.shouldOverrideMultiplayerNbt())
            {
                compoundtag = limitedTag ? stack.getShareTag() : stack.getTag();
            }
            self.writeNbt(compoundtag);
        }
        return self;
    }

    /**
     * 与 {@code readItem} 原实现逐句一致，唯一差别是数量那一行改用 {@code readVarInt}。
     *
     * <p>用 {@code @Inject(HEAD)}+{@code setReturnValue}（等价于整段替换）而非 {@code @Overwrite}：
     * 实测 {@code @Overwrite} 的原版方法不进 refmap，生产环境（方法名是 SRG {@code m_130267_}）找不到目标。</p>
     */
    @Inject(method = "readItem()Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void whiteLily$readBigCount(CallbackInfoReturnable<ItemStack> cir)
    {
        FriendlyByteBuf self = (FriendlyByteBuf) (Object) this;
        if (!self.readBoolean())
        {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        Item item = self.readById(BuiltInRegistries.ITEM);
        // 原版是 self.readByte()：byte 有符号，128 会读成 -128。
        int count = self.readVarInt();
        ItemStack itemstack = new ItemStack(item, count);
        itemstack.readShareTag(self.readNbt());
        cir.setReturnValue(itemstack);
    }
}
