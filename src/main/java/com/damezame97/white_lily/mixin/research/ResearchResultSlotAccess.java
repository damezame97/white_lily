package com.damezame97.white_lily.mixin.research;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResultSlot.class)
public interface ResearchResultSlotAccess
{
    @Accessor("player") Player whiteLily$researchPlayer();
    @Accessor("craftSlots") CraftingContainer whiteLily$researchGrid();
}
