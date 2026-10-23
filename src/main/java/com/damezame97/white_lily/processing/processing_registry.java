package com.damezame97.white_lily.processing;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.white_lily;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 加工台的公共注册入口；客户端界面在客户端事件中单独绑定。 */
public final class processing_registry
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, white_lily.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, white_lily.MODID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, white_lily.MODID);

    public static final RegistryObject<BlockEntityType<processing_block_entity>> TABLE_ENTITY =
            BLOCK_ENTITIES.register("simple_processing_table", () -> BlockEntityType.Builder.of(
                    processing_block_entity::new, medical_blocks.SIMPLE_PROCESSING_TABLE.get()).build(null));
    public static final RegistryObject<MenuType<processing_menu>> TABLE_MENU =
            MENUS.register("simple_processing_table", () -> IForgeMenuType.create(processing_menu::new));
    public static final RegistryObject<RecipeType<processing_recipe>> RECIPE_TYPE =
            TYPES.register("simple_processing", () -> new RecipeType<>()
            {
                @Override public String toString() { return white_lily.MODID + ":simple_processing"; }
            });

    private processing_registry() { }
}
