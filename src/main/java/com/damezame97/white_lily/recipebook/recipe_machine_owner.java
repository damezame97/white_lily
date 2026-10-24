package com.damezame97.white_lily.recipebook;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 非范围内工作方块的操作者绑定。
 *
 * <p>熔炉系、煋造台与葡萄酒机器已经不在「配方解锁与个人进度」的范围里，
 * 行为与原版一致；这里的绑定与校验保留为空实现，避免旧存档 NBT 逻辑被误用。</p>
 */
@Mod.EventBusSubscriber(modid="white_lily")
public final class recipe_machine_owner {
    private static final String OWNER="white_lily_recipe_operator";
    private static boolean supported(BlockEntity machine) {
        return machine instanceof AbstractFurnaceBlockEntity || machine instanceof BrewingStandBlockEntity
            || machine.getClass().getName().equals("net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity")
            || machine.getClass().getName().equals("net.satisfy.vinery.core.block.entity.ApplePressBlockEntity");
    }
    public static void bind(BlockEntity machine,Player player) {
        // 范围外的工作方块不再记录操作者，与原版一致。
    }
    private static boolean idle(BlockEntity machine) {
        if(!(machine instanceof Container container)) return false;
        // Fuel alone does not keep an idle vanilla furnace owned forever.
        if(machine instanceof AbstractFurnaceBlockEntity) return container.getItem(0).isEmpty() && container.getItem(2).isEmpty();
        for(int i=0;i<container.getContainerSize();i++) if(!container.getItem(i).isEmpty()) return false;
        return true;
    }
    /** 范围外的工作方块不需要解锁，永远放行。 */
    public static boolean allowed(BlockEntity machine,ResourceLocation recipe) {
        return true;
    }
    @SubscribeEvent public static void open(PlayerContainerEvent.Open event) {
        if(!(event.getEntity() instanceof ServerPlayer)) return;
        for(var slot:event.getContainer().slots) if(slot.container instanceof BlockEntity machine) bind(machine,event.getEntity());
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getEntity() instanceof ServerPlayer) {
            BlockEntity machine=event.getLevel().getBlockEntity(event.getPos()); if(machine!=null) bind(machine,event.getEntity());
        }
    }
    private recipe_machine_owner() { }
}
