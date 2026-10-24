package com.damezame97.white_lily.client;

import com.damezame97.white_lily.processing.processing_menu;
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
import com.damezame97.white_lily.recipebook.browser_display.*;
import com.damezame97.white_lily.recipebook.browser_item_catalog;
import com.damezame97.white_lily.recipebook.browser_order;
import com.damezame97.white_lily.recipebook.browser_transfer;

/** A client display index built exclusively from the server's personal unlock snapshot. */
public final class recipe_browser_index
{
    public static final String KEY = "screen.white_lily.recipe_browser.";
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
    private static Set<ResourceLocation> lastMachineRecipes = Set.of();
    private static long lastMachineRevision = Long.MIN_VALUE;
    private static boolean lastShowAll;
    private static long lastCheatRevision = Long.MIN_VALUE;
    private static List<Display> known = List.of();
    private static List<ItemStack> items = List.of();
    private static final Map<browser_order.Mode,List<ItemStack>> SORTED=new EnumMap<>(browser_order.Mode.class);
    private static final Map<Recipe<?>,String> FINGERPRINTS=new IdentityHashMap<>();
    public static String recipeFingerprint(ResourceLocation id){var mc=Minecraft.getInstance();var r=mc.level==null?null:mc.level.getRecipeManager().byKey(id).orElse(null);return r==null?"":FINGERPRINTS.computeIfAbsent(r,browser_transfer::fingerprint);}
    public static String modName(ItemStack stack){String namespace=BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();return namespace.equals("minecraft")?"Minecraft":net.minecraftforge.fml.ModList.get().getModContainerById(namespace).map(m->m.getModInfo().getDisplayName()).filter(name->!name.isBlank()).orElse(namespace);}
    public static List<ItemStack> sortedItems(browser_order.Mode mode)
    {
        refresh();return SORTED.computeIfAbsent(mode,key->{
            Map<ItemStack,browser_order.Key> names=new IdentityHashMap<>();
            for(ItemStack stack:items){var id=BuiltInRegistries.ITEM.getKey(stack.getItem());String name=net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString());var category=browser_category_reload.classify(stack);String group=category==null?name:Component.translatable(category.translation()).getString();names.put(stack,new browser_order.Key(name==null?"":name,id.getNamespace(),modName(stack),id.toString(),stack.hasTag()?stack.getTag().toString():"",group,category==null?"":category.id()));}
            return items.stream().sorted(Comparator.comparing(names::get,browser_order.comparator(mode,Minecraft.getInstance().options.languageCode))).toList();
        });
    }

    public static void register(DisplayProvider provider)
    { PROVIDERS.add(Objects.requireNonNull(provider)); invalidate(); }
    public static void invalidate() { lastManager = null; browser_category_reload.invalidate(); }

    public static List<Display> recipes()
    { refresh(); return known; }
    public static List<ItemStack> items()
    { refresh(); return items; }
    public static void clear()
    {
        lastManager = null; lastRevision = Long.MIN_VALUE; lastUnlocked = Set.of();
        lastMachineRecipes = Set.of(); lastMachineRevision = Long.MIN_VALUE;
        lastShowAll = false; lastCheatRevision = Long.MIN_VALUE;
        known = List.of(); items = List.of();SORTED.clear();FINGERPRINTS.clear();
    }

    @Nullable private static processing_menu currentProcessingMenu()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.containerMenu instanceof processing_menu menu ? menu : null;
    }

    @Nullable private static processing_client_context.Context machineContext()
    {
        processing_menu menu = currentProcessingMenu();
        if(menu==null)return null;
        var mc=Minecraft.getInstance();
        if(!menu.stillValid(mc.player)||menu.table()!=null&&(menu.table().isRemoved()||mc.level.getBlockEntity(menu.machinePos())!=menu.table()))
        {processing_client_context.clear(menu.containerId);return null;}
        return processing_client_context.get(menu.containerId,menu.machinePos());
    }

    public static Set<ResourceLocation> machineRecipes()
    {
        var context = machineContext();
        return context == null ? Set.of() : context.recipes();
    }

    public static long machineRevision()
    {
        var context = machineContext();
        return context == null ? Long.MIN_VALUE : context.revision();
    }

    public static boolean machineContextAvailable() { return machineContext() != null; }

    public static boolean isMachineRecipe(ResourceLocation id) { return id != null && machineRecipes().contains(id); }

    /** Fill is allowed only when the live machine already knows the exact processing recipe. */
    public static boolean canFillProcessing(ResourceLocation id) { return isMachineRecipe(id); }

    public static String transferHint(ResourceLocation id)
    {
        var context = machineContext();
        Minecraft mc = Minecraft.getInstance();
        Recipe<?> recipe = mc.level == null ? null : mc.level.getRecipeManager().byKey(id).orElse(null);
        if (recipe instanceof processing_recipe && context != null
                && recipe_unlock_client.isUnlocked(id) && !context.recipes().contains(id))
            return "transfer_unregistered";
        return "transfer_unavailable";
    }

    /** Personal revision plus the current menu's machine-context revision. */
    public static long revision()
    {
        long revision = recipe_unlock_client.revision();
        var context = machineContext();
        if (context != null) revision = revision * 31L + context.revision();
        return revision * 31L + browser_cheat_client.revision();
    }

    @Nullable public static Component processingStatus(processing_recipe recipe)
    {
        var context = machineContext();
        if (recipe == null) return null;
        if(recipe.researchNode()==null)return tr("status.base");
        if(context==null)return recipe_unlock_client.isUnlocked(recipe.getId())?tr("status.personal"):null;
        boolean personal = recipe_unlock_client.isUnlocked(recipe.getId());
        boolean machine = context.recipes().contains(recipe.getId());
        if (recipe.researchNode() == null) return machine ? tr("status.base") : null;
        if (personal && machine) return tr("status.ready");
        if (!personal && machine) return tr("status.machine");
        if (personal) return tr("status.unregistered");
        return null;
    }

    private static Component tr(String key) { return Component.translatable(KEY + key); }

    private static void refresh()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        RecipeManager manager = mc.level.getRecipeManager();
        String language = mc.options.languageCode;
        Set<ResourceLocation> machine = machineRecipes();
        long machineRevision = machineRevision();
        boolean showAll = browser_cheat_client.showAll();
        long cheatRevision = browser_cheat_client.indexRevision();
        if (lastManager == manager && lastRevision == recipe_unlock_client.revision() && lastLanguage.equals(language)
                && lastUnlocked.equals(recipe_unlock_client.unlocked())
                && lastMachineRevision == machineRevision && lastMachineRecipes.equals(machine)
                && lastShowAll == showAll && lastCheatRevision == cheatRevision) return;
        lastManager = manager; lastRevision = recipe_unlock_client.revision(); lastLanguage = language;
        lastUnlocked = recipe_unlock_client.unlocked();
        lastMachineRevision = machineRevision; lastMachineRecipes = machine;
        lastShowAll = showAll; lastCheatRevision = cheatRevision;

        List<Recipe<?>> sources = new ArrayList<>();
        if (showAll) sources.addAll(manager.getRecipes());
        else
        {
            Set<ResourceLocation> effective = new LinkedHashSet<>(recipe_unlock_client.unlocked());
            effective.addAll(machine);
            for (ResourceLocation id : effective)
            {
                Recipe<?> recipe = manager.byKey(id).orElse(null);
                if (recipe != null && (recipe_unlock_client.isUnlocked(id) || machine.contains(id))) sources.add(recipe);
            }
        }

        List<Display> displays = new ArrayList<>();
        Map<String, ItemStack> outputMap = new LinkedHashMap<>();
        if (showAll)
            for (ItemStack stack : browser_item_catalog.all())
                if (!stack.isEmpty()) outputMap.putIfAbsent(browser_item_catalog.key(stack), stack.copyWithCount(1));

        for (Recipe<?> recipe : sources)
        {
            try
            {
                Display display = null;
                for (DisplayProvider provider : PROVIDERS)
                    if (provider.supports(recipe)) { display = provider.create(recipe, mc.level.registryAccess()); break; }
                if (display == null) display = standard(recipe, mc.level.registryAccess());
                List<Component> notes = new ArrayList<>(display.notes());
                if (showAll) notes.add(tr("status.cheat"));
                else if (!(recipe instanceof processing_recipe)) notes.add(tr("status.personal"));
                String namespace = recipe.getId().getNamespace();
                String mod = namespace.equals("minecraft") ? "Minecraft" : net.minecraftforge.fml.ModList.get()
                        .getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName())
                        .filter(name -> !name.isBlank()).orElse(namespace);
                notes.add(Component.translatable(KEY + "recipe_mod", mod));
                display = new Display(display.recipe(), display.category(), display.title(), display.workstation(),
                        display.slots(), display.outputs(), List.copyOf(notes), display.contentHeight());
                displays.add(display);
                for (ItemStack output : display.outputs())
                    if (!output.isEmpty())
                        outputMap.putIfAbsent(browser_item_catalog.key(output), output.copyWithCount(1));
            }
            catch (RuntimeException exception)
            {
                // One malformed third-party display must not make the player's inventory unusable.
                com.mojang.logging.LogUtils.getLogger().warn("Cannot display recipe {}", recipe.getId(), exception);
            }
        }
        displays.sort(Comparator.comparing(d -> d.recipe().getId().toString()));
        List<ItemStack> outputs = new ArrayList<>(outputMap.values());
        outputs.sort(Comparator.comparing((ItemStack s) -> s.getHoverName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparing(s -> s.hasTag() ? s.getTag().toString() : ""));
        known = List.copyOf(displays); items = List.copyOf(outputs); SORTED.clear(); FINGERPRINTS.clear();
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

    private static Display standard(Recipe<?> recipe,RegistryAccess access)
    {
        Display special=special(recipe);if(special!=null)return special;
        Display result=com.damezame97.white_lily.recipebook.browser_recipe_display.build(recipe,access);
        if(recipe instanceof processing_recipe processing){Component status=processingStatus(processing);if(status!=null){List<Component> notes=new ArrayList<>(result.notes());notes.add(0,status);return new Display(recipe,result.category(),result.title(),result.workstation(),result.slots(),result.outputs(),List.copyOf(notes),result.contentHeight());}}
        return result;
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
