package com.damezame97.white_lily.white_lilybuff;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 「包扎」——{@code white_lily:bandaged}。
 *
 * <h2>它不再加恢复间隔</h2>
 * <p>旧版是按等级给 {@code recovery_interval} +30 / +60；这条已经移除。</p>
 *
 * <h2>现在的行为：一次性消耗</h2>
 * <ul>
 *     <li>身上有 {@code wound_seepage}：按双方等级差算出扣除量，砍掉渗血时长后<b>立刻移除自己</b>，
 *         并给玩家 1 级、180 秒的 {@code delayed_healing}。</li>
 *     <li>没有 {@code wound_seepage}：<b>立刻移除自己</b>，不给 {@code delayed_healing}。</li>
 * </ul>
 * <p>结算细节见 {@link com.damezame97.white_lily.infection.infection_symptom_rules#resolveBandaged}。</p>
 */
public final class bandaged extends MobEffect
{
    public bandaged()
    {
        super(MobEffectCategory.BENEFICIAL, 0xE4D9B6);
    }

    /** 每刻都要检查：它只活到下一次结算。 */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier)
    {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier)
    {
        if (!entity.level().isClientSide && entity instanceof Player player)
        {
            com.damezame97.white_lily.infection.infection_symptom_rules.resolveBandaged(player, amplifier);
        }
    }

    @Override
    public List<ItemStack> getCurativeItems() { return List.of(); }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer)
    { consumer.accept(new com.damezame97.white_lily.client.infection_effect_display()); }
}
