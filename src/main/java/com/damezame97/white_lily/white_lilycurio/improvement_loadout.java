package com.damezame97.white_lily.white_lilycurio;

import com.damezame97.white_lily.debugging.debug_file;
import com.damezame97.white_lily.item.items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.util.ISlotHelper;

import java.util.List;

/**
 * 「改良」（{@code improvement}）饰品栏的档位规则与「首次进入世界」时的发放逻辑。
 *
 * <h2>档位表</h2>
 * <table border="1">
 *     <caption>档位 → 栏位数量 / 装备内容</caption>
 *     <tr><th>档位</th><th>难度</th><th>栏位数量</th><th>装备</th></tr>
 *     <tr><td>1</td><td>和平 PEACEFUL</td><td>1</td><td>invalid_placeholder</td></tr>
 *     <tr><td>2</td><td>简单 EASY</td><td>2</td><td>invalid_placeholder + fate_compass</td></tr>
 *     <tr><td>3</td><td>普通 NORMAL</td><td>3</td><td>invalid_placeholder + fate_compass + improve_body</td></tr>
 *     <tr><td>4</td><td>困难 HARD</td><td>4</td><td>invalid_placeholder + fate_compass + improve_body + flesh_ring</td></tr>
 * </table>
 * <p>也就是「档位数字 = 栏位数量」，装备清单按同一张表往后叠。</p>
 *
 * <h2>档位从哪来（重点）</h2>
 * <p><b>不是</b>读当前世界难度，而是读世界文件夹下那份 {@link debug_file}（{@code Debugging_whitelilymod.json}）
 * 的 {@code difficulty_level}——<b>以 json 内的参数为准</b>。流程是：</p>
 * <ol>
 *     <li>世界加载时（{@code ServerStartedEvent}）／玩家首次进世界时，如果那份 json 不存在或坏了，
 *         就按<b>当时</b>的世界难度写一份（建图那一刻的难度）；</li>
 *     <li>之后一律读 json：想改档位就直接改 {@code difficulty_level}，再进游戏就按改过的值发。</li>
 * </ol>
 *
 * <h2>栏位数量是怎么改的</h2>
 * <p>栏位的基础数量写死在数据包里（{@code data/white_lily/curios/slots/improvement.json} 的
 * {@code size}，现在是 1），每个玩家各不相同的那部分由 Curios 的「栏位修正器」表达。</p>
 * <p>这里用的是 Curios 官方 API {@link ISlotHelper#setSlotsForType(String,
 * net.minecraft.world.entity.LivingEntity, int)}：它拿目标值和当前值相减，多退少补，
 * 内部走 {@code CurioStacksHandler#grow/shrink}，而这两条路会把修正器记进
 * {@code persistentModifiers}——也就是会随玩家的 Curios 库存一起存进 NBT、重登还在。</p>
 * <p>改完数量必须立刻调一次 {@link ICurioStacksHandler#update()}：{@code grow/shrink} 只是
 * 给修正器打了「待更新」标记（{@code flagUpdate}），真正扩容 {@code stackHandler} 是在
 * {@code update()} 里做的。不先更新就写第 2~4 格，写入会被静默忽略（越界）。</p>
 *
 * <h2>每次进入世界都重设</h2>
 * <p>玩家<b>每次</b>进入这个世界（登录）都会按 json 重新设置一遍：栏位数量设成档位数字，第 0~N-1 格
 * 里放上该档位的饰品。也就是说玩家自己把饰品拿掉/换成别的，下次进来就会被重新填回去；
 * 作者改了 json 的档位，玩家下次进来就跟着变。</p>
 * <p>（上一版是「只在首次进世界发一次、之后再怎么改都不动」，现按需求改成每次进世界都以 json 为准。）</p>
 */
public final class improvement_loadout
{
    /** 最小档位（和平）。 */
    public static final int MIN_LEVEL = 1;

    /** 最大档位（困难）。 */
    public static final int MAX_LEVEL = 4;

    private improvement_loadout()
    {
    }

    /** 档位是否合法（1~4）。 */
    public static boolean isValidLevel(int level)
    {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    /** 把档位夹到 1~4。 */
    public static int clampLevel(int level)
    {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    /** 难度 → 档位：和平 1、简单 2、普通 3、困难 4。 */
    public static int levelForDifficulty(Difficulty difficulty)
    {
        return difficulty.getId() + 1;
    }

    /** 档位 → 难度（用来写 json 里的难度名）。 */
    public static Difficulty difficultyForLevel(int level)
    {
        return Difficulty.byId(clampLevel(level) - 1);
    }

    /** 档位对应的栏位数量：直接就是档位数字。 */
    public static int slotsForLevel(int level)
    {
        return clampLevel(level);
    }

    /** 档位对应的装备清单，顺序 = 放进第 0、1、2、3 格的顺序。 */
    public static List<Item> loadoutForLevel(int level)
    {
        return switch (clampLevel(level))
        {
            case 1 -> List.of(
                    items.INVALID_PLACEHOLDER.get());
            case 2 -> List.of(
                    items.INVALID_PLACEHOLDER.get(),
                    items.FATE_COMPASS.get());
            case 3 -> List.of(
                    items.INVALID_PLACEHOLDER.get(),
                    items.FATE_COMPASS.get(),
                    items.IMPROVE_BODY.get());
            default -> List.of(
                    items.INVALID_PLACEHOLDER.get(),
                    items.FATE_COMPASS.get(),
                    items.IMPROVE_BODY.get(),
                    items.FLESH_RING.get());
        };
    }

    /** {@link #levelForDifficulty} + {@link #slotsForLevel} 的合并写法，读起来直观些。 */
    public static int slotsForDifficulty(Difficulty difficulty)
    {
        return slotsForLevel(levelForDifficulty(difficulty));
    }

    /** 同上，装备清单版本。 */
    public static List<Item> loadoutForDifficulty(Difficulty difficulty)
    {
        return loadoutForLevel(levelForDifficulty(difficulty));
    }

    /**
     * 每次进入世界时调一次：读世界调试文件的档位，把该玩家的栏位数量与饰品内容重设成该档位的样子。
     *
     * <p>档位取自世界文件夹里的 {@link debug_file}（没有就按当时难度生成一份），
     * 而不是当前世界难度。</p>
     *
     * @return 本次用的档位（1~4）
     */
    public static int syncFromDebugFile(Player player)
    {
        MinecraftServer server = player.getServer();
        int level = server != null
                ? debug_file.loadOrCreateLevel(server)
                : levelForDifficulty(player.level().getLevelData().getDifficulty());

        if (!player.level().isClientSide())
        {
            apply(player, level);
        }

        return level;
    }

    /** 按难度发放（内部换算成档位）。 */
    public static void apply(Player player, Difficulty difficulty)
    {
        apply(player, levelForDifficulty(difficulty));
    }

    /**
     * 把「档位对应的栏位数量 + 饰品」写进指定玩家的 Curios 库存。
     *
     * <p>逻辑与事件分离，GameTest 可以直接拿假玩家调它。</p>
     */
    public static void apply(Player player, int level)
    {
        ISlotHelper slotHelper = CuriosApi.getSlotHelper();
        slotHelper.setSlotsForType(curio_slots.IMPROVEMENT, player, slotsForLevel(level));

        // grow/shrink 只打标记，真正扩容要立刻 update()，否则下面越界写入会被忽略。
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.getStacksHandler(curio_slots.IMPROVEMENT)
                        .ifPresent(ICurioStacksHandler::update));

        List<Item> loadout = loadoutForLevel(level);
        for (int index = 0; index < loadout.size(); index++)
        {
            CuriosApi.getCuriosHelper().setEquippedCurio(
                    player, curio_slots.IMPROVEMENT, index, new ItemStack(loadout.get(index)));
        }
    }
}
