package com.damezame97.white_lily.mixin.client;

import com.damezame97.white_lily.client.effect_sidebar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla effect icons are replaced by the vertical rail, so both are never shown together. */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class MixinEffectRenderingInventoryScreen
{
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void whiteLily$replaceEffects(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback)
    {
        if (effect_sidebar.shouldReplaceEffects((EffectRenderingInventoryScreen<?>) (Object) this)) callback.cancel();
    }
}
