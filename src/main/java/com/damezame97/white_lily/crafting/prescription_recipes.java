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

    private prescription_recipes()
    {
    }
}
