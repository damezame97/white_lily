package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.infection.*;
import com.damezame97.white_lily.item.*;
import com.damezame97.white_lily.procedures.san_procedures;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.gametest.*;
import java.util.Objects;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public class MedicalGameTests
{
    @GameTest(template = "empty")
    public static void mutuallyExclusiveBandageGroupsAndExactBoundaries(GameTestHelper h)
    {
        double[][] inputs = {{0,0},{0.149999,0.5},{0.15,0},{0.449999,0.5},{0.45,0},{0.999,0.5}};
        int[] expected = {35,30,20,15,5,0};
        for (int i=0;i<inputs.length;i++)
        {
            Rolls random = new Rolls(inputs[i]);
            yes(h, medical_actions.bandageBase(random)==expected[i], "两组互斥和独立边界");
            yes(h, random.calls==2, "基础污染只能抽两次");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bandageContaminationIsOneScaledEvent(GameTestHelper h)
    {
        Player p=player(h);
        p.setHealth(6);
        attr(p,attributes.STAMINA.get()).setBaseValue(-2);
        attr(p,attributes.INFECTION_RESISTANCE.get()).setBaseValue(0.2);
        infection_service.set(p,80);
        Rolls random=new Rolls(0,0,0.99);
        yes(h,medical_actions.apply(p,medical_actions.Kind.SIMPLE,random),"完成绷带可使用");
        near(h,141.6,infection_service.query(p),"合并35后一次套用1.76得到61.6");
        yes(h,random.calls==3 && state(p).poolCooldown==600,"两次基础抽取+一次症状判定");
        p.removeEffect(mob_effects.BANDAGED.get());
        state(p).poolCooldown=0;
        Rolls zero=new Rolls(0.5,0.5);
        medical_actions.apply(p,medical_actions.Kind.SIMPLE,zero);
        yes(h,zero.calls==2 && state(p).poolCooldown==0,"基础量0不调用增加事件");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bandageUseInterruptionAndNoIntervalBonus(GameTestHelper h)
    {
        Player p=player(h);
        ItemStack simple=new ItemStack(items.SIMPLE_BANDAGE.get(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,simple);
        items.SIMPLE_BANDAGE.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        simple.releaseUsing(p.level(),p,30);
        p.stopUsingItem();
        yes(h,simple.getCount()==3 && !p.hasEffect(mob_effects.BANDAGED.get()),"中断不消耗、不包扎");
        near(h,0,infection_service.query(p),"中断不判污染");
        yes(h,simple.getUseDuration()==60 && items.CLEAN_FINE_BANDAGE.get().getDefaultInstance().getUseDuration()==60,"3秒使用");
        // 包扎不再给恢复间隔加成：效果在也在、不在也不该动属性。
        medical_actions.apply(p,medical_actions.Kind.SIMPLE,new Rolls(0.9,0.9));
        near(h,0,p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),"包扎不再加恢复间隔");
        yes(h,p.getEffect(mob_effects.BANDAGED.get()).getDuration()==6000,"Ⅰ仍是5分钟标记");
        ItemStack fine=new ItemStack(items.CLEAN_FINE_BANDAGE.get(),2);
        fine.getItem().finishUsingItem(fine,p.level(),p);
        yes(h,fine.getCount()==1 && p.getEffect(mob_effects.BANDAGED.get()).getAmplifier()==1,"高级替换并消耗1");
        near(h,0,p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),"Ⅱ也不再加间隔");
        h.succeed();
    }

    /** 包扎与渗血的等级差换算：同级 1000，每低一级除 2，每高一级乘 2。 */
    @GameTest(template = "empty")
    public static void bandagedDeductionScalesWithLevelGap(GameTestHelper h)
    {
        near(h,1000,infection_symptom_rules.bandagedDeduction(0,0),"同级1000");
        near(h,500,infection_symptom_rules.bandagedDeduction(0,1),"低一级除2");
        near(h,250,infection_symptom_rules.bandagedDeduction(0,2),"低两级除2再除2");
        near(h,2000,infection_symptom_rules.bandagedDeduction(1,0),"高一级乘2");
        near(h,4000,infection_symptom_rules.bandagedDeduction(2,0),"高两级乘2再乘2");
        near(h,4000,infection_symptom_rules.bandagedDeduction(3,1),"包扎高两级时乘4");
        h.succeed();
    }

    /** 包扎对渗血生效：先按等级扣时长，再移除自己，并给 1 级 180 秒 delayed_healing。 */
    @GameTest(template = "empty")
    public static void bandagedCutsSeepageThenGrantsDelayedHealing(GameTestHelper h)
    {
        Player p=player(h);
        infection_service.set(p,50);
        infection_symptom_rules.start(p,"wound_seepage",4000,2);
        infection_symptom_rules.reevaluate(p);
        MobEffectInstance bandage=new MobEffectInstance(mob_effects.BANDAGED.get(),6000,0,false,false,true);
        p.addEffect(bandage);
        int deduction=infection_symptom_rules.resolveBandaged(p,0);
        near(h,250,deduction,"0级包扎对2级渗血只扣250");
        yes(h,state(p).remaining("wound_seepage")==3750,"渗血时长4000-250");
        yes(h,!p.hasEffect(mob_effects.BANDAGED.get()),"包扎立即移除自身");
        MobEffectInstance heal=p.getEffect(mob_effects.DELAYED_HEALING.get());
        yes(h,heal!=null && heal.getAmplifier()==1 && heal.getDuration()==3600,"获得1级180秒延时治愈");
        // 下一次重评不能把这条 granted 效果冲掉。
        infection_symptom_rules.reevaluate(p);
        heal=p.getEffect(mob_effects.DELAYED_HEALING.get());
        yes(h,heal!=null && heal.getAmplifier()==1,"重评后仍保留1级");
        h.succeed();
    }

    /** 时长不够就直接结束渗血；没有渗血时只移除自己、不给延时治愈。 */
    @GameTest(template = "empty")
    public static void bandagedRemovesShortSeepageAndSkipsHealWithoutWound(GameTestHelper h)
    {
        Player p=player(h);
        infection_service.set(p,50);
        infection_symptom_rules.start(p,"wound_seepage",500,0);
        infection_symptom_rules.reevaluate(p);
        p.addEffect(new MobEffectInstance(mob_effects.BANDAGED.get(),6000,0,false,false,true));
        int deduction=infection_symptom_rules.resolveBandaged(p,0);
        near(h,1000,deduction,"同级扣1000");
        yes(h,!has(p,"wound_seepage"),"剩余不足扣除量时直接移除渗血");
        yes(h,!p.hasEffect(mob_effects.BANDAGED.get()),"包扎仍会移除自身");
        yes(h,p.getEffect(mob_effects.DELAYED_HEALING.get())!=null,"对渗血生效过就给延时治愈");
        // 没有渗血：只移除自己。
        infection_service.set(p,10);
        p.removeEffect(mob_effects.DELAYED_HEALING.get());
        infection_symptom_rules.reevaluate(p);
        yes(h,!has(p,"delayed_healing"),"前置：已清掉迟滞治愈");
        p.addEffect(new MobEffectInstance(mob_effects.BANDAGED.get(),6000,1,false,false,true));
        near(h,0,infection_symptom_rules.resolveBandaged(p,1),"没有渗血不产生扣除");
        yes(h,!p.hasEffect(mob_effects.BANDAGED.get()),"没有渗血也立即移除自身");
        yes(h,p.getEffect(mob_effects.DELAYED_HEALING.get())==null,"没有渗血不给延时治愈");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void herbsTreatEvenAtZeroAndKeepCurableSideEffects(GameTestHelper h)
    {
        Player p=player(h);
        infection_service.set(p,10);
        medical_actions.apply(p,medical_actions.Kind.BITTERLEAF,p.getRandom());
        near(h,7,infection_service.query(p),"苦叶降低3");
        yes(h,p.getEffect(MobEffects.HUNGER).getDuration()==900,"苦叶饥饿45秒");
        p.removeEffect(MobEffects.HUNGER);
        medical_actions.apply(p,medical_actions.Kind.PASTE,p.getRandom());
        near(h,0,infection_service.query(p),"药膏实际降低余下7");
        yes(h,p.getEffect(MobEffects.HUNGER).getDuration()==200,"药膏饥饿10秒");
        yes(h,!p.hasEffect(mob_effects.RECOVERY_FATIGUE.get()),"小额主动治疗不疲劳");
        ItemStack herb=new ItemStack(items.BITTERLEAF.get(),2);
        herb.getItem().finishUsingItem(herb,p.level(),p);
        yes(h,herb.getCount()==1 && p.hasEffect(MobEffects.HUNGER),"零污染仍消耗并有副作用");
        medical_actions.apply(p,medical_actions.Kind.YARROW,p.getRandom());
        yes(h,p.getEffect(MobEffects.WEAKNESS).getDuration()==600,"蓍草虚弱30秒");
        medical_actions.apply(p,medical_actions.Kind.FLOWER,p.getRandom());
        medical_actions.apply(p,medical_actions.Kind.FINE,p.getRandom());
        p.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        yes(h,!p.hasEffect(MobEffects.HUNGER)&&!p.hasEffect(MobEffects.WEAKNESS)&&!p.hasEffect(MobEffects.CONFUSION),"普通副作用可喝奶清除");
        yes(h,p.hasEffect(mob_effects.BANDAGED.get())&&p.hasEffect(mob_effects.WOUND_SUPPRESSION.get()),"包扎和抑制免疫牛奶");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void suppressionPreservesWoundAndOtherPenalties(GameTestHelper h)
    {
        Player p=player(h);
        infection_service.set(p,50);
        infection_symptom_rules.onDirectHit(p,3);
        near(h,-30,p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),"0级渗血的间隔惩罚");
        near(h,1,p.getAttributeValue(attributes.RESTORE_HEALTH.get()),"0级渗血不扣回复量");
        medical_actions.apply(p,medical_actions.Kind.YARROW,p.getRandom());
        yes(h,state(p).remaining("wound_seepage")==1200 && p.hasEffect(mob_effects.WOUND_SEEPAGE.get()),"渗血本体仍在");
        near(h,0,p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),"抑制后不再有渗血间隔惩罚");
        near(h,1,p.getAttributeValue(attributes.RESTORE_HEALTH.get()),"只抑制渗血回复量");
        infection_symptom_rules.tick(p,0);
        yes(h,state(p).remaining("wound_seepage")==1199,"渗血持续时间继续流逝");
        infection_symptom_rules.onDirectHit(p,3);
        yes(h,state(p).remaining("wound_seepage")==1599,"抑制期间受击仍可延长渗血");
        medical_actions.apply(p,medical_actions.Kind.DRESSING,p.getRandom());
        medical_actions.apply(p,medical_actions.Kind.YARROW,p.getRandom());
        yes(h,p.getEffect(mob_effects.WOUND_SUPPRESSION.get()).getDuration()==1200,"保留更长抑制");
        p.removeEffect(mob_effects.WOUND_SUPPRESSION.get());
        infection_symptoms.reevaluate(p);
        near(h,-30,p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),"抑制结束恢复仍在的渗血惩罚");
        infection_service.set(p,29.9);
        medical_actions.apply(p,medical_actions.Kind.DRESSING,p.getRandom());
        p.removeEffect(mob_effects.WOUND_SUPPRESSION.get());
        infection_symptoms.reevaluate(p);
        near(h,0,p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),"渗血提前结束后不凭空恢复惩罚");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bandagingKeepsRegenerationProgressAndFiveSecondFloor(GameTestHelper h)
    {
        Player p=player(h);
        var rule=com.damezame97.white_lily.gamerule.happy_mode.RULE;
        boolean old=p.level().getGameRules().getBoolean(rule);
        try
        {
            p.level().getGameRules().getRule(rule).set(true,null);
            attr(p,attributes.RECOVERY_INTERVAL.get()).setBaseValue(100);
            var food=p.getFoodData();food.setFoodLevel(20);food.setSaturation(10);
            for(int i=0;i<200;i++)food.tick(p);
            // 包扎不再改恢复间隔，所以间隔保持 180-100=80 秒（1600 刻）。
            medical_actions.apply(p,medical_actions.Kind.SIMPLE,new Rolls(0.9,0.9));
            for(int i=0;i<1399;i++)food.tick(p);
            near(h,10,p.getHealth(),"包扎后累计1599刻不提前恢复");
            food.tick(p);
            near(h,11,p.getHealth(),"累计1600刻恢复，未丢失包扎前200刻");
            attr(p,attributes.RECOVERY_INTERVAL.get()).setBaseValue(200);
            near(h,100,com.damezame97.white_lily.gamerule.happy_mode_food.intervalTicks(
                    p.getAttributeValue(attributes.RECOVERY_INTERVAL.get()),false),"仍遵守5秒下限");
        }
        finally{p.level().getGameRules().getRule(rule).set(old,null);}
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sanHerbsReadThresholdBeforeCompletionAndTeaReturnsBottle(GameTestHelper h)
    {
        Player p=player(h);
        int[] before={4995,4999,5000};
        int[] flowers={5000,5004,5010};
        int[] tea={5010,5014,5030};
        for(int i=0;i<before.length;i++)
        {
            attr(p,attributes.SAN.get()).setBaseValue(before[i]);
            medical_actions.apply(p,medical_actions.Kind.FLOWER,p.getRandom());
            near(h,flowers[i],san_procedures.currentSan(p),"花按使用前阈值");
            attr(p,attributes.SAN.get()).setBaseValue(before[i]);
            medical_actions.apply(p,medical_actions.Kind.TEA,p.getRandom());
            near(h,tea[i],san_procedures.currentSan(p),"茶按使用前阈值");
        }
        var san=attr(p,attributes.SAN.get());
        san.setBaseValue(2499.5);
        var modifier=new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.randomUUID(),"test",1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
        san.addTransientModifier(modifier);
        medical_actions.apply(p,medical_actions.Kind.FLOWER,p.getRandom());
        near(h,5004,san_procedures.currentSan(p),"有效SAN4999按低档且只实际恢复5");
        san.removeModifier(modifier);
        double maximum=((net.minecraft.world.entity.ai.attributes.RangedAttribute)attributes.SAN.get()).getMaxValue();
        san.setBaseValue(maximum-1);
        medical_actions.apply(p,medical_actions.Kind.TEA,p.getRandom());
        near(h,maximum,san_procedures.currentSan(p),"SAN恢复遵守属性上限");
        ItemStack teaStack=new ItemStack(items.CALMING_FLOWER_TEA.get());
        yes(h,teaStack.getMaxStackSize()==16,"花茶实际最大堆叠为16");
        Player batchPlayer=player(h);
        batchPlayer.getInventory().clearContent();
        ItemStack batch=new ItemStack(items.CALMING_FLOWER_TEA.get(),16);
        ItemStack batchResult=batch.finishUsingItem(batchPlayer.level(),batchPlayer);
        yes(h,batchResult==batch&&batch.is(items.CALMING_FLOWER_TEA.get())&&batch.getCount()==15
                        &&batchPlayer.getInventory().countItem(Items.GLASS_BOTTLE)==1,
                "喝一组中的一瓶只减少1瓶并返还1个玻璃瓶，剩余15瓶不被覆盖");
        Player fullBatchPlayer=player(h);
        for(int slot=0;slot<36;slot++)fullBatchPlayer.getInventory().setItem(slot,new ItemStack(Items.DIRT,64));
        int bottlesBefore=fullBatchPlayer.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                fullBatchPlayer.getBoundingBox().inflate(4),entity->entity.getItem().is(Items.GLASS_BOTTLE)).size();
        ItemStack fullBatch=new ItemStack(items.CALMING_FLOWER_TEA.get(),16);
        ItemStack fullBatchResult=fullBatch.finishUsingItem(fullBatchPlayer.level(),fullBatchPlayer);
        int bottlesAfter=fullBatchPlayer.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                fullBatchPlayer.getBoundingBox().inflate(4),entity->entity.getItem().is(Items.GLASS_BOTTLE)).size();
        yes(h,fullBatchResult==fullBatch&&fullBatch.getCount()==15
                        &&fullBatchPlayer.getInventory().countItem(Items.GLASS_BOTTLE)==0&&bottlesAfter==bottlesBefore+1,
                "背包满时仍只减少1瓶、剩余茶不被覆盖且空瓶安全掉落");
        for(int slot=0;slot<36;slot++)p.getInventory().setItem(slot,new ItemStack(Items.DIRT,64));
        ItemStack drink=new ItemStack(items.CALMING_FLOWER_TEA.get());
        yes(h,drink.getUseDuration()==32 && drink.getUseAnimation()==UseAnim.DRINK,"普通饮用32刻");
        ItemStack bottle=drink.finishUsingItem(p.level(),p);
        yes(h,bottle.is(Items.GLASS_BOTTLE)&&bottle.getCount()==1,"背包满时最后一杯的瓶子留手中");
        p.getAbilities().instabuild=true;
        ItemStack creative=new ItemStack(items.CALMING_FLOWER_TEA.get(),16);
        yes(h,creative.finishUsingItem(p.level(),p)==creative&&creative.getCount()==16,"创造饮用整组不消耗或额外产瓶");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesStrictWaterAndHoneyBatchRemainders(GameTestHelper h)
    {
        Menu honey=new Menu(player(h));
        com.damezame97.white_lily.research.research_capability.get(honey.player).orElseThrow().complete(infection_symptom_rules.id("clean_fine_bandage"));
        honey.getSlot(1).set(new ItemStack(items.SIMPLE_BANDAGE.get(),4));
        honey.getSlot(2).set(new ItemStack(items.YARROW_DRESSING.get(),4));
        honey.getSlot(3).set(new ItemStack(Items.HONEY_BOTTLE,4));
        honey.clicked(0,0,ClickType.QUICK_MOVE,honey.player);
        yes(h,honey.player.getInventory().countItem(items.CLEAN_FINE_BANDAGE.get())==4,"Shift批量产出4精良绷带");
        yes(h,count(honey,Items.GLASS_BOTTLE)==4,"每瓶蜂蜜返一个瓶子");
        Menu wax=new Menu(player(h));
        com.damezame97.white_lily.research.research_capability.get(wax.player).orElseThrow().complete(infection_symptom_rules.id("clean_fine_bandage"));
        wax.getSlot(1).set(new ItemStack(items.SIMPLE_BANDAGE.get()));
        wax.getSlot(2).set(new ItemStack(items.YARROW_DRESSING.get()));
        wax.getSlot(3).set(new ItemStack(items.BEESWAX_BLOCK.get()));
        wax.clicked(0,0,ClickType.PICKUP,wax.player);
        yes(h,wax.getCarried().is(items.CLEAN_FINE_BANDAGE.get())&&count(wax,Items.GLASS_BOTTLE)==0,"蜂蜡路线不返瓶");
        Menu tea=new Menu(player(h));
        // 本测试验证已有茶配方的水瓶和返瓶规则；研究权限由专门的真实菜单回归覆盖。
        com.damezame97.white_lily.research.research_capability.get(tea.player).orElseThrow()
                .complete(infection_symptom_rules.id("calming_flower_tea"));
        tea.getSlot(1).set(new ItemStack(items.CALMING_FLOWER.get(),2));
        tea.getSlot(2).set(new ItemStack(items.CALMING_FLOWER.get(),2));
        ItemStack[] invalid={PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.AWKWARD),
                PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.HEALING),
                PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.WATER),
                PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION),Potions.WATER)};
        var recipe=h.getLevel().getRecipeManager().byKey(infection_symptom_rules.id("calming_flower_tea")).orElseThrow();
        for(var input:invalid){tea.getSlot(3).set(input);yes(h,!((CraftingRecipe)recipe).matches(tea.grid(),h.getLevel()),"拒绝其他药水");}
        tea.getSlot(3).set(PotionUtils.setPotion(new ItemStack(Items.POTION,2),Potions.WATER));
        tea.clicked(0,0,ClickType.QUICK_MOVE,tea.player);
        yes(h,tea.player.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==2&&count(tea,Items.GLASS_BOTTLE)==0,"批量茶合成不返空瓶");
        for(String id:new String[]{"simple_bandage","clean_fine_bandage_honey","clean_fine_bandage_beeswax","calming_flower_tea",
                "processing/hemp_fiber","processing/bitterleaf_paste","processing/yarrow_dressing"})
            yes(h,h.getLevel().getRecipeManager().byKey(infection_symptom_rules.id(id)).isPresent(),"已加载配方："+id);
        for(String id:new String[]{"bitterleaf_paste","yarrow_dressing"})
            yes(h,h.getLevel().getRecipeManager().byKey(infection_symptom_rules.id(id)).isEmpty(),"药膏和敷料已移出普通合成："+id);
        h.succeed();
    }

    private static int count(Menu menu,Item item)
    {
        int count=menu.player.getInventory().countItem(item);
        for(int slot=1;slot<=9;slot++){ItemStack stack=menu.getSlot(slot).getItem();if(stack.is(item))count+=stack.getCount();}
        if(menu.getCarried().is(item))count+=menu.getCarried().getCount();
        return count;
    }
    static Player player(GameTestHelper h)
    {
        Player p=h.makeMockSurvivalPlayer();p.getAbilities().invulnerable=false;p.setHealth(10);
        attr(p,attributes.SAN.get()).setBaseValue(10000);return p;
    }
    static net.minecraft.world.entity.ai.attributes.AttributeInstance attr(Player p,net.minecraft.world.entity.ai.attributes.Attribute a)
    {return Objects.requireNonNull(p.getAttribute(a));}
    static infection_symptom_state state(Player p){return infection_symptom_rules.state(p);}
    static boolean has(Player p,String name){return infection_symptom_rules.has(p,name);}
    static void yes(GameTestHelper h,boolean v,String message){h.assertTrue(v,message);}
    static void near(GameTestHelper h,double expected,double actual,String message){yes(h,Math.abs(expected-actual)<0.00001,message+": "+expected+" != "+actual);}
    static final class Rolls extends LegacyRandomSource
    {
        final double[] values;int calls;
        Rolls(double...values){super(0);this.values=values;}
        @Override public double nextDouble(){return values[calls++];}
    }
    private static class Menu extends CraftingMenu
    {
        final Player player;
        Menu(Player p){super(1,p.getInventory());player=p;}
        CraftingContainer grid(){return (CraftingContainer)getSlot(1).container;}
        @Override public void slotsChanged(Container container)
        {
            if(player!=null)getSlot(0).set(player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid(),player.level())
                    .map(recipe->recipe.assemble(grid(),player.level().registryAccess())).orElse(ItemStack.EMPTY));
        }
    }
}
