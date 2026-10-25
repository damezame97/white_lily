package com.damezame97.white_lily.recipebook;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** A client-side view of the server's permissions. Contains no Minecraft client classes. */
public final class recipe_unlock_client {
    private static Set<ResourceLocation> recipes = Set.of();
    private static Set<ResourceLocation> viewable = Set.of();
    private static long version;
    public static boolean isUnlocked(ResourceLocation id) { return id != null && recipes.contains(id); }
    public static boolean isViewable(ResourceLocation id) { return id != null && viewable.contains(id); }
    public static Set<ResourceLocation> unlocked() { return recipes; }
    public static Set<ResourceLocation> viewable() { return viewable; }
    public static long revision() { return version; }
    public static void accept(Set<ResourceLocation> allowed, Set<ResourceLocation> visible, long revision) {
        recipes = Set.copyOf(allowed); viewable = Set.copyOf(visible); version = revision;
    }
    public static void clear() { recipes = Set.of(); viewable = Set.of(); version = 0; }
    private recipe_unlock_client() { }
}
