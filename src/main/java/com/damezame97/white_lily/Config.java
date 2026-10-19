package com.damezame97.white_lily;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * White Lily 的通用配置。
 *
 * <p>配置文件由 Forge 生成在 {@code config/white_lily-common.toml}。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("是否在游戏启动时把 White Lily 注册的自定义属性打印到日志（排查问题时打开）")
            .define("debugLogging", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean debugLogging;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        debugLogging = DEBUG_LOGGING.get();
    }
}
