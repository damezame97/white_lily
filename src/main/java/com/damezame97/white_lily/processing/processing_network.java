package com.damezame97.white_lily.processing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Binds the current machine's exact processing capability set to one open menu session.
 *
 * <p>The packet is only a browsing hint for the client; every fill/take action is still
 * checked by the server against the live block entity and recipe manager.</p>
 */
@Mod.EventBusSubscriber(modid = "white_lily")
public final class processing_network
{
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath("white_lily", "processing_context"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();
    private static final AtomicLong REVISION = new AtomicLong();
    private static boolean registered;

    public record Context(int containerId, BlockPos pos, long revision, Set<ResourceLocation> recipes)
    {
        static Context read(FriendlyByteBuf buffer)
        {
            int containerId = buffer.readVarInt();
            BlockPos pos = buffer.readBlockPos();
            long revision = buffer.readLong();
            int size = buffer.readVarInt();
            if (size < 0 || size > 100000) throw new IllegalArgumentException("Invalid processing context size");
            Set<ResourceLocation> recipes = new LinkedHashSet<>();
            for (int index = 0; index < size; index++) recipes.add(buffer.readResourceLocation());
            return new Context(containerId, pos, revision, Set.copyOf(recipes));
        }

        void write(FriendlyByteBuf buffer)
        {
            buffer.writeVarInt(containerId);
            buffer.writeBlockPos(pos);
            buffer.writeLong(revision);
            buffer.writeVarInt(recipes.size());
            recipes.forEach(buffer::writeResourceLocation);
        }

        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT)
            {
                context.setPacketHandled(true);
                return;
            }
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.damezame97.white_lily.client.processing_client_context.accept(
                            containerId, pos, revision, recipes)));
            context.setPacketHandled(true);
        }
    }

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Context.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Context::write).decoder(Context::read).consumerNetworkThread(Context::handle).add();
    }

    public static void send(ServerPlayer player, processing_menu menu)
    {
        if (!registered || player == null || player.connection == null || menu == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Context(menu.containerId, menu.machinePos(), REVISION.incrementAndGet(), menu.machineRecipes()));
    }

    public static void broadcast(processing_block_entity table)
    {
        if (table == null || table.getLevel() == null || table.getLevel().isClientSide) return;
        if (table.getLevel().getServer() == null) return;
        for (ServerPlayer player : table.getLevel().getServer().getPlayerList().getPlayers())
            if (player.containerMenu instanceof processing_menu menu && menu.table() == table)
                send(player, menu);
    }

    @SubscribeEvent
    public static void datapack(OnDatapackSyncEvent event)
    {
        if (event.getPlayer() != null)
        {
            if (event.getPlayer().containerMenu instanceof processing_menu menu)
                send(event.getPlayer(), menu);
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers())
            if (player.containerMenu instanceof processing_menu menu)
                send(player, menu);
    }

    private processing_network() { }
}
