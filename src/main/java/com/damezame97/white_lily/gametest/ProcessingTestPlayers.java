package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.recipe_unlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A scoped real server-world player list, always removed even if a synchronous assertion fails. */
final class ProcessingTestPlayers implements AutoCloseable
{
    private static final ThreadLocal<ProcessingTestPlayers> ACTIVE = new ThreadLocal<>();
    private final GameTestHelper helper;
    private final List<ServerPlayer> players = new ArrayList<>();

    ProcessingTestPlayers(GameTestHelper helper)
    {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested processing test player scope");
        this.helper = helper;
        ACTIVE.set(this);
    }

    static ServerPlayer create(GameTestHelper helper)
    { return create(helper, UUID.randomUUID()); }

    static ServerPlayer create(GameTestHelper helper, UUID id)
    {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(id, "ProcessingQA"))
        { @Override public boolean hasPermissions(int permission) { return permission <= 2; } };
        player.connection = new ServerGamePacketListenerImpl(player.server, new Connection(PacketFlow.SERVERBOUND), player)
        {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
        };
        track(player);
        recipe_unlocks.initialize(player);
        return player;
    }

    static void track(ServerPlayer player)
    {
        ProcessingTestPlayers scope = ACTIVE.get();
        if (scope == null || player.level() != scope.helper.getLevel()) throw new IllegalStateException("Missing processing test player scope");
        scope.players.add(player);
        if (!scope.helper.getLevel().players().contains(player)) scope.helper.getLevel().players().add(player);
    }

    @Override public void close()
    {
        for (ServerPlayer player : players) helper.getLevel().players().remove(player);
        ACTIVE.remove();
    }
}
