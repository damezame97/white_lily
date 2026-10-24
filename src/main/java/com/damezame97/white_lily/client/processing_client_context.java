package com.damezame97.white_lily.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client-only, menu-session-scoped view of a processing table's exact capabilities.
 *
 * <p>It is deliberately not merged into the personal recipe snapshot. Closing the menu,
 * replacing the block or switching machines removes it; the browser then falls back to
 * personal knowledge only.</p>
 */
public final class processing_client_context
{
    public record Context(BlockPos pos, long revision, Set<ResourceLocation> recipes) { }

    private static final Map<Integer, Context> CONTEXTS = new HashMap<>();

    public static void accept(int containerId, BlockPos pos, long revision, Set<ResourceLocation> recipes)
    {
        Context previous = CONTEXTS.get(containerId);
        if (previous != null && revision < previous.revision()) return;
        CONTEXTS.put(containerId, new Context(pos, revision, Set.copyOf(recipes)));
        recipe_browser_index.invalidate();
    }

    @Nullable
    public static Context get(int containerId, BlockPos pos)
    {
        Context context = CONTEXTS.get(containerId);
        return context != null && context.pos().equals(pos) ? context : null;
    }

    public static void clear(int containerId)
    {
        if (CONTEXTS.remove(containerId) != null) recipe_browser_index.invalidate();
    }

    public static void clear()
    {
        if (!CONTEXTS.isEmpty()) CONTEXTS.clear();
        recipe_browser_index.invalidate();
    }

    private processing_client_context() { }
}
