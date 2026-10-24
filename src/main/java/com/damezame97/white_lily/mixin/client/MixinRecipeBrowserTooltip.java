package com.damezame97.white_lily.mixin.client;

import com.damezame97.white_lily.client.recipe_browser_tooltips;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Scoped to successful native tooltip rendering; never replaces another mod's tooltip components. */
@Mixin(GuiGraphics.class)
public abstract class MixinRecipeBrowserTooltip
{
    @Unique private boolean whiteLily$scrollingTooltip;
    @Redirect(method="renderTooltipInternal",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    private org.joml.Vector2ic whiteLily$position(ClientTooltipPositioner positioner,int width,int height,int x,int y,int tooltipWidth,int tooltipHeight)
    {return recipe_browser_tooltips.position(positioner,width,height,x,y,tooltipWidth,tooltipHeight);}
    @Inject(method="renderTooltipInternal",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V"))
    private void whiteLily$beginTooltip(Font font,List<ClientTooltipComponent> components,int x,int y,ClientTooltipPositioner positioner,CallbackInfo ci)
    { whiteLily$scrollingTooltip=recipe_browser_tooltips.begin((GuiGraphics)(Object)this,font,components,x,y); }
    @Inject(method="renderTooltipInternal",at=@At("RETURN"))
    private void whiteLily$endTooltip(Font font,List<ClientTooltipComponent> components,int x,int y,ClientTooltipPositioner positioner,CallbackInfo ci)
    { if(whiteLily$scrollingTooltip){whiteLily$scrollingTooltip=false;recipe_browser_tooltips.end((GuiGraphics)(Object)this);} }
}
