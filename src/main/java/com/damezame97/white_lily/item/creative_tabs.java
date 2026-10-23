package com.damezame97.white_lily.item;

import com.damezame97.white_lily.white_lily;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 三栏使用明确的公开清单；绑定改良饰品与旧标签图标不进入展示及搜索。 */
public final class creative_tabs
{
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, white_lily.MODID);
    public static final String MISGUIDED_DELICACIES_NAME = "misguided_delicacies";
    public static final String MISGUIDED_DELICACIES_TITLE_KEY =
            "itemGroup." + white_lily.MODID + "." + MISGUIDED_DELICACIES_NAME;
    public static final String MISGUIDED_LIVELIHOOD_NAME = "misguided_livelihood";
    public static final String MISGUIDED_LIVELIHOOD_TITLE_KEY =
            "itemGroup." + white_lily.MODID + "." + MISGUIDED_LIVELIHOOD_NAME;

    public static final RegistryObject<CreativeModeTab> MISGUIDED_DELICACIES =
            CREATIVE_TABS.register(MISGUIDED_DELICACIES_NAME, () -> CreativeModeTab.builder()
                    .title(Component.translatable(MISGUIDED_DELICACIES_TITLE_KEY))
                    .icon(() -> new ItemStack(items.TAB_ICON.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(items.MAGIC_APPLE.get());
                        output.accept(items.LOW_QUALITY_POTION.get());
                        output.accept(items.STORED_SUFFERING.get());
                    }).build());

    public static final RegistryObject<CreativeModeTab> MISGUIDED_LIVELIHOOD =
            CREATIVE_TABS.register(MISGUIDED_LIVELIHOOD_NAME, () -> CreativeModeTab.builder()
                    .title(Component.translatable(MISGUIDED_LIVELIHOOD_TITLE_KEY).withStyle(ChatFormatting.GREEN))
                    .icon(() -> new ItemStack(items.HERBAL_POUCH.get()))
                    // Forge 的 tabsBefore 指排在本栏前面的栏，不是把本栏放在参数前面。
                    .withTabsBefore(MISGUIDED_DELICACIES.getId())
                    .displayItems((parameters, output) -> {
                        // 图标、工作台与工具。
                        output.accept(items.HERBAL_POUCH.get());
                        output.accept(items.INVESTIGATION_HANDBOOK.get());
                        output.accept(items.SIMPLE_PROCESSING_TABLE.get());
                        output.accept(items.STONE_MORTAR.get());
                        // 种植物与药草。
                        output.accept(items.HEMP_SEEDS.get());
                        output.accept(items.HEMP_STALK.get());
                        output.accept(items.BITTERLEAF.get());
                        output.accept(items.YARROW.get());
                        output.accept(items.CALMING_FLOWER.get());
                        // 基础材料与组件。
                        output.accept(items.PLANT_FIBER.get());
                        output.accept(items.COARSE_CLOTH.get());
                        output.accept(items.THIN_CLOTH_STRIP.get());
                        output.accept(items.WOODEN_TRAY.get());
                        output.accept(items.CORK_STOPPER.get());
                        output.accept(items.BEESWAX_BLOCK.get());
                        output.accept(items.CHARCOAL_POWDER.get());
                        output.accept(items.CLAY_BOWL.get());
                        output.accept(items.GLASS_TUBE.get());
                        output.accept(items.FILTER_PAPER.get());
                        output.accept(items.DESICCANT_PACKET.get());
                        // 标签与处方。
                        output.accept(items.BLANK_LABEL.get());
                        output.accept(items.BLANK_PRESCRIPTION.get());
                        output.accept(items.REVEALED_PRESCRIPTION.get());
                        // 医疗成品。
                        output.accept(items.SIMPLE_BANDAGE.get());
                        output.accept(items.CLEAN_FINE_BANDAGE.get());
                        output.accept(items.BITTERLEAF_PASTE.get());
                        output.accept(items.YARROW_DRESSING.get());
                        output.accept(items.CALMING_FLOWER_TEA.get());
                        output.accept(items.HERBAL_RESIDUE.get());
                    }).build());

    public static final RegistryObject<CreativeModeTab> MEDICAL_TESTS = CREATIVE_TABS.register("medical_tests", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.white_lily.medical_tests"))
                    .icon(() -> new ItemStack(items.POLLUTION_DETECTOR.get()))
                    .withTabsBefore(MISGUIDED_LIVELIHOOD.getId())
                    .displayItems((parameters, output) -> {
                        // 展示不依赖权限；使用测试片时仍由服务端检查管理员权限。
                        output.accept(items.POLLUTION_DETECTOR.get());
                        output.accept(items.POLLUTION_CHIP_SMALL.get());
                        output.accept(items.POLLUTION_CHIP_MEDIUM.get());
                        output.accept(items.POLLUTION_CHIP_LARGE.get());
                        output.accept(items.CLEANSING_CHIP_SMALL.get());
                        output.accept(items.CLEANSING_CHIP_MEDIUM.get());
                        output.accept(items.CLEANSING_CHIP_LARGE.get());
                    }).build());

    private creative_tabs() { }
}
