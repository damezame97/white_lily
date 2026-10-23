package com.damezame97.white_lily.block;

import com.damezame97.white_lily.white_lily;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.*;

public final class medical_blocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, white_lily.MODID);
    public static final RegistryObject<Block> BITTERLEAF = BLOCKS.register("bitterleaf", () -> new herb_block(true));
    public static final RegistryObject<Block> YARROW = BLOCKS.register("yarrow", () -> new herb_block(true));
    public static final RegistryObject<Block> CALMING_FLOWER = BLOCKS.register("calming_flower", () -> new herb_block(false));
    public static final RegistryObject<Block> HEMP_CROP = BLOCKS.register("hemp_crop", hemp_crop::new);
    public static final RegistryObject<Block> WILD_HEMP = BLOCKS.register("wild_hemp", wild_hemp::new);
    public static final RegistryObject<Block> SIMPLE_PROCESSING_TABLE = BLOCKS.register("simple_processing_table", simple_processing_table::new);
    private medical_blocks() { }
}
