package com.damezame97.white_lily.client;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="white_lily",value=Dist.CLIENT)
public final class machine_view_client {
    private static List<Recipe<?>> recipes=List.of();
    private static Map<ResourceLocation,Recipe<?>> byId=Map.of();
    private static long revision;
    public static List<Recipe<?>> recipes(){return recipes;}
    public static Recipe<?> find(ResourceLocation id){return byId.get(id);}
    public static long revision(){return revision;}
    public static void accept(Map<ResourceLocation,List<Recipe<?>>> tables){
        Map<ResourceLocation,Recipe<?>> unique=new LinkedHashMap<>();
        tables.forEach((d,list)->list.forEach(r->unique.putIfAbsent(r.getId(),r)));
        byId=Map.copyOf(unique);recipes=List.copyOf(unique.values());revision++;
        recipe_browser_index.invalidate();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){accept(Map.of());}
}
