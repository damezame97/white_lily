package com.damezame97.white_lily.item;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组专属的创造模式物品栏。
 *
 * <p>注册名 {@value #MISGUIDED_DELICACIES_NAME}，游戏内显示为「歧途“珍馐”」
 * （标题走语言键 {@value #MISGUIDED_DELICACIES_TITLE_KEY}，中英各一份）。</p>
 *
 * <p>标签图标由隐藏物品 {@link items#TAB_ICON} 承载，
 * 它用的是 {@code white_lily_food.png} 那张贴图。</p>
 *
 * <p>排序交给 Forge 的 {@code CreativeModeTabRegistry}：这里没写
 * {@code withTabsBefore/withTabsAfter}，所以本栏会排在原版最后一个标签之后（第 11 位），
 * 也就是创造界面分页规则下的第 2 页（每页 10 栏：上排 5 + 下排 5）。</p>
 *
 * <p><b>必须至少放一件物品，否则这一栏根本不会显示。</b>
 * {@code CreativeModeTab#shouldDisplay()} 的实现是
 * {@code type != CATEGORY || hasAnyItems()}：{@code CATEGORY} 类型的空物品栏会被
 * 客户端 {@code CreativeTabsScreenPage#getVisibleTabs()} 过滤掉，任何一页都画不出来
 * （它仍占分页名额，所以那一页看起来是空的）。</p>
 *
 * <p><b>注意</b>：曾经用来「占位」的 {@link items#TAB_ICON} 已经从这里移除（它只保留作图标）；
 * 现在这一栏里放的是真正的物品 {@link items#MAGIC_APPLE}。</p>
 */
public final class creative_tabs
{
    /** 物品栏注册表，由主类 {@link white_lily} 挂到模组事件总线。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, white_lily.MODID);

    /** 物品栏的注册名，同时也是语言键的尾段。 */
    public static final String MISGUIDED_DELICACIES_NAME = "misguided_delicacies";

    /** 物品栏标题的语言键（中英 lang 里都必须有这一条）。 */
    public static final String MISGUIDED_DELICACIES_TITLE_KEY =
            "itemGroup." + white_lily.MODID + "." + MISGUIDED_DELICACIES_NAME;

    /** 「歧途“珍馐”」创造模式物品栏。 */
    public static final RegistryObject<CreativeModeTab> MISGUIDED_DELICACIES =
            CREATIVE_TABS.register(MISGUIDED_DELICACIES_NAME, () -> CreativeModeTab.builder()
                    .title(Component.translatable(MISGUIDED_DELICACIES_TITLE_KEY))
                    .icon(() -> new ItemStack(items.TAB_ICON.get()))
                    .displayItems((parameters, output) ->
                    {
                        // 食物与饮品。
                        output.accept(items.MAGIC_APPLE.get());
                        output.accept(items.LOW_QUALITY_POTION.get());
                        output.accept(items.CALMING_FLOWER_TEA.get());

                        // 医疗成品，由简易到精良。
                        output.accept(items.SIMPLE_BANDAGE.get());
                        output.accept(items.CLEAN_FINE_BANDAGE.get());
                        output.accept(items.BITTERLEAF_PASTE.get());
                        output.accept(items.YARROW_DRESSING.get());

                        // 药草与种植物。
                        output.accept(items.BITTERLEAF.get());
                        output.accept(items.YARROW.get());
                        output.accept(items.CALMING_FLOWER.get());
                        output.accept(items.HEMP_SEEDS.get());
                        output.accept(items.HEMP_STALK.get());

                        // 基础材料与处方。
                        output.accept(items.COARSE_CLOTH.get());
                        output.accept(items.THIN_CLOTH_STRIP.get());
                        output.accept(items.CORK_STOPPER.get());
                        output.accept(items.BEESWAX_BLOCK.get());
                        output.accept(items.CHARCOAL_POWDER.get());
                        output.accept(items.BLANK_LABEL.get());
                        output.accept(items.BLANK_PRESCRIPTION.get());
                        output.accept(items.REVEALED_PRESCRIPTION.get());

                        // 普通可获取饰品；作者的绑定改良饰品继续隐藏。
                        output.accept(items.STORED_SUFFERING.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> MEDICAL_TESTS = CREATIVE_TABS.register("medical_tests", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.white_lily.medical_tests"))
                    .icon(() -> new ItemStack(items.POLLUTION_DETECTOR.get()))
                    .withTabsAfter(MISGUIDED_DELICACIES.getId())
                    .displayItems((parameters, output) -> {
                        // 显示不依赖管理员权限，实际增减污染仍由服务端校验。
                        output.accept(items.POLLUTION_DETECTOR.get());
                        output.accept(items.POLLUTION_CHIP_SMALL.get());
                        output.accept(items.POLLUTION_CHIP_MEDIUM.get());
                        output.accept(items.POLLUTION_CHIP_LARGE.get());
                        output.accept(items.CLEANSING_CHIP_SMALL.get());
                        output.accept(items.CLEANSING_CHIP_MEDIUM.get());
                        output.accept(items.CLEANSING_CHIP_LARGE.get());
                    }).build());

    private creative_tabs()
    {
    }
}
