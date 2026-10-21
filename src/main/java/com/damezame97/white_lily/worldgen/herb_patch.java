package com.damezame97.white_lily.worldgen;

import com.damezame97.white_lily.infection.infection_symptom_rules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import java.util.*;

/** 有界区块内小片生成；没有 random_patch 的跨边界偏移，也不扫描已有世界。 */
public final class herb_patch extends Feature<herb_patch.Configuration>
{
    public record Configuration(BlockState plant, ResourceLocation biomes, double probability,
                                int minimum, int maximum, boolean openSky) implements FeatureConfiguration
    {
        public static final Codec<Configuration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockState.CODEC.fieldOf("plant").forGetter(Configuration::plant),
                ResourceLocation.CODEC.fieldOf("biomes").forGetter(Configuration::biomes),
                Codec.doubleRange(0, 1).fieldOf("probability").forGetter(Configuration::probability),
                Codec.intRange(1, 4).fieldOf("minimum").forGetter(Configuration::minimum),
                Codec.intRange(1, 4).fieldOf("maximum").forGetter(Configuration::maximum),
                Codec.BOOL.fieldOf("open_sky").forGetter(Configuration::openSky)).apply(instance, Configuration::new));
    }
    // 同一装饰流程中，防御多群系/多个步骤对同一 ChunkAccess 重复调用。
    // 弱键不长期保留区块或世界；正常流程仍由 ChunkGenerator 的 feature 索引集合去重。
    private static final Map<ChunkAccess, Set<ResourceLocation>> ATTEMPTED = Collections.synchronizedMap(new WeakHashMap<>());
    public herb_patch() { super(Configuration.CODEC); }
    public static boolean claim(ChunkAccess chunk, ResourceLocation herb)
    {
        synchronized (ATTEMPTED) { return ATTEMPTED.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(herb); }
    }
    public static int target(RandomSource random, Configuration config)
    {
        if (random.nextDouble() >= config.probability()) return 0;
        int max = Math.max(config.minimum(), config.maximum());
        return config.minimum() + random.nextInt(max - config.minimum() + 1);
    }
    public static boolean inside(BlockPos pos, int minX, int minZ)
    { return pos.getX() >= minX && pos.getX() < minX + 16 && pos.getZ() >= minZ && pos.getZ() < minZ + 16; }

    @Override public boolean place(FeaturePlaceContext<Configuration> context)
    {
        var level = context.level();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        var config = context.config();
        int minX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int minZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        if (!claim(level.getChunk(context.origin()), net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(config.plant().getBlock())))
            return false;
        RandomSource random = context.random();
        int target = target(random, config);
        if (target == 0) return false;
        int centerX = minX + random.nextInt(16);
        int centerZ = minZ + random.nextInt(16);
        TagKey<Biome> biomes = TagKey.create(Registries.BIOME, config.biomes());
        int placed = 0;
        for (int attempt = 0; attempt < 32 && placed < target; attempt++)
        {
            int x = centerX + random.nextInt(7) - 3;
            int z = centerZ + random.nextInt(7) - 3;
            BlockPos horizontal = new BlockPos(x, 0, z);
            if (!inside(horizontal, minX, minZ)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                    || !config.plant().canSurvive(level, pos) || !level.getBiome(pos).is(biomes)
                    // FEATURES 时照明尚未完成，不能用 canSeeSky（读取天空光照）。
                    || (config.openSky() && level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) > y)) continue;
            if (level.setBlock(pos, config.plant(), 2)) placed++;
        }
        return placed > 0;
    }
}
