package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.basic_material_item;
import com.damezame97.white_lily.item.creative_tabs;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.white_lily;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Forge 通过注解发现测试。
@SuppressWarnings("unused")
@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
public class BasicMaterialsGameTests
{
    @GameTest(template = "empty")
    public static void basicMaterialsAndPrescriptionDescriptions(GameTestHelper helper)
    {
        CreativeModeTab tab = creative_tabs.MISGUIDED_DELICACIES.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        for (String name : List.of("coarse_cloth", "thin_cloth_strip", "cork_stopper",
                "beeswax_block", "charcoal_powder", "blank_label"))
        {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(white_lily.MODID, name);
            var item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id), "Missing material: " + id);
            helper.assertTrue(item instanceof basic_material_item, "普通材料注册缺失或类型错误：" + id);
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(stack.getMaxStackSize() == 64 && !stack.isDamageableItem()
                    && !stack.isEdible() && !stack.hasFoil(), "材料应为普通 64 堆叠物品：" + id);
            helper.assertTrue(tab.getDisplayItems().contains(stack) && tab.getSearchTabDisplayItems().contains(stack),
                    "创造展示和搜索内容应包含：" + id);
            List<Component> tooltip = new ArrayList<>();
            item.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
            helper.assertTrue(tooltip.size() == 3, "材料三行简介应直接显示：" + id);
            for (Component line : tooltip)
            {
                helper.assertTrue(Objects.equals(line.getStyle().getColor(), TextColor.fromRgb(0xAAAAAA)),
                        "简介应使用普通灰色：" + id);
            }
        }
        for (var item : List.of(items.BLANK_PRESCRIPTION.get(), items.REVEALED_PRESCRIPTION.get()))
        {
            List<Component> tooltip = new ArrayList<>();
            item.appendHoverText(new ItemStack(item), helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
            helper.assertTrue(tooltip.size() == 1
                    && Objects.equals(tooltip.get(0).getStyle().getColor(), TextColor.fromRgb(0xAAAAAA)),
                    "处方简介应直接显示，无 Shift 提示");
        }
        helper.succeed();
    }
}
