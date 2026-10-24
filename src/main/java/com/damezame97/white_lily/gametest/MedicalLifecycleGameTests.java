package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.debugging.debug_file;
import com.damezame97.white_lily.infection.*;
import com.damezame97.white_lily.item.*;
import com.damezame97.white_lily.procedures.san_procedures;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
import java.nio.file.Files;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.*;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public class MedicalLifecycleGameTests
{
    @GameTest(template = "empty")
    public static void allWorldLevelsResetOnlyDeathAndSendCorrectAttribute(GameTestHelper h) throws Exception
    {
        var path=debug_file.path(h.getLevel().getServer());
        byte[] original=Files.readAllBytes(path);
        int[] expected={50000,22000,10000,2000};
        try
        {
            for(int level=1;level<=4;level++)
            {
                Files.writeString(path,"{\"difficulty_level\":"+level+",\"happy_mode\":false}");
                ToolPlayer first=new ToolPlayer(h,true);
                san_procedures.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(first));
                near(h,expected[level-1],san_procedures.currentSan(first),"四档首次初始化");
                attr(first,attributes.SAN.get()).setBaseValue(1234);
                var old=player_variables.san(first).orElseThrow();
                old.setMeltdownPenalty(4);old.setInjuryTicks(500);
                first.invalidateCaps(); // 模拟 Forge 在 Clone 前旧实体能力已失效。
                ToolPlayer second=new ToolPlayer(h,true);
                second.restoreFrom(first,false); // 真实 Forge Clone 事件总线链。
                second.setId(first.getId());
                san_procedures.onRespawn(new PlayerEvent.PlayerRespawnEvent(second,false));
                near(h,expected[level-1],san_procedures.currentSan(second),"四档死亡重生按权威档位");
                var state=player_variables.san(second).orElseThrow();
                yes(h,state.isInitialized()&&state.getMeltdownPenalty()==0&&state.getInjuryTicks()==0,"死亡清空应清数据");
                var snapshot=second.packets.stream().filter(p->p instanceof ClientboundUpdateAttributesPacket)
                        .map(p->(ClientboundUpdateAttributesPacket)p).reduce((a,b)->b).orElseThrow();
                near(h,expected[level-1],snapshot.getValues().stream().filter(v->v.getAttribute()==attributes.SAN.get())
                        .findFirst().orElseThrow().getBase(),"实际发往客户端的属性基础值");
                ToolPlayer repeated=new ToolPlayer(h,true);
                second.invalidateCaps();
                repeated.restoreFrom(second,false);
                near(h,expected[level-1],san_procedures.currentSan(repeated),"重复死亡仍按档位");
                attr(repeated,attributes.SAN.get()).setBaseValue(4321);
                repeated.addEffect(new MobEffectInstance(mob_effects.BANDAGED.get(),12000,1,false,false));
                ToolPlayer dimension=new ToolPlayer(h,true);
                repeated.invalidateCaps();
                dimension.restoreFrom(repeated,true);
                near(h,4321,san_procedures.currentSan(dimension),"非死亡Clone不得重置SAN");
                yes(h,dimension.getEffect(mob_effects.BANDAGED.get()).getDuration()==12000,"非死亡Clone保留医疗效果");
                san_procedures.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(dimension));
                near(h,4321,san_procedures.currentSan(dimension),"重新登录不得重置");
                CompoundTag saved=dimension.saveWithoutId(new CompoundTag());
                ToolPlayer loaded=new ToolPlayer(h,true);
                loaded.load(saved);
                san_procedures.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(loaded));
                near(h,4321,san_procedures.currentSan(loaded),"保存加载后登录保留SAN");
                yes(h,player_variables.san(loaded).orElseThrow().isInitialized(),"标记也应保存加载");
            }
        }
        finally{Files.write(path,original);}
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testChipsWorkWithoutPermissionAreReusablePreciseAndDoNotTreat(GameTestHelper h)
    {
        ToolPlayer p=new ToolPlayer(h,true);
        p.setGameMode(GameType.CREATIVE);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items.POLLUTION_CHIP_SMALL.get(),3));
        items.POLLUTION_CHIP_SMALL.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        near(h,10,infection_service.query(p),"创造模式直接加10");
        yes(h,p.getMainHandItem().getCount()==3,"创造不消耗");
        items.POLLUTION_CHIP_SMALL.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        near(h,10,infection_service.query(p),"使用冷却阻止连发");
        p.setGameMode(GameType.SURVIVAL);
        infection_service.set(p,280);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items.POLLUTION_CHIP_LARGE.get(),2));
        items.POLLUTION_CHIP_LARGE.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        near(h,300,infection_service.query(p),"大污染片硬上限300");
        yes(h,p.getMainHandItem().getCount()==2,"生存重复使用不消耗");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items.CLEANSING_CHIP_MEDIUM.get(),2));
        items.CLEANSING_CHIP_MEDIUM.get().use(p.level(),p,InteractionHand.MAIN_HAND);
        near(h,270,infection_service.query(p),"洁净片直接减30");
        yes(h,!p.hasEffect(mob_effects.RECOVERY_FATIGUE.get())&&state(p).poolCooldown==0,"不当作治疗或随机感染事件");
        yes(h,!p.hasEffect(mob_effects.DELAYED_HEALING.get()),"愈合迟滞不再随感染值自动发放");
        ToolPlayer denied=new ToolPlayer(h,false);
        denied.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items.POLLUTION_CHIP_LARGE.get(),3));
        items.POLLUTION_CHIP_LARGE.get().use(denied.level(),denied,InteractionHand.MAIN_HAND);
        near(h,100,infection_service.query(denied),"生存玩家无需管理员权限即可改值");
        yes(h,denied.getMainHandItem().getCount()==3,"生存无权限也不消耗");
        denied.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items.CLEANSING_CHIP_SMALL.get(),3));
        items.CLEANSING_CHIP_SMALL.get().use(denied.level(),denied,InteractionHand.MAIN_HAND);
        near(h,90,infection_service.query(denied),"生存玩家无需管理员权限即可净化");
        yes(h,denied.getMainHandItem().getCount()==3,"净化片不消耗");
        denied.setGameMode(GameType.CREATIVE);
        denied.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(items.POLLUTION_DETECTOR.get()));
        var result=items.POLLUTION_DETECTOR.get().use(denied.level(),denied,InteractionHand.MAIN_HAND);
        yes(h,result.getResult().consumesAction(),"检测仪无需管理员权限，创造可读");
        Item[] chips={items.POLLUTION_CHIP_SMALL.get(),items.POLLUTION_CHIP_MEDIUM.get(),items.POLLUTION_CHIP_LARGE.get()};
        ChatFormatting[] colors={ChatFormatting.WHITE,ChatFormatting.BLUE,ChatFormatting.RED};
        for(int i=0;i<3;i++)yes(h,Objects.equals(chips[i].getName(chips[i].getDefaultInstance()).getStyle().getColor(),
                net.minecraft.network.chat.TextColor.fromLegacyFormat(colors[i])),"名称颜色按小中大");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void soilsDropsAndBonemealRespectNoFreeFlowerCopy(GameTestHelper h)
    {
        BlockPos base=h.absolutePos(new BlockPos(1,2,1));
        var level=h.getLevel();
        Block[] herbs={medical_blocks.BITTERLEAF.get(),medical_blocks.YARROW.get(),medical_blocks.CALMING_FLOWER.get()};
        for(Block herb:herbs)
        {
            for(Block soil:new Block[]{Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.PODZOL})
            {
                level.setBlock(base.below(),soil.defaultBlockState(),3);
                level.setBlock(base,Blocks.AIR.defaultBlockState(),3);
                yes(h,herb.defaultBlockState().canSurvive(level,base),"独立土壤标签允许种植");
            }
            level.setBlock(base.below(),Blocks.OAK_LEAVES.defaultBlockState(),3);
            yes(h,!herb.defaultBlockState().canSurvive(level,base),"树叶不可种植");
            level.setBlock(base.below(),Blocks.DIRT.defaultBlockState(),3);
            level.setBlock(base,Blocks.WATER.defaultBlockState(),3);
            yes(h,!herb.defaultBlockState().canSurvive(level,base),"水中不可种植");
            level.setBlock(base,herb.defaultBlockState(),3);
            var drops=Block.getDrops(herb.defaultBlockState(),level,base,null);
            yes(h,drops.size()==1&&drops.get(0).is(herb.asItem())&&drops.get(0).getCount()==1,"徒手掉落恰好1份");
            ItemStack meal=new ItemStack(Items.BONE_MEAL,2);
            boolean used=BoneMealItem.growCrop(meal,level,base);
            boolean reproduces=herb!=medical_blocks.CALMING_FLOWER.get();
            yes(h,used==reproduces&&meal.getCount()==(reproduces?1:2),"安神花无效骨粉不得消耗");
            yes(h,level.getBlockState(base).is(herb),"骨粉保留原株");
            yes(h,!herb.defaultBlockState().isRandomlyTicking(),"不随机刻复制或蔓延");
        }
        level.setBlock(base,Blocks.AIR.defaultBlockState(),3);
        h.succeed();
    }

    static final class ToolPlayer extends ServerPlayer
    {
        final boolean allowed;
        final List<Packet<?>> packets=new ArrayList<>();
        ToolPlayer(GameTestHelper h,boolean allowed)
        {
            super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"MedicalTest"));
            this.allowed=allowed;
            connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),this)
            {
                @Override public void send(Packet<?> packet){packets.add(packet);}
                @Override public void send(Packet<?> packet,PacketSendListener listener){packets.add(packet);}
            };
            setHealth(10);getAbilities().invulnerable=false;
        }
        @Override public boolean hasPermissions(int level){return allowed&&level<=2;}
    }
}
