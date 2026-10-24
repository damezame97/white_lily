package com.damezame97.white_lily.capability;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-authoritative pending damage, sent only to the affected player. */
@Mod.EventBusSubscriber(modid = "white_lily", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class suffering_network {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("white_lily", "suffering_hud"),
            () -> "1", "1"::equals, "1"::equals);
    private record Snapshot(UUID player, float amount) { }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buf) -> { buf.writeUUID(packet.player()); buf.writeFloat(packet.amount()); })
                .decoder(buf -> new Snapshot(buf.readUUID(), buf.readFloat()))
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            com.damezame97.white_lily.client.suffering_hud_sync.apply(packet.player(), packet.amount()));
                    context.get().setPacketHandled(true);
                }).add();
    }

    public static void sync(ServerPlayer player, suffering_data data, boolean force) {
        float total = data.total();
        if (force || Float.compare(total, data.lastHudSynced) != 0) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(player.getUUID(), total));
            data.lastHudSynced = total;
        }
    }
}
