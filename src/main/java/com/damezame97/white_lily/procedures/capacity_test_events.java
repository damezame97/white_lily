package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.capability.food_capacity;
import com.damezame97.white_lily.item.capacity_test_item;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "white_lily")
public final class capacity_test_events {
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        food_capacity.set(event.getEntity().getFoodData(), food_capacity.get(event.getOriginal().getFoodData()));
        var old = event.getOriginal().getAttribute(Attributes.MAX_HEALTH);
        var next = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
        if (old != null && next != null && old.getModifier(capacity_test_item.HEALTH_MODIFIER) != null) {
            next.removeModifier(capacity_test_item.HEALTH_MODIFIER);
            next.addPermanentModifier(old.getModifier(capacity_test_item.HEALTH_MODIFIER));
        }
        if (event.getEntity().getHealth() > event.getEntity().getMaxHealth())
            event.getEntity().setHealth(event.getEntity().getMaxHealth());
    }
}
