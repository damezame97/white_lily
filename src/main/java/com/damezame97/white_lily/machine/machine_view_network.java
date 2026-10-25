package com.damezame97.white_lily.machine;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Private recipe views sync independently; copied recipes never enter the global manager. */
@Mod.EventBusSubscriber(modid = "white_lily")
public final class machine_view_network {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("white_lily","machine_views"), () -> "1", "1"::equals,"1"::equals);
    public record Snapshot(Map<ResourceLocation,List<Recipe<?>>> tables) {
        @SuppressWarnings({"unchecked","rawtypes"})
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(tables.size());
            tables.forEach((device,recipes) -> {
                b.writeResourceLocation(device); b.writeVarInt(recipes.size());
                for (var r : recipes) {
                    b.writeResourceLocation(r.getId()); b.writeResourceLocation(BuiltInRegistries.RECIPE_SERIALIZER.getKey(r.getSerializer()));
                    ((RecipeSerializer)r.getSerializer()).toNetwork(b,r);
                }
            });
        }
        public static Snapshot read(FriendlyByteBuf b) {
            int size = b.readVarInt(); if(size < 0 || size > 256) throw new IllegalArgumentException("Invalid device count");
            Map<ResourceLocation,List<Recipe<?>>> result = new LinkedHashMap<>(); int total=0;
            for(int i=0;i<size;i++) {
                var device=b.readResourceLocation(); int count=b.readVarInt(); total+=count;
                if(count<0 || total>10000) throw new IllegalArgumentException("Invalid machine recipe count");
                List<Recipe<?>> recipes=new ArrayList<>();
                for(int j=0;j<count;j++) {
                    var id=b.readResourceLocation(); var serializer=BuiltInRegistries.RECIPE_SERIALIZER.getOptional(b.readResourceLocation()).orElseThrow();
                    recipes.add(Objects.requireNonNull(serializer.fromNetwork(id,b)));
                }
                if(result.put(device,List.copyOf(recipes))!=null) throw new IllegalArgumentException("Duplicate device");
            }
            return new Snapshot(Map.copyOf(result));
        }
    }
    @Mod.EventBusSubscriber(modid="white_lily",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
            CHANNEL.messageBuilder(Snapshot.class,0,NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(Snapshot::write).decoder(Snapshot::read).consumerMainThread((p,c) -> {
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> com.damezame97.white_lily.client.machine_view_client.accept(p.tables()));
                        c.get().setPacketHandled(true);
                    }).add();
        }
    }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        var manager=event.getPlayerList().getServer().getRecipeManager();
        var snapshot=new Snapshot(machine_rules.state(manager).tables());
        if(event.getPlayer()!=null) CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer),snapshot);
        else CHANNEL.send(PacketDistributor.ALL.noArg(),snapshot);
    }
}
