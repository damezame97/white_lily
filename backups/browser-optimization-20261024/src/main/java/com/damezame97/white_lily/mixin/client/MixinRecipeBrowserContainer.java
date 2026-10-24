package com.damezame97.white_lily.mixin.client;

import com.damezame97.white_lily.client.recipe_browser;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opening a recipe is navigation inside the live menu, so its removal hook must wait for a real close. */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinRecipeBrowserContainer
{
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void whiteLily$keepMenuForRecipeBrowser(CallbackInfo callback)
    {
        if (recipe_browser.isOpeningChild()) callback.cancel();
    }
}
