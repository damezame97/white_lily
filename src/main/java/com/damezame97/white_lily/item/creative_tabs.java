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
                        // ⚠ 这一栏至少要有一件物品，否则 shouldDisplay() 为 false，客户端任何一页都不会画它。
                        // 之前的占位物品 items.TAB_ICON 已经撤掉（它只负责当标签图标），换成真物品：
                        output.accept(items.MAGIC_APPLE.get());
                    })
                    .build());

    private creative_tabs()
    {
    }
}
