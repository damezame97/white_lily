package com.damezame97.white_lily.white_lilycurio;

import com.damezame97.white_lily.debugging.debug_file;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.util.ISlotHelper;

/**
 * 「幻影碎片」（{@code phantom_fragments}）饰品栏的档位规则。
 *
 * <h2>档位表</h2>
 * <table border="1">
 *     <caption>档位 → 栏位数量</caption>
 *     <tr><th>档位</th><th>难度</th><th>栏位数量</th></tr>
 *     <tr><td>1</td><td>和平 PEACEFUL</td><td>1</td></tr>
 *     <tr><td>2</td><td>简单 EASY</td><td>2</td></tr>
 *     <tr><td>3</td><td>普通 NORMAL</td><td>4</td></tr>
 *     <tr><td>4</td><td>困难 HARD</td><td>5</td></tr>
 * </table>
 * <p>注意<b>不是</b>「档位 = 数量」：普通是 4、困难是 5，所以单独列了
 * {@link #slotsForLevel(int)}，不要照抄 {@link improvement_loadout} 那套。</p>
 *
 * <h2>档位从哪来</h2>
 * <p>和「改良」一样读世界文件夹下的 {@link debug_file}
 * （{@code Debugging_whitelilymod.json}）的 {@code difficulty_level}，<b>以 json 内的参数为准</b>，
 * 不是当前世界难度。玩家每次进入世界都会按它重设一遍数量。</p>
 *
 * <h2>这个栏位不发东西</h2>
 * <p>与 {@link improvement_loadout} 不同，这里只改<b>数量</b>：栏里放什么完全由玩家/作者自己决定，
 * 登录时不会往里面塞任何物品，也不会清空已有内容。</p>
 *
 * <h2>数量是怎么改的</h2>
 * <p>数据包里的 {@code size} 是 1（基础值），每个玩家各不相同的那部分走 Curios 的
 * 栏位修正器：{@link ISlotHelper#setSlotsForType(String, net.minecraft.world.entity.LivingEntity, int)}
 * 会把差值记进 {@code persistentModifiers}（随 Curios 库存存进 NBT）。
 * 改完必须立刻 {@link ICurioStacksHandler#update()}：{@code grow/shrink} 只打「待更新」标记，
 * 真正扩容在 {@code update()} 里做。</p>
 */
public final class phantom_fragments_slots
{
    /** 档位 1（和平）的栏位数量。 */
    public static final int SLOTS_PEACEFUL = 1;

    /** 档位 2（简单）的栏位数量。 */
    public static final int SLOTS_EASY = 2;

    /** 档位 3（普通）的栏位数量。 */
    public static final int SLOTS_NORMAL = 4;

    /** 档位 4（困难）的栏位数量。 */
    public static final int SLOTS_HARD = 5;

    private phantom_fragments_slots()
    {
    }

    /** 档位 → 栏位数量：和平 1 / 简单 2 / 普通 4 / 困难 5。 */
    public static int slotsForLevel(int level)
    {
        return switch (improvement_loadout.clampLevel(level))
        {
            case 1 -> SLOTS_PEACEFUL;
            case 2 -> SLOTS_EASY;
            case 3 -> SLOTS_NORMAL;
            default -> SLOTS_HARD;
        };
    }

    /** 难度 → 栏位数量（内部换算成档位）。 */
    public static int slotsForDifficulty(Difficulty difficulty)
    {
        return slotsForLevel(improvement_loadout.levelForDifficulty(difficulty));
    }

    /**
     * 玩家每次进入世界时调一次：读调试文件的档位，把栏位数量重设成对应值。
     *
     * @return 本次用的档位（1~4）
     */
    public static int syncFromDebugFile(Player player)
    {
        MinecraftServer server = player.getServer();
        int level = server != null
                ? debug_file.loadOrCreateLevel(server)
                : improvement_loadout.levelForDifficulty(player.level().getLevelData().getDifficulty());

        if (!player.level().isClientSide())
        {
            apply(player, level);
        }

        return level;
    }

    /** 按难度重设（内部换算成档位）。 */
    public static void apply(Player player, Difficulty difficulty)
    {
        apply(player, improvement_loadout.levelForDifficulty(difficulty));
    }

    /** 按档位重设栏位数量（不动栏里的东西）。 */
    public static void apply(Player player, int level)
    {
        ISlotHelper slotHelper = CuriosApi.getSlotHelper();
        slotHelper.setSlotsForType(curio_slots.PHANTOM_FRAGMENTS, player, slotsForLevel(level));

        // grow/shrink 只打标记，真正扩容要立刻 update()。
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getStacksHandler(curio_slots.PHANTOM_FRAGMENTS)
                        .ifPresent(ICurioStacksHandler::update));
    }
}
