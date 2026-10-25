package com.damezame97.white_lily.machine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;

public final class vanilla_cooking_adapter implements machine_adapter {
    @Override public boolean matches(net.minecraft.world.level.block.entity.BlockEntity machine, ResourceLocation device) {
        if (!device.equals(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(machine.getBlockState().getBlock()))) return false;
        var expected = switch (device.toString()) {
            case "minecraft:furnace" -> net.minecraft.world.level.block.entity.BlockEntityType.FURNACE;
            case "minecraft:smoker" -> net.minecraft.world.level.block.entity.BlockEntityType.SMOKER;
            case "minecraft:blast_furnace" -> net.minecraft.world.level.block.entity.BlockEntityType.BLAST_FURNACE;
            default -> null;
        };
        return expected != null && machine.getType() == expected;
    }
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("white_lily", "vanilla_cooking"); }
    public RecipeType<?> type(ResourceLocation device) {
        return switch (device.toString()) {
            case "minecraft:furnace" -> RecipeType.SMELTING;
            case "minecraft:smoker" -> RecipeType.SMOKING;
            case "minecraft:blast_furnace" -> RecipeType.BLASTING;
            default -> throw new IllegalArgumentException("Unsupported cooking device " + device);
        };
    }
    public Recipe<?> copy(ResourceLocation device, ResourceLocation newId, Recipe<?> source, int ticks) {
        // Unknown subclasses may contain extra conditions; never silently discard them.
        if (source.getClass() != SmeltingRecipe.class && source.getClass() != SmokingRecipe.class
                && source.getClass() != BlastingRecipe.class)
            throw new IllegalArgumentException("Cannot safely copy custom cooking recipe " + source.getId());
        var r = (AbstractCookingRecipe) source;
        int time = ticks > 0 ? ticks : r.getCookingTime();
        var input = r.getIngredients().get(0);
        var output = r.getResultItem(RegistryAccess.EMPTY).copy();
        var type = type(device);
        if (type == RecipeType.SMELTING) return new SmeltingRecipe(newId, r.getGroup(), r.category(), input, output, r.getExperience(), time);
        if (type == RecipeType.SMOKING) return new SmokingRecipe(newId, r.getGroup(), r.category(), input, output, r.getExperience(), time);
        return new BlastingRecipe(newId, r.getGroup(), r.category(), input, output, r.getExperience(), time);
    }
}
