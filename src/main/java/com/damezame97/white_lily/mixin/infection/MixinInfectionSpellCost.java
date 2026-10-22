package com.damezame97.white_lily.mixin.infection;

import com.damezame97.white_lily.infection.infection_mana;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅针对已核对的 ISS 3.16.3 公共成本路径，不碰作者原有 Mixin。 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinInfectionSpellCost
{
    @Redirect(method = InfectionMixinPlugin.CHECK, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"), remap = false)
    private int whiteLily$check(AbstractSpell spell, int level, int spellLevel, CastSource source, MagicData data, Player player)
    {
        int cost = spell.getManaCost(level);
        return player.level().isClientSide ? cost : infection_mana.cost(player, cost);
    }

    @Redirect(method = InfectionMixinPlugin.PAY, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"), remap = false)
    private int whiteLily$payment(AbstractSpell spell, int level, Level world, int spellLevel, ServerPlayer player,
                                  CastSource source, boolean cooldown)
    {
        return infection_mana.cost(player, spell.getManaCost(level));
    }

    @Inject(method = "getManaCost(I)I", at = @At("RETURN"), cancellable = true, remap = false)
    private void whiteLily$display(int level, CallbackInfoReturnable<Integer> callback)
    {
        // 集成服务器与客户端共享类，必须再核实当前线程，避免用本地玩家给远端玩家加价。
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.damezame97.white_lily.client.infection_mana_display.apply(callback));
    }
}
