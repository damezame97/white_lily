package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.capability.infection_state;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

/** 本项目首个自有通道，仅注册 PLAY_TO_CLIENT，无客户端修改感染的消息。 */
public final class infection_network
{
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("white_lily", "infection"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    public record Snapshot(int units, int passiveTicks)
    {
        public static Snapshot read(FriendlyByteBuf buffer) { return new Snapshot(buffer.readVarInt(), buffer.readVarInt()); }
        public void write(FriendlyByteBuf buffer) { buffer.writeVarInt(units); buffer.writeVarInt(passiveTicks); }
        public void handle(Supplier<NetworkEvent.Context> supplier)
        {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) { context.setPacketHandled(true); return; }
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.damezame97.white_lily.client.infection_client_sync.apply(this)));
            context.setPacketHandled(true);
        }
    }

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Snapshot::write).decoder(Snapshot::read).consumerNetworkThread(Snapshot::handle).add();
    }

    public static void sync(Player player, boolean force)
    {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        player_variables.infection(player).ifPresent(data -> {
            if (!needsSync(data, force)) return;
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new Snapshot(data.units(), data.passiveTicks()));
            data.lastSyncedUnits = data.units();
        });
    }

    public static boolean needsSync(infection_state data, boolean force)
    {
        return force || data.units() != data.lastSyncedUnits;
    }

    private infection_network() { }
}
