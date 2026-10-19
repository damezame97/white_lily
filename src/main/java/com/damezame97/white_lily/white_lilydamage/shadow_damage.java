package com.damezame97.white_lily.white_lilydamage;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;

/**
 * 「梦魇伤害」标记标签 —— {@code white_lily:shadow_damage}。
 *
 * <h2>它表达什么</h2>
 * <p>凡是带着这个标签的伤害类型，都属于本模组设定里的「梦魇伤害」：无视盔甲、无视无敌帧、
 * 无视抗性提升等效果、无视附魔保护、无视盾牌。这五条恰好就是原版现成的五个伤害类型标签，
 * 所以实现上就是「同时进这五个标签」（见下面的 {@link #IMPLIED_BYPASSES}）。</p>
 *
 * <h2>为什么标签本身做不到「蕴含」</h2>
 * <p>数据包标签只是一个<b>集合</b>，只能表达「谁属于我」，没法表达「属于我的人自动获得别的标签」。
 * 所以「加入 shadow_damage」这件事在文件层面是<b>六个文件一起写</b>：</p>
 * <ol>
 *     <li>{@code data/white_lily/tags/damage_type/shadow_damage.json} —— 标记本身；</li>
 *     <li>{@code data/minecraft/tags/damage_type/bypasses_armor.json}；</li>
 *     <li>{@code data/minecraft/tags/damage_type/bypasses_cooldown.json}；</li>
 *     <li>{@code data/minecraft/tags/damage_type/bypasses_effects.json}；</li>
 *     <li>{@code data/minecraft/tags/damage_type/bypasses_enchantments.json}；</li>
 *     <li>{@code data/minecraft/tags/damage_type/bypasses_shield.json}。</li>
 * </ol>
 * <p>注意第 2~6 个是<b>原版命名空间</b>的文件：数据包之间靠同名路径合并（{@code replace} 默认 false），
 * 所以在我们这些文件里写 {@code ["white_lily:xxx"]} 是「往原版标签里追加」，不会覆盖原版内容。</p>
 *
 * <h2>以后加新的梦魇伤害类型</h2>
 * <p>把那 6 个文件的 {@code values} 里补上新的 ID。GameTest
 * {@code shadowDamageTagCarriesAllBypasses} 会遍历 {@link #IMPLIED_BYPASSES} 逐个断言，
 * 漏写会被测试当场抓住。</p>
 */
public final class shadow_damage
{
    /** 标签 ID（即 {@code white_lily:shadow_damage} 的路径部分）。 */
    public static final String NAME = "shadow_damage";

    /** 梦魇伤害标记标签。 */
    public static final TagKey<DamageType> TAG =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(white_lily.MODID, NAME));

    /** 这个标签所「蕴含」的五个原版无视类标签，顺序与文件清单一致。 */
    public static final List<TagKey<DamageType>> IMPLIED_BYPASSES = List.of(
            DamageTypeTags.BYPASSES_ARMOR,
            DamageTypeTags.BYPASSES_COOLDOWN,
            DamageTypeTags.BYPASSES_EFFECTS,
            DamageTypeTags.BYPASSES_ENCHANTMENTS,
            DamageTypeTags.BYPASSES_SHIELD);

    private shadow_damage()
    {
    }
}
