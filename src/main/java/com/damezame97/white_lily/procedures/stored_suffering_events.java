package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.capability.suffering_capability;
import com.damezame97.white_lily.capability.suffering_data;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilycurio.stored_suffering;
import com.damezame97.white_lily.white_lilydamage.stored_suffering_damage;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = white_lily.MODID)
// Level 由游戏引擎持有；这里只借用，不得关闭世界。
@SuppressWarnings("resource")
public final class stored_suffering_events
{
    /** 源码核对：Player.actuallyHurt 中，此事件位于护甲/魔法减伤和吸收扣除之后。
     * LOWEST 在本项目 NORMAL 优先级的 damage_mitigation_procedures 之后运行。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || !player.isAlive() || player.isSpectator() || player.getAbilities().invulnerable
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || event.getSource().is(stored_suffering_damage.CANNOT_DELAY))
        {
            return;
        }
        float actual = event.getAmount();
        if (!Float.isFinite(actual) || actual <= 0.0F)
        {
            return;
        }
        suffering_capability.get(player).ifPresent(data -> {
            List<SlotResult> rings = rings(player);
            Map<Integer, UUID> equipment = equipment(rings);
            if (data.equipmentReady && data.total() > 0.0F && lostEquipment(data.equipment, equipment))
            {
                // 若外部模组绕过了卸下回调，在本次已经经过减伤/吸收的最终伤害里结清旧账。
                // 此时不能嵌套 die/hurt，避免外层 hurt 再次执行死亡流程。
                event.setAmount(actual + data.drain());
                data.equipment = equipment;
                sync(player, data, true);
                return;
            }
            data.equipmentReady = true;
            data.equipment = equipment;
            if (actual < 3.0F || rings.isEmpty())
            {
                return;
            }

            SlotResult selected = rings.get(0); // 多枚也只触发一次，只扣选中戒指一点耐久。
            ItemStack ring = selected.stack();
            float deferred = actual * 0.3F;
            data.add(deferred, clock(player));
            event.setAmount(actual - deferred);
            int damage = ring.getDamageValue() + 1;
            if (player instanceof ServerPlayer serverPlayer)
            {
                CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, ring, damage);
            }
            ring.setDamageValue(damage); // 固定一点，不让耐久附魔或 Unbreakable 标签改变触发次数。
            if (damage >= ring.getMaxDamage())
            {
                ring.shrink(1);
                ring.setDamageValue(0);
                player.awardStat(Stats.ITEM_BROKEN.get(items.STORED_SUFFERING.get()));
                if (player instanceof ServerPlayer)
                {
                    CuriosApi.broadcastCurioBreakEvent(selected.slotContext());
                }
                // 在同一次实际扣血中加入所有欠款，包括刚生成的 30%；原版负责唯一一次死亡判定。
                // 这些金额已经越过护甲、抗性、吸收和本项目减伤，不会再次处理。
                event.setAmount(event.getAmount() + data.drain());
                data.equipment = equipment(rings(player));
            }
            sync(player, data, false);
        });
    }

    public static List<SlotResult> rings(Player player)
    {
        return CuriosApi.getCuriosInventory(player).map(handler -> handler.findCurios(stack ->
                        stack.is(items.STORED_SUFFERING.get()) && stack.getDamageValue() < stack.getMaxDamage())
                .stream().filter(result -> "ring".equals(result.slotContext().identifier())
                        && !result.slotContext().cosmetic()).toList()).orElse(List.of());
    }

    private static Map<Integer, UUID> equipment(List<SlotResult> rings)
    {
        Map<Integer, UUID> snapshot = new LinkedHashMap<>();
        for (SlotResult result : rings)
        {
            snapshot.put(result.slotContext().index(), stored_suffering.identity(result.stack()));
        }
        return snapshot;
    }

    private static boolean lostEquipment(Map<Integer, UUID> previous, Map<Integer, UUID> current)
    {
        return previous.entrySet().stream().anyMatch(entry -> !entry.getValue().equals(current.get(entry.getKey())));
    }

    public static void onRingRemoved(Player player)
    {
        suffering_capability.get(player).ifPresent(data -> {
            if (data.equipmentReady && player.isAlive() && !player.isRemoved())
            {
                settle(player, data.drain());
                sync(player, data, true);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void playerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide)
        {
            tick(event.player);
        }
    }

    public static void tick(Player player)
    {
        if (player.isRemoved() || (player instanceof ServerPlayer serverPlayer && serverPlayer.isChangingDimension()))
        {
            return;
        }
        suffering_capability.get(player).ifPresent(data -> {
            if (!player.isAlive())
            {
                data.drain();
                sync(player, data, false);
                return;
            }
            Map<Integer, UUID> current = equipment(rings(player));
            boolean changed = data.equipmentReady && lostEquipment(data.equipment, current);
            data.equipmentReady = true;
            data.equipment = current;
            if (data.total() > 0.0F && (changed || current.isEmpty()))
            {
                settle(player, data.drain());
            }
            else
            {
                settle(player, data.advance(clock(player)));
            }
            sync(player, data, false);
        });
    }

    /** 专用最终生命扣除：不调用 hurt，因此不再进入护甲/吸收/反伤/吸血事件，不产生击退或使用中断。
     * 致死时明确调用正常 die，触发 Forge 死亡事件、掉落与死亡界面，不能只 setHealth(0)。 */
    public static void settle(Player player, float amount)
    {
        if (player.level().isClientSide || !player.isAlive() || !Float.isFinite(amount) || amount <= 0.0F)
        {
            return;
        }
        DamageSource source = stored_suffering_damage.source(player.level());
        if (player.isSpectator() || player.getAbilities().invulnerable || player.isInvulnerableTo(source))
        {
            return;
        }
        player.getCombatTracker().recordDamage(source, amount);
        player.setHealth(player.getHealth() - amount);
        player.awardStat(Stats.DAMAGE_TAKEN, Math.round(amount * 10.0F));
        if (player.getHealth() <= 0.0F)
        {
            player.die(source);
        }
    }

    public static Component pendingMessage(float amount)
    {
        return Component.translatable("message.white_lily.stored_suffering.pending",
                String.format(Locale.ROOT, "%.1f", amount)).withStyle(ChatFormatting.DARK_RED);
    }

    /** 复用原版 action bar 数据包，仅发给本人：总额变化即更新，未变化每 30 刻续显一次。 */
    public static void sync(Player player, suffering_data data, boolean force)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return;
        }
        float total = data.total();
        long tick = clock(player);
        if (force || Float.compare(total, data.lastDisplayed) != 0 || (total > 0 && tick >= data.nextDisplayTick))
        {
            serverPlayer.displayClientMessage(total > 0 ? pendingMessage(total) : Component.empty(), true);
            data.lastDisplayed = total;
            data.nextDisplayTick = tick + 30;
        }
    }

    private static long clock(Player player)
    {
        return java.util.Objects.requireNonNull(player.getServer(), "Suffering logic requires server-side player").getTickCount();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide)
        {
            suffering_capability.get(player).ifPresent(data -> {
                data.drain();
                sync(player, data, true);
            });
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event)
    {
        suffering_capability.get(event.getEntity()).ifPresent(data -> {
            data.equipmentReady = false; // 只在完整装备已加载后的第一次玩家 tick 建立基线。
            sync(event.getEntity(), data, true);
        });
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event)
    {
        suffering_capability.get(event.getEntity()).ifPresent(data -> sync(event.getEntity(), data, true));
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        suffering_capability.get(event.getEntity()).ifPresent(data -> sync(event.getEntity(), data, true));
    }

    private stored_suffering_events()
    {
    }
}
