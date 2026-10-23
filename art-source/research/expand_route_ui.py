"""Extend the existing dynamic detail renderer; keep the approved UI art intact."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2];J=ROOT/'src/main/java/com/damezame97/white_lily';R=ROOT/'src/main/resources'
def write(p,s):p.write_text(s,encoding='utf-8')
def change(p,a,b):
    s=p.read_text('utf-8');assert a in s,(p,a[:90]);write(p,s.replace(a,b))
p=J/'research/research_service.java'
if 'p.addProperty("jump",jump.toString())' in p.read_text('utf-8'):
    raise SystemExit('One-time UI migration already applied; no files modified.')
change(p,'parents.add(p);','''// A hidden prerequisite can only link to an already visible clue, never disclose its archive.
                    ResourceLocation jump=visible.contains(parent)?parent:research_catalog.nodes().get(parent).prerequisites().stream().filter(visible::contains).findFirst().orElse(null);
                    if(jump!=null)p.addProperty("jump",jump.toString());
                    parents.add(p);''')
p=J/'client/research_client.java'
change(p,'public record Requirement(String id, String name, boolean complete) { }','public record Requirement(String id, String name, boolean complete, String jump) { }')
change(p,'int ticks, ItemStack returned, ItemStack byproduct) { }','int ticks, ItemStack returned, ItemStack byproduct, ItemStack tool, int toolDamage) { }')
change(p,'@Nullable Recipe recipe,','@Nullable Recipe recipe, List<Recipe> recipes,')
change(p,'bool(p, "complete", false)));','bool(p, "complete", false),string(p,"jump","")));')
a='''            Recipe recipe = null;
            if (n.has("recipe") && n.get("recipe").isJsonObject())'''
start=p.read_text('utf-8').index(a);s=p.read_text('utf-8');end=s.index('            nodes.add(new Node',start)
s=s[:start]+'''            List<Recipe> recipes=new ArrayList<>();
            for(JsonElement entryRecipe:array(n,"recipes"))recipes.add(decodeRecipe(entryRecipe.getAsJsonObject()));
            if(recipes.isEmpty() && n.has("recipe"))recipes.add(decodeRecipe(n.getAsJsonObject("recipe")));
            Recipe recipe=recipes.isEmpty()?null:recipes.get(0);
'''+s[end:];write(p,s)
change(p,'List.copyOf(archive), recipe,','List.copyOf(archive), recipe, List.copyOf(recipes),')
change(p,'    private static List<String> strings(','''    private static Recipe decodeRecipe(JsonObject r)
    {
        return new Recipe(string(r,"type",""),string(r,"device","crafting_table"),materials(r,"ingredients"),stack(r.getAsJsonObject("result")),
            (int)number(r,"ticks",0),stack(r.get("returned")),stack(r.get("byproduct")),stack(r.get("tool")),(int)number(r,"toolDamage",0));
    }
    private static List<String> strings(''')
p=J/'client/InvestigationScreen.java'
change(p,'boolean showSubmit=!n.complete()', '''List<Node> jumpTargets=new ArrayList<>();
        for(Requirement requirement:n.prerequisites())if(!requirement.complete())
            data.nodes().stream().filter(target->target.id().equals(requirement.jump())&&!target.chapter().equals(chapter)).findFirst().ifPresent(jumpTargets::add);
        boolean hasJump=!jumpTargets.isEmpty();
        boolean showSubmit=!n.complete()''')
change(p,'-(showSubmit?31:8)))','-(showSubmit?31:8)-(hasJump?23:0)))')
change(p,'if(n.state().equals("discover")){', '''if(hasJump)button(detail.x+7,detail.y+detail.h-(showSubmit?49:25),detail.w-14,18,tr("jump_prerequisite"),()->{
            List<Entry> entries=new ArrayList<>();for(Node target:jumpTargets)entries.add(new Entry(Component.translatable(target.name()),true,()->jumpTo(target)));
            openPopup(detail.x,detail.y+detail.h-100,List.of(),entries);
        });
        if(n.state().equals("discover")){''')
change(p,'if(n.recipe()!=null)\n                {','for(Recipe recipe:n.recipes())\n                {')
s=p.read_text('utf-8').replace('n.recipe().','recipe.');write(p,s)
change(p,'Map<String,Material> combined=new LinkedHashMap<>();', '''if(!recipe.tool().isEmpty()){heading("tool");material(new Material(recipe.tool(),1,0),false);text(tr("tool_damage",recipe.toolDamage()),MUTED);text(tr("registration_hint"),GOLD);}
                    Map<String,Material> combined=new LinkedHashMap<>();''')
change(p,'    private void chapter(Category c,Chapter ch)', '''    private void jumpTo(Node target)
    {
        for(Category c:data.categories())if(c.open()&&c.id().equals(target.category()))for(Chapter ch:c.chapters())if(ch.id().equals(target.chapter()))
        {chapter(c,ch);select(target);view.centerX=target.x();view.centerY=target.y();return;}
    }
    private void chapter(Category c,Chapter ch)''')

zh={
'research.white_lily.chapter.bandages':'包扎与敷料',
'screen.white_lily.research.jump_prerequisite':'查看跨章节前置',
'screen.white_lily.research.tool':'加工工具',
'screen.white_lily.research.tool_damage':'每批固定消耗 %s 点耐久。',
'screen.white_lily.research.registration_hint':'在简易加工台中点击“登记已学配方”，即可向该机器登记此加工方法。',
'screen.white_lily.research.device.simple_processing_table':'简易加工台',
'screen.white_lily.processing.register':'登记已学配方',
'screen.white_lily.processing.register_hint':'将你已掌握的加工配方登记到此工作台，供所有使用者加工。',
'screen.white_lily.processing.registered_added':'已新增登记 %s 项配方。',
'screen.white_lily.processing.registered_none':'没有可新增登记的配方。',
'screen.white_lily.processing.status.unregistered':'此加工台尚未登记该配方',
}
en={
'research.white_lily.chapter.bandages':'Bandages and Dressings',
'screen.white_lily.research.jump_prerequisite':'View prerequisite chapter',
'screen.white_lily.research.tool':'Processing tool',
'screen.white_lily.research.tool_damage':'Consumes %s durability per batch.',
'screen.white_lily.research.registration_hint':'Click Register Known Recipes in a Simple Processing Table to teach that machine this method.',
'screen.white_lily.research.device.simple_processing_table':'Simple Processing Table',
'screen.white_lily.processing.register':'Register Known Recipes',
'screen.white_lily.processing.register_hint':'Register your known processing recipes at this table for everyone to use.',
'screen.white_lily.processing.registered_added':'Registered %s new recipes.',
'screen.white_lily.processing.registered_none':'No new recipes to register.',
'screen.white_lily.processing.status.unregistered':'Recipe not registered at this table',
}
texts={
'bitterleaf':('发现苦叶草','Discover Bitterleaf','记录苦叶草的性质。','Record the properties of bitterleaf.','获得一株苦叶草，记录它的性质。','Obtain bitterleaf to record its properties.','使用完成后主动降低3.0污染；饥饿Ⅰ持续45秒。可种植，对植株使用骨粉额外产出1份。','Actively removes 3.0 infection; Hunger I for 45 seconds. Can be planted and duplicated using bone meal.'),
'bitterleaf_paste':('研究苦叶药膏','Research Bitterleaf Paste','研磨苦叶草，记录药膏的加工方法。','Grind bitterleaf and record the paste recipe.','先记录苦叶草的性质。','First discover bitterleaf.','使用完成后主动降低8.0污染；饥饿Ⅰ持续10秒。污染为零时仍消耗并产生副作用。','Actively removes 8.0 infection; Hunger I for 10 seconds. Consumed with side effects even at zero infection.'),
'yarrow':('发现止血蓍草','Discover Yarrow','记录止血蓍草的性质。','Record the properties of yarrow.','获得一株止血蓍草，记录它的性质。','Obtain yarrow to record its properties.','抑制伤口渗血惩罚20秒；虚弱Ⅰ持续30秒。不删除渗血，可种植并用骨粉繁殖。','Suppresses wound seepage penalties for 20 seconds; Weakness I for 30 seconds. Does not remove seepage. Can be planted and duplicated using bone meal.'),
'yarrow_dressing':('研究蓍草敷料','Research Yarrow Dressing','研磨蓍草，记录敷料的加工方法。','Grind yarrow and record the dressing recipe.','先记录止血蓍草的性质。','First discover yarrow.','抑制伤口渗血惩罚60秒；虚弱Ⅰ持续10秒。较长抑制时间保留，渗血本体继续计时。','Suppresses wound seepage penalties for 60 seconds; Weakness I for 10 seconds. Keeps the longer suppression; seepage duration still runs.'),
'coarse_cloth':('发现粗布片','Discover Coarse Cloth','记录粗布片的包扎用途。','Record the use of coarse cloth in bandaging.','获得一片粗布，记录它的包扎用途。','Obtain coarse cloth to record its bandaging use.','基础包扎材料；麻秆或植物纤维可以织成粗布片。','A basic bandaging material, crafted from hemp stalks or plant fiber.'),
'simple_bandage':('研究简易绷带','Research Simple Bandages','用粗布与细布条制作基础绷带。','Make basic bandages from cloth and thin cloth strips.','先发现粗布片。','First discover coarse cloth.','按住右键3秒，包扎Ⅰ持续5分钟，回血间隔缩短30秒；不直接回血。污染基量可能为0～35，按现有感染公式计算。牛奶不能解除包扎。','Use for 3 seconds: Bandaged I for 5 minutes, reducing recovery interval by 30 seconds without direct healing. Base infection 0–35 is scaled by the existing infection formula. Milk cannot remove bandaging.'),
'clean_fine_bandage':('研究洁净的精良绷带','Research Clean Fine Bandages','结合基础包扎与蓍草敷料，记录两种洁净绷带配方。','Combine bandaging and yarrow dressings to record both clean bandage recipes.','需要简易绷带和蓍草敷料研究。','Requires Simple Bandages and Yarrow Dressing research.','按住右键3秒，包扎Ⅱ持续10分钟，回血间隔缩短60秒；不直接回血、不增减污染。高级替换低级，同级刷新，牛奶不能解除包扎。','Use for 3 seconds: Bandaged II for 10 minutes, reducing recovery interval by 60 seconds without direct healing or changing infection. Higher tiers replace lower tiers; equal tiers refresh. Milk cannot remove bandaging.'),
}
for name,values in texts.items():
    for i,key in enumerate(['name','intro','hint','archive.1']):zh[f'research.white_lily.node.{name}.{key}']=values[i*2];en[f'research.white_lily.node.{name}.{key}']=values[i*2+1]
for locale,extra in [('zh_cn',zh),('en_us',en)]:
    p=R/f'assets/white_lily/lang/{locale}.json';s=p.read_text('utf-8');d=json.loads(s)
    assert not set(extra)&set(d),'Migration already applied'
    # Preserve all pre-existing language text and formatting.
    at=s.rfind('}');addition=json.dumps(extra,ensure_ascii=False,indent=2)[2:-2]
    write(p,s[:at].rstrip()+',\n'+addition+'\n}\n')
print('Extended detail data, visible prerequisite navigation and translations.')
