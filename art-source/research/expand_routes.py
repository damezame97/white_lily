"""One-time, reviewed source/data migration for the first nine research nodes."""
from pathlib import Path
import json, shutil
ROOT=Path(__file__).resolve().parents[2]
J=ROOT/'src/main/java/com/damezame97/white_lily'
R=ROOT/'src/main/resources'
BACK=ROOT/'backups/research-machine-v1-20261023'
def write(p,s):
    if p.exists():
        b=BACK/p.relative_to(ROOT)
        if not b.exists(): b.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(p,b)
    p.parent.mkdir(parents=True,exist_ok=True); p.write_text(s,encoding='utf-8')
def change(p,a,b):
    s=p.read_text('utf-8'); assert a in s,(p,a[:100]); write(p,s.replace(a,b))
def data(p,o): write(p,json.dumps(o,ensure_ascii=False,indent=2)+'\n')

p=R/'data/white_lily/research/handbook.json'; d=json.loads(p.read_text('utf-8'))
if len(d.get('nodes',[]))!=2:
    raise SystemExit('One-time migration already applied or catalog changed; no files modified.')
d['categories'][0]['chapters'].append({'id':'white_lily:bandages','title':'research.white_lily.chapter.bandages'})
def node(name,chapter,y,parents=(),materials=(),recipes=(),reveal=None):
    root=not parents; prefix='research.white_lily.node.'+name+'.'
    n=dict(id='white_lily:'+name,category='white_lily:herbs_medical',chapter='white_lily:'+chapter,icon='white_lily:'+name,name=prefix+'name',intro=prefix+'intro',hint=prefix+'hint',x=0 if root else 130,y=y,prerequisites=['white_lily:'+x for x in parents],root_visible=root,condition='obtain' if root else 'submit',materials=[{'item':'white_lily:'+i,'count':c} for i,c in materials],recipes=['white_lily:'+x for x in recipes],archive=[prefix+'archive.1'])
    if root:n['target']='white_lily:'+name
    if reveal is not None:n['reveal_after']=['white_lily:'+x for x in reveal]
    return n
# Keep original stable IDs and their full archives.
old={n['id'].split(':')[1]:n for n in d['nodes']}
old['calming_flower']['y']=140; old['calming_flower_tea']['y']=140; old['calming_flower_tea']['x']=130
d['nodes']=[node('bitterleaf','basic_herbs',-140),node('bitterleaf_paste','basic_herbs',-140,['bitterleaf'],[('bitterleaf',1),('blank_label',1)],['processing/bitterleaf_paste']),node('yarrow','basic_herbs',0),node('yarrow_dressing','basic_herbs',0,['yarrow'],[('yarrow',1),('blank_label',1)],['processing/yarrow_dressing']),old['calming_flower'],old['calming_flower_tea'],node('coarse_cloth','bandages',0),node('simple_bandage','bandages',0,['coarse_cloth'],[('coarse_cloth',1),('thin_cloth_strip',1)],['simple_bandage']),node('clean_fine_bandage','bandages',0,['simple_bandage','yarrow_dressing'],[('simple_bandage',1),('yarrow_dressing',1),('blank_label',1)],['clean_fine_bandage_beeswax','clean_fine_bandage_honey'],['simple_bandage'])]
d['nodes'][-1]['x']=260
data(p,d)
for name in ['simple_bandage','clean_fine_bandage_beeswax','clean_fine_bandage_honey']:
    p=R/f'data/white_lily/recipes/{name}.json'; d=json.loads(p.read_text('utf-8'));d['type']='white_lily:research_crafting';data(p,d)
for name in ['bitterleaf_paste','yarrow_dressing']:
    p=R/f'data/white_lily/recipes/processing/{name}.json';d=json.loads(p.read_text('utf-8'));d['research']='white_lily:'+name;data(p,d)

p=J/'research/research_catalog.java'
change(p,'List<ResourceLocation> recipes, List<String> archive) { }','''List<ResourceLocation> recipes, List<String> archive, List<ResourceLocation> revealAfter) {
        public Node(ResourceLocation id,ResourceLocation category,ResourceLocation chapter,Item icon,String name,String intro,String hint,int x,int y,List<ResourceLocation> prerequisites,boolean rootVisible,String condition,Item target,List<Material> materials,List<ResourceLocation> recipes,List<String> archive)
        { this(id,category,chapter,icon,name,intro,hint,x,y,prerequisites,rootVisible,condition,target,materials,recipes,archive,prerequisites); }
    }''')
change(p,'ids(n,"recipes"), texts(n,"archive"));','ids(n,"recipes"), texts(n,"archive"), n.has("reveal_after")?ids(n,"reveal_after"):ids(n,"prerequisites"));')
change(p,'for(ResourceLocation recipe:node.recipes()) if(owners.putIfAbsent(recipe,node.id())!=null)', '''for(ResourceLocation trigger:node.revealAfter()) if(!node.prerequisites().contains(trigger))
                throw new JsonParseException("Reveal trigger is not a prerequisite: "+trigger+" for "+node.id());
            for(ResourceLocation recipe:node.recipes()) if(owners.putIfAbsent(recipe,node.id())!=null)''')
p=J/'research/research_service.java'
change(p,'import com.google.gson.*;','import com.google.gson.*;\nimport com.damezame97.white_lily.processing.processing_recipe;')
change(p,'node.prerequisites().stream().anyMatch(data::has)) visible.add','node.revealAfter().stream().anyMatch(data::has)) visible.add')
change(p,'else if(!(actual instanceof ShapelessRecipe))','else if(!(actual instanceof ShapelessRecipe) && !(actual instanceof processing_recipe processing && processing.researchNode()!=null && processing.researchNode().equals(research_catalog.recipeOwners().get(recipe))))')
change(p,'if(canCraft(server,id)) server.level().getRecipeManager().byKey(id).ifPresent(unlocked::add);','if(canCraft(server,id)) server.level().getRecipeManager().byKey(id).filter(recipe->recipe instanceof research_crafting_recipe).ifPresent(unlocked::add);')
change(p,'if(state.equals("ready"))','if(!complete && !node.condition().equals("obtain"))')
change(p,'if(!(recipe instanceof ShapelessRecipe)) throw new IllegalArgumentException', '''if(recipe instanceof processing_recipe processing)
        {
            JsonObject result=new JsonObject();result.addProperty("id",recipe.getId().toString());result.addProperty("type","processing");result.addProperty("device","simple_processing_table");
            result.addProperty("ticks",processing.duration());result.addProperty("toolDamage",processing.toolDamage());
            result.add("tool",stack(processing.tool().getItems()[0]));
            JsonArray ingredients=new JsonArray();
            for(var material:processing.materials())
            {JsonObject entry=stack(material.ingredient().getItems()[0]);entry.addProperty("count",material.count());ingredients.add(entry);}
            if(processing.container()!=null){var material=processing.container();JsonObject entry=stack(material.ingredient().getItems()[0]);entry.addProperty("count",material.count());ingredients.add(entry);}
            result.add("ingredients",ingredients);result.add("result",stack(processing.result()));
            if(!processing.returned().isEmpty())result.add("returned",stack(processing.returned()));
            if(!processing.byproduct().isEmpty())result.add("byproduct",stack(processing.byproduct()));return result;
        }
        if(!(recipe instanceof ShapelessRecipe)) throw new IllegalArgumentException''')

p=J/'processing/processing_recipe.java'
change(p,'private final int duration;','private final int duration;\n    @Nullable private ResourceLocation researchNode;\n    @Nullable public ResourceLocation researchNode() { return researchNode; }')
change(p,".append('|').append(duration);",".append('|').append(duration).append('|').append(researchNode);")
change(p,'return new processing_recipe(id, tool, damage, materials, container, result, returned, byproduct, duration);','''processing_recipe recipe=new processing_recipe(id, tool, damage, materials, container, result, returned, byproduct, duration);
            if(json.has("research")){recipe.researchNode=ResourceLocation.tryParse(GsonHelper.getAsString(json,"research"));if(recipe.researchNode==null)throw new JsonParseException("Invalid processing research ID");}
            return recipe;''')
change(p,'return new processing_recipe(id, tool, damage, materials, container, buffer.readItem(), buffer.readItem(),','processing_recipe recipe=new processing_recipe(id, tool, damage, materials, container, buffer.readItem(), buffer.readItem(),')
change(p,'buffer.readItem(), bounded(buffer.readVarInt(), 1, MAX_PROCESSING_TIME, "processing_time"));','buffer.readItem(), bounded(buffer.readVarInt(), 1, MAX_PROCESSING_TIME, "processing_time"));\n            recipe.researchNode=buffer.readBoolean()?buffer.readResourceLocation():null;return recipe;')
change(p,'buffer.writeVarInt(recipe.duration);','buffer.writeVarInt(recipe.duration);\n            buffer.writeBoolean(recipe.researchNode!=null);if(recipe.researchNode!=null)buffer.writeResourceLocation(recipe.researchNode);')

p=J/'processing/processing_block_entity.java'
change(p,'import java.util.Comparator;','''import java.util.Comparator;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import com.damezame97.white_lily.research.research_catalog;
import com.damezame97.white_lily.research.research_service;''')
change(p,'IDLE = 0, WORKING = 1, BLOCKED = 2;','IDLE = 0, WORKING = 1, BLOCKED = 2, UNREGISTERED = 3;\n    private final Set<ResourceLocation> registeredRecipes=new LinkedHashSet<>();')
change(p,'public ItemStackHandler getInventory() { return inventory; }','''public ItemStackHandler getInventory() { return inventory; }
    public Set<ResourceLocation> registeredRecipes() { return Set.copyOf(registeredRecipes); }
    public boolean canProcess(processing_recipe recipe)
    {
        return recipe.researchNode()==null || registeredRecipes.contains(recipe.getId())
                && recipe.researchNode().equals(research_catalog.recipeOwners().get(recipe.getId()));
    }
    /** Records only the actual player's current knowledge, never another machine's records. */
    public int registerLearned(Player player)
    {
        if(level==null || level.isClientSide || player.level()!=level || isRemoved())return 0;
        int added=0;
        for(processing_recipe recipe:level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()))
            if(recipe.researchNode()!=null && recipe.researchNode().equals(research_catalog.recipeOwners().get(recipe.getId()))
                    && research_service.canCraft(player,recipe.getId()) && registeredRecipes.add(recipe.getId()))added++;
        if(added>0)setChanged();return added;
    }''')
change(p,'.filter(recipe -> recipe.matchesInputs(inventory))','.filter(recipe -> recipe.matchesInputs(inventory) && canProcess(recipe))')
change(p,'if (recipeId != null || progress != 0 || status != IDLE) resetProcessing();\n            return;\n        }\n\n        // 配方对象', '''// Preserve a loaded old recipe/progress while waiting for registration; no input is consumed.
            boolean restricted=level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()).stream()
                    .anyMatch(recipe->recipe.matchesInputs(inventory) && !canProcess(recipe));
            if(restricted){if(status!=UNREGISTERED){status=UNREGISTERED;setChanged();}}
            else if(recipeId!=null || progress!=0 || status!=IDLE)resetProcessing();
            return;
        }

        // 配方对象''')
change(p,'if (consumed == null || !recipe.matchesInputs(inventory) || !outputsFit(recipe)) return;','if (consumed == null || !canProcess(recipe) || !recipe.matchesInputs(inventory) || !outputsFit(recipe)) return;')
change(p,'tag.put("Inventory", inventory.serializeNBT());','''tag.put("Inventory", inventory.serializeNBT());
        ListTag registered=new ListTag();registeredRecipes.stream().sorted().forEach(id->registered.add(StringTag.valueOf(id.toString())));tag.put("RegisteredRecipes",registered);''')
change(p,'super.load(tag);','''super.load(tag);
        registeredRecipes.clear();
        ListTag registered=tag.getList("RegisteredRecipes",8);
        for(int i=0;i<Math.min(registered.size(),16384);i++){ResourceLocation id=ResourceLocation.tryParse(registered.getString(i));if(id!=null)registeredRecipes.add(id);}''')

p=J/'processing/processing_menu.java'
change(p,'private final Level level;','private final Level level;\n    private long lastRegistration=Long.MIN_VALUE;')
change(p,'public int getProgress()', '''/** Vanilla menu-button packet carries only container ID + button ID; no remote position/list. */
    @Override public boolean clickMenuButton(Player player,int button)
    {
        if(button!=0 || level.isClientSide || table==null || player.containerMenu!=this || !stillValid(player))return false;
        long tick=level.getGameTime();if(lastRegistration!=Long.MIN_VALUE && tick>=lastRegistration && tick-lastRegistration<10)return false;
        lastRegistration=tick;int added=table.registerLearned(player);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.white_lily.processing."+(added==0?"registered_none":"registered_added"),added),false);
        broadcastChanges();return true;
    }

    public int getProgress()''')

p=J/'block/simple_processing_table.java'
change(p,'@Override public BlockEntity newBlockEntity', '''@Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack)
    {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof processing_block_entity table)table.registerLearned(player);
    }

    @Override public BlockEntity newBlockEntity''')
p=J/'item/processing_table_item.java'
change(p,'    @Override\n    public void appendHoverText', '''    /** A dropped/creative copied table never restores a previous machine's knowledge or inventory. */
    @Override protected boolean updateCustomBlockEntityTag(net.minecraft.core.BlockPos pos,Level level,@Nullable net.minecraft.world.entity.player.Player player,ItemStack stack,net.minecraft.world.level.block.state.BlockState state)
    { return false; }

    @Override
    public void appendHoverText''')
p=J/'client/processing_screen.java'
change(p,'    @Override\n    public void render(', '''    @Override protected void init()
    {
        super.init();
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable(KEY+"register"),button->{
            if(minecraft!=null && minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        }).bounds(leftPos+92,topPos+96,130,18).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(KEY+"register_hint"))).build());
    }

    @Override
    public void render(''')
change(p,'case 2 -> "blocked";','case 2 -> "blocked";\n            case 3 -> "unregistered";')
change(p,'graphics.drawString(font, Component.translatable(KEY + "status." + status), 8, 90,','graphics.drawWordWrap(font, Component.translatable(KEY + "status." + status), 8, 85, 214,')
change(p,'menu.getStatus() == 2 ? 0xAA0000 : TEXT, false);','menu.getStatus() >= 2 ? 0xAA0000 : TEXT);')
change(p,'8, 100, TEXT, false);','8, 104, TEXT, false);')

print('Expanded node data and server-side recipe registration. Existing pre-edit files backed up:',BACK)
