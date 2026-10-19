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

    private mob_effects()
    {
    }
}
