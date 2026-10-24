package com.damezame97.white_lily.client;

import com.damezame97.white_lily.recipebook.browser_categories;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client resource packs may replace classification without modifying recipes or server data. */
@Mod.EventBusSubscriber(modid="white_lily",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class browser_category_reload {
    public static final ResourceLocation FILE=ResourceLocation.fromNamespaceAndPath("white_lily","recipe_browser/categories.json");
    private static browser_categories.Rules rules=browser_categories.empty();
    static browser_categories.Category classify(net.minecraft.world.item.ItemStack stack){return rules.classify(stack);}
    static void invalidate(){rules.invalidate();}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)manager->{
            try(var reader=manager.getResource(FILE).orElseThrow().openAsReader()){
                rules=browser_categories.parse(JsonParser.parseReader(reader),message->com.mojang.logging.LogUtils.getLogger().warn("{}: {}",FILE,message));
            }catch(Exception e){rules=browser_categories.empty();com.mojang.logging.LogUtils.getLogger().warn("Cannot load {}; using item-name fallback",FILE,e);}
            recipe_browser_index.invalidate();
        });
    }
    private browser_category_reload() { }
}
