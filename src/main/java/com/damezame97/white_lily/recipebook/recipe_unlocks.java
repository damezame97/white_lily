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
        .networkProtocolVersion(()->"2").clientAcceptedVersions("2"::equals).serverAcceptedVersions("2"::equals).simpleChannel();
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
        Progress data=progress(player); if(!data.recipes.add(id)) return false;
        data.revision++;
        if(evaluateConditional(player)) data.revision++;
        save(player,data); sync(player); refresh(player); return true;
    }
    private static boolean exists(ServerPlayer player,ResourceLocation id) {
        return isOperation(id) || player.level().getRecipeManager().byKey(id).isPresent();
    }
    /** True for a real recipe/operation whose current research binding is also valid. */
    public static boolean canGrant(ServerPlayer player,ResourceLocation id) {
        return player!=null && id!=null && exists(player,id) && research_service.canCraft(player,id);
    }
    public static boolean isOperation(ResourceLocation id) {
        return id!=null && id.getNamespace().equals(com.damezame97.white_lily.white_lily.MODID)
                && id.getPath().startsWith("operation/");
    }
    private static boolean grant(ServerPlayer player,recipe_unlock_rules.Grant rule) {
        Progress data=progress(player); boolean changed=false;
        for(ResourceLocation id:recipe_unlock_rules.resolve(rule,player.level())) if(exists(player,id) && data.recipes.add(id)) changed=true;
        return changed;
    }
    /** Re-evaluate data-driven AND conditions after a new item or recipe unlock. */
    private static boolean evaluateConditional(ServerPlayer player) {
        Progress data=progress(player); boolean changed=false;
        // A condition may unlock the recipe another condition requires; converge in bounded passes.
        for(int pass=0;pass<16;pass++) {
            boolean passChanged=false;
            // Snapshot only recipes the player really owns; tags such as #minecraft:wooden_doors
            // then mean "any one door recipe this player has learned", not "all species".
            List<ItemStack> unlockedOutputs=new ArrayList<>();
            for(ResourceLocation id:unlocked(player)) {
                Recipe<?> recipe=player.level().getRecipeManager().byKey(id).orElse(null);
                if(recipe==null) continue;
                ItemStack output=recipe.getResultItem(player.level().registryAccess());
                if(!output.isEmpty()) unlockedOutputs.add(output);
            }
            java.util.function.Predicate<String> outputUnlocked=selector->
                    unlockedOutputs.stream().anyMatch(output->recipe_unlock_rules.matches(selector,output));
            for(recipe_unlock_rules.Conditional rule:recipe_unlock_rules.conditionals()) {
                if(!rule.matches(data.seen,id->isUnlocked(player,id),outputUnlocked)) continue;
                for(ResourceLocation id:recipe_unlock_rules.resolve(rule.grant(),player.level()))
                    if(exists(player,id) && research_service.canCraft(player,id) && data.recipes.add(id)) passChanged=true;
            }
            if(!passChanged) break;
            changed=true;
        }
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
        changed|=evaluateConditional(player);
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
        changed|=evaluateConditional(player);
        if(changed) data.revision++; save(player,data); if(changed) { sync(player); refresh(player); }
    }
    public static synchronized void register() {
        if(registered) return; registered=true;
        CHANNEL.messageBuilder(Snapshot.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::write).decoder(Snapshot::read).consumerNetworkThread(Snapshot::handle).add();
        CHANNEL.messageBuilder(TransferReply.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(TransferReply::write).decoder(TransferReply::read).consumerNetworkThread(TransferReply::handle).add();
        CHANNEL.messageBuilder(Transfer.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(Transfer::write).decoder(Transfer::read).consumerNetworkThread(Transfer::handle).add();
    }
    private static int legacyRequest;
    public static void requestTransfer(int containerId,ResourceLocation recipe,boolean maximum) {
        var mcRecipe=net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.damezame97.white_lily.client.recipe_browser_index.recipeFingerprint(recipe));
        requestTransfer(containerId,recipe,maximum,mcRecipe==null?"":mcRecipe,--legacyRequest);
    }
    public static void requestTransfer(int menu,ResourceLocation recipe,boolean maximum,String fingerprint,int request) {
        if(recipe!=null)CHANNEL.sendToServer(new Transfer(menu,recipe,maximum,fingerprint,request));
    }
    private static final Map<Player,Long> LAST_TRANSFER=Collections.synchronizedMap(new WeakHashMap<>());
    private record Processed(int menu,ResourceLocation recipe,boolean maximum,String fingerprint,transfer_reason reason) { }
    private static final Map<Player,LinkedHashMap<Integer,Processed>> TRANSFERS=Collections.synchronizedMap(new WeakHashMap<>());
    public static transfer_reason processTransfer(ServerPlayer player,int menu,ResourceLocation recipe,boolean maximum,String fingerprint,int request)
    {
        if(!player.isAlive()||player.containerMenu.containerId!=menu||!player.containerMenu.stillValid(player))return transfer_reason.MENU_INVALID;
        var cache=TRANSFERS.computeIfAbsent(player,p->new LinkedHashMap<>());Processed prior=cache.get(request);
        if(prior!=null)return prior.menu()==menu&&prior.recipe().equals(recipe)&&prior.maximum()==maximum&&prior.fingerprint().equals(fingerprint)?prior.reason():transfer_reason.DATA_CHANGED;
        long tick=player.level().getGameTime(),last=LAST_TRANSFER.getOrDefault(player,Long.MIN_VALUE);
        transfer_reason result;
        if(last!=Long.MIN_VALUE&&tick>=last&&tick-last<3)result=transfer_reason.COOLDOWN;
        else{LAST_TRANSFER.put(player,tick);result=browser_transfer.apply(player,menu,recipe,fingerprint,maximum);}
        if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());cache.put(request,new Processed(menu,recipe,maximum,fingerprint,result));return result;
    }
    private record Transfer(int menu,ResourceLocation recipe,boolean maximum,String fingerprint,int request) {
        void write(FriendlyByteBuf b){b.writeVarInt(menu);b.writeResourceLocation(recipe);b.writeBoolean(maximum);b.writeUtf(fingerprint,128);b.writeVarInt(request);}
        static Transfer read(FriendlyByteBuf b){return new Transfer(b.readVarInt(),b.readResourceLocation(),b.readBoolean(),b.readUtf(128),b.readVarInt());}
        void handle(Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();context.enqueueWork(()->{
                ServerPlayer player=context.getSender();if(player==null||player.connection==null)return;
                transfer_reason result=processTransfer(player,menu,recipe,maximum,fingerprint,request);
                CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new TransferReply(menu,recipe,request,result));
            });context.setPacketHandled(true);
        }
    }
    private record TransferReply(int menu,ResourceLocation recipe,int request,transfer_reason reason){
        void write(FriendlyByteBuf b){b.writeVarInt(menu);b.writeResourceLocation(recipe);b.writeVarInt(request);b.writeEnum(reason);}
        static TransferReply read(FriendlyByteBuf b){return new TransferReply(b.readVarInt(),b.readResourceLocation(),b.readVarInt(),b.readEnum(transfer_reason.class));}
        void handle(Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->browser_transfer_feedback.accept(new browser_transfer_feedback.Reply(menu,recipe,request,reason)));context.setPacketHandled(true);}
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
