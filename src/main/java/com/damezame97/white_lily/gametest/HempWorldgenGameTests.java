package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.worldgen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class HempWorldgenGameTests
{
    private static final ResourceLocation ID=new ResourceLocation("white_lily","hemp_patch");
    @GameTest(template="empty")
    public static void exactHempConfigurationAndOncePerChunkPlacement(GameTestHelper h)
    {
        var config=(herb_patch.Configuration)Objects.requireNonNull(h.getLevel().registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).get(ID)).config();
        yes(h,config.plant().is(medical_blocks.WILD_HEMP.get())&&config.openSky()&&config.probability()==0.1
                &&config.minimum()==2&&config.maximum()==4,"真实配置10%、2至4株、露天地表野生麻");
        for(int result=0;result<3;result++)yes(h,herb_patch.target(new Rolls(0.0999,result),config)==2+result,"目标均匀范围");
        yes(h,herb_patch.target(new Rolls(0.1,0),config)==0,"概率边界拒绝");
        var registry=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for(var key:List.of(Biomes.PLAINS,Biomes.SUNFLOWER_PLAINS,Biomes.FOREST,Biomes.BIRCH_FOREST))
        {
            var features=registry.getHolderOrThrow(key).value().getGenerationSettings().features();
            yes(h,features.stream().flatMap(set->set.stream()).filter(holder->holder.unwrapKey()
                    .map(k->k.location().equals(ID)).orElse(false)).count()==1,"目标群系仅挂载一次");
        }
        for(var key:List.of(Biomes.DESERT,Biomes.SNOWY_PLAINS,Biomes.NETHER_WASTES,Biomes.THE_END))
            yes(h,registry.getHolderOrThrow(key).value().getGenerationSettings().features().stream().flatMap(set->set.stream())
                    .noneMatch(holder->holder.unwrapKey().map(k->k.location().equals(ID)).orElse(false)),"非目标群系未挂载");
        for(int mode=0;mode<6;mode++)
        {
            final int m=mode;
            int x=Math.floorDiv(h.absolutePos(BlockPos.ZERO).getX(),16)*16+2048+mode*32;
            int z=Math.floorDiv(h.absolutePos(BlockPos.ZERO).getZ(),16)*16+2048;
            var chunk=h.getLevel().getChunk(x>>4,z>>4);
            Map<BlockPos,BlockState> placed=new HashMap<>();int[] heights={0};
            WorldGenLevel world=(WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,method,args)->{
                if(method.getName().equals("getLevel"))return h.getLevel();
                if(method.getName().equals("getChunk"))return chunk;
                if(method.getName().equals("getHeight"))
                {
                    yes(h,(int)args[1]>=x&&(int)args[1]<x+16&&(int)args[2]>=z&&(int)args[2]<z+16,"高度查询不跨区块");
                    heights[0]++;return m==3&&args[0]==Heightmap.Types.WORLD_SURFACE_WG?70:64;
                }
                BlockPos pos=args!=null&&args.length>0&&args[0] instanceof BlockPos p?p:null;
                if(pos!=null)yes(h,herb_patch.inside(pos,x,z),"所有落点读写限制区块");
                return switch(method.getName())
                {
                    case "getBlockState" -> placed.getOrDefault(pos,pos.getY()<64?
                            (m==2?Blocks.OAK_LEAVES:m==5?Blocks.STONE:Blocks.GRASS_BLOCK).defaultBlockState():Blocks.AIR.defaultBlockState());
                    case "getFluidState" -> m==1?Fluids.WATER.defaultFluidState():Fluids.EMPTY.defaultFluidState();
                    case "getBiome" -> registry.getHolderOrThrow(m==4?Biomes.DESERT:Biomes.PLAINS);
                    case "setBlock" -> {placed.put(pos.immutable(),(BlockState)args[1]);yield true;}
                    default -> throw new AssertionError("Unexpected API: "+method);
                };
            });
            var context=new FeaturePlaceContext<>(Optional.empty(),world,h.getLevel().getChunkSource().getGenerator(),
                    new Rolls(0,2),new BlockPos(x,0,z),config);
            boolean result=medical_worldgen.HERB_PATCH.get().place(context);
            yes(h,mode==0?result&&placed.size()==4:!result&&placed.isEmpty(),"合法位置4株，其余拒绝水/树叶/遮挡/错误群系/洞穴石面");
            yes(h,heights[0]<=64,"有限32次尝试");
            int count=placed.size();
            yes(h,!medical_worldgen.HERB_PATCH.get().place(context)&&placed.size()==count,"重复调用不得再次判定或突破4株");
        }
        yes(h,herb_patch.inside(new BlockPos(-16,64,-1),-16,-16)&&!herb_patch.inside(new BlockPos(0,64,-1),-16,-16),"负坐标边界");
        h.succeed();
    }
    private static final class Rolls extends LegacyRandomSource
    {
        final double chance;final int target;int offset;
        Rolls(double chance,int target){super(0);this.chance=chance;this.target=target;}
        @Override public double nextDouble(){return chance;}
        @Override public int nextInt(int bound){return bound==16?8:bound==7?offset++%7:target;}
    }
}
