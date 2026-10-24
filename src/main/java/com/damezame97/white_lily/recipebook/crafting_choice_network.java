package com.damezame97.white_lily.recipebook;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

public final class crafting_choice_network {
    private static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath("white_lily","crafting_choice"))
            .networkProtocolVersion(()->"1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    public static void register(){
        CHANNEL.messageBuilder(Open.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->b.writeVarInt(p.menu)).decoder(b->new Open(b.readVarInt())).consumerNetworkThread((p,s)->{var c=s.get();c.enqueueWork(()->{var player=c.getSender();if(player!=null){var view=crafting_choice.open(player,p.menu);if(view!=null)send(player,view);}});c.setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(Choose.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(Choose::write).decoder(Choose::read).consumerNetworkThread(Choose::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::write).decoder(Snapshot::read).consumerNetworkThread(Snapshot::handle).add();
    }
    public static void open(int menu){CHANNEL.sendToServer(new Open(menu));}
    public static void choose(crafting_choice.View view,int request,ResourceLocation recipe){CHANNEL.sendToServer(new Choose(view.menu(),view.session(),view.version(),request,recipe));}
    public static void send(ServerPlayer player,crafting_choice.View view){if(player.connection!=null)CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(view));}
    private record Open(int menu) { }
    private record Choose(int menu,long session,long version,int request,ResourceLocation recipe){
        void write(FriendlyByteBuf b){b.writeVarInt(menu);b.writeLong(session);b.writeLong(version);b.writeVarInt(request);b.writeResourceLocation(recipe);}
        static Choose read(FriendlyByteBuf b){return new Choose(b.readVarInt(),b.readLong(),b.readLong(),b.readVarInt(),b.readResourceLocation());}
        void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
            var player=c.getSender();if(player==null)return;
            var reason=crafting_choice.select(player,menu,session,version,request,recipe);
            if(player.containerMenu.containerId==menu&&crafting_choice.supports(player.containerMenu))send(player,crafting_choice.view(player.containerMenu,player,request,reason));
        });c.setPacketHandled(true);}
    }
    private record Snapshot(crafting_choice.View view){
        void write(FriendlyByteBuf b){b.writeVarInt(view.menu());b.writeLong(view.session());b.writeLong(view.version());b.writeVarInt(view.width());b.writeVarInt(view.height());b.writeVarInt(view.inputs().size());view.inputs().forEach(b::writeItem);
            b.writeVarInt(view.candidates().size());for(var candidate:view.candidates()){b.writeResourceLocation(candidate.id());b.writeItem(candidate.output());b.writeBoolean(candidate.shapeless());b.writeVarInt(candidate.returned().size());candidate.returned().forEach(b::writeItem);}
            b.writeNullable(view.selected(),FriendlyByteBuf::writeResourceLocation);b.writeVarInt(view.request());b.writeEnum(view.reason());}
        static Snapshot read(FriendlyByteBuf b){int menu=b.readVarInt();long session=b.readLong(),version=b.readLong();int width=b.readVarInt(),height=b.readVarInt();if(width<1||width>3||height<1||height>3)throw new IllegalArgumentException("Invalid crafting choice grid");
            int size=count(b,9);List<ItemStack> inputs=new ArrayList<>();for(int i=0;i<size;i++)inputs.add(b.readItem());
            int total=count(b,100000);List<crafting_choice.Candidate> candidates=new ArrayList<>();for(int i=0;i<total;i++){var id=b.readResourceLocation();var output=b.readItem();boolean shapeless=b.readBoolean();int n=count(b,9);List<ItemStack> returned=new ArrayList<>();for(int j=0;j<n;j++)returned.add(b.readItem());candidates.add(new crafting_choice.Candidate(id,output,shapeless,List.copyOf(returned)));}
            return new Snapshot(new crafting_choice.View(menu,session,version,width,height,List.copyOf(inputs),List.copyOf(candidates),b.readNullable(FriendlyByteBuf::readResourceLocation),b.readVarInt(),b.readEnum(transfer_reason.class)));}
        void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.damezame97.white_lily.client.crafting_choice_client.accept(view)));c.setPacketHandled(true);}
    }
    private static int count(FriendlyByteBuf b,int maximum){int size=b.readVarInt();if(size<0||size>maximum)throw new IllegalArgumentException("Invalid crafting choice count");return size;}
    private crafting_choice_network() { }
}
