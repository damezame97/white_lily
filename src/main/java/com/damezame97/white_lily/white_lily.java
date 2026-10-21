package com.damezame97.white_lily;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.crafting.prescription_recipes;
import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.item.creative_tabs;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * White Lily 模组主类。
 *
 * <p>目前只做四件事：</p>
 * <ol>
 *     <li>注册自定义属性（见 {@link attributes}）；</li>
 *     <li>注册物品（见 {@link items}）、创造模式物品栏（见 {@link creative_tabs}）
 *         与状态效果（见 {@link mob_effects}）；</li>
 *     <li>注册游戏规则 {@code Happy_mode}（见 {@link happy_mode}）——
 *         必须在任何世界数据被加载前完成，所以放在构造器里；</li>
 *     <li>注册 COMMON 配置（见 {@link Config}）；</li>
 * </ol>
 *
 * <p><b>重要</b>：本模组向属性注册表（{@code minecraft:attribute}）注入了条目，
 * 客户端与服务端必须同时安装，否则连接时会因注册表不一致而崩溃。</p>
 */
@Mod(white_lily.MODID)
public class white_lily
{
    /** 模组 ID，必须与 META-INF/mods.toml 及 gradle.properties 中的 mod_id 一致。 */
    public static final String MODID = "white_lily";

    private static final Logger LOGGER = LogUtils.getLogger();

    public white_lily(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // 游戏规则要在任何世界数据被加载之前注册完，这里先把 happy_mode 的静态初始化顶出来。
        // （只引用一次返回值，类初始化就会跑，GameRules.register 在里面。）
        happy_mode.bootstrap();

        // 把自定义属性注册表挂到模组事件总线，由 Forge 在 RegisterEvent 阶段统一注册
        attributes.ATTRIBUTES.register(modEventBus);

        // 物品与创造模式物品栏。注意顺序没有依赖：物品栏的图标是个 Supplier，渲染时才取。
        items.ITEMS.register(modEventBus);
        creative_tabs.CREATIVE_TABS.register(modEventBus);
        prescription_recipes.SERIALIZERS.register(modEventBus);

        // 状态效果（white_lilybuff 包）。
        // 注意：伤害类型<b>不</b>在这里注册——它是数据包注册表（minecraft:damage_type），
        // 由 data/white_lily/damage_type/*.json 声明，代码侧入口在 white_lilydamage 包。
        mob_effects.MOB_EFFECTS.register(modEventBus);

        // common setup
        modEventBus.addListener(this::commonSetup);

        // 服务端等游戏事件的监听入口
        MinecraftForge.EVENT_BUS.register(this);

        // 注册 COMMON 配置，让 Forge 帮我们生成并加载配置文件
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        if (Config.debugLogging)
        {
            LOGGER.info("White Lily: 已注册 {} 个自定义属性", attributes.ALL.size());
            for (RegistryObject<?> attribute : attributes.ALL)
            {
                LOGGER.info("White Lily:   - {}", attribute.getId());
            }

            LOGGER.info("White Lily: 已注册创造模式物品栏 {}（标题语言键 {}）",
                    creative_tabs.MISGUIDED_DELICACIES.getId(),
                    creative_tabs.MISGUIDED_DELICACIES_TITLE_KEY);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("White Lily: 服务端已启动");
    }
}
