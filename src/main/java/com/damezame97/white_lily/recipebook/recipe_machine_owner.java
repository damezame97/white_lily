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

/** Operator identity for the explicitly supported cooking machines, saved with that block entity. */
@Mod.EventBusSubscriber(modid="white_lily")
public final class recipe_machine_owner {
    private static final String OWNER="white_lily_recipe_operator";
    private static boolean supported(BlockEntity machine) {
        return machine instanceof AbstractFurnaceBlockEntity || machine instanceof BrewingStandBlockEntity
            || machine.getClass().getName().equals("net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity")
            || machine.getClass().getName().equals("net.satisfy.vinery.core.block.entity.ApplePressBlockEntity");
    }
    public static void bind(BlockEntity machine,Player player) {
        if(player.level().isClientSide || !supported(machine)) return;
        var tag=machine.getPersistentData();
        if(!tag.hasUUID(OWNER) || idle(machine)) { tag.putUUID(OWNER,player.getUUID()); machine.setChanged(); }
    }
    private static boolean idle(BlockEntity machine) {
        if(!(machine instanceof Container container)) return false;
        // Fuel alone does not keep an idle vanilla furnace owned forever.
        if(machine instanceof AbstractFurnaceBlockEntity) return container.getItem(0).isEmpty() && container.getItem(2).isEmpty();
        for(int i=0;i<container.getContainerSize();i++) if(!container.getItem(i).isEmpty()) return false;
        return true;
    }
    public static boolean allowed(BlockEntity machine,ResourceLocation recipe) {
        if(machine.getLevel()==null || machine.getLevel().isClientSide || machine.getLevel().getServer()==null) return false;
        var tag=machine.getPersistentData(); if(!tag.hasUUID(OWNER)) return false;
        ServerPlayer player=machine.getLevel().getServer().getPlayerList().getPlayer(tag.getUUID(OWNER));
        return player!=null && player.isAlive() && recipe_unlocks.isUnlocked(player,recipe);
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
