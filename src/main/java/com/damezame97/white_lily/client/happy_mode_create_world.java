package com.damezame97.white_lily.client;

import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.white_lily;
import com.mojang.logging.LogUtils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 把 {@code Happy_mode} 的开关放进原版「创建世界」界面的「世界」页（纯客户端）。
 *
 * <h2>为什么不在「更多 → 游戏规则」里等它</h2>
 * <p>游戏规则本身会出现在「更多」页的「游戏规则」界面里，而且按 {@code GameRules.Category}
 * 分组（我们的规则是 MISC，所以显示在「杂项」下面）——那是原版的固定布局，位置改不了。
 * 所以这里另外往「世界」页塞了一个开关：它读写的是同一份 {@code GameRules}
 * （{@code WorldCreationUiState#getGameRules()}，也就是建世界用的那份草稿），
 * 所以两处永远是同一个值。</p>
 *
 * <h2>找「世界」页：全走公开 API</h2>
 * <ol>
 *     <li>{@code Screen#children()} 里捞出 {@link TabNavigationBar}（原版就是把它当普通控件加进去的）；</li>
 *     <li>{@code TabNavigationBar#children()} 返回的是 {@link TabButton}，而 {@code TabButton#tab()} 是 public；</li>
 *     <li>拿到的 {@link Tab} 再看标题语言键是不是 {@link #WORLD_TAB_TITLE_KEY}——比语言键，
 *         所以不依赖下标、换语言也认得出。</li>
 * </ol>
 *
 * <h2>⚠ 往页里加控件要反射，而且名字必须「双份」</h2>
 * <p>「往某个页里加控件」没有任何公开 API，只能够到 {@link GridLayoutTab} 的 {@code layout}
 * 字段；算「下一行行号」还得看 {@link GridLayout} 的 {@code cellInhabitants}。
 * 这两个字段分别是 protected / private。</p>
 * <p><b>这里有个大坑</b>：开发环境里字段是官方名（{@code layout}），生产环境里是 SRG 名
 * （{@code f_267367_}）。只写官方名的话，开发能跑、<b>打包后必挂</b>
 * （{@code NoSuchFieldException: tabs}，然后开关根本不显示）。所以下面每个成员都同时给
 * 「官方名 + SRG 名」两个候选，按顺序试——两边都能命中，是确定的，不是猜。</p>
 * <p>理论上更「正统」的做法是 Access Transformer，但 ForgeGradle 6（47.4.x）在生成
 * AT 产物时会递归爆栈（{@code _at_<hash>} 那个 artifact 解析时又去建 compileJava 任务），
 * 这个项目上实测直接 {@code StackOverflowError}，所以退回到双名反射。</p>
 *
 * <h2>摆放</h2>
 * <p>行号必须是「现有格子里最大的最后占用行 + 1」：{@code GridLayout.arrangeElements}
 * 拿行号当数组下标用（{@code rowHeights[row]}）。我们占满两列，和这个页上原版的
 * 「生成结构 / 奖励箱」那几行对齐。</p>
 *
 * <h2>Shift 彩蛋</h2>
 * <p>开关的 {@code renderWidget} 每帧看一眼「鼠标是不是悬在它上面 + 有没有按 Shift」，
 * 是就把标题换成 {@link #PARTY_KEY}（中英各一份）。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class happy_mode_create_world
{
    /** 原版「世界」页的标题语言键——用它认页签，不依赖下标，也不怕换语言。 */
    public static final String WORLD_TAB_TITLE_KEY = "createWorld.tab.world.title";

    /** 开关上显示的名字（在 {@link happy_mode} 里定义，和「游戏规则」界面共用同一份文本）。 */
    public static final String TITLE_KEY = happy_mode.TITLE_KEY;

    /** 悬停说明。 */
    public static final String INFO_KEY = happy_mode.INFO_KEY;

    /** 彩蛋：鼠标悬在开关上 + 按住 Shift 时改成这句。 */
    public static final String PARTY_KEY = happy_mode.PARTY_KEY;

    // ==================================================================
    // 需要反射的成员：每项都是「官方名 + SRG 名」两个候选
    // ==================================================================

    /** {@code GridLayoutTab.layout} */
    private static final String[] LAYOUT_FIELD = {"layout", "f_267367_"};

    /** {@code GridLayout.cellInhabitants} */
    private static final String[] CELLS_FIELD = {"cellInhabitants", "f_263660_"};

    /** {@code GridLayout.CellInhabitant.getLastOccupiedRow()}（1.20.1 里是 {@code m_264275_}） */
    private static final String[] LAST_ROW_METHOD = {"getLastOccupiedRow", "m_264275_"};

    /** 和原版这个页里那几个开关一样的宽度。 */
    private static final int WIDGET_WIDTH = 310;
    private static final int WIDGET_HEIGHT = 20;

    /** 这个页的布局是两列。 */
    private static final int WORLD_TAB_COLUMNS = 2;

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof CreateWorldScreen screen))
        {
            return;
        }

        GridLayoutTab worldTab = findWorldTab(screen);
        if (worldTab == null)
        {
            LOGGER.warn("White Lily: 创建世界界面里没找到「世界」页，Happy_mode 开关这次不显示");
            return;
        }

        GridLayout layout = layoutOf(worldTab);
        if (layout == null)
        {
            LOGGER.warn("White Lily: 拿不到「世界」页的布局，Happy_mode 开关这次不显示");
            return;
        }

        GameRules rules = screen.getUiState().getGameRules();
        Checkbox created = createSwitch(rules);
        created.setTooltip(Tooltip.create(Component.translatable(INFO_KEY)));

        int row = nextFreeRow(layout);
        layout.addChild(created, row, 0, 1, WORLD_TAB_COLUMNS);

        LOGGER.info("White Lily: 已把 Happy_mode 开关放进「世界」页（第 {} 行）", row);
    }

    // ==================================================================
    // 开关本体
    // ==================================================================

    /**
     * 一个 {@link Checkbox}：勾选框 + 标题；勾选状态直接读写建世界那份 {@code GameRules}。
     */
    private static Checkbox createSwitch(GameRules rules)
    {
        return new Checkbox(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable(TITLE_KEY), rules.getBoolean(happy_mode.RULE), true)
        {
            @Override
            public void onPress()
            {
                super.onPress();
                // server 传 null：和原版游戏规则界面一样，这属于「建世界时那条路」，放行
                rules.getRule(happy_mode.RULE).set(selected(), null);
            }

            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
            {
                // 先去「更多→游戏规则」改过的话，把勾选状态纠正过来（顺手把值再写回去，值不变）
                syncFrom(this, rules);

                setMessage(isMouseOver(mouseX, mouseY) && Screen.hasShiftDown()
                        ? Component.translatable(PARTY_KEY)
                        : Component.translatable(TITLE_KEY));

                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }
        };
    }

    /** 让勾选框的显示状态跟上 {@code GameRules}。 */
    private static void syncFrom(Checkbox box, GameRules rules)
    {
        if (box.selected() != rules.getBoolean(happy_mode.RULE))
        {
            // onPress() 会翻一下自己的状态，我们的重写会把这个（已经正确的）值写回同一份 GameRules，
            // 所以规则值不动，只是把显示纠正过来。
            box.onPress();
        }
    }

    // ==================================================================
    // 找页签
    // ==================================================================

    /** 找出「世界」页那个 {@link GridLayoutTab}；找不到返回 {@code null}。 */
    private static GridLayoutTab findWorldTab(CreateWorldScreen screen)
    {
        for (GuiEventListener child : screen.children())
        {
            if (!(child instanceof TabNavigationBar bar))
            {
                continue;
            }

            for (GuiEventListener entry : bar.children())
            {
                if (entry instanceof TabButton button)
                {
                    Tab tab = button.tab();
                    if (tab instanceof GridLayoutTab gridTab && hasWorldTitle(tab))
                    {
                        return gridTab;
                    }
                }
            }
        }
        return null;
    }

    /** 页签标题是不是「世界」——比的是语言键，所以换语言也认得出。 */
    private static boolean hasWorldTitle(Tab tab)
    {
        return tab.getTabTitle().getContents() instanceof TranslatableContents contents
                && WORLD_TAB_TITLE_KEY.equals(contents.getKey());
    }

    // ==================================================================
    // 反射（官方名 / SRG 名 双查）
    // ==================================================================

    /** 拿 {@link GridLayoutTab} 里的布局；失败返回 {@code null}。 */
    private static GridLayout layoutOf(GridLayoutTab tab)
    {
        Field field = findField(GridLayoutTab.class, LAYOUT_FIELD);
        if (field == null)
        {
            return null;
        }

        try
        {
            return (GridLayout) field.get(tab);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.warn("White Lily: 读「世界」页布局失败：{}", e.toString());
            return null;
        }
    }

    /**
     * 下一个空行号。
     *
     * <p>{@code GridLayout#arrangeElements} 拿行号当数组下标用（{@code rowHeights[row]}），
     * 所以只能取「现有格子里最大的最后占用行 + 1」，不能随手给个大数字。</p>
     */
    private static int nextFreeRow(GridLayout layout)
    {
        Field cellsField = findField(GridLayout.class, CELLS_FIELD);
        if (cellsField == null)
        {
            return 0;
        }

        try
        {
            if (!(cellsField.get(layout) instanceof List<?> cells))
            {
                return 0;
            }

            int last = -1;
            for (Object cell : cells)
            {
                Method lastRow = findMethod(cell.getClass(), LAST_ROW_METHOD);
                if (lastRow != null)
                {
                    last = Math.max(last, (int) lastRow.invoke(cell));
                }
            }
            return last + 1;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.warn("White Lily: 算「世界」页下一行失败：{}", e.toString());
            return 0;
        }
    }

    /**
     * 按顺序试 {@code candidates} 里每个名字，拿到第一个存在的字段。
     *
     * <p>开发环境用官方名，生产环境用 SRG 名——两个都试，保证哪边都对。</p>
     */
    private static Field findField(Class<?> owner, String... candidates)
    {
        for (String name : candidates)
        {
            try
            {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                // 试下一个名字
            }
            catch (RuntimeException e)
            {
                LOGGER.warn("White Lily: 取字段 {}#{} 出错：{}", owner.getSimpleName(), name, e.toString());
            }
        }
        return null;
    }

    /** 同 {@link #findField}，但找的是无参方法。 */
    private static Method findMethod(Class<?> owner, String... candidates)
    {
        for (String name : candidates)
        {
            try
            {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                // 试下一个名字
            }
            catch (RuntimeException e)
            {
                LOGGER.warn("White Lily: 取方法 {}#{} 出错：{}", owner.getSimpleName(), name, e.toString());
            }
        }
        return null;
    }

    private happy_mode_create_world()
    {
    }
}
