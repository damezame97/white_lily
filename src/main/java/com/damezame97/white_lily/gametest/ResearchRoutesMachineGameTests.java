package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.processing.*;
import com.damezame97.white_lily.research.*;
import com.damezame97.white_lily.crafting.prescription_recipes;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** Real recipe manager, result slots, block placement, menus and saved machines; no fake success flags. */
@GameTestHolder("white_lily") @PrefixGameTestTemplate(false) @SuppressWarnings("unused")
public final class ResearchRoutesMachineGameTests
{
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("white_lily",path);}
    private static research_progress state(net.minecraft.world.entity.player.Player p){return research_capability.get(p).orElseThrow();}
    private static void learn(net.minecraft.world.entity.player.Player p,String... paths){for(String path:paths)state(p).complete(id(path));}
    private static processing_block_entity table(GameTestHelper h)
    {var pos=new BlockPos(1,2,1);h.setBlock(pos,Blocks.AIR);h.setBlock(pos,medical_blocks.SIMPLE_PROCESSING_TABLE.get());return (processing_block_entity)h.getBlockEntity(pos);}
    private static void tick(processing_block_entity t,int count){for(int i=0;i<count;i++)processing_block_entity.serverTick(t.getLevel(),t.getBlockPos(),t.getBlockState(),t);}
    private static void fill(processing_block_entity t,Item raw,int count)
    {t.getInventory().setStackInSlot(0,new ItemStack(items.STONE_MORTAR.get()));t.getInventory().setStackInSlot(1,new ItemStack(raw,count));}
    private static com.google.gson.JsonObject node(com.google.gson.JsonObject snapshot,String path)
    {for(var entry:snapshot.getAsJsonArray("nodes"))if(entry.getAsJsonObject().get("id").getAsString().equals(id(path).toString()))return entry.getAsJsonObject();return null;}

    @GameTest(template="empty") public static void nineNodesKeepOldIdsAndCrossChapterRevealIsNotAnExtraRoute(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var p=new Fixture(h);yes(h,research_catalog.nodes().size()==9,"exactly nine nodes");
            yes(h,research_catalog.categories().get(0).chapters().size()==2,"two chapters");
            var initial=research_service.buildSnapshot(p,false);yes(h,initial.getAsJsonArray("nodes").size()==4,"four discovery roots only");
            yes(h,node(initial,"clean_fine_bandage")==null,"fine bandage hidden initially");
            learn(p,"yarrow","yarrow_dressing");yes(h,node(research_service.buildSnapshot(p,false),"clean_fine_bandage")==null,"dressing alone does not reveal fine bandage");
            var other=new Fixture(h);learn(other,"coarse_cloth","simple_bandage");
            var fine=node(research_service.buildSnapshot(other,false),"clean_fine_bandage");
            yes(h,fine!=null&&fine.get("state").getAsString().equals("locked")&&!fine.has("recipes"),"visible but cross prerequisite locks recipe");
            var clue=fine.getAsJsonArray("prerequisites").get(1).getAsJsonObject();
            yes(h,clue.get("jump").getAsString().equals(id("yarrow").toString())&&!clue.has("id"),"hidden dressing only links to visible yarrow clue");
            learn(other,"calming_flower","calming_flower_tea","legacy_unknown");var saved=state(other).save();var restored=new research_progress();restored.load(saved);
            yes(h,restored.has(id("calming_flower_tea"))&&restored.has(id("legacy_unknown")),"old tea IDs and unknown historical records preserved");h.succeed();
        }
    }

    @GameTest(template="empty") public static void submissionsAndMultiPrerequisiteEditingRemainAtomic(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            Fixture p=new Fixture(h);learn(p,"coarse_cloth");
            p.getInventory().setItem(3,new ItemStack(items.COARSE_CLOTH.get()));
            long rev=research_service.revision(p);
            yes(h,research_service.submit(p,id("simple_bandage"),rev)==research_service.SubmitResult.MISSING_MATERIALS,"missing strip no partial debit");
            yes(h,p.getInventory().getItem(3).getCount()==1,"cloth retained");p.getInventory().setItem(22,new ItemStack(items.THIN_CLOTH_STRIP.get()));
            yes(h,research_service.submit(p,id("simple_bandage"),rev)==research_service.SubmitResult.COMPLETE,"materials in different slots");
            yes(h,research_service.submit(p,id("simple_bandage"),rev)==research_service.SubmitResult.ALREADY_COMPLETE,"repeat packet no debit");
            yes(h,research_service.submit(p,id("clean_fine_bandage"),research_service.revision(p))==research_service.SubmitResult.LOCKED,"cross chapter required");
            research_service.setEditing(p,true,research_service.revision(p));
            yes(h,research_service.edit(p,id("clean_fine_bandage"),true,research_service.revision(p))==research_service.EditResult.APPLIED,"complete missing prerequisites");
            yes(h,state(p).has(id("yarrow"))&&state(p).has(id("yarrow_dressing"))&&!state(p).has(id("bitterleaf")),"necessary cross branch only");
            var fine=node(research_service.buildSnapshot(p,false),"clean_fine_bandage");yes(h,fine.getAsJsonArray("recipes").size()==2,"both actual bandage recipes displayed");
            research_service.edit(p,id("yarrow"),false,research_service.revision(p));
            yes(h,!state(p).has(id("clean_fine_bandage"))&&state(p).has(id("simple_bandage")),"dependent reset leaves unrelated ancestor branch intact");h.succeed();
        }
    }

    @GameTest(template="empty") public static void bandageResultSlotsBookShiftAndHoneyRemaindersUseIndividualResearch(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            Fixture p=new Fixture(h),other=new Fixture(h);var menu=p.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(items.COARSE_CLOTH.get(),3));menu.getSlot(2).set(new ItemStack(items.THIN_CLOTH_STRIP.get(),3));
            yes(h,menu.getSlot(0).getItem().isEmpty()&&menu.quickMoveStack(p,0).isEmpty(),"manual and shift locked");
            learn(p,"coarse_cloth","simple_bandage");research_service.reconcile(p);
            yes(h,menu.getSlot(0).getItem().getCount()==2,"actual result opens immediately");menu.clicked(0,0,ClickType.QUICK_MOVE,p);
            yes(h,p.getInventory().countItem(items.SIMPLE_BANDAGE.get())==6,"three batches yield six, not research consumption");
            other.inventoryMenu.getSlot(1).set(new ItemStack(items.COARSE_CLOTH.get()));other.inventoryMenu.getSlot(2).set(new ItemStack(items.THIN_CLOTH_STRIP.get()));
            yes(h,other.inventoryMenu.getSlot(0).getItem().isEmpty(),"no cross-player permission");
            learn(p,"yarrow","yarrow_dressing","clean_fine_bandage");research_service.reconcile(p);
            menu.getSlot(1).set(new ItemStack(items.SIMPLE_BANDAGE.get(),2));menu.getSlot(2).set(new ItemStack(items.YARROW_DRESSING.get(),2));menu.getSlot(3).set(new ItemStack(Items.HONEY_BOTTLE,2));
            menu.clicked(0,0,ClickType.QUICK_MOVE,p);
            yes(h,p.getInventory().countItem(items.CLEAN_FINE_BANDAGE.get())==2,"honey batch");
            int bottles=p.getInventory().countItem(Items.GLASS_BOTTLE);for(int i=1;i<=4;i++)if(menu.getSlot(i).getItem().is(Items.GLASS_BOTTLE))bottles+=menu.getSlot(i).getItem().getCount();
            yes(h,bottles==2,"honey retains both container returns");
            research_service.setEditing(p,true,research_service.revision(p));research_service.edit(p,id("simple_bandage"),false,research_service.revision(p));
            yes(h,!research_service.canCraft(p,id("clean_fine_bandage_honey"))&&!research_service.canCraft(p,id("clean_fine_bandage_beeswax"))&&!p.getRecipeBook().contains(id("simple_bandage")),"both variants and recipe book revoked");h.succeed();
        }
    }

    @GameTest(template="empty") public static void actualPlacementMergesOnlyPlacerKnowledgeAndIgnoresForgedBlockEntityTag(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var t=table(h);h.setBlock(new BlockPos(1,2,1),Blocks.AIR);h.setBlock(new BlockPos(1,1,1),Blocks.STONE);
            Fixture a=new Fixture(h);learn(a,"bitterleaf","bitterleaf_paste");
            BlockPos support=h.absolutePos(new BlockPos(1,1,1));ItemStack stack=new ItemStack(items.SIMPLE_PROCESSING_TABLE.get());
            CompoundTag forged=new CompoundTag();ListTag records=new ListTag();records.add(StringTag.valueOf(id("processing/yarrow_dressing").toString()));forged.put("RegisteredRecipes",records);stack.getOrCreateTag().put("BlockEntityTag",forged);
            a.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
            yes(h,((BlockItem)stack.getItem()).place(new BlockPlaceContext(a,InteractionHand.MAIN_HAND,stack,hit)).consumesAction(),"real BlockItem placement succeeds");
            t=(processing_block_entity)h.getBlockEntity(new BlockPos(1,2,1));
            yes(h,t.registeredRecipes().equals(Set.of(id("processing/bitterleaf_paste"))),"only learned processing, not forged NBT or crafting");
            Fixture b=new Fixture(h);learn(b,"yarrow","yarrow_dressing");yes(h,t.registerLearned(b)==1&&t.registerLearned(b)==0,"merge once, preserve original");
            var c=h.makeMockPlayer();var fresh=table(h);medical_blocks.SIMPLE_PROCESSING_TABLE.get().setPlacedBy(h.getLevel(),fresh.getBlockPos(),fresh.getBlockState(),c,new ItemStack(items.SIMPLE_PROCESSING_TABLE.get()));
            yes(h,fresh.registeredRecipes().isEmpty()&&fresh.registerLearned(c)==0,"creative grants no research or machine-to-machine copying");h.succeed();
        }
    }

    @GameTest(template="empty") public static void registrationRemainsAndMachineKeepsRunningAfterPersonalReset(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var t=table(h);fill(t,items.BITTERLEAF.get(),6);tick(t,150);
            yes(h,t.getData().get(2)==processing_block_entity.UNREGISTERED&&t.getData().get(0)==0&&t.getInventory().getStackInSlot(1).getCount()==6&&t.getInventory().getStackInSlot(0).getDamageValue()==0&&t.getInventory().getStackInSlot(5).isEmpty(),"unregistered no processing side effects");
            Fixture a=new Fixture(h);learn(a,"bitterleaf","bitterleaf_paste");yes(h,t.registerLearned(a)==1,"register without moving inputs");
            tick(t,200);yes(h,t.getInventory().getStackInSlot(5).getCount()==4&&t.getInventory().getStackInSlot(0).getDamageValue()==2,"registered machine runs two batches without a live operator");
            research_service.setEditing(a,true,research_service.revision(a));research_service.edit(a,id("bitterleaf"),false,research_service.revision(a));
            yes(h,!state(a).has(id("bitterleaf_paste"))&&t.registeredRecipes().size()==1,"machine registration persists after personal reset");
            fill(t,items.BITTERLEAF.get(),3);tick(t,100);
            yes(h,t.getInventory().getStackInSlot(5).getCount()==6,"registered machine keeps producing after the registrant's personal reset");
            var fresh=new processing_block_entity(t.getBlockPos(),t.getBlockState());fresh.setLevel(h.getLevel());
            yes(h,fresh.registerLearned(a)==0,"reset player cannot teach a new machine");
            fill(fresh,items.HEMP_STALK.get(),1);tick(fresh,40);
            yes(h,fresh.getInventory().getStackInSlot(5).is(items.PLANT_FIBER.get()),"base recipe runs with no registration and no player");h.succeed();
        }
    }

    @GameTest(template="empty") public static void savedRecordsAndLegacyMachinesPauseWithoutErasingInventory(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var t=table(h);var p=new Fixture(h);learn(p,"bitterleaf_paste");t.registerLearned(p);fill(t,items.BITTERLEAF.get(),3);tick(t,37);
            CompoundTag saved=t.saveWithFullMetadata();var restored=restore(t,saved);tick(restored,63);
            yes(h,restored.getInventory().getStackInSlot(5).getCount()==2&&restored.registeredRecipes().size()==1,"saved records + remaining progress resume");
            var legacyTag=saved.copy();legacyTag.remove("RegisteredRecipes");var legacy=restore(t,legacyTag);tick(legacy,200);
            yes(h,legacy.getInventory().getStackInSlot(1).getCount()==3&&legacy.getData().get(0)==37&&legacy.getData().get(2)==3,"legacy retains inputs and paused progress");
            legacy.registerLearned(p);tick(legacy,63);yes(h,legacy.getInventory().getStackInSlot(5).getCount()==2,"registration resumes only valid same fingerprint");h.succeed();
        }
    }

    @GameTest(template="empty") public static void invalidSavedIdsAndMerelyOpeningMachineNeverTeachKnowledge(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var t=table(h);fill(t,items.BITTERLEAF.get(),3);CompoundTag saved=t.saveWithFullMetadata();
            ListTag forged=new ListTag();forged.add(StringTag.valueOf("bad id!"));forged.add(StringTag.valueOf(id("calming_flower_tea").toString()));
            saved.put("RegisteredRecipes",forged);
            var loaded=restore(t,saved);tick(loaded,100);
            yes(h,loaded.getInventory().getStackInSlot(5).isEmpty()&&loaded.getData().get(0)==0,"invalid IDs and non-processing/node IDs cannot authorize");
            Fixture p=new Fixture(h);learn(p,"bitterleaf_paste");p.setPos(t.getBlockPos().getX()+.5,t.getBlockPos().getY(),t.getBlockPos().getZ()+.5);
            var menu=new processing_menu(4,p.getInventory(),t);p.containerMenu=menu;tick(t,100);
            yes(h,t.registeredRecipes().isEmpty()&&t.getInventory().getStackInSlot(5).isEmpty(),"opening, materials and a knowledgeable viewer do not auto-register");
            menu.clickMenuButton(p,0);tick(t,100);yes(h,t.getInventory().getStackInSlot(5).getCount()==2,"explicit register changes actual machine capability");
            yes(h,!state(p).has(id("yarrow_dressing")),"machine knowledge never creates personal research");h.succeed();
        }
    }

    @GameTest(template="empty") public static void registrationMenuRejectsWrongDistanceAndReplacedMachines(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var t=table(h);Fixture p=new Fixture(h);learn(p,"bitterleaf_paste");var menu=new processing_menu(4,p.getInventory(),t);
            p.setPos(t.getBlockPos().getX()+.5,t.getBlockPos().getY(),t.getBlockPos().getZ()+.5);
            yes(h,!menu.clickMenuButton(p,0),"wrong current container rejected");p.containerMenu=menu;
            p.setPos(t.getBlockPos().getX()+100,t.getBlockPos().getY(),t.getBlockPos().getZ());yes(h,!menu.clickMenuButton(p,0),"remote rejected");
            p.setPos(t.getBlockPos().getX()+.5,t.getBlockPos().getY(),t.getBlockPos().getZ()+.5);
            yes(h,!menu.clickMenuButton(p,99)&&menu.clickMenuButton(p,0),"only valid current menu registers");
            yes(h,t.registeredRecipes().size()==1&&p.getInventory().isEmpty(),"no material cost");
            table(h);yes(h,!menu.clickMenuButton(p,0),"old menu cannot access replaced entity");h.succeed();
        }
    }

    @GameTest(template="empty") public static void restrictedRecipeCannotBlockAllowedMatchAndReloadCannotReviveMissingRecipe(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var manager=h.getLevel().getRecipeManager();List<Recipe<?>> originals=new ArrayList<>(manager.getRecipes());
            var source=(processing_recipe)manager.byKey(id("processing/bitterleaf_paste")).orElseThrow();
            var json=com.google.gson.JsonParser.parseString("{\"tool\":{\"item\":\"white_lily:stone_mortar\"},\"ingredients\":[{\"ingredient\":{\"item\":\"white_lily:bitterleaf\"},\"count\":3}],\"result\":{\"item\":\"white_lily:plant_fiber\"},\"processing_time\":2}").getAsJsonObject();
            var allowed=prescription_recipes.PROCESSING.get().fromJson(id("zzz_allowed_fixture"),json);
            try{
                var all=new ArrayList<>(originals);all.add(allowed);manager.replaceRecipes(all);var t=table(h);
                var authorizer=ProcessingTestPlayers.create(h);com.damezame97.white_lily.recipebook.recipe_unlocks.unlock(authorizer,allowed.getId());
                fill(t,items.BITTERLEAF.get(),3);tick(t,2);
                yes(h,t.getInventory().getStackInSlot(5).is(items.PLANT_FIBER.get()),"restricted lexically earlier recipe does not block allowed match");
                manager.replaceRecipes(originals);t=table(h);var p=new Fixture(h);learn(p,"bitterleaf_paste");t.registerLearned(p);fill(t,items.BITTERLEAF.get(),3);tick(t,20);
                manager.replaceRecipes(originals.stream().filter(r->!r.getId().equals(source.getId())).toList());tick(t,100);
                yes(h,t.getInventory().getStackInSlot(5).isEmpty()&&t.getData().get(0)==0,"removed recipe does not continue from saved permission");
                var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{prescription_recipes.PROCESSING.get().toNetwork(buf,source);yes(h,prescription_recipes.PROCESSING.get().fromNetwork(source.getId(),buf).researchNode().equals(id("bitterleaf_paste")),"research binding network round trip");}finally{buf.release();}
            }finally{manager.replaceRecipes(originals);}h.succeed();
        }
    }

    @GameTest(template="empty") public static void breakingDropsCleanTableAndInventoryOnlyOnce(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var t=table(h);var p=new Fixture(h);learn(p,"bitterleaf_paste");t.registerLearned(p);fill(t,items.BITTERLEAF.get(),3);
            var area=new AABB(t.getBlockPos()).inflate(.5);int before=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.getLevel().destroyBlock(t.getBlockPos(),true);t.dropContents();
            int total=0,tables=0;for(var entity:h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area))
            {var stack=entity.getItem();total+=stack.getCount();if(stack.is(items.SIMPLE_PROCESSING_TABLE.get())){tables+=stack.getCount();yes(h,!stack.hasTag(),"dropped machine has no records or inventory NBT");}}
            yes(h,total-before==5&&tables==1,"one tool, three herbs, one clean table, no duplicate callback");h.succeed();
        }
    }
    private static processing_block_entity restore(processing_block_entity t,CompoundTag tag)
    {var loaded=new processing_block_entity(t.getBlockPos(),t.getBlockState());loaded.load(tag);loaded.setLevel(t.getLevel());return loaded;}
    private static final class Fixture extends ServerPlayer
    {
        Fixture(GameTestHelper h){super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"RouteQA"));connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),this){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketSendListener listener){}};setHealth(getMaxHealth());ProcessingTestPlayers.track(this);com.damezame97.white_lily.recipebook.recipe_unlocks.initialize(this);}
        @Override public boolean hasPermissions(int level){return level<=2;}
    }
}

