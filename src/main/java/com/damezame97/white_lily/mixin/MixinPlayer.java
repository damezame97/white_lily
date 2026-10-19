package com.damezame97.white_lily.mixin;

import com.damezame97.white_lily.gamerule.eating_player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 只为 {@code Building_endurance} 服务的一把「进出登记」：
 * 在 {@code Player#eat(Level, ItemStack)} 前后告诉 {@link eating_player} 是谁在吃东西。
 *
 * <h2>为什么分两个类</h2>
 * <p>抬高饱和度上限这件事必须落在 {@code FoodData#eat(int, float)} 上（那里才夹得着上限），
 * 但那里拿不到玩家。所以这边的 HEAD/TAIL 只做登记，真正的改法在
 * {@code MixinFoodData#whiteLily$raiseSaturationCap}。</p>
 *
 * <h2>为什么两个注入都不捕获参数</h2>
 * <p>实测（Forge 47.4.10 + Mixin 0.8.5）在 {@code Player#eat} 上捕获 {@code ItemStack} 参数
 * 拿到的是空栈（{@code 0 air}），而同一个方法里的 {@code this} 是好的。既然只要 {@code this}，
 * 就干脆不捕获任何参数，绕开这个坑。食物的营养/饱和度系数也不需要在这里拿——
 * {@code FoodData#eat(int, float)} 里本来就有。</p>
 */
@Mixin(Player.class)
public abstract class MixinPlayer
{
    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private void whiteLily$beginEating(CallbackInfoReturnable<ItemStack> cir)
    {
        eating_player.begin((Player) (Object) this);
    }

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void whiteLily$endEating(CallbackInfoReturnable<ItemStack> cir)
    {
        eating_player.end();
    }
}
