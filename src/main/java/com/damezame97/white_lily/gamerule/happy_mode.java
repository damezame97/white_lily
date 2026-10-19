package com.damezame97.white_lily.gamerule;

import com.damezame97.white_lily.debugging.debug_file;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * 游戏规则 {@code Happy_mode}。
 *
 * <h2>它能干什么</h2>
 * <ol>
 *     <li><b>建世界时能开关</b>：它是一条普通的布尔游戏规则（类别 {@code MISC}），
 *         所以原版建世界界面的「更多」页 →「游戏规则」里会多出一行 {@code Happy_mode} 的开关。</li>
 *     <li><b>游戏内改不动</b>：进了世界之后再 {@code /gamerule Happy_mode true/false}，
 *         值会被立刻拉回世界 json 里记的那个值（见下面的判断）。</li>
 *     <li><b>值落在世界 json 里</b>：世界的 {@code Debugging_whitelilymod.json} 里会多一项
 *         {@code happy_mode}，世界第一次生成时写进去（老世界缺这一项会自动补上）。</li>
 * </ol>
 *
 * <h2>「游戏内改不动」是怎么做到的</h2>
 * <p>{@code GameRules.BooleanValue.create(默认值, 改动回调)} 的第二个参数会在<b>每次</b>
 * {@code BooleanValue#set(...)} 之后被调用。关键在于「谁在改」：</p>
 * <ul>
 *     <li>原版建世界/编辑存档那个游戏规则界面，
 *         {@code EditGameRulesScreen$BooleanRuleEntry} 里是写死的
 *         {@code value.set(新值, (MinecraftServer) null)}——<b>server 永远是 null</b>，
 *         所以 {@code server == null} 就是「这是建世界那个开关」，放行；</li>
 *     <li>{@code /gamerule} 命令走的是 `GameRuleCommand`，它传的是真的服务器实例，
 *         所以 {@code server != null} 就是「游戏内改动」——这时读世界 json 里的
 *         {@code happy_mode} 当权威值，不一样就把值设回去。</li>
 * </ul>
 * <p>设回去时故意传 {@code null}，这样不会再触发一次本回调（不会递归）。</p>
 *
 * <h2>注册时机</h2>
 * <p>{@code GameRules.register} 必须在任何世界数据被加载<b>之前</b>跑完，所以主类
 * {@code white_lily} 的构造器里会调一次 {@link #bootstrap()} 把静态初始化顶出来。</p>
 */
public final class happy_mode
{
    /** 规则名（游戏里就是 {@code /gamerule Happy_mode ...}）。 */
    public static final String NAME = "Happy_mode";

    /** 界面上的显示名（原版「游戏规则」界面就是用 {@code gamerule.<名字>} 取名字的）。 */
    public static final String TITLE_KEY = "gamerule." + NAME;

    /** 悬停说明（原版就用这个键）。 */
    public static final String INFO_KEY = TITLE_KEY + ".description";

    /** 彩蛋：在开关上悬停 + 按住 Shift 时显示的那句。 */
    public static final String PARTY_KEY = TITLE_KEY + ".party";

    /** 规则本体；默认关。 */
    public static final GameRules.Key<GameRules.BooleanValue> RULE = GameRules.register(
            NAME,
            GameRules.Category.MISC,
            GameRules.BooleanValue.create(false, (server, value) ->
            {
                // server == null：原版建世界/编辑存档的界面那条路 —— 那就是开关本身，放行
                if (server == null)
                {
                    return;
                }

                // 剩下的都是游戏内改动：以世界 json 里记的值为准，不一样就拉回来
                boolean locked = debug_file.readHappyMode(server).orElse(value.get());
                if (value.get() != locked)
                {
                    value.set(locked, null);
                }
            }));

    /** 让本类的静态初始化（= 注册这条游戏规则）在模组加载期就发生。 */
    public static GameRules.Key<GameRules.BooleanValue> bootstrap()
    {
        return RULE;
    }

    /** 读当前值。 */
    public static boolean isEnabled(MinecraftServer server)
    {
        return server.getGameRules().getBoolean(RULE);
    }

    private happy_mode()
    {
    }
}
