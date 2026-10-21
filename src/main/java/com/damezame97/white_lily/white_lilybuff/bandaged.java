package com.damezame97.white_lily.white_lilybuff;

import com.damezame97.white_lily.attribute.attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class bandaged extends MobEffect
{
    public bandaged()
    {
        super(MobEffectCategory.BENEFICIAL, 0xE4D9B6);
    }
    /** 等到所有注册表完成后绑定本模组属性，避免效果注册早于属性注册。 */
    public void bindAttributes()
    {
        addAttributeModifier(attributes.RECOVERY_INTERVAL.get(), "045b7542-1343-4b22-ae4d-d05a9d25f7aa", 30,
                AttributeModifier.Operation.ADDITION);
    }
    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) { return amplifier <= 0 ? 30 : 60; }
    @Override
    public List<ItemStack> getCurativeItems() { return List.of(); }
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer)
    { consumer.accept(new com.damezame97.white_lily.client.infection_effect_display()); }
}
