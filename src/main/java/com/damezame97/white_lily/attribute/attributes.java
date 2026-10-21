package com.damezame97.white_lily.attribute;

import com.damezame97.white_lily.white_lily;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * White Lily 的自定义属性注册表。
 *
 * <p>注册方式：使用 Forge 的 {@link DeferredRegister}。它在 RegisterEvent 阶段由 Forge
 * 统一完成注册，不需要自己写 {@code RegisterEvent} 监听器，也不存在"静态字段在某个
 * 事件里被赋值、别处却提前读取"的顺序问题；属性的实例统一用
 * {@link RegistryObject} 持有，真正做到"注册名只出现一次"。</p>
 *
 * <p>需要在模组主类构造函数里把 {@link #ATTRIBUTES} 挂到模组事件总线上：
 * {@code attributes.ATTRIBUTES.register(modEventBus);}</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class attributes {

    /**
     * 本地化键前缀，最终 descriptionId = 本前缀 + 属性注册名（例如
     * {@code white_lily.damage_mitigation}）。
     *
     * <p>已用 Forge 47.4.10 的官方映射源码核对：1.20.1 的
     * {@code Attribute}/{@code RangedAttribute} 构造函数会把传入的字符串
     * <b>原样</b>保存为 descriptionId，<b>不会</b>自动追加 {@code "attribute."}
     * 前缀（原版自己传的就是完整的 {@code attribute.name.generic.max_health}）。
     * 因此语言文件里的键必须与 descriptionId 逐字一致。</p>
     */
    private static final String LANG_PREFIX = white_lily.MODID + ".";

    /** 属性注册表，由 Forge 在 RegisterEvent 阶段统一注册。 */
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.Keys.ATTRIBUTES, white_lily.MODID);

    /** 按注册顺序保存全部属性，供统一遍历、调试或后续功能扩展使用。 */
    private static final List<RegistryObject<Attribute>> REGISTERED = new ArrayList<>(16);

    /** {@link #REGISTERED} 的只读视图，注册顺序即列表顺序。 */
    public static final List<RegistryObject<Attribute>> ALL = Collections.unmodifiableList(REGISTERED);

    // ==================================================================
    // 属性定义
    //
    // 每个属性独立声明 [初始值, 最小值, 最大值]，互不影响，方便逐条调整。
    // （目前 14 条数值相同，只是因为还没做到逐个属性的部分。）
    // 新增属性 = 在末尾追加一条 register(...)，会自动登记进 ALL。
    //
    // 注意：RangedAttribute 要求 最小值 ≤ 初始值 ≤ 最大值，否则构造时直接抛异常。
    // ==================================================================

    /** 伤害抵抗：固定值加减，先于伤害减免结算 */
    public static final RegistryObject<Attribute> DAMAGE_MITIGATION =
            register("damage_mitigation", 0.0D, 0.0D, 1038576.0D);

    /** 伤害减免：每 0.01 提供 1% 减免，后于伤害抵抗结算 */
    public static final RegistryObject<Attribute> DAMAGE_REDUCTION =
            register("damage_reduction", 0.0D, 0.0D, 1.0D);

    /**
     * 载重值。
     *
     * <p><b>注册默认值只是兵底</b>：真正的默认值按世界档位在玩家每次登录时写入
     * （和平 150 / 简单 140 / 普通 120 / 困难 100，见
     * {@link com.damezame97.white_lily.procedures.load_capacity_procedures}）。
     * 这里取 100（机制的中性点：低于 100 才开始有惩罚），万一登录钩子没跑到也不会把玩家直接坑死。</p>
     */
    public static final RegistryObject<Attribute> LOAD_CAPACITY =
            register("load_capacity", 100.0D, 0.0D, 10024.0D);

    /**
     * 全局法强：等于 ISS 里所有学派法术强度同时获得该值。
     *
     * <p>0.10 → 每个学派法强 +10%；−0.10 → −10%。结算见
     * {@link com.damezame97.white_lily.procedures.spell_power_procedures}。</p>
     */
    public static final RegistryObject<Attribute> ALL_SPELL_POWER =
            register("all_spell_power", 0.00D, -10024.0D, 10024.0D);

    /**
     * 全局法抗：等于 ISS 里所有学派法术抗性同时获得该值。
     *
     * <p>0.10 → 每个学派法抗 +10%；−0.10 → −10%。默认 0.00，即「开局不影响任何人」——
     * 之前这里是 0.05，一旦功能性接上就等于每个玩家白送 5% 法抗，故与
     * {@link #ALL_SPELL_POWER} 对齐改成 0.00。</p>
     */
    public static final RegistryObject<Attribute> ALL_SPELL_RESIS =
            register("all_spell_resis", 0.00D, -10024.0D, 10024.0D);

    /**
     * 理智值（{@code white_lily.san}）。
     *
     * <p><b>注册默认值只是兵底</b>：真正的默认值按世界档位在玩家每次登录时写入
     * （和平 50000 / 简单 22000 / 普通 10000 / 困难 2000，见
     * {@link com.damezame97.white_lily.procedures.san_procedures}）。这里取 10000（普通档）。</p>
     *
     * <p>四种状态：过盛 &gt; 100000、正常 [5000, 100000]、崩溃 [1000, 5000)、融毁 &lt; 1000。</p>
     */
    public static final RegistryObject<Attribute> SAN =
            register("san", 10000.0D, 0.0D, 100000024.0D);

    /** 意志坚韧度 */
    public static final RegistryObject<Attribute> WILLPOWER_STRENGTH =
            register("willpower_strength", 0.05D, -10024.0D, 10024.0D);

    /** 铳械精通 */
    public static final RegistryObject<Attribute> RIFLE_EXPERTISE =
            register("rifle_expertise", 0.00D, -10024.0D, 10024.0D);

    /** 铳械增幅 */
    public static final RegistryObject<Attribute> RIFLE_ENHANCEMENT =
            register("rifle_enhancement", 0.00D, -10024.0D, 10024.0D);

    /** 法术增幅 */
    public static final RegistryObject<Attribute> SPELL_ENHANCEMENT =
            register("spell_enhancement", 0.00D, -10024.0D, 10024.0D);

    /** 法术暴击 */
    public static final RegistryObject<Attribute> SPELL_CRITICAL_STRIKE =
            register("spell_critical_strike", 1.00D, -10024.0D, 10024.0D);

    /** 箭术精通 */
    public static final RegistryObject<Attribute> EXPERT_IN_ARCHERY =
            register("expert_in_archery", 1.00D, -10024.0D, 10024.0D);

    /** 箭矢暴击 */
    public static final RegistryObject<Attribute> ARROW_STRIKE =
            register("arrow_strike", 0.05D, -0.00D, 10024.0D);

    /** 箭矢暴击伤害 */
    public static final RegistryObject<Attribute> ARROW_CRIT_DAMAGE =
            register("arrow_crit_damage", 1.50D, -0.00D, 10024.0D);

    /**
     * 恢复间隔（秒）：Happy_mode 开启时那套回血机制的间隔修正。
     * 每 +1 让间隔少 1 秒、每 -1 多 1 秒，间隔不会低于 5 秒。
     *
     * @see com.damezame97.white_lily.gamerule.happy_mode_food
     */
    public static final RegistryObject<Attribute> RECOVERY_INTERVAL =
            register("recovery_interval", 0.0D, -10086.0D, 10086.0D);

    /** 恢复生命值：Happy_mode 开启时每次恢复的血量（可为负 → 变成掉血）。 */
    public static final RegistryObject<Attribute> RESTORE_HEALTH =
            register("restore_health", 1.0D, -10086.0D, 10086.0D);

    /** 耐力：Happy_mode 开启时每次恢复要扣多少饱和度（≥0 是「每几次扣 1 点」，<0 是「每次扣几点」）。 */
    public static final RegistryObject<Attribute> STAMINA =
            register("stamina", 0.0D, -10086.0D, 10086.0D);

    /** 储存饱和度上限：每 1 点让「储存饱和度」的上限比饥饿值多 1（原版是饥饿值本身）。 */
    public static final RegistryObject<Attribute> BUILDING_ENDURANCE =
            register("building_endurance", 0.0D, 0.0D, 10086.0D);

    private attributes() {
    }

    /**
     * 按注册名创建一个可同步的 RangedAttribute 并登记到 {@link #REGISTERED}。
     *
     * @param name         属性注册名，与语言文件键的后半段一致
     * @param defaultValue 初始值（必须落在 [minValue, maxValue] 内）
     * @param minValue     最小值
     * @param maxValue     最大值
     */
    private static RegistryObject<Attribute> register(String name,
                                                      double defaultValue, double minValue, double maxValue) {
        RegistryObject<Attribute> attribute = ATTRIBUTES.register(name,
                () -> new RangedAttribute(LANG_PREFIX + name, defaultValue, minValue, maxValue)
                        .setSyncable(true));
        REGISTERED.add(attribute);
        return attribute;
    }

    /**
     * 把全部自定义属性添加到玩家（PLAYER）实体上。
     *
     * <p>该事件在注册完成后、common setup 之前于模组事件总线上触发，
     * 此时 {@link RegistryObject#get()} 已经可以安全取值。</p>
     */
    @SubscribeEvent
    public static void addAttributesToPlayers(EntityAttributeModificationEvent event) {
        for (RegistryObject<Attribute> attribute : ALL) {
            event.add(EntityType.PLAYER, attribute.get());
        }
    }
}
