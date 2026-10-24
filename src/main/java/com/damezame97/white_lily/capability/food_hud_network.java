package com.damezame97.white_lily.capability;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.gamerule.happy_mode;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Vanilla does not synchronize every saturation change; send an authoritative HUD snapshot. */
@Mod.EventBusSubscriber(modid = "white_lily")
public final class food_hud_network {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("white_lily", "food_hud"),
            () -> "1", "1"::equals, "1"::equals);
    private static final Map<ServerPlayer, Snapshot> LAST = new WeakHashMap<>();
    private record Snapshot(UUID player, int food, float saturation, float capacity, int foodCapacity) { }

    @Mod.EventBusSubscriber(modid = "white_lily", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void setup(FMLCommonSetupEvent event) {
            CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder((p, b) -> { b.writeUUID(p.player()); b.writeVarInt(p.food());
                        b.writeFloat(p.saturation()); b.writeFloat(p.capacity()); b.writeVarInt(p.foodCapacity()); })
                    .decoder(b -> new Snapshot(b.readUUID(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readVarInt()))
                    .consumerMainThread((p, ctx) -> {
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                                com.damezame97.white_lily.client.percentage_food_overlay.accept(
                                        p.player(), p.food(), p.saturation(), p.capacity(), p.foodCapacity()));
                        ctx.get().setPacketHandled(true);
                    }).add();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.connection == null) return;
        // A stable full-food capacity avoids the percentage rising when hunger falls.
        double extra = player.level().getGameRules().getBoolean(happy_mode.RULE)
                ? Math.max(0, player.getAttributeValue(attributes.BUILDING_ENDURANCE.get())) : 0;
        Snapshot next = new Snapshot(player.getUUID(), player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(), (float) (food_capacity.get(player.getFoodData()) + extra), food_capacity.get(player.getFoodData()));
        if (!next.equals(LAST.get(player)) || player.tickCount % 20 == 0) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), next);
            LAST.put(player, next);
        }
    }
}
