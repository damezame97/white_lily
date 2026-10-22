package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.infection.*;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.mixin.infection.InfectionMixinPlugin;
import com.damezame97.white_lily.procedures.san_procedures;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.damezame97.white_lily.white_lilycurio.stored_suffering;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.spells.fire.FireballSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.damezame97.white_lily.gametest.MedicalGameTests.*;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class ReviewRegressionGameTests
{
    @GameTest(template="empty")
    public static void infiniteTimedEffectsSurviveTicksSaveLoadPauseAndRemoval(GameTestHelper h)
    {
        var p=player(h);infection_service.set(p,100);
        p.addEffect(new MobEffectInstance(mob_effects.SHIVERING.get(),-1,0,false,false));
        var state=infection_symptom_rules.state(p);
        yes(h,state!=null&&state.remaining("shivering")==-1,"命令添加无限效果保存-1");
        p.addEffect(new MobEffectInstance(mob_effects.SHIVERING.get(),5,0,false,false));
        yes(h,state.remaining("shivering")==-1,"短时重复添加不能截断原版已保留的无限效果");
        for(int i=0;i<40;i++)infection_symptom_rules.tick(p,i);
        yes(h,p.hasEffect(mob_effects.SHIVERING.get())&&state.remaining("shivering")==-1,"无限时长不被减为0");
        var loaded=new infection_symptom_state();loaded.load(state.save());
        yes(h,loaded.remaining("shivering")==-1,"保存加载保留无限时长");
        p.getAbilities().invulnerable=true;infection_symptom_rules.tick(p,40);
        yes(h,!p.hasEffect(mob_effects.SHIVERING.get())&&state.remaining("shivering")==-1,"暂停只移除显示与惩罚，不丢失无限计时");
        p.getAbilities().invulnerable=false;infection_symptom_rules.tick(p,41);
        yes(h,p.getEffect(mob_effects.SHIVERING.get()).isInfiniteDuration(),"恢复仍是无限效果");
        p.removeEffect(mob_effects.SHIVERING.get());infection_symptom_rules.tick(p,42);
        yes(h,!p.hasEffect(mob_effects.SHIVERING.get())&&!state.running("shivering"),"管理移除不自动补回事件效果");
        p.addEffect(new MobEffectInstance(mob_effects.SHIVERING.get(),500,0,false,false));
        p.addEffect(new MobEffectInstance(mob_effects.SHIVERING.get(),5,0,false,false));
        yes(h,state.remaining("shivering")==500,"短时重加不能缩短已有500刻计时");
        p.removeEffect(mob_effects.SHIVERING.get());
        p.addEffect(new MobEffectInstance(mob_effects.SHIVERING.get(),1,0,false,false));
        infection_symptom_rules.tick(p,43);
        yes(h,!p.hasEffect(mob_effects.SHIVERING.get()),"有限1刻仍正常到期");
        CompoundTag invalid=new CompoundTag();CompoundTag durations=new CompoundTag();durations.putInt("shivering",-2);invalid.put("remaining",durations);
        loaded.load(invalid);yes(h,loaded.remaining("shivering")==0,"异常负数不能冒充无限");
        attr(p,attributes.SAN.get()).setBaseValue(999);infection_service.set(p,150);
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobEffectEvent.Applicable> deny=event->{
            if(event.getEntity()==p&&event.getEffectInstance().getEffect()==mob_effects.INFECTION_BLINDNESS.get())
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
        try
        {
            yes(h,!infection_symptom_rules.blind(p)&&!state.running("infection_blindness"),"外部明确拒绝的失明不记录假发作");
            for(int i=44;i<110;i++)infection_symptom_rules.tick(p,i);
            yes(h,!p.hasEffect(mob_effects.BRIEF_LUCIDITY.get()),"未成功施加失明不能凭空获得清明");
        }
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
        h.succeed();
    }

    @GameTest(template="empty")
    public static void infectionPulseIsTenTicksButStateChangesAndExpiryAreImmediate(GameTestHelper h)
    {
        var p=player(h);infection_service.set(p,200);
        var state=Objects.requireNonNull(infection_symptom_rules.state(p));
        infection_symptom_rules.tick(p,0);
        for(int i=1;i<10;i++)infection_symptom_rules.tick(p,i);
        yes(h,state.lastEvaluation==0,"稳定9刻不完整重评");
        infection_symptom_rules.tick(p,10);yes(h,state.lastEvaluation==10,"第10刻校验");
        p.setHealth(20);infection_symptom_rules.tick(p,11);
        yes(h,p.hasEffect(mob_effects.HEALTHY_CONDITION.get())&&state.lastEvaluation==11,"生命变化下一服务端tick立即重评");
        infection_symptom_rules.onHealthLoss(p);infection_symptom_rules.onDirectHit(p,3);
        yes(h,!p.hasEffect(mob_effects.HEALTHY_CONDITION.get()),"扣血前的渗血重评不能把体况良好错误补回");
        p.setHealth(17);infection_symptom_rules.tick(p,12);
        attr(p,attributes.SAN.get()).setBaseValue(4000);infection_symptom_rules.tick(p,13);
        yes(h,p.hasEffect(mob_effects.MENTAL_DISTRACTION.get())&&state.lastEvaluation==13,"SAN变化不等10刻");
        infection_symptom_rules.start(p,"recovery_fatigue",1);infection_symptom_rules.tick(p,14);
        yes(h,!p.hasEffect(mob_effects.RECOVERY_FATIGUE.get())&&state.lastEvaluation==14,"事件到期不等10刻");
        p.getAbilities().invulnerable=true;infection_symptom_rules.tick(p,15);
        for(int i=16;i<35;i++)infection_symptom_rules.tick(p,i);
        yes(h,state.lastEvaluation==15&&!p.hasEffect(mob_effects.MENTAL_DISTRACTION.get()),"暂停状态只清理一次");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void passiveTicksDoNotRecalculateStaminaUntilSettlement(GameTestHelper h)
    {
        var p=player(h);p.setHealth(6);infection_service.set(p,10);
        AtomicInteger reads=new AtomicInteger();
        var modifier=new AttributeModifier(UUID.randomUUID(),"counted equipment",0,AttributeModifier.Operation.ADDITION)
        { @Override public String getName(){reads.incrementAndGet();return super.getName();} };
        attr(p,attributes.STAMINA.get()).addTransientModifier(modifier);reads.set(0);
        for(int i=0;i<1199;i++)infection_service.tick(p,i);
        yes(h,reads.get()==0&&player_variables.infection(p).orElseThrow().passiveTicks()==1199,"1199刻只推进计时，不遍历耐力修正");
        infection_service.tick(p,1199);
        yes(h,reads.get()>0&&infection_service.query(p)>10,"1200刻才计算有效耐力并结算");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void missingCapabilitiesInvalidSpellIdsAndDisconnectedRespawnDoNotCrash(GameTestHelper h) throws ReflectiveOperationException
    {
        var p=player(h);
        yes(h,infection_symptom_rules.effect("INVALID ID")==null&&!infection_symptom_rules.has(p,"missing_effect"),"缺少或非法注册名安全返回");
        for(String raw:new String[]{"not a valid:spell id","invalid::spell"})
        {
            FireballSpell invalid=new FireballSpell();
            var field=io.redspace.ironsspellbooks.api.spells.AbstractSpell.class.getDeclaredField("spellID");
            field.setAccessible(true);field.set(invalid,raw);
            yes(h,!infection_damage.direct(SpellDamageSource.source(p,invalid)),"非法法术ID不进入ConcurrentHashMap空键查询");
        }
        p.invalidateCaps();
        yes(h,infection_symptom_rules.state(p)==null,"缺失capability不抛异常");
        infection_symptom_rules.start(p,"shivering",20);infection_symptom_rules.tick(p,0);
        infection_symptom_rules.reevaluate(p);infection_symptom_rules.onDirectHit(p,10);
        infection_symptom_rules.blind(p);
        ServerPlayer fake=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"NoConnection"));
        yes(h,fake.connection==null,"测试无真实连接的ServerPlayer");
        san_procedures.onRespawn(new PlayerEvent.PlayerRespawnEvent(fake,false));
        infection_network.sync(fake,true);
        com.damezame97.white_lily.white_lilyfood.magic_apple.grantMana(fake,1);
        h.succeed();
    }

    @GameTest(template="empty")
    public static void ringAnvilRenameRetainsIdentityAndCannotRepairOrEnchant(GameTestHelper h)
    {
        var p=player(h);p.experienceLevel=50;
        BlockPos pos=h.absolutePos(new BlockPos(1,2,1));h.getLevel().setBlock(pos,Blocks.ANVIL.defaultBlockState(),3);
        var menu=new AnvilMenu(1,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));
        p.containerMenu=menu;
        var ring=new ItemStack(items.STORED_SUFFERING.get());ring.setDamageValue(200);
        UUID identity=stored_suffering.identity(ring);ring.getOrCreateTag().putString("custom_data","keep");
        menu.getSlot(0).set(ring);menu.setItemName("Remembered Pain");
        var result=menu.getSlot(2).getItem();
        yes(h,!result.isEmpty()&&result.getHoverName().getString().equals("Remembered Pain"),"实际铁砧生成重命名输出");
        yes(h,result.getDamageValue()==200&&result.getTag().getUUID(stored_suffering.INSTANCE_ID).equals(identity)
                &&result.getTag().getString("custom_data").equals("keep"),"重命名完整保留耐久和实例NBT");
        yes(h,Objects.equals(result.getHoverName().getStyle().getColor(),net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.DARK_RED)),"重命名后仍暗红");
        menu.clicked(2,0,ClickType.PICKUP,p);
        yes(h,menu.getCarried().getHoverName().getString().equals("Remembered Pain")&&menu.getSlot(0).getItem().isEmpty()&&p.experienceLevel==49,"取出消耗原戒指和1级经验");
        var renamed=menu.getCarried().copy();
        menu.setCarried(ItemStack.EMPTY);menu.getSlot(0).set(renamed);menu.setItemName("");
        yes(h,!menu.getSlot(2).getItem().isEmpty()&&!menu.getSlot(2).getItem().hasCustomHoverName(),"可以清除自定义名称");
        menu.getSlot(1).set(new ItemStack(items.STORED_SUFFERING.get()));
        yes(h,menu.getSlot(2).getItem().isEmpty(),"第二枚戒指不能合并维修");
        menu.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.MENDING,1)));
        yes(h,menu.getSlot(2).getItem().isEmpty(),"禁止经验修补附魔旁路");
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));menu.getSlot(1).set(ItemStack.EMPTY);menu.setItemName("Normal sword");
        yes(h,!menu.getSlot(2).getItem().isEmpty(),"其他物品铁砧逻辑不受影响");
        h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.succeed();
    }

    @GameTest(template="empty")
    public static void issCompatibilityGuardRejectsPartialOrChangedInjectionLayouts(GameTestHelper h)
    {
        ClassNode node=new ClassNode();node.name=InfectionMixinPlugin.SPELL;
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC,"getManaCost","(I)I",null,null));
        for(String signature:List.of(InfectionMixinPlugin.CHECK,InfectionMixinPlugin.PAY))
        {
            int split=signature.indexOf('(');
            var method=new MethodNode(Opcodes.ACC_PUBLIC,signature.substring(0,split),signature.substring(split),null,null);
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,node.name,"getManaCost","(I)I",false));
            node.methods.add(method);
        }
        yes(h,InfectionMixinPlugin.compatible(node),"三个已知接口全部存在才启用");
        node.methods.get(1).desc="()V";
        yes(h,!InfectionMixinPlugin.compatible(node),"预检查签名变化时整体关闭");
        node.methods.get(1).desc=InfectionMixinPlugin.CHECK.substring(InfectionMixinPlugin.CHECK.indexOf('('));
        node.methods.get(2).instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,node.name,"getManaCost","(I)I",false));
        yes(h,!InfectionMixinPlugin.compatible(node),"扣费调用数量变化时整体关闭，不能半启用");
        h.succeed();
    }
}
