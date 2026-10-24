package com.damezame97.white_lily.recipebook;

import com.damezame97.white_lily.research.research_catalog;
import com.damezame97.white_lily.research.research_service;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Per-player recipe whitelist. Only server code can grant permissions. */
@Mod.EventBusSubscriber(modid="white_lily")
public final class recipe_unlocks {
    public static final String DATA_KEY="white_lily_recipe_unlocks";
    private static final Map<Player,Progress> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath("white_lily","recipe_unlocks"))
        .networkProtocolVersion(()->"1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    private static boolean registered;
    private static final class Progress {
        final Set<ResourceLocation> recipes=new LinkedHashSet<>(), seen=new LinkedHashSet<>();
        long revision, generation=Long.MIN_VALUE;
        Set<ResourceLocation> lastEffective=Set.of();
        Progress(CompoundTag tag) {
            for(Tag value:tag.getList("recipes",Tag.TAG_STRING)) { ResourceLocation id=ResourceLocation.tryParse(value.getAsString()); if(id!=null) recipes.add(id); }
            for(Tag value:tag.getList("seen_items",Tag.TAG_STRING)) { ResourceLocation id=ResourceLocation.tryParse(value.getAsString()); if(id!=null) seen.add(id); }
            revision=tag.getLong("revision");
            generation=tag.contains("generation",Tag.TAG_LONG)?tag.getLong("generation"):Long.MIN_VALUE;
        }
    }
    private static CompoundTag persisted(Player player) {
        CompoundTag data=player.getPersistentData();
        if(!data.contains(Player.PERSISTED_NBT_TAG,Tag.TAG_COMPOUND)) data.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }
    private static Progress progress(Player player) { return CACHE.computeIfAbsent(player,key->new Progress(persisted(player).getCompound(DATA_KEY))); }
    private static void save(Player player,Progress data) {
        CompoundTag tag=new CompoundTag(); ListTag recipes=new ListTag(),seen=new ListTag();
        data.recipes.forEach(id->recipes.add(StringTag.valueOf(id.toString())));
        data.seen.forEach(id->seen.add(StringTag.valueOf(id.toString())));
        tag.put("recipes",recipes); tag.put("seen_items",seen); tag.putLong("revision",data.revision); tag.putLong("generation",data.generation);
        persisted(player).put(DATA_KEY,tag);
    }
    public static boolean isUnlocked(Player player,ResourceLocation id) {
        if(player==null || id==null) return false;
        if(player.level().isClientSide) return recipe_unlock_client.isUnlocked(id);
        // Completing a research node grants its recipes, but a recipe book cannot skip that node.
        if(research_catalog.recipeOwners().containsKey(id)) return research_service.canCraft(player,id);
        return progress(player).recipes.contains(id) && research_service.canCraft(player,id);
    }
    public static Set<ResourceLocation> unlocked(Player player) {
        Set<ResourceLocation> result=new LinkedHashSet<>();
        for(ResourceLocation id:progress(player).recipes) if(isUnlocked(player,id)) result.add(id);
        for(ResourceLocation id:research_catalog.recipeOwners().keySet()) if(isUnlocked(player,id)) result.add(id);
        return Set.copyOf(result);
    }
    public static long revision(Player player) { return progress(player).revision; }
    /** GameTest-only: grant a non-networked fake player without touching a client snapshot. */
    public static void grantForTesting(Player player,ResourceLocation id) {
        if(player==null || id==null || player.level().isClientSide) return;
        Progress data=progress(player); if(data.recipes.add(id)) { data.revision++; save(player,data); }
    }
    public static boolean unlock(ServerPlayer player,ResourceLocation id) {
        if(id==null || !exists(player,id) || !research_service.canCraft(player,id)) return false;
        if(!progress(player).recipes.add(id)) return false;
        progress(player).revision++; save(player,progress(player)); sync(player); refresh(player); return true;
    }
    private static boolean exists(ServerPlayer player,ResourceLocation id) {
        return id.getNamespace().equals("white_lily") && id.getPath().startsWith("operation/") || player.level().getRecipeManager().byKey(id).isPresent();
    }
    private static boolean grant(ServerPlayer player,recipe_unlock_rules.Grant rule) {
        Progress data=progress(player); boolean changed=false;
        for(ResourceLocation id:recipe_unlock_rules.resolve(rule,player.level())) if(exists(player,id) && data.recipes.add(id)) changed=true;
        return changed;
    }
    public static void initialize(ServerPlayer player) {
        Progress data=progress(player); boolean changed=false;
        if(data.generation!=recipe_unlock_rules.generation()) {
            for(var initial:recipe_unlock_rules.initial()) changed|=grant(player,initial);
            data.generation=recipe_unlock_rules.generation();
            for(var entry:recipe_unlock_rules.advancements().entrySet()) {
                var advancement=player.server.getAdvancements().getAdvancement(entry.getKey());
                if(advancement!=null && player.getAdvancements().getOrStartProgress(advancement).isDone())
                    for(var rule:entry.getValue()) changed|=grant(player,rule);
            }
            // Reloaded pickup rules apply to items that this player has already discovered.
            for(var rule:recipe_unlock_rules.pickups()) for(ResourceLocation item:data.seen) {
                var actual=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(item);
                if(actual!=null && recipe_unlock_rules.matches(rule.item(),new ItemStack(actual))) changed|=grant(player,rule.grant());
            }
        }
        for(ResourceLocation id:research_catalog.recipeOwners().keySet()) if(research_service.canCraft(player,id)) changed|=data.recipes.add(id);
        if(changed) data.revision++;
        save(player,data); sync(player);
    }
    public static void researchChanged(ServerPlayer player) { initialize(player); }
    public static void sync(ServerPlayer player) {
        Set<ResourceLocation> allowed=unlocked(player);
        Progress data=progress(player);
        if(!allowed.equals(data.lastEffective)) { data.lastEffective=allowed; data.revision++; save(player,data); }
        if(player.connection==null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(progress(player).revision,allowed));
        List<Recipe<?>> add=new ArrayList<>(),remove=new ArrayList<>();
        for(Recipe<?> recipe:player.level().getRecipeManager().getRecipes()) {
            if(allowed.contains(recipe.getId())) { if(!player.getRecipeBook().contains(recipe)) add.add(recipe); }
            else if(player.getRecipeBook().contains(recipe)) remove.add(recipe);
        }
        if(!remove.isEmpty()) player.resetRecipes(remove);
        if(!add.isEmpty()) player.awardRecipes(add);
    }
    private static void refresh(ServerPlayer player) {
        if(player.connection==null) return;
        com.damezame97.white_lily.research.research_crafting_gate.refresh(player);
        player.containerMenu.slotsChanged(player.containerMenu.slots.isEmpty()?player.getInventory():player.containerMenu.getSlot(0).container);
        player.containerMenu.broadcastChanges();
    }
    public static void acquired(ServerPlayer player,ItemStack stack) {
        if(stack.isEmpty()) return;
        ResourceLocation id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        Progress data=progress(player); if(id==null || !data.seen.add(id)) return;
        boolean changed=false; for(var rule:recipe_unlock_rules.pickups()) if(recipe_unlock_rules.matches(rule.item(),stack)) changed|=grant(player,rule.grant());
        if(changed) data.revision++; save(player,data); if(changed) { sync(player); refresh(player); }
    }
    public static synchronized void register() {
        if(registered) return; registered=true;
        CHANNEL.messageBuilder(Snapshot.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::write).decoder(Snapshot::read).consumerNetworkThread(Snapshot::handle).add();
        CHANNEL.messageBuilder(Transfer.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(Transfer::write).decoder(Transfer::read).consumerNetworkThread(Transfer::handle).add();
    }
    public static void requestTransfer(int containerId,ResourceLocation recipe,boolean maximum) {
        if(recipe!=null) CHANNEL.sendToServer(new Transfer(containerId,recipe,maximum));
    }
    private record Transfer(int menu,ResourceLocation recipe,boolean maximum) {
        void write(FriendlyByteBuf buffer) { buffer.writeVarInt(menu); buffer.writeResourceLocation(recipe); buffer.writeBoolean(maximum); }
        static Transfer read(FriendlyByteBuf buffer) { return new Transfer(buffer.readVarInt(),buffer.readResourceLocation(),buffer.readBoolean()); }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context=supplier.get(); ServerPlayer player=context.getSender();
            context.enqueueWork(()-> {
                if(player!=null && player.isAlive() && player.containerMenu.containerId==menu
                    && player.containerMenu instanceof com.damezame97.white_lily.processing.processing_menu table
                    && table.authorizeRecipeTransfer(player,recipe)) table.fillRecipe(player,recipe,maximum);
            }); context.setPacketHandled(true);
        }
    }
    private record Snapshot(long revision,Set<ResourceLocation> recipes) {
        void write(FriendlyByteBuf buffer) { buffer.writeLong(revision); buffer.writeVarInt(recipes.size()); recipes.forEach(buffer::writeResourceLocation); }
        static Snapshot read(FriendlyByteBuf buffer) {
            long revision=buffer.readLong(); int size=buffer.readVarInt(); if(size<0 || size>100000) throw new IllegalArgumentException("Invalid recipe snapshot size");
            Set<ResourceLocation> recipes=new LinkedHashSet<>(); for(int i=0;i<size;i++) recipes.add(buffer.readResourceLocation()); return new Snapshot(revision,Set.copyOf(recipes));
        }
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var context=supplier.get(); context.enqueueWork(()->recipe_unlock_client.accept(recipes,revision)); context.setPacketHandled(true);
        }
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(recipe_unlock_rules.INSTANCE); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        CompoundTag old=persisted(event.getOriginal()).getCompound(DATA_KEY).copy();
        persisted(event.getEntity()).put(DATA_KEY,old); CACHE.remove(event.getEntity());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { if(event.getEntity() instanceof ServerPlayer p) initialize(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { if(event.getEntity() instanceof ServerPlayer p) { initialize(p); refresh(p); } }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if(event.getEntity() instanceof ServerPlayer p) initialize(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { CACHE.remove(event.getEntity()); }
    @SubscribeEvent public static void pickup(PlayerEvent.ItemPickupEvent event) { if(event.getEntity() instanceof ServerPlayer p) acquired(p,event.getStack()); }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) { if(event.getEntity() instanceof ServerPlayer p) acquired(p,event.getCrafting()); }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) { if(event.getEntity() instanceof ServerPlayer p) acquired(p,event.getSmelting()); }
    @SubscribeEvent public static void advancement(AdvancementEvent.AdvancementEarnEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean changed=false; for(var rule:recipe_unlock_rules.advancements().getOrDefault(event.getAdvancement().getId(),List.of())) changed|=grant(player,rule);
        if(changed) { Progress data=progress(player); data.revision++; save(player,data); sync(player); refresh(player); }
    }
    @SubscribeEvent public static void datapack(OnDatapackSyncEvent event) {
        if(event.getPlayer()!=null) { initialize(event.getPlayer()); refresh(event.getPlayer()); }
        else for(ServerPlayer player:event.getPlayerList().getPlayers()) { initialize(player); refresh(player); }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount%20!=0) return;
        // Inventory acquisition also covers container transfers, commands and other mods that skip pickup events.
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) acquired(player,player.getInventory().getItem(slot));
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("recipeunlock").requires(source->source.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("grant")
                .then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player())
                    .then(net.minecraft.commands.Commands.argument("recipe",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                        .executes(context-> {
                            ServerPlayer player=net.minecraft.commands.arguments.EntityArgument.getPlayer(context,"player");
                            ResourceLocation id=net.minecraft.commands.arguments.ResourceLocationArgument.getId(context,"recipe");
                            boolean changed=unlock(player,id);
                            context.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(changed?"Unlocked "+id:"Recipe already unlocked, invalid, or requires research: "+id),false);
                            return changed?1:0;
                        })))));
    }
    private recipe_unlocks() { }
}
