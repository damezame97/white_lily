package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.white_lilydamage.stored_suffering_damage;
import com.tacz.guns.init.ModDamageTypes;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** 明确的直接命中入口；ISS 共用法术伤害类型，需额外核实法术 ID。 */
public final class infection_damage
{
    private static final java.util.Map<ResourceLocation, java.util.function.Predicate<SpellDamageSource>> SPELL_HITS =
            new java.util.concurrent.ConcurrentHashMap<>();
    static
    {
        registerSpell(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blaze_storm"),
                source -> source.getDirectEntity() instanceof io.redspace.ironsspellbooks.entity.spells.fireball.SmallMagicFireball);
    }
    public static void registerSpell(ResourceLocation spell, java.util.function.Predicate<SpellDamageSource> verifiedHit)
    {
        java.util.Objects.requireNonNull(spell);
        java.util.Objects.requireNonNull(verifiedHit);
        if (SPELL_HITS.putIfAbsent(spell, verifiedHit) != null)
            throw new IllegalArgumentException("Duplicate direct spell classifier: " + spell);
    }
    public static final TagKey<DamageType> DIRECT = TagKey.create(Registries.DAMAGE_TYPE,
            infection_symptom_rules.id("infection_direct_hit"));
    public static final TagKey<DamageType> EXCLUDED = TagKey.create(Registries.DAMAGE_TYPE,
            infection_symptom_rules.id("infection_wound_excluded"));
    public static boolean direct(DamageSource source)
    {
        if (source.is(EXCLUDED) || source.is(stored_suffering_damage.CANNOT_DELAY)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source instanceof SpellDamageSource spell)
        {
            // SmallMagicFireball.onHitEntity 的 Blaze Storm 命中；Fireball 的范围爆炸不冒充直接命中。
            if (spell.spell() == null) return false;
            var rule = SPELL_HITS.get(ResourceLocation.tryParse(spell.spell().getSpellId()));
            return rule != null && rule.test(spell);
        }
        return source.is(DIRECT) || source.is(ModDamageTypes.BULLETS_TAG);
    }
    private infection_damage() { }
}
