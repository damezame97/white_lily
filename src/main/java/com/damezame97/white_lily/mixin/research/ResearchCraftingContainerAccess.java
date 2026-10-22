package com.damezame97.white_lily.mixin.research;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TransientCraftingContainer.class)
public interface ResearchCraftingContainerAccess
{
    @Accessor("menu") AbstractContainerMenu whiteLily$researchMenu();
}
