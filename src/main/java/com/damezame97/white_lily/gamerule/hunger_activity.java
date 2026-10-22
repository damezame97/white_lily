package com.damezame97.white_lily.gamerule;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.hunger_state;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilydamage.depletion;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * {@code Happy_mode} 的「消耗计数器」：谁在什么时候扣多少，以及饿到归零之后那一串负面效果。
 *
 * <h2>计数器怎么跑</h2>
 * <p>上限见 {@link happy_mode_hunger#counterMax(double)}（默认 40，受 {@code stamina} 影响）。
 * 玩家做「消耗饥饿度的行为」时往下减，减到 0（{@link happy_mode_hunger#COUNTER_MIN}）就
 * 扣固定 1 点饱和度、没饱和度改扣 1 点饥饿值，然后把计数器填回上限——
 * 结构和原版那个「疲劳值攒过 4 就扣 1 点」是一样的，只是数和触发方式换成了需求里那套。</p>
 *
 * <p>各种行为分散在不同事件里：位移和状态效果在每刻的 {@link #tick(Player)} 里看，
 * 跳跃/攻击/挖方块/受伤各挂各的 Forge 事件。</p>
 *
 * <h2>饥饿值归零之后</h2>
 * <p>饥饿值 ≤ 0 时<b>不再扣计数器</b>，改为挂一串负面效果：速度 −60%、
 * 挖掘速度 ×0.2、台阶高度 −0.3、游泳速度 −70%，每 2 秒掉 1 点 {@code san}
 * （<b>永久</b>下降，吃饱也不还），每 12 秒扣「最大生命值 20%」的枯竭伤害直至死亡。
 * 饿回来（饥饿值 &gt; 0）就把前四条属性修正摘掉。</p>
 *
 * <h2>和原版的关系</h2>
 * <p>没开 {@code Happy_mode} 的世界里这里什么都不做（原版疲劳值照旧）；创造/旁观
 * （{@code abilities.invulnerable}）也跳过，和原版 {@code Player#causeFoodExhaustion} 的判断一致。</p>
 *
 * <p>每刻那段逻辑单独抽成 {@link #tick(Player)}，GameTest 直接调它，不走事件。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class hunger_activity
{
    /** 负面效果用的属性修正器 ID（固定值，方便按 id 摘掉）。 */
    private static final UUID STARVING_SPEED_ID = UUID.fromString("3f1c9a54-6b28-4d70-9c1f-52a8e7b0d341");
    private static final UUID STARVING_STEP_HEIGHT_ID = UUID.fromString("7d0b4e21-9c35-4a68-b1e2-8f46c9a70d52");
    private static final UUID STARVING_SWIM_ID = UUID.fromString("b48a2d76-1e93-4f05-8a7c-29d3f6b1e970");

    /** 修正器名字（调试时能在属性界面看到）。 */
    private static final String MODIFIER_NAME = "white_lily.happy_mode.starving";

    /**
     * 单刻位移超过这个距离就当「不是自己走的」——传送、换维度、坐骑瞬移之类，
     * 不该按米数扣计数器。
     */
    private static final double MAX_WALKED_PER_TICK = 10.0D;

    // ==================================================================
    // 每刻
    // ==================================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        tick(event.player);
    }

    /** 每刻主逻辑（服务端）。 */
    public static void tick(Player player)
    {
        if (player.level().isClientSide())
        {
            return;
        }

        // 没开 Happy_mode：原版那一套照旧，同时把可能残留的修正摘干净
        if (!player.level().getGameRules().getBoolean(happy_mode.RULE))
        {
            clearStarving(player);
            return;
        }

        // 创造/旁观：和原版 causeFoodExhaustion 一样直接不管
        if (player.getAbilities().invulnerable)
        {
            clearStarving(player);
            return;
        }

        hunger_state state = state(player);
        if (state == null)
        {
            return;
        }

        FoodData food = player.getFoodData();
        if (food.getFoodLevel() <= 0)
        {
            starve(player, state);
            return;
        }

        clearStarving(player, state);

        drainByMovement(player, state);
        drainByEffects(player, state);
        settleIfDepleted(player, food, state);
    }

    // ==================================================================
    // 消耗：位移与状态效果（每刻）
    // ==================================================================

    /** 按这一帧走了多远扣计数器（游泳 / 疾跑 / 普通移动三选一）。 */
    private static void drainByMovement(Player player, hunger_state state)
    {
        double x = player.getX();
        double z = player.getZ();

        if (!state.hasLastPosition())
        {
            state.setLastPosition(x, z);
            return;
        }

        double dx = x - state.getLastX();
        double dz = z - state.getLastZ();
        state.setLastPosition(x, z);

        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 0.0D || distance > MAX_WALKED_PER_TICK)
        {
            return;   // 没动，或者是传送
        }

        float perMeter = happy_mode_hunger.movementCostPerMeter(player.isSwimming(), player.isSprinting());
        state.add(-(float) (distance * perMeter * com.damezame97.white_lily.infection.infection_symptom_rules
                .movementMultiplier(player, player.isSwimming(), player.isSprinting())));
    }

    /** 原版饥饿效果每秒扣、原版饱和效果每秒加。 */
    private static void drainByEffects(Player player, hunger_state state)
    {
        MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
        if (hunger != null)
        {
            state.add(-happy_mode_hunger.hungerEffectCostPerTick(hunger.getAmplifier()));
        }

        MobEffectInstance saturation = player.getEffect(MobEffects.SATURATION);
        if (saturation != null)
        {
            state.add(happy_mode_hunger.saturationEffectGainPerTick(saturation.getAmplifier()));
        }
    }

    /** 计数器见底就扣 1 点饱和度（没饱和度扣 1 点饥饿值），然后填回上限。 */
    private static void settleIfDepleted(Player player, FoodData food, hunger_state state)
    {
        if (state.getCounter() > happy_mode_hunger.COUNTER_MIN)
        {
            return;
        }

        if (food.getSaturationLevel() > 0.0F)
        {
            food.setSaturation(Math.max(food.getSaturationLevel() - 1.0F, 0.0F));
        }
        else
        {
            food.setFoodLevel(Math.max(food.getFoodLevel() - 1, 0));
        }

        state.setCounter(happy_mode_hunger.counterMax(player.getAttributeValue(attributes.STAMINA.get())));
    }

    // ==================================================================
    // 消耗：一次性行为（各自的事件）
    // ==================================================================

    /** 跳跃：普通 2 点，疾跑中 4 点。 */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !active(player))
        {
            return;
        }

        hunger_state state = state(player);
        if (state != null)
        {
            state.add(-happy_mode_hunger.jumpCost(player.isSprinting()));
        }
    }

    /** 攻击：每一击 8 点。 */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event)
    {
        Player player = event.getEntity();
        if (!active(player))
        {
            return;
        }

        hunger_state state = state(player);
        if (state != null)
        {
            state.add(-happy_mode_hunger.ATTACK_COST);
        }
    }

    /** 摧毁方块：每个 5 点。 */
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event)
    {
        Player player = event.getPlayer();
        if (!active(player))
        {
            return;
        }

        hunger_state state = state(player);
        if (state != null)
        {
            state.add(-happy_mode_hunger.BREAK_BLOCK_COST);
        }
    }

    /**
     * 受伤：只有「盔甲值能挡」的那种才扣 4 点。
     *
     * <p>判据直接用原版的 {@link DamageTypeTags#BYPASSES_ARMOR} 标签——不绕过盔甲的伤害，
     * 才算是「受到被盔甲值影响的伤害」。</p>
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !active(player))
        {
            return;
        }

        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(depletion.KEY))
        {
            return;
        }

        hunger_state state = state(player);
        if (state != null)
        {
            state.add(-happy_mode_hunger.ARMORED_DAMAGE_COST);
        }
    }

    // ==================================================================
    // 饥饿值归零之后
    // ==================================================================

    /** 饿到归零：挂上负面效果，并按两个计时器推进「掉意志值」和「扣血」。 */
    private static void starve(Player player, hunger_state state)
    {
        if (!state.isStarving())
        {
            state.setStarving(true);
            state.setSpiritTimer(happy_mode_hunger.spiritLossIntervalTicks());
            state.setHealthTimer(happy_mode_hunger.healthDrainIntervalTicks());
            applyStarvingModifiers(player);
            return;
        }

        state.setSpiritTimer(state.getSpiritTimer() - 1);
        if (state.getSpiritTimer() <= 0)
        {
            state.setSpiritTimer(happy_mode_hunger.spiritLossIntervalTicks());
            lowerSpirit(player);
        }

        state.setHealthTimer(state.getHealthTimer() - 1);
        if (state.getHealthTimer() <= 0)
        {
            state.setHealthTimer(happy_mode_hunger.healthDrainIntervalTicks());
            drainHealth(player);
        }
    }

    /** 速度 −60%、台阶高度 −0.3、游泳速度 −70%（挖掘速度不走属性，见 {@link #onBreakSpeed}）。 */
    private static void applyStarvingModifiers(Player player)
    {
        setModifier(player, Attributes.MOVEMENT_SPEED,
                STARVING_SPEED_ID, happy_mode_hunger.STARVING_SPEED_MODIFIER,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        setModifier(player, ForgeMod.STEP_HEIGHT_ADDITION.get(),
                STARVING_STEP_HEIGHT_ID, happy_mode_hunger.STARVING_STEP_HEIGHT_ADDITION,
                AttributeModifier.Operation.ADDITION);
        setModifier(player, ForgeMod.SWIM_SPEED.get(),
                STARVING_SWIM_ID, happy_mode_hunger.STARVING_SWIM_SPEED_MODIFIER,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 挖掘速度 −80%：原版没有这个属性，走 Forge 的 {@code PlayerEvent.BreakSpeed}。 */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();
        hunger_state state = player_variables.hunger(player).orElse(null);
        if (state == null || !state.isStarving())
        {
            return;
        }
        event.setNewSpeed(event.getNewSpeed() * happy_mode_hunger.STARVING_BREAK_SPEED_MULTIPLIER);
    }

    /** 每 2 秒掉 1 点意志坚定值（<b>永久</b>，吃饱也不还——需求已确认）。 */
    private static void lowerSpirit(Player player)
    {
        AttributeInstance spirit = player.getAttribute(attributes.SAN.get());
        if (spirit != null)
        {
            spirit.setBaseValue(spirit.getBaseValue() - 1.0D);
        }
    }

    /** 每 12 秒扣最大生命值的 20%（枯竭伤害，无视盔甲/附魔等）。 */
    private static void drainHealth(Player player)
    {
        player.hurt(depletion.source(player.level()),
                happy_mode_hunger.healthDrainAmount(player.getMaxHealth()));
    }

    /** 摘掉饿到归零时挂的属性修正（吃饱、关规则、切创造时都走这里）。 */
    private static void clearStarving(Player player)
    {
        hunger_state state = player_variables.hunger(player).orElse(null);
        if (state == null)
        {
            removeStarvingModifiers(player);
            return;
        }
        clearStarving(player, state);
    }

    private static void clearStarving(Player player, hunger_state state)
    {
        if (!state.isStarving())
        {
            return;
        }
        state.setStarving(false);
        state.setSpiritTimer(0);
        state.setHealthTimer(0);
        removeStarvingModifiers(player);
    }

    private static void removeStarvingModifiers(Player player)
    {
        removeModifier(player, Attributes.MOVEMENT_SPEED, STARVING_SPEED_ID);
        removeModifier(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STARVING_STEP_HEIGHT_ID);
        removeModifier(player, ForgeMod.SWIM_SPEED.get(), STARVING_SWIM_ID);
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 这套逻辑现在该不该跑（服务端 + Happy_mode + 不是创造/旁观）。 */
    private static boolean active(Player player)
    {
        return !player.level().isClientSide()
                && player.level().getGameRules().getBoolean(happy_mode.RULE)
                && !player.getAbilities().invulnerable;
    }

    /** 取计数器状态；顺便在「档案里还没有」时按当前 {@code stamina} 填满。 */
    private static hunger_state state(Player player)
    {
        hunger_state state = player_variables.hunger(player).orElse(null);
        if (state != null && state.isUninitialized())
        {
            state.setCounter(happy_mode_hunger.counterMax(player.getAttributeValue(attributes.STAMINA.get())));
        }
        return state;
    }

    private static void setModifier(Player player, Attribute attribute, UUID id,
                                    double amount, AttributeModifier.Operation operation)
    {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null)
        {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, MODIFIER_NAME, amount, operation));
    }

    private static void removeModifier(Player player, Attribute attribute, UUID id)
    {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null)
        {
            instance.removeModifier(id);
        }
    }

    private hunger_activity()
    {
    }
}
