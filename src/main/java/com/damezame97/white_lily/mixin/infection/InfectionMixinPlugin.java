package com.damezame97.white_lily.mixin.infection;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;

/** 只门控本模组新增的 ISS 兼容 Mixin；不会加载游戏类或干预作者原有 Mixin。 */
public final class InfectionMixinPlugin implements IMixinConfigPlugin
{
    public static final String SPELL = "io/redspace/ironsspellbooks/api/spells/AbstractSpell";
    public static final String CHECK = "canBeCastedBy(ILio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/magic/MagicData;Lnet/minecraft/world/entity/player/Player;)Lio/redspace/ironsspellbooks/api/spells/CastResult;";
    public static final String PAY = "castSpell(Lnet/minecraft/world/level/Level;ILnet/minecraft/server/level/ServerPlayer;Lio/redspace/ironsspellbooks/api/spells/CastSource;Z)V";
    private static final String COST = "getManaCost(I)I";

    /** 三个注入点必须整体存在；未知接口不能只注入显示或扣费的一半。 */
    public static boolean compatible(ClassNode target)
    {
        return method(target, COST) != null && callsCost(method(target, CHECK)) == 1 && callsCost(method(target, PAY)) == 1;
    }
    private static MethodNode method(ClassNode node, String signature)
    {
        return node.methods.stream().filter(m -> (m.name + m.desc).equals(signature)
                && (m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) == 0).findFirst().orElse(null);
    }
    private static int callsCost(MethodNode method)
    {
        if (method == null) return -1;
        int count = 0;
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && call.owner.equals(SPELL) && (call.name + call.desc).equals(COST)) count++;
        return count;
    }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        if (!mixinClassName.endsWith(".MixinInfectionSpellCost")) return true;
        try
        {
            if (compatible(MixinService.getService().getBytecodeProvider().getClassNode(targetClassName))) return true;
        }
        catch (Exception exception)
        {
            LogUtils.getLogger().warn("White Lily: cannot inspect ISS mana cost API", exception);
        }
        LogUtils.getLogger().error("White Lily: unsupported ISS mana cost API; disabling ALL infection mana cost hooks together. Other infection features remain enabled. Tested ISS version: 1.20.1-3.16.3.");
        return false;
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
