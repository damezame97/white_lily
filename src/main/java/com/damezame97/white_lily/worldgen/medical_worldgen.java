package com.damezame97.white_lily.worldgen;

import com.damezame97.white_lily.white_lily;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.*;

public final class medical_worldgen
{
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, white_lily.MODID);
    public static final RegistryObject<herb_patch> HERB_PATCH = FEATURES.register("herb_patch", herb_patch::new);
    private medical_worldgen() { }
}
