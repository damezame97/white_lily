package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.infection.infection_symptom_rules;
import com.damezame97.white_lily.worldgen.herb_patch;
import com.damezame97.white_lily.worldgen.medical_worldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.*;
import java.lang.reflect.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.*;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public class MedicalWorldgenGameTests
{
    @GameTest(template="empty")
    public static void realWorldgenConfigurationsHaveExactProbabilitiesAndCaps(GameTestHelper h)
    {
        String[] names={"bitterleaf","yarrow","calming_flower"};
        double[] probabilities={0.15,0.10,0.05};
        int[] caps={4,3,1};
        for(int i=0;i<3;i++)
        {
            String herbName=names[i];
            var config=config(h,herbName);
            near(h,probabilities[i],config.probability(),"生成概率");
            yes(h,config.maximum()==caps[i],"单区块上限配置");
            var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(i==2?Biomes.FOREST:Biomes.PLAINS).value();
            long occurrences=biome.getGenerationSettings().features().stream().flatMap(set->set.stream())
                    .filter(holder->holder.unwrapKey().map(key->key.location().equals(infection_symptom_rules.id(herbName+"_patch"))).orElse(false)).count();
            yes(h,occurrences==0,"自然生成暂停：群系装饰表不挂载药草feature");
            yes(h,herb_patch.target(new Random(0,0),config)==caps[i],"成功目标为范围上界");
            yes(h,herb_patch.target(new Random(probabilities[i],0),config)==0,"概率边界严格小于");
            Probe probe=new Probe(h,i);
            probe.forest=i==2;
            yes(h,place(h,probe,config),"有合法落点时生成");
            yes(h,probe.placed.size()==caps[i],"达到目标但不超过上限");
            for(var pos:probe.placed.keySet())yes(h,herb_patch.inside(pos,probe.x,probe.z)&&pos.getY()==64,"地表且不跨区块");
            int count=probe.placed.size();
            yes(h,!place(h,probe,config)&&probe.placed.size()==count,"多群系或多步骤重复调用不得补种或突破上限");
        }
        // 在全部已加载群系上检查自然生成入口，保留手动 Feature 放置用于测试和后续接入。
        Set<String> pausedFeatures=Set.of("bitterleaf_patch","yarrow_patch","calming_flower_patch","hemp_patch");
        for(var biome:h.getLevel().registryAccess().registryOrThrow(Registries.BIOME))
            yes(h,biome.getGenerationSettings().features().stream().flatMap(set->set.stream())
                    .noneMatch(holder->holder.unwrapKey().map(key->key.location().getNamespace().equals("white_lily")
                            &&pausedFeatures.contains(key.location().getPath())).orElse(false)),"所有群系均不自然生成暂停的药草和野生麻");
        // 正负区块边界都使用 floorDiv；不是向零截断。
        yes(h,herb_patch.inside(new BlockPos(-16,64,-1),-16,-16),"负坐标区块内");
        yes(h,!herb_patch.inside(new BlockPos(0,64,-1),-16,-16),"负坐标上边界不跨区块");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void generationRejectsWaterLeavesCavesAndWrongBiomes(GameTestHelper h)
    {
        var bitter=config(h,"bitterleaf");
        for(int mode=0;mode<5;mode++)
        {
            Probe probe=new Probe(h,10+mode);
            probe.water=mode==0;probe.leafSoil=mode==1;probe.desert=mode==2;
            probe.sky=mode!=3;probe.cave=mode==4;
            yes(h,!place(h,probe,bitter)&&probe.placed.isEmpty(),"拒绝水/叶土/沙漠/遮阴/洞穴落点");
            yes(h,probe.heightQueries<=64,"32次落点、每次最多两个高度图查询");
        }
        Probe shade=new Probe(h,16);shade.forest=true;shade.sky=false;
        yes(h,place(h,shade,config(h,"calming_flower"))&&shade.placed.size()==1,"安神花允许树荫地表");
        h.succeed();
    }

    private static herb_patch.Configuration config(GameTestHelper h,String name)
    {
        return (herb_patch.Configuration)Objects.requireNonNull(h.getLevel().registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).get(infection_symptom_rules.id(name+"_patch"))).config();
    }
    private static boolean place(GameTestHelper h,Probe probe,herb_patch.Configuration config)
    {
        return medical_worldgen.HERB_PATCH.get().place(new FeaturePlaceContext<>(Optional.empty(),probe.world,
                h.getLevel().getChunkSource().getGenerator(),new Random(0,0),new BlockPos(probe.x,0,probe.z),config));
    }
    private static final class Random extends LegacyRandomSource
    {
        final double roll;int offset;
        Random(double roll,int offset){super(0);this.roll=roll;this.offset=offset;}
        @Override public double nextDouble(){return roll;}
        @Override public int nextInt(int bound){return bound==16?8:bound==7?offset++%7:bound-1;}
    }
    /** 使用真正 Feature 主逻辑，受控世界明确记录每次读写，意外 API 或跨区块访问直接失败。 */
    private static final class Probe implements InvocationHandler
    {
        final GameTestHelper helper;
        final WorldGenLevel world;
        final net.minecraft.world.level.chunk.ChunkAccess chunk;
        final int x,z;
        final Map<BlockPos,BlockState> placed=new HashMap<>();
        int heightQueries;
        boolean sky=true,forest,desert,water,leafSoil,cave;
        Probe(GameTestHelper helper,int index)
        {
            this.helper=helper;
            x=helper.absolutePos(BlockPos.ZERO).getX()/16*16+512+index*32;
            z=helper.absolutePos(BlockPos.ZERO).getZ()/16*16+512;
            chunk=helper.getLevel().getChunk(x>>4,z>>4);
            world=(WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},this);
        }
        @Override public Object invoke(Object proxy,Method method,Object[] args)
        {
            String name=method.getName();
            if(name.equals("getLevel"))return helper.getLevel();
            if(name.equals("getChunk"))return chunk;
            if(name.equals("getHeight"))
            {
                int px=(int)args[1],pz=(int)args[2];
                if(px<x||px>=x+16||pz<z||pz>=z+16)throw new AssertionError("跨区块高度读取");
                heightQueries++;
                int surface=cave?80:64;
                return args[0]==net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG&&!sky?surface+5:surface;
            }
            BlockPos pos=args!=null&&args.length>0&&args[0] instanceof BlockPos p?p:null;
            if(pos!=null&&!herb_patch.inside(pos,x,z))throw new AssertionError("跨区块落点读取/写入");
            return switch(name)
            {
                case "getBlockState" -> placed.getOrDefault(pos,leafSoil&&pos.getY()==63?Blocks.OAK_LEAVES.defaultBlockState():
                        pos.getY()<64?Blocks.DIRT.defaultBlockState():
                        cave&&pos.getY()==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
                case "getFluidState" -> water?Fluids.WATER.defaultFluidState():Fluids.EMPTY.defaultFluidState();
                case "getBiome" -> helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                        .getHolderOrThrow(desert?Biomes.DESERT:forest?Biomes.FOREST:Biomes.PLAINS);
                case "setBlock" -> {placed.put(pos.immutable(),(BlockState)args[1]);yield true;}
                case "toString" -> "ControlledHerbWorld";
                default -> throw new AssertionError("Unexpected worldgen API: "+method);
            };
        }
    }
}
