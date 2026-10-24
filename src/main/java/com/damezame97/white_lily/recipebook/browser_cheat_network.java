package com.damezame97.white_lily.recipebook;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Administrator-only browser test mode and /give-backed item grants.
 *
 * <p>Permission is checked on the server for every request; the client state is only a UI
 * hint. The give command always targets the requesting player (@s) and uses the player's
 * own command source, never the console or a temporarily elevated source.</p>
 */
@Mod.EventBusSubscriber(modid = "white_lily")
public final class browser_cheat_network
{
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath("white_lily", "browser_cheat"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();
    private static final AtomicLong REVISION = new AtomicLong();
    private static final Map<UUID, Boolean> ALLOWED = new HashMap<>();
    private static final Map<UUID, Long> LAST_GIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST_PERMISSION_REQUEST = new HashMap<>();
    private static boolean registered;

    public enum Result { OK, DENIED, INVALID, COOLDOWN, FAILED }

    public record Permission(boolean allowed, long revision)
    {
        static Permission read(FriendlyByteBuf buffer) { return new Permission(buffer.readBoolean(), buffer.readLong()); }
        void write(FriendlyByteBuf buffer) { buffer.writeBoolean(allowed); buffer.writeLong(revision); }
        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) { context.setPacketHandled(true); return; }
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.damezame97.white_lily.client.browser_cheat_client.acceptPermission(allowed, revision)));
            context.setPacketHandled(true);
        }
    }

    public record RequestPermission()
    {
        static RequestPermission read(FriendlyByteBuf buffer) { return new RequestPermission(); }
        void write(FriendlyByteBuf buffer) { }
        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER) { context.setPacketHandled(true); return; }
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                long tick = player.level().getGameTime();
                Long last = LAST_PERMISSION_REQUEST.get(player.getUUID());
                if (last != null && tick >= last && tick - last < 10) return;
                LAST_PERMISSION_REQUEST.put(player.getUUID(), tick);
                sendPermission(player);
            });
            context.setPacketHandled(true);
        }
    }

    public record GiveRequest(ItemStack stack, boolean fullStack, int requestId, int containerId)
    {
        static GiveRequest read(FriendlyByteBuf buffer)
        {
            ItemStack stack = buffer.readItem();
            return new GiveRequest(stack, buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt());
        }
        void write(FriendlyByteBuf buffer)
        {
            buffer.writeItem(stack);
            buffer.writeBoolean(fullStack);
            buffer.writeVarInt(requestId);
            buffer.writeVarInt(containerId);
        }
        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER) { context.setPacketHandled(true); return; }
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) give(player, stack, fullStack, requestId, containerId);
            });
            context.setPacketHandled(true);
        }
    }

    public record GiveResult(int requestId, Result result)
    {
        static GiveResult read(FriendlyByteBuf buffer) { return new GiveResult(buffer.readVarInt(), Result.values()[buffer.readVarInt()]); }
        void write(FriendlyByteBuf buffer) { buffer.writeVarInt(requestId); buffer.writeVarInt(result.ordinal()); }
        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) { context.setPacketHandled(true); return; }
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.damezame97.white_lily.client.browser_cheat_client.acceptResult(requestId, result)));
            context.setPacketHandled(true);
        }
    }

    public static synchronized void register()
    {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Permission.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Permission::write).decoder(Permission::read).consumerNetworkThread(Permission::handle).add();
        CHANNEL.messageBuilder(RequestPermission.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestPermission::write).decoder(RequestPermission::read).consumerNetworkThread(RequestPermission::handle).add();
        CHANNEL.messageBuilder(GiveRequest.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GiveRequest::write).decoder(GiveRequest::read).consumerNetworkThread(GiveRequest::handle).add();
        CHANNEL.messageBuilder(GiveResult.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GiveResult::write).decoder(GiveResult::read).consumerNetworkThread(GiveResult::handle).add();
    }

    public static boolean canUse(ServerPlayer player)
    {
        return player != null && player.isCreative() && player.hasPermissions(2);
    }

    public static void requestPermission()
    {
        if (registered) CHANNEL.sendToServer(new RequestPermission());
    }

    public static void requestGive(ItemStack stack, boolean fullStack, int containerId)
    {
        if (!registered || stack == null || stack.isEmpty()) return;
        CHANNEL.sendToServer(new GiveRequest(stack.copyWithCount(1), fullStack, nextRequest(), containerId));
    }

    private static final AtomicLong REQUEST = new AtomicLong();

    private static int nextRequest()
    {
        return (int) (REQUEST.incrementAndGet() & 0x7FFFFFFF);
    }

    private static void sendPermission(ServerPlayer player)
    {
        sendPermission(player, canUse(player));
    }

    private static void sendPermission(ServerPlayer player, boolean allowed)
    {
        if (player == null || player.connection == null) return;
        ALLOWED.put(player.getUUID(), allowed);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Permission(allowed, REVISION.incrementAndGet()));
    }

    private static void sendResult(ServerPlayer player, int requestId, Result result)
    {
        if (player == null || player.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GiveResult(requestId, result));
    }

    /** Public for GameTests; the packet handler is the only production caller. */
    public static Result give(ServerPlayer player, ItemStack requested, boolean fullStack, int requestId, int containerId)
    {
        if (player == null || player.connection == null) return Result.DENIED;
        if (!player.isAlive() || !canUse(player))
        {
            sendResult(player, requestId, Result.DENIED);
            return Result.DENIED;
        }
        if (player.containerMenu == null || player.containerMenu.containerId != containerId
                || !player.containerMenu.stillValid(player))
        {
            sendResult(player, requestId, Result.INVALID);
            return Result.INVALID;
        }
        if (requested == null || requested.isEmpty() || requested.getItem() == Items.AIR || requested.getCount() != 1)
        {
            sendResult(player, requestId, Result.INVALID);
            return Result.INVALID;
        }
        long tick = player.level().getGameTime();
        Long last = LAST_GIVE.get(player.getUUID());
        if (last != null && tick >= last && tick - last < 5)
        {
            sendResult(player, requestId, Result.COOLDOWN);
            return Result.COOLDOWN;
        }
        LAST_GIVE.put(player.getUUID(), tick);

        ItemStack stack = requested.copy();
        int count = fullStack ? Math.max(1, stack.getMaxStackSize()) : 1;
        stack.setCount(count);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null)
        {
            sendResult(player, requestId, Result.INVALID);
            return Result.INVALID;
        }
        String command = "give @s " + id + (stack.hasTag() ? stack.getTag().toString() : "") + " " + count;
        if (command.length() > 30000)
        {
            sendResult(player, requestId, Result.INVALID);
            return Result.INVALID;
        }
        try
        {
            int value = player.server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withSuppressedOutput(), command);
            Result result = value > 0 ? Result.OK : Result.FAILED;
            sendResult(player, requestId, result);
            return result;
        }
        catch (RuntimeException exception)
        {
            com.mojang.logging.LogUtils.getLogger().warn("White Lily browser give failed for {}", player.getGameProfile().getName(), exception);
            sendResult(player, requestId, Result.FAILED);
            return Result.FAILED;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide || player.tickCount % 20 != 0) return;
        boolean allowed = canUse(player);
        Boolean previous = ALLOWED.put(player.getUUID(), allowed);
        if (previous == null || previous != allowed) sendPermission(player);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendPermission(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendPermission(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendPermission(player);
    }
    @SubscribeEvent public static void gameMode(PlayerEvent.PlayerChangeGameModeEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
            sendPermission(player, event.getNewGameMode() == net.minecraft.world.level.GameType.CREATIVE
                    && player.hasPermissions(2));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        ALLOWED.remove(event.getEntity().getUUID());
        LAST_GIVE.remove(event.getEntity().getUUID());
        LAST_PERMISSION_REQUEST.remove(event.getEntity().getUUID());
    }

    private browser_cheat_network() { }
}
