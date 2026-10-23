package com.damezame97.white_lily.client;

import com.damezame97.white_lily.processing.processing_recipe;
import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.recipebook.recipe_unlock_client;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.BannerItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.common.crafting.IShapedRecipe;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/** A client display index built exclusively from the server's personal unlock snapshot. */
public final class recipe_browser_index
{
    public static final String KEY = "screen.white_lily.recipe_browser.";
    public record DisplaySlot(Ingredient ingredient, int count, int x, int y, Component label, boolean output)
    {
        public ItemStack stack(long cycle)
        {
            ItemStack[] choices = ingredient.getItems();
            if (choices.length == 0) return ItemStack.EMPTY;
            ItemStack result = choices[Math.floorMod(cycle, choices.length)].copy();
            result.setCount(Math.max(1, count));
            return result;
        }
    }
    public record Display(Recipe<?> recipe, ResourceLocation category, Component title, ItemStack workstation,
                          List<DisplaySlot> slots, List<ItemStack> outputs, List<Component> notes, int contentHeight)
    { }

    /** Client-only extension point. Register before opening a container; the unlock filter cannot be bypassed. */
    public interface DisplayProvider
    {
        boolean supports(Recipe<?> recipe);
        Display create(Recipe<?> recipe, RegistryAccess registries);
    }
    private static final List<DisplayProvider> PROVIDERS = new ArrayList<>();
    private static RecipeManager lastManager;
    private static long lastRevision = Long.MIN_VALUE;
    private static String lastLanguage = "";
    private static Set<ResourceLocation> lastUnlocked = Set.of();
    private static List<Display> known = List.of();
    private static List<ItemStack> items = List.of();

    public static void register(DisplayProvider provider)
    { PROVIDERS.add(Objects.requireNonNull(provider)); invalidate(); }
    public static void invalidate() { lastManager = null; }

    public static List<Display> recipes()
    { refresh(); return known; }
    public static List<ItemStack> items()
    { refresh(); return items; }
    public static void clear()
    { lastManager = null; lastRevision = Long.MIN_VALUE; lastUnlocked = Set.of(); known = List.of(); items = List.of(); }

    private static void refresh()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        RecipeManager manager = mc.level.getRecipeManager();
        String language = mc.options.languageCode;
        if (lastManager == manager && lastRevision == recipe_unlock_client.revision() && lastLanguage.equals(language)
                && lastUnlocked.equals(recipe_unlock_client.unlocked())) return;
        lastManager = manager; lastRevision = recipe_unlock_client.revision(); lastLanguage = language;
        lastUnlocked = recipe_unlock_client.unlocked();
        List<Display> displays = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        for (ResourceLocation id : recipe_unlock_client.unlocked())
        {
            Recipe<?> recipe = manager.byKey(id).orElse(null);
            if (recipe == null || !recipe_unlock_client.isUnlocked(id)) continue;
            try
            {
                Display display = null;
                for (DisplayProvider provider : PROVIDERS)
                    if (provider.supports(recipe)) { display = provider.create(recipe, mc.level.registryAccess()); break; }
                if (display == null) display = standard(recipe, mc.level.registryAccess());
                displays.add(display);
                for (ItemStack output : display.outputs())
                    if (!output.isEmpty() && outputs.stream().noneMatch(s -> ItemStack.isSameItemSameTags(s, output)))
                    { ItemStack entry = output.copy(); entry.setCount(1); outputs.add(entry); }
            }
            catch (RuntimeException exception)
            {
                // One malformed third-party display must not make the player's inventory unusable.
                com.mojang.logging.LogUtils.getLogger().warn("Cannot display unlocked recipe {}", id, exception);
            }
        }
        displays.sort(Comparator.comparing(d -> d.recipe().getId().toString()));
        outputs.sort(Comparator.comparing(s -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        known = List.copyOf(displays); items = List.copyOf(outputs);
    }

    public static List<Display> find(ItemStack item, boolean uses)
    {
        if (item.isEmpty()) return List.of();
        return recipes().stream().filter(display -> uses
                ? display.slots().stream().anyMatch(s -> !s.output() && s.ingredient().test(item))
                : display.outputs().stream().anyMatch(s -> ItemStack.isSameItemSameTags(s, item) ||
                        (s.is(item.getItem()) && (!item.hasTag() || display.recipe().isSpecial())))).toList();
    }

    /** AND search terms; @namespace and #tag are evaluated against the result item. */
    public static boolean matches(ItemStack stack, String query)
    {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        for (String term : query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
        {
            if (term.isBlank()) continue;
            if (term.startsWith("@")) { if (!id.getNamespace().contains(term.substring(1))) return false; }
            else if (term.startsWith("#"))
            { if (stack.getTags().noneMatch(tag -> tag.location().toString().contains(term.substring(1)))) return false; }
            else if (!name.contains(term) && !id.toString().contains(term)) return false;
        }
        return true;
    }

    private static Display standard(Recipe<?> recipe, RegistryAccess access)
    {
        Display special = special(recipe);
        if (special != null) return special;
        ResourceLocation category = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (category == null) category = recipe.getId();
        String kind = category.getPath();
        Component title = Component.translatable(KEY + "category." + kind);
        if (!category.getNamespace().equals("minecraft") && !(recipe instanceof processing_recipe)) title = Component.literal(category.toString());
        List<DisplaySlot> slots = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        List<Component> notes = new ArrayList<>();
        ItemStack workstation = recipe.getToastSymbol();
        int contentHeight = 90;
        if (recipe instanceof processing_recipe processing)
        {
            title = Component.translatable(KEY + "category.processing");
            workstation = new ItemStack(medical_blocks.SIMPLE_PROCESSING_TABLE.get());
            add(slots, processing.tool(), 1, 4, 14, "tool", false);
            if (processing.container() != null)
                add(slots, processing.container().ingredient(), processing.container().count(), 34, 14, "container", false);
            int i = 0;
            for (processing_recipe.Material material : processing.materials())
            { add(slots, material.ingredient(), material.count(), 4 + (i % 5) * 24, 49 + (i / 5) * 24, "material", false); i++; }
            int outputY = 60 + ((i + 4) / 5) * 24;
            addOutput(slots, outputs, processing.result(), 4, outputY, "result");
            addOutput(slots, outputs, processing.returned(), 52, outputY, "returned");
            addOutput(slots, outputs, processing.byproduct(), 100, outputY, "byproduct");
            contentHeight = outputY + 27;
            notes.add(Component.translatable(KEY + "tool_damage", processing.toolDamage()));
            notes.add(Component.translatable(KEY + "time", processing.duration() / 20.0));
        }
        else if (recipe instanceof SmithingRecipe smithing)
        {
            Ingredient template = candidates(smithing::isTemplateIngredient);
            Ingredient base = candidates(smithing::isBaseIngredient);
            Ingredient addition = candidates(smithing::isAdditionIngredient);
            add(slots, template, 1, 4, 18, "template", false);
            add(slots, base, 1, 34, 18, "base", false);
            add(slots, addition, 1, 64, 18, "addition", false);
            ItemStack result = recipe.getResultItem(access);
            if (result.isEmpty() && template.getItems().length > 0 && base.getItems().length > 0 && addition.getItems().length > 0)
                result = smithing.assemble(new SimpleContainer(template.getItems()[0], base.getItems()[0], addition.getItems()[0]), access);
            addOutput(slots, outputs, result, 118, 18, "result");
        }
        else if (recipe instanceof CraftingRecipe)
        {
            List<Ingredient> ingredients = recipe.getIngredients();
            int recipeWidth = recipe instanceof IShapedRecipe<?> shaped ? shaped.getRecipeWidth() : 3;
            int recipeHeight = recipe instanceof IShapedRecipe<?> shaped ? shaped.getRecipeHeight() : (ingredients.size() + 2) / 3;
            int columns = Math.max(1, recipeWidth);
            for (int i = 0; i < ingredients.size(); i++)
                add(slots, ingredients.get(i), 1, 4 + (i % columns) * 20, 10 + (i / columns) * 20, "material", false);
            int resultX = 18 + columns * 20;
            addOutput(slots, outputs, recipe.getResultItem(access), resultX, 30, "result");
            contentHeight = Math.max(80, recipeHeight * 20 + 20);
            notes.add(Component.translatable(KEY + (recipe instanceof IShapedRecipe<?> ? "shaped" : "shapeless")));
        }
        else
        {
            List<Ingredient> ingredients = recipe.getIngredients();
            int i = 0;
            for (Ingredient ingredient : ingredients)
            { add(slots, ingredient, 1, 4 + (i % 5) * 24, 10 + (i / 5) * 24, "material", false); i++; }
            int resultY = 24 + ((i + 4) / 5) * 24;
            addOutput(slots, outputs, recipe.getResultItem(access), 4, resultY, "result");
            contentHeight = resultY + 30;
            if (recipe instanceof AbstractCookingRecipe cooking)
            {
                notes.add(Component.translatable(KEY + "time", cooking.getCookingTime() / 20.0));
                notes.add(Component.translatable(KEY + "experience", cooking.getExperience()));
            }
            else if (!(recipe instanceof StonecutterRecipe)) notes.add(Component.translatable(KEY + "generic"));
        }
        if (outputs.isEmpty()) notes.add(Component.translatable(KEY + "dynamic"));
        return new Display(recipe, category, title, workstation, List.copyOf(slots), List.copyOf(outputs), List.copyOf(notes), contentHeight);
    }

    /** Vanilla special recipes have empty getIngredients/getResultItem; these are illustrative rule displays. */
    @Nullable private static Display special(Recipe<?> recipe)
    {
        ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        if (serializer == null || !serializer.getNamespace().equals("minecraft")) return null;
        String kind = serializer.getPath();
        if (!kind.startsWith("crafting_special_") && !kind.equals("crafting_decorated_pot")) return null;
        List<DisplaySlot> slots = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        Ingredient dyes = candidates(s -> s.getItem() instanceof DyeItem);
        Ingredient banners = candidates(s -> s.getItem() instanceof BannerItem);
        List<ItemStack> examples = new ArrayList<>();
        int resultY = 78;
        switch (kind)
        {
            case "crafting_special_armordye" ->
            {
                Ingredient armor = candidates(s -> s.getItem() instanceof DyeableLeatherItem);
                add(slots, armor, 1, 4, 14, "base", false); add(slots, dyes, 1, 32, 14, "material", false);
                examples.addAll(Arrays.asList(armor.getItems()));
            }
            case "crafting_special_bookcloning" ->
            {
                add(slots, Ingredient.of(Items.WRITTEN_BOOK), 1, 4, 14, "base", false);
                add(slots, Ingredient.of(Items.WRITABLE_BOOK), 1, 32, 14, "material", false);
                examples.add(new ItemStack(Items.WRITTEN_BOOK, 2));
            }
            case "crafting_special_mapcloning" ->
            {
                add(slots, Ingredient.of(Items.FILLED_MAP), 1, 4, 14, "base", false);
                add(slots, Ingredient.of(Items.MAP), 1, 32, 14, "material", false);
                examples.add(new ItemStack(Items.FILLED_MAP, 2));
            }
            case "crafting_special_mapextending" ->
            {
                for (int i = 0; i < 9; i++) add(slots, Ingredient.of(i == 4 ? Items.FILLED_MAP : Items.PAPER), 1, 4 + i % 3 * 20, 10 + i / 3 * 20, "material", false);
                examples.add(new ItemStack(Items.FILLED_MAP));
            }
            case "crafting_special_firework_rocket" ->
            {
                add(slots, Ingredient.of(Items.PAPER), 1, 4, 14, "material", false);
                add(slots, Ingredient.of(Items.GUNPOWDER), 1, 32, 14, "material", false);
                add(slots, Ingredient.of(Items.FIREWORK_STAR), 1, 60, 14, "optional", false);
                examples.add(new ItemStack(Items.FIREWORK_ROCKET, 3));
            }
            case "crafting_special_firework_star" ->
            {
                add(slots, Ingredient.of(Items.GUNPOWDER), 1, 4, 14, "material", false);
                add(slots, dyes, 1, 32, 14, "material", false);
                add(slots, Ingredient.of(Items.FIRE_CHARGE, Items.GOLD_NUGGET, Items.FEATHER, Items.CREEPER_HEAD, Items.WITHER_SKELETON_SKULL,
                        Items.SKELETON_SKULL, Items.ZOMBIE_HEAD, Items.PLAYER_HEAD, Items.DRAGON_HEAD, Items.PIGLIN_HEAD), 1, 60, 14, "optional", false);
                add(slots, Ingredient.of(Items.DIAMOND), 1, 88, 14, "optional", false);
                add(slots, Ingredient.of(Items.GLOWSTONE_DUST), 1, 116, 14, "optional", false);
                examples.add(new ItemStack(Items.FIREWORK_STAR));
            }
            case "crafting_special_firework_star_fade" ->
            {
                add(slots, Ingredient.of(Items.FIREWORK_STAR), 1, 4, 14, "base", false); add(slots, dyes, 1, 32, 14, "material", false);
                examples.add(new ItemStack(Items.FIREWORK_STAR));
            }
            case "crafting_special_tippedarrow" ->
            {
                for (int i = 0; i < 9; i++) add(slots, Ingredient.of(i == 4 ? Items.LINGERING_POTION : Items.ARROW), 1, 4 + i % 3 * 20, 10 + i / 3 * 20, "material", false);
                examples.add(new ItemStack(Items.TIPPED_ARROW, 8));
            }
            case "crafting_special_bannerduplicate" ->
            {
                add(slots, banners, 1, 4, 14, "base", false); add(slots, banners, 1, 32, 14, "material", false);
                for (ItemStack banner : banners.getItems()) examples.add(banner.copyWithCount(2));
            }
            case "crafting_special_shielddecoration" ->
            {
                add(slots, Ingredient.of(Items.SHIELD), 1, 4, 14, "base", false); add(slots, banners, 1, 32, 14, "material", false);
                examples.add(new ItemStack(Items.SHIELD));
            }
            case "crafting_special_shulkerboxcoloring" ->
            {
                Ingredient boxes = candidates(s -> s.getItem() instanceof net.minecraft.world.item.BlockItem block && block.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock);
                add(slots, boxes, 1, 4, 14, "base", false); add(slots, dyes, 1, 32, 14, "material", false);
                examples.addAll(Arrays.asList(boxes.getItems()));
            }
            case "crafting_special_suspiciousstew" ->
            {
                add(slots, Ingredient.of(Items.BOWL), 1, 4, 14, "material", false);
                add(slots, Ingredient.of(Items.RED_MUSHROOM), 1, 32, 14, "material", false);
                add(slots, Ingredient.of(Items.BROWN_MUSHROOM), 1, 60, 14, "material", false);
                add(slots, Ingredient.of(ItemTags.SMALL_FLOWERS), 1, 88, 14, "material", false);
                examples.add(new ItemStack(Items.SUSPICIOUS_STEW));
            }
            case "crafting_special_repairitem" ->
            {
                Ingredient tools = candidates(ItemStack::isDamageableItem);
                add(slots, tools, 1, 4, 14, "base", false); add(slots, tools, 1, 32, 14, "material", false);
                examples.addAll(Arrays.asList(tools.getItems()));
            }
            case "crafting_decorated_pot" ->
            {
                Ingredient sherd = Ingredient.of(ItemTags.DECORATED_POT_INGREDIENTS);
                add(slots, sherd, 1, 24, 10, "material", false); add(slots, sherd, 1, 4, 30, "material", false);
                add(slots, sherd, 1, 44, 30, "material", false); add(slots, sherd, 1, 24, 50, "material", false);
                examples.add(new ItemStack(Items.DECORATED_POT));
            }
            default -> { return null; }
        }
        // The complete output family is indexed, while one representative cycles in the example result slot.
        outputs.addAll(examples);
        int outputCount = examples.isEmpty() ? 1 : examples.get(0).getCount();
        if (!examples.isEmpty()) add(slots, Ingredient.of(examples.stream()), outputCount, 4, resultY, "example_result", true);
        List<Component> notes = List.of(Component.translatable(KEY + "special." + kind), Component.translatable(KEY + "dynamic"));
        return new Display(recipe, ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"), Component.translatable(KEY + "category.crafting"),
                new ItemStack(Items.CRAFTING_TABLE), List.copyOf(slots), List.copyOf(outputs), notes, resultY + 30);
    }

    private static Ingredient candidates(Predicate<ItemStack> predicate)
    {
        return Ingredient.of(BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance())
                .filter(s -> !s.isEmpty()).filter(predicate));
    }
    private static void add(List<DisplaySlot> slots, Ingredient ingredient, int count, int x, int y, String label, boolean output)
    { if (!ingredient.isEmpty()) slots.add(new DisplaySlot(ingredient, count, x, y, Component.translatable(KEY + label), output)); }
    private static void addOutput(List<DisplaySlot> slots, List<ItemStack> outputs, ItemStack stack, int x, int y, String label)
    {
        if (stack.isEmpty()) return;
        outputs.add(stack.copy()); add(slots, Ingredient.of(stack), stack.getCount(), x, y, label, true);
    }
    private recipe_browser_index() { }
}
