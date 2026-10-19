package com.damezame97.white_lily.white_lilycurio;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.debugging.debug_file;
import com.damezame97.white_lily.white_lily;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Curios 相关的游戏事件监听。
 *
 * <p>四件事：</p>
 * <ol>
 *     <li>世界加载完就确保世界文件夹下有 {@code Debugging_whitelilymod.json}
 *         （{@link debug_file}）；</li>
 *     <li>玩家<b>每次</b>进入世界时，按那份 json 里的 {@code difficulty_level} 重设
 *         {@code improvement} 饰品栏的栏位数量与栏内饰品
 *         （见 {@link improvement_loadout#syncFromDebugFile}）；</li>
 *     <li>非创造玩家的物品栏里出现「改良」系列饰品就<b>立刻删掉</b>（每 tick 查一次）；</li>
 *     <li>兜底：掉落物里出现这四件就剔除（正常应由 {@link improvement_curio} 的掉落规则拦住）。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class curio_events
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 世界加载完就把世界级调试文件备好（不存在才写）。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        debug_file.loadOrCreateLevel(event.getServer());
    }

    /** 每次进世界都按调试文件重设一遍 improvement 饰品栏。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        int level = improvement_loadout.syncFromDebugFile(player);

        if (Config.debugLogging)
        {
            LOGGER.info("White Lily: 进入世界，已按 {} 的档位 {} 重设 {} 饰品栏",
                    debug_file.FILE_NAME, level, curio_slots.IMPROVEMENT);
        }
    }

    /**
     * 每 tick 清一次：非创造玩家的物品栏里不该出现「改良」系列饰品。
     *
     * <p>只处理服务端；创造模式玩家（{@code abilities.instabuild}）跳过。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide() || player.isCreative())
        {
            return;
        }

        int removed = purgeImprovementCurios(player);
        if (removed > 0 && Config.debugLogging)
        {
            LOGGER.info("White Lily: 从 {} 的物品栏里删掉 {} 件「改良」饰品",
                    player.getName().getString(), removed);
        }
    }

    /** 兜底：死亡掉落物里出现这四件就剔除。 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event)
    {
        if (event.getEntity().level().isClientSide())
        {
            return;
        }

        event.getDrops().removeIf(drop -> improvement_curio.isImprovementCurio(drop.getItem()));
    }

    /**
     * 删掉玩家物品栏（含快捷栏、副手、盔甲格）里的「改良」系列饰品。
     *
     * @return 删掉了几件
     */
    private static int purgeImprovementCurios(Player player)
    {
        Inventory inventory = player.getInventory();
        int removed = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            if (improvement_curio.isImprovementCurio(inventory.getItem(slot)))
            {
                inventory.setItem(slot, ItemStack.EMPTY);
                removed++;
            }
        }

        if (removed > 0)
        {
            // 标脏，让服务端下一 tick 把变化同步给客户端
            inventory.setChanged();
        }

        return removed;
    }

    private curio_events()
    {
    }
}
