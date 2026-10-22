package com.damezame97.white_lily.white_lilybuff;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** 只豁免 Forge 治疗物品机制；命令和其他效果移除 API 仍然正常工作。 */
public final class infection_effect extends MobEffect
{
    public infection_effect(boolean beneficial, int color)
    {
        super(beneficial ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL, color);
    }

    @Override
    public List<ItemStack> getCurativeItems() { return List.of(); }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer)
    {
        consumer.accept(new com.damezame97.white_lily.client.infection_effect_display());
    }
}
