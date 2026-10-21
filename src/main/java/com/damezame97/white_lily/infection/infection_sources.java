package com.damezame97.white_lily.infection;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 生产表初始为空。由后续明确的玩法代码在 common setup 中登记，不从客户端接收登记。 */
public final class infection_sources
{
    public record Source(ResourceLocation id, double probability, double base)
    {
        public Source
        {
            Objects.requireNonNull(id);
            if (!Double.isFinite(probability) || probability < 0 || probability > 1
                    || !Double.isFinite(base) || base < 0)
                throw new IllegalArgumentException("Invalid infection source: " + id);
        }
    }

    private static final Map<ResourceLocation, Source> SOURCES = new ConcurrentHashMap<>();

    public static void register(Source source)
    {
        if (SOURCES.putIfAbsent(source.id(), source) != null)
            throw new IllegalArgumentException("Duplicate infection source: " + source.id());
    }

    public static Optional<Source> find(ResourceLocation id) { return Optional.ofNullable(SOURCES.get(id)); }
    public static Map<ResourceLocation, Source> entries() { return Map.copyOf(SOURCES); }
    private infection_sources() { }
}
