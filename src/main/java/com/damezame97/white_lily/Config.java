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

    private static final ForgeConfigSpec.BooleanValue ENABLE_SPECIAL_WINDOW = BUILDER
            .comment("是否用特殊窗口替代创建世界界面中的快乐模式开关")
            .define("Enable special window", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean debugLogging;
    public static boolean enableSpecialWindow;

    /** 保存配置界面的选择；下一次打开创建世界界面时使用新入口。 */
    public static void setSpecialWindowEnabled(boolean enabled)
    {
        ENABLE_SPECIAL_WINDOW.set(enabled);
        enableSpecialWindow = enabled;
        SPEC.save();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }

        debugLogging = DEBUG_LOGGING.get();
        enableSpecialWindow = ENABLE_SPECIAL_WINDOW.get();
    }
}
