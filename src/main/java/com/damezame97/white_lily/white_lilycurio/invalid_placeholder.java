package com.damezame97.white_lily.white_lilycurio;

import com.damezame97.white_lily.client.invalid_placeholder_tooltip;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 饰品「无效占位符」（{@code invalid_placeholder}）。
 *
 * <p>继承 {@link improvement_curio}，所以自动带上三条统一特性：拿不下来、死了不掉、
 * 非创造玩家的物品栏里出现就删。</p>
 *
 * <h2>名称与介绍（跟 magic_apple 一个路子，但有两点不同）</h2>
 * <ol>
 *     <li><b>名称会自己变色</b>：名字在 lang 里，渲染时由 {@link #colorize} 给每个字配一个
 *         原版 16 色代码，整条名字在「两个指定颜色」之间铺一条渐变，并且随时间平移（所以看着是流动的）。
 *         两端颜色、播放速度都写在下面几个常量/语言键里。</li>
 *     <li><b>Shift 是「切换」而不是「按住」</b>：按一下 Shift 在
 *         {@code tooltip.text.1.*} 与 {@code tooltip.text.2.*} 两段介绍之间来回切，
 *         松手后保持切换后的那一段。状态与按键钩子在客户端类
 *         {@link invalid_placeholder_tooltip} 里。</li>
 * </ol>
 *
 * <h2>要改内容改哪里</h2>
 * <ul>
 *     <li>名字：{@code item.white_lily.invalid_placeholder}（现在是<b>空例子</b>，填上就显示）；</li>
 *     <li>渐变两端颜色：{@code .name_color.from} / {@code .name_color.to}，值是原版颜色代码，
 *         例如 {@code "§b"}（青）、{@code "§d"}（粉）；</li>
 *     <li>提示语 / 表头：{@code .tooltip.hint} / {@code .tooltip.header}；</li>
 *     <li>两段介绍正文：{@code .tooltip.text.1.N}（默认那段）与 {@code .tooltip.text.2.N}（切换后那段），
 *         每段预留了 {@link #TOOLTIP_LINE_COUNT} 行，值都是空串，填了才显示。</li>
 * </ul>
 */
public class invalid_placeholder extends improvement_curio
{
    /** 注册名，同时也是贴图/模型/语言键的路径部分。 */
    public static final String NAME = "invalid_placeholder";

    /** 名字的语言键（就是 {@code Item#getDescriptionId} 的结果，显式写出来方便引用）。 */
    public static final String NAME_KEY = "item.white_lily.invalid_placeholder";

    /** 名字渐变「起点色」的语言键，值是原版 16 色代码，例如 {@code "§b"}。 */
    public static final String NAME_COLOR_FROM_KEY = "item.white_lily.invalid_placeholder.name_color.from";

    /** 名字渐变「终点色」的语言键。 */
    public static final String NAME_COLOR_TO_KEY = "item.white_lily.invalid_placeholder.name_color.to";

    /** 名字渐变跑完一整轮（起点→终点→起点）的毫秒数；数值越小颜色流动越快。 */
    public static final long NAME_GRADIENT_PERIOD_MS = 4000L;

    /** 没切换时那一行提示语的语言键（内容就是「按 Shift 切换文本介绍」）。 */
    public static final String TOOLTIP_HINT_KEY = "item.white_lily.invalid_placeholder.tooltip.hint";

    /** 切换之后才多出来的表头语言键。 */
    public static final String TOOLTIP_HEADER_KEY = "item.white_lily.invalid_placeholder.tooltip.header";

    /** 两段介绍正文的语言键前缀：后面拼 {@code 1.} 或 {@code 2.}，再拼行号。 */
    public static final String TOOLTIP_TEXT_KEY_PREFIX = "item.white_lily.invalid_placeholder.tooltip.text.";

    /** 每段介绍最多显示几行（要和 lang 里预留的 {@code text.N.M} 数量一致）。 */
    public static final int TOOLTIP_LINE_COUNT = 3;

    /** 原版 16 色（{@code §0}~{@code §f}）的 RGB，下标与 {@link #PALETTE_CODES} 一一对应。 */
    private static final int[] PALETTE_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** 16 色代码（不含 {@code §}），下标与 {@link #PALETTE_RGB} 一一对应。 */
    private static final String PALETTE_CODES = "0123456789abcdef";

    /** 认不出颜色代码时用的兜底色：青。 */
    private static final int FALLBACK_FROM = 0x0b;

    /** 认不出颜色代码时用的兜底色：粉。 */
    private static final int FALLBACK_TO = 0x0d;

    // ==================================================================
    // 名字：语言键 + 16 色渐变
    // ==================================================================

    /**
     * 名字 = 语言键取出来的文本，逐字套上「当前时刻」的颜色代码。
     *
     * <p>注意：语言键的值现在是空的，所以名字暂时是空字符串；填上
     * {@link #NAME_KEY} 就正常显示。</p>
     */
    @Override
    public Component getName(ItemStack stack)
    {
        String base = Component.translatable(this.getDescriptionId(stack)).getString();
        if (base.isEmpty())
        {
            return Component.literal("");
        }

        return Component.literal(colorize(
                base,
                Util.getMillis(),
                langValue(NAME_COLOR_FROM_KEY),
                langValue(NAME_COLOR_TO_KEY)));
    }

    /**
     * 给文本铺一条「两端颜色之间」的渐变，并按时间让它流动。
     *
     * <p>做法：把名字的每个字映射到 0~1 的位置，加上一个随时间推进的相位，再用三角波折回来
     * （0→1→0，所以是一来一回，不会突然跳回去），在两端颜色的 RGB 之间插值，
     * 最后取<b>离插值结果最近的原版 16 色</b>——1.20.1 的聊天文本只认这 16 个颜色代码，
     * 不能写 {@code #RRGGBB}。</p>
     *
     * <p>纯函数，方便测试与你自己调：{@code millis} 是动画时钟，{@code fromCode}/{@code toCode}
     * 是形如 {@code "§b"}（或 {@code "b"}）的颜色代码。</p>
     */
    public static String colorize(String text, long millis, String fromCode, String toCode)
    {
        if (text.isEmpty())
        {
            return text;
        }

        int from = paletteIndex(fromCode);
        int to = paletteIndex(toCode);
        int fromRgb = PALETTE_RGB[from < 0 ? FALLBACK_FROM : from];
        int toRgb = PALETTE_RGB[to < 0 ? FALLBACK_TO : to];

        // 一个 0~1 的相位，随时间线性推进
        long period = Math.max(1L, NAME_GRADIENT_PERIOD_MS);
        double phase = (millis % period) / (double) period;

        StringBuilder out = new StringBuilder(text.length() * 3);
        for (int i = 0; i < text.length(); i++)
        {
            double along = text.length() <= 1 ? 0.0D : i / (double) (text.length() - 1);

            // 名字上的位置 + 时间相位，绕回 0~1
            double t = (along + phase) % 1.0D;
            // 三角波：0→1→0，来回渐变
            double mix = t <= 0.5D ? t * 2.0D : (1.0D - t) * 2.0D;

            int blended = blend(fromRgb, toRgb, mix);
            out.append('§').append(PALETTE_CODES.charAt(nearestPaletteIndex(blended)));
            out.append(text.charAt(i));
        }

        return out.toString();
    }

    /** 颜色代码（{@code "§b"} 或 {@code "b"}）在 16 色表里的下标；不认识返回 -1。 */
    public static int paletteIndex(String code)
    {
        if (code == null || code.isEmpty())
        {
            return -1;
        }

        char last = code.charAt(code.length() - 1);
        return PALETTE_CODES.indexOf(Character.toLowerCase(last));
    }

    /** 取一组 RGB 里离 16 色表最近的那个下标（并列时取表里靠前的，结果稳定）。 */
    public static int nearestPaletteIndex(int rgb)
    {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE_RGB.length; i++)
        {
            int pr = (PALETTE_RGB[i] >> 16) & 0xFF;
            int pg = (PALETTE_RGB[i] >> 8) & 0xFF;
            int pb = PALETTE_RGB[i] & 0xFF;
            long distance = (long) (r - pr) * (r - pr)
                    + (long) (g - pg) * (g - pg)
                    + (long) (b - pb) * (b - pb);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** 两个 RGB 之间按 {@code mix}（0~1）插值。 */
    private static int blend(int fromRgb, int toRgb, double mix)
    {
        int r = (int) Math.round((((fromRgb >> 16) & 0xFF) * (1.0D - mix)) + (((toRgb >> 16) & 0xFF) * mix));
        int g = (int) Math.round((((fromRgb >> 8) & 0xFF) * (1.0D - mix)) + (((toRgb >> 8) & 0xFF) * mix));
        int b = (int) Math.round(((fromRgb & 0xFF) * (1.0D - mix)) + ((toRgb & 0xFF) * mix));
        return (r << 16) | (g << 8) | b;
    }

    // ==================================================================
    // 悬浮提示：Shift 切换两段介绍
    // ==================================================================

    /**
     * 提示 = 一行「按 Shift 切换」的提示语（切换后多一个表头）+ 当前那一段的介绍正文。
     *
     * <p>正文全部走语言键，值为空串的行会被跳过，所以 lang 里那几个空例子填之前什么都不显示。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);

        // 告诉客户端「这一帧正在看这件饰品」，Shift 的按键钩子靠这个判断要不要切换
        invalid_placeholder_tooltip.markHovered();
        boolean switched = invalid_placeholder_tooltip.isSwitched();

        tooltip.add(Component.translatable(TOOLTIP_HINT_KEY));
        if (switched)
        {
            tooltip.add(Component.translatable(TOOLTIP_HEADER_KEY));
        }

        String prefix = TOOLTIP_TEXT_KEY_PREFIX + (switched ? 2 : 1) + ".";
        for (int line = 1; line <= TOOLTIP_LINE_COUNT; line++)
        {
            String key = prefix + line;
            if (hasTranslatedText(key))
            {
                tooltip.add(Component.translatable(key));
            }
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 该语言键在「当前语言」里有没有非空内容。
     *
     * <p>用 {@link Component#getString()} 让 Minecraft 自己查语言表：键存在但值是空串时返回空串，
     * 键压根不存在时返回键名本身——两种都算「没内容」。</p>
     */
    private static boolean hasTranslatedText(String key)
    {
        String text = Component.translatable(key).getString();
        return !text.equals(key) && !text.isBlank();
    }

    /** 读语言键的值；键不存在时 {@code getString()} 会返回键名本身，这里原样返回，交给调用方兜底。 */
    private static String langValue(String key)
    {
        return Component.translatable(key).getString();
    }
}
