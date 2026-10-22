package com.damezame97.white_lily.white_lilybuff;

import com.damezame97.white_lily.white_lily;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的<b>状态效果</b>注册表（所有 buff / debuff 的唯一注册入口）。
 *
 * <p>这个包（{@code white_lilybuff}）专门收纳本模组的状态效果类，目前只有一个成员：</p>
 * <ol>
 *     <li>{@link #IMPURE_MAGIC}：「不洁魔力」，类在 {@link impure_magic}。</li>
 * </ol>
 *
 * <p>效果注册到原版注册表 {@code minecraft:mob_effect}（{@link ForgeRegistries#MOB_EFFECTS}），
 * 所以它的 ID 是 {@code white_lily:impure_magic}。HUD 里的图标贴图按原版约定去找
 * {@code assets/white_lily/textures/mob_effect/<路径>.png}。</p>
 */
public final class mob_effects
{
    /** 状态效果注册表，由主类 {@link white_lily} 挂到模组事件总线。 */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.Keys.MOB_EFFECTS, white_lily.MODID);

    /**
     * 「不洁魔力」——见 {@link impure_magic}。
     *
     * <p>贴图：{@code assets/white_lily/textures/mob_effect/impure_magic.png}（即 Impure_magic.png）。</p>
     */
    public static final RegistryObject<MobEffect> IMPURE_MAGIC =
            MOB_EFFECTS.register(impure_magic.NAME, impure_magic::new);

    public static final RegistryObject<MobEffect> HEALTHY_CONDITION = infection("healthy_condition", true, 0x69B85C);
    public static final RegistryObject<MobEffect> DELAYED_HEALING = infection("delayed_healing", false, 0xA38D62);
    public static final RegistryObject<MobEffect> RAPID_BREATHING = infection("rapid_breathing", false, 0x91A7B2);
    public static final RegistryObject<MobEffect> WOUND_SEEPAGE = infection("wound_seepage", false, 0xA73439);
    public static final RegistryObject<MobEffect> INFECTION_ANXIETY = infection("infection_anxiety", false, 0xAA8848);
    public static final RegistryObject<MobEffect> PERCEPTION_DISORDER = infection("perception_disorder", false, 0x655589);
    public static final RegistryObject<MobEffect> MENTAL_DISTRACTION = infection("mental_distraction", false, 0x8D68AE);
    public static final RegistryObject<MobEffect> BRIEF_LUCIDITY = infection("brief_lucidity", true, 0xC7D9BC);
    public static final RegistryObject<MobEffect> RECOVERY_FATIGUE = infection("recovery_fatigue", false, 0x93805B);
    public static final RegistryObject<MobEffect> SHIVERING = infection("shivering", false, 0x8DB8CD);
    public static final RegistryObject<MobEffect> UNSTABLE_SPELLCASTING = infection("unstable_spellcasting", false, 0xA45DC4);
    public static final RegistryObject<MobEffect> INFECTION_BLINDNESS = infection("infection_blindness", false, 0x272B35);
    public static final RegistryObject<MobEffect> BANDAGED = MOB_EFFECTS.register("bandaged", bandaged::new);
    public static final RegistryObject<MobEffect> WOUND_SUPPRESSION = infection("wound_suppression", true, 0x96B970);

    private static RegistryObject<MobEffect> infection(String name, boolean beneficial, int color)
    {
        return MOB_EFFECTS.register(name, () -> new infection_effect(beneficial, color));
    }

    private mob_effects()
    {
    }
}
