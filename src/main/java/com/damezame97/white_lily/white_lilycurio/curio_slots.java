package com.damezame97.white_lily.white_lilycurio;

import com.damezame97.white_lily.white_lily;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组的 Curios 饰品栏定义 —— {@code Improvement}（改良）。
 *
 * <h2>为什么这里没有 DeferredRegister</h2>
 * <p>Curios 5.x 的<b>栏位</b>不是注册表项，而是<b>数据包</b>：定义文件放在
 * {@code data/<命名空间>/curios/slots/<栏位 id>.json}，由 Curios 自己的
 * {@code CuriosSlotManager} 在资源重载时读取。所以栏位「注册」这件事在 Java 侧没有对应的
 * {@code DeferredRegister}，本类只提供 Java 侧要用的那几个常量（id、语言键、贴图 id），
 * 真正的定义在 {@code resources/data/white_lily/curios/slots/improvement.json}。</p>
 *
 * <h2>贴图为什么要单独声明图集</h2>
 * <p>Curios 画栏位底图用的是「方块图集里的一个 sprite」（{@code Slot#setBackground} 传的是
 * {@code InventoryMenu.BLOCK_ATLAS} + sprite id），不是直接读贴图文件。sprite id 与贴图文件的
 * 换算规则是 {@code <命名空间>:<textures/ 之后的路径>}，所以
 * {@code assets/white_lily/textures/slot/improvement.png} 的 sprite id 是
 * {@code white_lily:slot/improvement}。而这个 sprite 要真的被拼进图集，必须有图集声明：
 * 本模组在 {@code assets/minecraft/atlases/blocks.json} 里声明了一个 directory 源
 * （{@code source: "slot"} + {@code prefix: "slot/"}），它的效果就是把
 * <b>所有模组</b>的 {@code assets/*&#47;textures/slot/**.png} 都按 {@code slot/} 前缀拼进 blocks 图集。
 * Curios 自己也有一份同样的声明，两份同名文件在重载时靠 {@code ResourceManager#getResourceStack}
 * 合并（不是覆盖），所以本模组即使单独装也能正常显示栏位贴图。</p>
 *
 * <h2>以后加新的饰品栏</h2>
 * <ol>
 *     <li>在 {@code data/white_lily/curios/slots/} 下加 {@code <id>.json}；</li>
 *     <li>贴图放 {@code assets/white_lily/textures/slot/<id>.png}；</li>
 *     <li>中英 lang 各加一条 {@code curios.identifier.<id>}；</li>
 *     <li>在本类里补上 id / 语言键 / 贴图 id 常量。</li>
 * </ol>
 *
 * <h2>现在有两个栏</h2>
 * <ul>
 *     <li>{@code improvement}（改良）—— 数量与内容按档位发放，见
 *         {@link improvement_loadout}；</li>
 *     <li>{@code phantom_fragments}（幻影碎片）—— 只有数量按档位（1/2/4/5），
 *         不自动发放任何东西，见 {@link phantom_fragments_slots}。</li>
 * </ul>
 */
public final class curio_slots
{
    /** 栏位 id。Curios 的栏位 id 就是数据包文件名，且注册名必须小写，所以显示名走 lang。 */
    public static final String IMPROVEMENT = "improvement";

    /** 第二个栏位 id：幻影碎片。 */
    public static final String PHANTOM_FRAGMENTS = "phantom_fragments";

    /**
     * 栏位 id 的语言键前缀。Curios 用 {@code curios.identifier.<栏位 id>} 取栏位显示名，
     * 这个键写进<b>本模组</b>的 lang 文件一样生效（语言键是全局的）。
     */
    public static final String NAME_KEY_PREFIX = "curios.identifier.";

    /** 「改良 / Improvement」的语言键。 */
    public static final String IMPROVEMENT_NAME_KEY = NAME_KEY_PREFIX + IMPROVEMENT;

    /** 栏位底图 sprite id（= {@code assets/white_lily/textures/slot/improvement.png}）。 */
    public static final ResourceLocation IMPROVEMENT_ICON =
            new ResourceLocation(white_lily.MODID, "slot/" + IMPROVEMENT);

    /** 「幻影碎片 / Phantom fragments」的语言键（<b>按需求留空</b>，内容由作者自己填）。 */
    public static final String PHANTOM_FRAGMENTS_NAME_KEY = NAME_KEY_PREFIX + PHANTOM_FRAGMENTS;

    /** 幻影碎片栏位底图 sprite id（= {@code assets/white_lily/textures/slot/phantom_fragments.png}）。 */
    public static final ResourceLocation PHANTOM_FRAGMENTS_ICON =
            new ResourceLocation(white_lily.MODID, "slot/" + PHANTOM_FRAGMENTS);

    /** 本模组所有栏位共用的校验器：物品需带 {@code curios:<栏位 id>} 标签（或 {@code curios:curio}）。 */
    public static final String VALIDATOR_TAG = "curios:tag";

    private curio_slots()
    {
    }
}
