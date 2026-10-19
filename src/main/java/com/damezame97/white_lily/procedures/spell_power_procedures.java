package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.white_lily;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 「全局法强」（{@code all_spell_power}）与「全局法抗」（{@code all_spell_resis}）的实际功能性。
 *
 * <h2>需求</h2>
 * <ul>
 *     <li>{@code all_spell_power} = 0.10 → 《Iron's Spells 'n Spellbooks》里<b>全部</b>「学派类」
 *         法术强度都 +10%；负数同理（−0.10 → −10%）。</li>
 *     <li>{@code all_spell_resis} = 0.10 → 全部「学派类」法术抗性都 +10%；负数同理。</li>
 * </ul>
 *
 * <h2>为什么这样实现（而不是 mixin 进 ISS）</h2>
 * <p>看过 ISS 3.16.3 的字节码：{@code SchoolType#getPowerFor(LivingEntity)} 与
 * {@code getResistanceFor(LivingEntity)} 的实现就是「该实体挂没挂这条学派属性？挂了就直接取
 * {@code getAttributeValue}，没挂返回 1.0」。也就是说<b>学派法强/法抗就是那条属性的值本身</b>，
 * 学派属性的默认基值正好是 1.0（100%）。所以「+10%」等价于「给那条属性加一个 +0.10 的加法修饰符」。</p>
 * <p>于是不需要去改 ISS 的方法名（那种做法会随 ISS 改版而碎），只用 Forge 公开 API：
 * 把本模组属性的值当作一个<b>临时修饰符</b>加到 ISS 的每条学派属性上。</p>
 *
 * <h2>为什么不连 {@code irons_spellbooks:spell_power}/{@code spell_resist} 一起加</h2>
 * <p>那两条是 ISS 自己的「全局法强/法抗」，ISS 结算时是
 * {@code 学派值 × 全局值}。如果我们也去加它，等于同一次加成算两遍。所以这里只覆盖
 * <b>学派专属</b>的那批属性——也就是需求里的「元素类法术强度」。</p>
 *
 * <h2>哪些属性算「元素类」</h2>
 * <p>ISS 并没有给学派属性打 tag（扒过 jar：{@code data/irons_spellbooks/tags/} 下只有
 * damage_type / items / blocks 等，没有 attribute tag），所以按需求的后备方案，
 * <b>直接按注册名枚举</b>：命名空间是 {@code irons_spellbooks}、且注册名以
 * {@code _spell_power} / {@code _magic_resist} 结尾的属性就是全部学派属性。</p>
 * <p>这样刚好把 ISS 自己的全局 {@code spell_power}/{@code spell_resist} 排除在外
 * （{@code "spell_power"} 并不以 {@code "_spell_power"} 结尾），也顺带自动涵盖 ISS
 * 以后新增的学派。当前实测命中 <b>9 条法强 + 9 条法抗</b>（fire / ice / lightning / holy /
 * ender / blood / evocation / nature / eldritch）。</p>
 *
 * <h2>替换时机与代价</h2>
 * <p>{@link AttributeInstance#addTransientModifier} 是<b>临时</b>修饰符：不写进存档，玩家重生 /
 * 换维度时自动清空，随后最多 10 刻内被重新补上。修饰符只在<b>数值真的变了</b>时才增删，
 * 平时每 10 刻只做几次读值比较，几乎不产生同步包。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class spell_power_procedures {

    /** ISS 的命名空间。 */
    public static final String ISS_NAMESPACE = "irons_spellbooks";

    /** 学派法术强度的注册名后缀（{@code fire_spell_power} …）。 */
    public static final String POWER_SUFFIX = "_spell_power";

    /** 学派法术抗性的注册名后缀（{@code fire_magic_resist} …）。 */
    public static final String RESIST_SUFFIX = "_magic_resist";

    /** 挂在学派法强属性上的那个修饰符的固定 UUID（每次更新靠它找回来）。 */
    public static final UUID POWER_MODIFIER_ID = UUID.fromString("b7c1f0a0-1c2d-4e3f-9a4b-51c0de000001");

    /** 挂在学派法抗属性上的那个修饰符的固定 UUID。 */
    public static final UUID RESIST_MODIFIER_ID = UUID.fromString("b7c1f0a0-1c2d-4e3f-9a4b-51c0de000002");

    /** 修饰符名字（只为可读，反查一律走 UUID）。 */
    public static final String POWER_MODIFIER_NAME = white_lily.MODID + ":all_spell_power";

    /** 修饰符名字（法抗）。 */
    public static final String RESIST_MODIFIER_NAME = white_lily.MODID + ":all_spell_resis";

    /** 每多少刻对齐一次。属性值平时不变，没必要每刻都遍历。 */
    public static final int CHECK_INTERVAL_TICKS = 10;

    /** ISS 学派法强属性，首次用到时枚举并缓存。 */
    private static volatile List<Attribute> powerTargets;

    /** ISS 学派法抗属性。 */
    private static volatile List<Attribute> resistTargets;

    private spell_power_procedures() {
    }

    // ==================================================================
    // 驱动：玩家刻事件
    // ==================================================================

    /**
     * 每隔 {@link #CHECK_INTERVAL_TICKS} 刻把两条属性的值对齐到 ISS 学派属性上。
     *
     * <p>只在服务端做：属性值由服务端裁决，客户端那份会跟着同步过去。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide()) {
            return;
        }
        if (event.player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        sync(event.player);
    }

    /**
     * 把 {@code all_spell_power} / {@code all_spell_resis} 的当前值对齐到所有 ISS 学派属性。
     *
     * <p>公开是为了让 GameTest 能直接调用（假玩家不会被 tick，走不到事件）。</p>
     *
     * @param player 目标玩家；其身上没有本模组这两条属性时直接返回
     */
    public static void sync(Player player) {
        if (player == null) {
            return;
        }

        AttributeInstance power = player.getAttribute(attributes.ALL_SPELL_POWER.get());
        AttributeInstance resis = player.getAttribute(attributes.ALL_SPELL_RESIS.get());
        if (power == null && resis == null) {
            return;
        }

        applyTo(player, schoolPowerAttributes(), power, POWER_MODIFIER_ID, POWER_MODIFIER_NAME);
        applyTo(player, schoolResistAttributes(), resis, RESIST_MODIFIER_ID, RESIST_MODIFIER_NAME);
    }

    /**
     * 把 {@code source} 的当前值作为加法修饰符写到 {@code targets} 的每一条属性上。
     *
     * <p>值变了才增删修饰符；值为 0（或来源属性不存在）时把修饰符摘掉，不留空包。</p>
     */
    private static void applyTo(Player player, List<Attribute> targets, AttributeInstance source,
                                UUID modifierId, String modifierName) {
        double value = source == null ? 0.0D : source.getValue();

        for (Attribute target : targets) {
            AttributeInstance instance = player.getAttribute(target);
            if (instance == null) {
                // 该实体没被挂上这条学派属性（例如非玩家），跳过。
                continue;
            }

            AttributeModifier existing = instance.getModifier(modifierId);

            if (value == 0.0D) {
                if (existing != null) {
                    instance.removeModifier(modifierId);
                }
                continue;
            }

            if (existing != null && existing.getAmount() == value) {
                continue;
            }
            if (existing != null) {
                instance.removeModifier(modifierId);
            }
            instance.addTransientModifier(new AttributeModifier(
                    modifierId, modifierName, value, AttributeModifier.Operation.ADDITION));
        }
    }

    // ==================================================================
    // 「元素类」属性有哪些
    // ==================================================================

    /** ISS 的学派法强属性（缓存）。 */
    public static List<Attribute> schoolPowerAttributes() {
        List<Attribute> local = powerTargets;
        if (local == null) {
            synchronized (spell_power_procedures.class) {
                if (powerTargets == null) {
                    powerTargets = resolve(POWER_SUFFIX);
                }
                local = powerTargets;
            }
        }
        return local;
    }

    /** ISS 的学派法抗属性（缓存）。 */
    public static List<Attribute> schoolResistAttributes() {
        List<Attribute> local = resistTargets;
        if (local == null) {
            synchronized (spell_power_procedures.class) {
                if (resistTargets == null) {
                    resistTargets = resolve(RESIST_SUFFIX);
                }
                local = resistTargets;
            }
        }
        return local;
    }

    /** 这条属性是不是 ISS 的学派属性（命名空间对 + 注册名以对应后缀结尾）。 */
    public static boolean isSchoolAttribute(ResourceLocation id, String suffix) {
        return id != null && ISS_NAMESPACE.equals(id.getNamespace()) && id.getPath().endsWith(suffix);
    }

    /**
     * 清掉缓存，下次重新枚举。
     *
     * <p>正常游戏里属性注册表加载完就不会再变，缓存一直有效；这个入口只给「世界重载 /
     * GameTest 反复跑」之类场景兜底。</p>
     */
    public static void invalidateTargets() {
        synchronized (spell_power_procedures.class) {
            powerTargets = null;
            resistTargets = null;
        }
    }

    /** 按后缀从属性注册表里枚举，结果按注册名排序（顺序稳定，方便测试逐条比对）。 */
    private static List<Attribute> resolve(String suffix) {
        List<Attribute> found = new ArrayList<>(16);
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (isSchoolAttribute(id, suffix)) {
                found.add(attribute);
            }
        }
        found.sort(Comparator.comparing(a -> String.valueOf(ForgeRegistries.ATTRIBUTES.getKey(a))));
        return Collections.unmodifiableList(found);
    }
}
