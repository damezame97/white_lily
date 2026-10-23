package com.damezame97.white_lily.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

/** 有方向约束的最小协议；所有库存/进度工作均排入服务端主线程。 */
public final class research_network
{
    private static final String VERSION="1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("white_lily","research"),()->VERSION,VERSION::equals,VERSION::equals);
    private static boolean registered;
    private static final int MAX_SNAPSHOT=262144;
    public record Request(int action, String node, long revision)
    {
        static Request read(FriendlyByteBuf b) { return new Request(b.readVarInt(),b.readUtf(256),b.readLong()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(action); b.writeUtf(node,256); b.writeLong(revision); }
        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            var context=supplier.get();
            if(context.getDirection()!=NetworkDirection.PLAY_TO_SERVER) { context.setPacketHandled(true); return; }
            context.enqueueWork(()->{
                ServerPlayer player=context.getSender(); if(player==null||player.connection==null) return;
                research_progress data=research_capability.get(player).orElse(null); if(data==null) return;
                if(action==2) { close(data); return; }
                long tick=player.level().getGameTime();
                if(action==0)
                {
                    if(beginOpen(data,tick)) research_service.refresh(player,true);
                    return;
                }
                if(!requestAllowed(data,tick))
                {
                    // 给正常快速点击一个权威回复，同时每玩家每刻至多回应一次，避免网络连点放大。
                    if(action==1 && data.lastReplyTick!=tick)
                    { data.lastReplyTick=tick; research_service.acknowledge(player); }
                    return;
                }
                data.lastRequestTick=tick;
                if(action==1)
                {
                    research_service.submit(player,ResourceLocation.tryParse(node),revision);
                    // 失败/重复请求也回覆当前状态，客户端研究按钮不会卡在等待状态。
                    research_service.acknowledge(player); data.lastReplyTick=tick;
                }
            }); context.setPacketHandled(true);
        }
    }
    public record Snapshot(String json)
    {
        static Snapshot read(FriendlyByteBuf b) { return new Snapshot(b.readUtf(MAX_SNAPSHOT)); }
        void write(FriendlyByteBuf b) { b.writeUtf(json,MAX_SNAPSHOT); }
        void handle(Supplier<NetworkEvent.Context> supplier)
        {
            var context=supplier.get();
            if(context.getDirection()!=NetworkDirection.PLAY_TO_CLIENT) { context.setPacketHandled(true); return; }
            context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.damezame97.white_lily.client.research_client.accept(JsonParser.parseString(json).getAsJsonObject())));
            context.setPacketHandled(true);
        }
    }
    public static synchronized void register()
    {
        if(registered) return; registered=true;
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::write).decoder(Request::read).consumerNetworkThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::write).decoder(Snapshot::read).consumerNetworkThread(Snapshot::handle).add();
    }
    public static void requestOpen() { CHANNEL.sendToServer(new Request(0,"",0)); }
    public static void requestSubmit(ResourceLocation id,long revision) { if(id!=null) CHANNEL.sendToServer(new Request(1,id.toString(),revision)); }
    public static void requestClose() { CHANNEL.sendToServer(new Request(2,"",0)); }
    private static boolean requestAllowed(research_progress data,long tick)
    {
        // 时间倒退只可能来自服务端世界生命周期；重新开始该玩家的限流窗口，避免永久等待。
        return data.lastRequestTick==Long.MIN_VALUE || tick<data.lastRequestTick || tick-data.lastRequestTick>=3;
    }
    /** 返回true时立即打开；否则只保留一个待打开请求，不放开网络限流。 */
    public static boolean beginOpen(research_progress data,long tick)
    {
        if(!requestAllowed(data,tick)) { data.pendingOpen=true; return false; }
        data.pendingOpen=false;
        data.lastRequestTick=tick;
        return true;
    }
    /** PlayerTick使用同一调度规则；无待处理请求时只作一次布尔判断。 */
    public static boolean takePendingOpen(research_progress data,long tick)
    { return data.pendingOpen && beginOpen(data,tick); }
    public static void close(research_progress data)
    { data.handbookOpen=false; data.pendingOpen=false; }
    public static void tickPendingOpen(ServerPlayer player,long tick)
    {
        research_capability.get(player).ifPresent(data->{
            if(data.pendingOpen && player.connection!=null && takePendingOpen(data,tick)) research_service.refresh(player,true);
        });
    }
    public static void send(ServerPlayer player,JsonObject snapshot)
    {
        String json=snapshot.toString();
        if(player.connection!=null && json.length()<=MAX_SNAPSHOT) CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(json));
    }
    private research_network() { }
}
