package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** 固定条件、事件时钟和等权随机池。计时只由服务端玩家 tick 推进。 */
public final class infection_symptom_rules
{
    public static final List<String> TIMED_IDS = List.of("wound_seepage", "infection_blindness",
            "brief_lucidity", "recovery_fatigue", "shivering", "unstable_spellcasting");
    public static final List<String> ALL_IDS = List.of("healthy_condition", "delayed_healing", "rapid_breathing",
            "wound_seepage", "infection_anxiety", "perception_disorder", "mental_distraction", "brief_lucidity",
            "recovery_fatigue", "shivering", "unstable_spellcasting", "infection_blindness");
    private static final Map<ResourceLocation, Integer> POOL = new LinkedHashMap<>();
    private static volatile List<String> timed = TIMED_IDS;
    private static volatile List<String> all = ALL_IDS;
    private static final Map<String, MobEffect> EFFECTS = new java.util.concurrent.ConcurrentHashMap<>();
    static
    {
        registerRandom(id("shivering"), 1200);
        registerRandom(id("unstable_spellcasting"), 1200);
    }

    /** 意志尺度未定稿：不读取/假定 SAN 或 willpower_strength 的换算关系。 */
    public static double strength(Player player) { return 1.0D; }

    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("white_lily", name); }
    @Nullable
    public static MobEffect effect(@Nullable String name)
    {
        if (name == null) return null;
        MobEffect cached = EFFECTS.get(name);
        if (cached != null) return cached;
        ResourceLocation key = ResourceLocation.tryParse("white_lily:" + name);
        MobEffect found = key == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(key);
        if (found != null) EFFECTS.put(name, found);
        return found;
    }
    @Nullable
    public static infection_symptom_state state(Player player)
    {
        return player_variables.infection(player).map(data -> data.symptoms).orElse(null);
    }
    public static boolean active(Player player)
    {
        return connected(player) && !player.level().isClientSide && player_variables.infection(player).isPresent() && player.isAlive() && player.deathTime == 0
                && Float.isFinite(player.getHealth()) && Float.isFinite(player.getMaxHealth()) && player.getMaxHealth() > 0
                && !player.isRemoved() && Config.infectionEnabled && !infection_service.excluded(player);
    }
    private static boolean connected(Player player)
    { return !(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection != null; }
    public static boolean has(Player player, String name)
    { MobEffect effect = effect(name); return effect != null && player.hasEffect(effect); }

    public static synchronized void registerRandom(ResourceLocation effect, int duration)
    {
        if (!"white_lily".equals(effect.getNamespace()) || duration <= 0 || duration > 12000 || POOL.putIfAbsent(effect, duration) != null)
            throw new IllegalArgumentException("Invalid or duplicate random symptom: " + effect);
        var names = new java.util.LinkedHashSet<>(TIMED_IDS);
        POOL.keySet().forEach(key -> names.add(key.getPath()));
        timed = List.copyOf(names);
        names.addAll(ALL_IDS);
        all = List.copyOf(names);
    }

    public static List<String> timedIds() { return timed; }

    public static List<String> allIds() { return all; }

    public static double poolChance(double infection)
    {
        return infection < 80 ? 0 : infection < 150 ? 0.1 : infection < 230 ? 0.2 : 0.3;
    }

    /** 只由成功产生实际增量的外来来源/被动增长调用；命令和读档不调用。 */
    public static void onIncrease(Player player, int actualUnits, RandomSource random)
    {
        if (!active(player) || actualUnits <= 0 || infection_service.query(player) < 80) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        if (state.poolCooldown > 0) return;
        state.poolCooldown = 600; // 包括失败与重复命中。
        if (random.nextDouble() >= poolChance(infection_service.query(player))) return;
        List<Map.Entry<ResourceLocation, Integer>> entries;
        synchronized (infection_symptom_rules.class) { entries = new ArrayList<>(POOL.entrySet()); }
        if (entries.isEmpty()) return;
        var entry = entries.get(random.nextInt(entries.size()));
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(entry.getKey());
        if (effect == null || player.hasEffect(effect) || state.running(entry.getKey().getPath())) return;
        start(player, entry.getKey().getPath(), entry.getValue());
        reevaluate(player);
    }

    public static void start(Player player, String name, int duration)
    {
        infection_symptom_state state = state(player);
        if (!active(player) || state == null || effect(name) == null || !timedIds().contains(name)) return;
        state.set(name, duration);
        state.dirty = true;
        if (!display(player, name, duration)) state.set(name, 0);
    }

    public static boolean blind(Player player)
    {
        var state = state(player);
        if (!active(player) || state == null || has(player, "brief_lucidity") || state.running("brief_lucidity")) return false;
        start(player, "infection_blindness", Math.max(1, (int) Math.round(60 * strength(player))));
        return state.running("infection_blindness") && has(player, "infection_blindness");
    }

    public static void onDirectHit(Player player, float originalHealthDamage)
    {
        if (!active(player) || infection_service.query(player) < 30 || originalHealthDamage < 3
                || !Float.isFinite(originalHealthDamage)) return;
        var state = state(player);
        if (state == null) return;
        int previous = state.remaining("wound_seepage");
        start(player, "wound_seepage", previous == -1 ? -1 : previous == 0 ? 600 : Math.min(1200, previous + 200));
        reevaluate(player);
    }

    public static void onTreatment(Player player, double actualReduction)
    {
        if (active(player) && actualReduction >= 30) { start(player, "recovery_fatigue", 1200); reevaluate(player); }
    }

    public static void onHealthLoss(Player player)
    {
        var state = state(player);
        if (!connected(player) || state == null) return;
        state.dirty = true;
        state.healthLossPending = true;
        removeDisplay(player, "healthy_condition");
        modifiers(player);
    }

    public static void tick(Player player, long clock)
    {
        if (!connected(player) || player.level().isClientSide || player_variables.infection(player).isEmpty()) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        if (state.lastTick == clock) return;
        state.lastTick = clock;
        if (state.healthLossPending) { state.healthLossPending = false; state.dirty = true; }
        if (!player.isAlive() || player.deathTime > 0)
        {
            if (!state.evaluated || state.active || !state.remaining.isEmpty())
            { state.clear(); reevaluate(player); }
            return;
        }
        boolean active = active(player);
        if (!active)
        {
            if (!state.evaluated || state.active || state.dirty) reevaluate(player);
            return;
        }
        // 只读缓存属性及生命值；状态变化立即重评，稳定时每10刻校验。
        double san = san(player);
        boolean changed = !state.evaluated || state.active != active || state.health != player.getHealth()
                || state.maxHealth != player.getMaxHealth() || state.san != san
                || state.infection != player_variables.infection(player).map(data -> data.units()).orElse(0);
        // 从上刻已有的事件计时开始扣减；本刻新发作拥有完整持续时间。
        if (state.poolCooldown > 0) state.poolCooldown--;
        boolean blindnessEnded = state.remaining("infection_blindness") == 1;
        var iterator = state.remaining.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            if (entry.getValue() == -1) continue;
            if (entry.getValue() <= 1) { iterator.remove(); state.dirty = true; }
            else entry.setValue(entry.getValue() - 1);
        }
        if (blindnessEnded) start(player, "brief_lucidity", 600);
        if (changed || state.dirty || state.lastEvaluation == Long.MIN_VALUE || clock - state.lastEvaluation >= 10)
        {
            reevaluate(player);
            state.lastEvaluation = clock;
        }
        if (has(player, "perception_disorder"))
        {
            if (++state.perceptionTicks >= 1200) { state.perceptionTicks = 0; blind(player); }
        }
        else state.perceptionTicks = 0;
    }

    /** 条件改变立即重评；不会推进任何时钟，也不会抽取随机池。 */
    public static void reevaluate(Player player)
    {
        if (!connected(player) || player.level().isClientSide || player_variables.infection(player).isEmpty()) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        if (!active(player))
        {
            for (String name : allIds()) removeDisplay(player, name);
            modifiers(player);
            remember(player, state, false);
            return;
        }
        var health = infection_service.health(player);
        double infection = infection_service.query(player);
        double san = san(player);
        double h = health.ratio();
        boolean valid = health.valid();
        conditional(player, "healthy_condition", valid && !state.healthLossPending && player.getMaxHealth() - player.getHealth()
                <= Math.max(1.0E-5D, player.getMaxHealth() * 1.0E-6D));
        conditional(player, "delayed_healing", valid && infection >= 50 && h < 0.8);
        conditional(player, "rapid_breathing", valid && infection >= 80 && h <= 0.7);
        conditional(player, "infection_anxiety", infection >= 80 && san < 5000);
        boolean perception = infection >= 150 && san < 1000;
        conditional(player, "perception_disorder", perception);
        conditional(player, "mental_distraction", infection >= 200 && san < 5000);
        if (infection < 30) stop(player, "wound_seepage");
        if (infection < 80)
        {
            List<ResourceLocation> randomIds;
            synchronized (infection_symptom_rules.class) { randomIds = List.copyOf(POOL.keySet()); }
            for (var key : randomIds) stop(player, key.getPath());
        }
        if (!perception)
        {
            state.perceptionTicks = 0;
            if (state.running("infection_blindness"))
            {
                stop(player, "infection_blindness");
                start(player, "brief_lucidity", 600);
            }
        }
        for (String name : timedIds())
        {
            if (state.running(name))
            {
                if (!display(player, name, state.remaining(name))) state.set(name, 0);
            }
            else removeDisplay(player, name);
        }
        modifiers(player);
        remember(player, state, true);
    }

    private static double san(Player player)
    {
        var attribute = attributes.SAN.isPresent() ? player.getAttribute(attributes.SAN.get()) : null;
        return attribute == null ? 10000 : attribute.getValue();
    }
    private static void remember(Player player, infection_symptom_state state, boolean active)
    {
        state.active = active;
        state.health = player.getHealth();
        state.maxHealth = player.getMaxHealth();
        state.san = san(player);
        state.infection = player_variables.infection(player).map(data -> data.units()).orElse(0);
        state.evaluated = true;
        state.dirty = false;
        state.lastEvaluation = state.lastTick;
    }

    private static void conditional(Player player, String name, boolean enabled)
    {
        if (enabled) display(player, name, -1);
        else removeDisplay(player, name);
    }

    private static boolean display(Player player, String name, int ticks)
    {
        MobEffect effect = effect(name);
        if (effect == null) return false;
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() == 0 && (existing.getDuration() == ticks
                || (existing.getDuration() >= 0 && ticks >= 0 && Math.abs(existing.getDuration() - ticks) <= 2))) return true;
        infection_symptom_state state = state(player);
        if (state == null) return false;
        state.changingEffects = true;
        try
        {
            // 替换以允许缩短时长；不留下隐藏的高等级/无限效果。
            player.removeEffect(effect);
            return player.addEffect(new MobEffectInstance(effect, ticks, 0, false, false, true));
        }
        finally { state.changingEffects = false; }
    }

    private static void removeDisplay(Player player, String name)
    {
        if (!has(player, name)) return;
        infection_symptom_state state = state(player);
        if (state == null) return;
        state.changingEffects = true;
        try { player.removeEffect(effect(name)); }
        finally { state.changingEffects = false; }
    }

    private static void stop(Player player, String name)
    { var state = state(player); if (state != null) state.set(name, 0); removeDisplay(player, name); }

    /** /effect clear 只清除当前实例；不会凭空触发清明或清除外部失明。 */
    public static void removed(Player player, MobEffect effect)
    {
        if (player.level().isClientSide || player_variables.infection(player).isEmpty()) return;
        var state = state(player);
        if (state == null || state.changingEffects) return;
        state.dirty = true;
        ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (key != null && key.getNamespace().equals("white_lily") && timedIds().contains(key.getPath()))
            state.set(key.getPath(), 0);
    }

    public static double movementMultiplier(Player player, boolean swimming, boolean sprinting)
    {
        if ((!swimming && !sprinting) || !active(player)) return 1;
        return 1 + strength(player) * ((has(player, "rapid_breathing") ? 0.25 : 0)
                + (has(player, "recovery_fatigue") ? 0.10 : 0));
    }

    private static void modifier(Player player, String name, Attribute attribute, double amount)
    {
        if (amount == 0) infection_modifiers.remove(player, id(name), attribute);
        else infection_modifiers.put(player, id(name), attribute, amount, AttributeModifier.Operation.ADDITION);
    }

    public static void modifiers(Player player)
    {
        double scale = strength(player);
        modifier(player, "healthy_condition", attributes.STAMINA.get(), has(player, "healthy_condition") ? 1 : 0);
        modifier(player, "healthy_condition", attributes.INFECTION_RESISTANCE.get(), has(player, "healthy_condition") ? 0.10 : 0);
        modifier(player, "delayed_healing", attributes.RECOVERY_INTERVAL.get(), has(player, "delayed_healing") ? -20 * scale : 0);
        boolean seepage = has(player, "wound_seepage") && !player.hasEffect(mob_effects.WOUND_SUPPRESSION.get());
        modifier(player, "wound_seepage", attributes.RECOVERY_INTERVAL.get(), seepage ? -20 * scale : 0);
        modifier(player, "infection_anxiety", attributes.RECOVERY_INTERVAL.get(), has(player, "infection_anxiety") ? -10 * scale : 0);
        modifier(player, "shivering", attributes.RIFLE_EXPERTISE.get(), has(player, "shivering") ? -0.05 * scale : 0);
        modifier(player, "shivering", attributes.EXPERT_IN_ARCHERY.get(), has(player, "shivering") ? -0.5 * scale : 0);
        modifier(player, "unstable_spellcasting", attributes.SPELL_ENHANCEMENT.get(), has(player, "unstable_spellcasting") ? -0.5 * scale : 0);
        var restore = player.getAttribute(attributes.RESTORE_HEALTH.get());
        if (restore != null)
        {
            // SAN 修正先按不含感染的值钳制；我们只扣余下的非负部分。
            double current = infection_modifiers.valueWithoutInfection(restore);
            double reduction = infection_recovery.safeReduction(restore, current,
                    seepage ? -0.5 * scale : 0);
            modifier(player, "wound_seepage", attributes.RESTORE_HEALTH.get(), reduction);
        }
    }

    private infection_symptom_rules() { }
}
