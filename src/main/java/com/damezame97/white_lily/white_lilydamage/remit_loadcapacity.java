package com.damezame97.white_lily.white_lilydamage;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;

/**
 * 「免除载重追罚」伤害标签 —— {@code white_lily:remit_loadcapacity}。
 *
 * <h2>它挡什么</h2>
 * <p>{@code white_lily.load_capacity} 低于 90 之后，玩家每次受伤都会被追加一次
 * {@code white_lily:load_burden}（见
 * {@link com.damezame97.white_lily.procedures.load_capacity_procedures}）。
 * 有些伤害本身就是「系统追罚」，不能再追一次——那会变成无限叠乘。这个标签就是那个白名单。</p>
 *
 * <h2>谁在里面</h2>
 * <ul>
 *     <li>{@link load_burden} —— 载重反噬本身（不套娃）；</li>
 *     <li>{@link mental_injury} —— 精神损伤（崩溃状态下从主伤害里切出来的那一份）。</li>
 *     <li>{@link excessive_hemorrhage} —— 大出血（自己流出来的伤害不能再触发一轮追罚）。</li>
 * </ul>
 *
 * <p>数据文件：{@code data/white_lily/tags/damage_type/remit_loadcapacity.json}。</p>
 */
public final class remit_loadcapacity
{
    /** 标签 ID（即 {@code white_lily:remit_loadcapacity} 的路径部分）。 */
    public static final String NAME = "remit_loadcapacity";

    /** 免除载重追罚的伤害标签。 */
    public static final TagKey<DamageType> TAG =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(white_lily.MODID, NAME));

    /** 这个标签里的成员（与 json 里的 values 一一对应，GameTest 会逐个断言）。 */
    public static final List<ResourceKey<DamageType>> MEMBERS = List.of(
            load_burden.KEY,
            mental_injury.KEY,
            excessive_hemorrhage.KEY);

    private remit_loadcapacity()
    {
    }
}
