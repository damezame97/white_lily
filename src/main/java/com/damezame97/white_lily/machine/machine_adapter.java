package com.damezame97.white_lily.machine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/** Implement once per integration; recipe policy remains in data packs. */
public interface machine_adapter {
    ResourceLocation id();
    RecipeType<?> type(ResourceLocation device);
    Recipe<?> copy(ResourceLocation device, ResourceLocation newId, Recipe<?> source, int ticks);
    default boolean matches(net.minecraft.world.level.block.entity.BlockEntity machine, ResourceLocation device) { return false; }
}
