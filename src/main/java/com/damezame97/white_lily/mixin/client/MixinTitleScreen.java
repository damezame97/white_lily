package com.damezame97.white_lily.mixin.client;

import com.damezame97.white_lily.client.NoiseTitleEasterEgg;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只在客户端载入：替换主菜单背景，保留原版 logo、按钮、版权和 Realms 的绘制/输入。 */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {
    protected MixinTitleScreen(Component title) { super(title); }

    @Inject(method = "added", at = @At("RETURN"))
    private void whiteLily$enteredTitle(CallbackInfo callback) {
        NoiseTitleEasterEgg.enteredTitle();
    }

    @Inject(method = "removed", at = @At("RETURN"))
    private void whiteLily$leftTitle(CallbackInfo callback) {
        NoiseTitleEasterEgg.leftTitle();
    }

    // 1.20.1 顺序为 panorama -> overlay -> logo -> 按钮。这里绘制完整不透明黑白背景，
    // 覆盖全景和其遮罩，不能放在 render HEAD（会被全景盖住）或 TAIL（会遮住菜单）。
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void whiteLily$noiseBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTick, CallbackInfo callback) {
        NoiseTitleEasterEgg.renderBackground(graphics, width, height);
    }
}
