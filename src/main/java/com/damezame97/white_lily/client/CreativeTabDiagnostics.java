package com.damezame97.white_lily.client;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.white_lily;
import com.mojang.logging.LogUtils;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * 客户端诊断：把 Forge 真正会拿去渲染的「创造模式物品栏顺序表」原样打进日志。
 *
 * <p>客户端渲染标签栏时读的是
 * {@link CreativeModeTabRegistry#getSortedCreativeModeTabs()}，而不是
 * {@code BuiltInRegistries.CREATIVE_MODE_TAB} 的注册表内容。两者可能不一致：
 * Forge 的 {@code sortTabs()} 会先排除 hotbar/search/op_blocks/inventory，
 * 再按 tabsBefore/tabsAfter 的边关系做拓扑排序。</p>
 *
 * <p>打开 {@code debugLogging} 后，启动一次 runClient 就能在
 * {@code run/logs/latest.log} 里看到客户端眼里的完整顺序、每一栏的标题与图标物品。
 * 顺序号后面第 11 位起就是「第 2 页」。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreativeTabDiagnostics
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private CreativeTabDiagnostics()
    {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event)
    {
        if (!Config.debugLogging)
        {
            return;
        }

        event.enqueueWork(() ->
        {
            List<CreativeModeTab> tabs = CreativeModeTabRegistry.getSortedCreativeModeTabs();
            LOGGER.info("White Lily: 客户端创造物品栏排序表共 {} 栏（每页 10 栏，第 11 栏起在第 2 页）", tabs.size());

            for (int i = 0; i < tabs.size(); i++)
            {
                CreativeModeTab tab = tabs.get(i);
                ResourceLocation id = CreativeModeTabRegistry.getName(tab);

                String title;
                try
                {
                    title = tab.getDisplayName().getString();
                }
                catch (Throwable t)
                {
                    title = "<取标题出错: " + t + ">";
                }

                String icon;
                try
                {
                    icon = String.valueOf(tab.getIconItem());
                }
                catch (Throwable t)
                {
                    icon = "<取图标出错: " + t + ">";
                }

                // 注意：这里不打印 displayItems——客户端在 setup 阶段还没构建各栏内容，
                // 那时读出来的都是空的。
                LOGGER.info("White Lily:   {}. {} | 标题={} | 图标={}", i + 1, id, title, icon);
            }
        });
    }
}
