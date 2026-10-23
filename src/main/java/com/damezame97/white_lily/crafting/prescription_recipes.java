package com.damezame97.white_lily.crafting;

import com.damezame97.white_lily.white_lily;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class prescription_recipes
{
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, white_lily.MODID);

    public static final RegistryObject<RecipeSerializer<prescription_revealing_recipe>> REVEALING =
            SERIALIZERS.register("prescription_revealing", prescription_revealing_recipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<durable_tool_recipe>> DURABLE_TOOL =
            SERIALIZERS.register("durable_tool_shapeless", durable_tool_recipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<com.damezame97.white_lily.processing.processing_recipe>> PROCESSING =
            SERIALIZERS.register("simple_processing", com.damezame97.white_lily.processing.processing_recipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<research_crafting_recipe>> RESEARCH_CRAFTING =
            SERIALIZERS.register("research_crafting", research_crafting_recipe.Serializer::new);

    private prescription_recipes()
    {
    }
}
