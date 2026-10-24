package com.damezame97.white_lily.processing;

import com.damezame97.white_lily.crafting.prescription_recipes;
import com.damezame97.white_lily.item.items;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 带数量的无序加工配方。最大流分配允许分散堆叠，也正确处理相互重叠的配料标签。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class processing_recipe implements Recipe<Container>
{
    // 原版菜单属性使用有符号 short 同步；本轮不需要额外拆分高低位的数据包。
    public static final int MAX_PROCESSING_TIME = 32767;
    public record Material(Ingredient ingredient, int count) { }
    private final ResourceLocation id;
    private final Ingredient tool;
    private final int toolDamage;
    private final List<Material> materials;
    @Nullable private final Material container;
    private final ItemStack result;
    private final ItemStack returned;
    private final ItemStack byproduct;
    private final int duration;
    @Nullable private ResourceLocation researchNode;
    @Nullable public ResourceLocation researchNode() { return researchNode; }

    public processing_recipe(ResourceLocation id, Ingredient tool, int toolDamage, List<Material> materials,
                             @Nullable Material container, ItemStack result, ItemStack returned,
                             ItemStack byproduct, int duration)
    {
        validate(tool, toolDamage, materials, container, result, returned, byproduct, duration);
        this.id = id;
        this.tool = tool;
        this.toolDamage = toolDamage;
        this.materials = List.copyOf(materials);
        this.container = container;
        this.result = result.copy();
        this.returned = returned.copy();
        this.byproduct = byproduct.copy();
        this.duration = duration;
    }

    /** JSON、网络与 Java 扩展使用同一组边界，避免不同入口产生不一致配方。 */
    private static void validate(Ingredient tool, int damage, List<Material> materials, @Nullable Material container,
                                 ItemStack result, ItemStack returned, ItemStack byproduct, int duration)
    {
        if (tool.isEmpty() || damage < 1 || damage > 128 || duration < 1 || duration > MAX_PROCESSING_TIME)
            throw new JsonParseException("Invalid processing tool, durability or time");
        if (materials.isEmpty() || materials.size() > 16) throw new JsonParseException("Expected 1..16 processing ingredients");
        int total = 0;
        for (Material material : materials)
        {
            if (material.ingredient().isEmpty() || material.count() < 1 || material.count() > 192)
                throw new JsonParseException("Invalid processing ingredient");
            total += material.count();
        }
        if (total > 192) throw new JsonParseException("Processing ingredients exceed three input slots");
        if (container != null && (container.ingredient().isEmpty() || container.count() < 1 || container.count() > 64))
            throw new JsonParseException("Invalid processing container");
        if (result.isEmpty()) throw new JsonParseException("Missing processing result");
        for (ItemStack output : new ItemStack[]{result, returned, byproduct})
            if (!output.isEmpty() && (output.getCount() < 1 || output.getCount() > Math.min(64, output.getMaxStackSize())))
                throw new JsonParseException("Processing output cannot fit one slot");
    }

    public Ingredient tool() { return tool; }
    public int toolDamage() { return toolDamage; }
    public int duration() { return duration; }
    public List<Material> materials() { return materials; }
    @Nullable public Material container() { return container; }
    public ItemStack result() { return result.copy(); }
    public ItemStack returned() { return returned.copy(); }
    public ItemStack byproduct() { return byproduct.copy(); }

    public static boolean isSupportedTool(ItemStack stack, Level level)
    {
        if (stack.isEmpty() || stack.getCount() != 1 || !stack.isDamageableItem()
                || stack.getDamageValue() < 0 || stack.getDamageValue() >= stack.getMaxDamage()) return false;
        // 没有配方时也允许放入本轮公开的研钵；其他工具由配方显式声明。
        if (stack.is(items.STONE_MORTAR.get())) return true;
        return level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()).stream()
                .anyMatch(recipe -> recipe.tool.test(stack));
    }

    public static boolean isAllowedContainer(ItemStack stack, Level level)
    {
        return !stack.isEmpty() && level.getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()).stream()
                .anyMatch(recipe -> recipe.container != null && recipe.container.ingredient().test(stack));
    }

    @Override public boolean matches(Container inventory, Level level)
    {
        if (inventory.getContainerSize() < 8) return false;
        return toolMatches(inventory.getItem(0)) && containerMatches(inventory.getItem(4))
                && allocateInputs(inventory) != null;
    }

    public boolean matchesInputs(ItemStackHandler inventory)
    {
        return toolMatches(inventory.getStackInSlot(0)) && containerMatches(inventory.getStackInSlot(4))
                && allocateInputs(inventory) != null;
    }

    private boolean toolMatches(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getCount() == 1 && tool.test(stack) && stack.isDamageableItem()
                && stack.getDamageValue() >= 0 && stack.getDamageValue() < stack.getMaxDamage();
    }

    private boolean containerMatches(ItemStack stack)
    {
        return container == null ? stack.isEmpty()
                : container.ingredient().test(stack) && stack.getCount() >= container.count();
    }

    @Nullable public int[] allocateInputs(Container inventory)
    {
        return allocate(new ItemStack[]{inventory.getItem(1), inventory.getItem(2), inventory.getItem(3)});
    }

    @Nullable public int[] allocateInputs(ItemStackHandler inventory)
    {
        return allocate(new ItemStack[]{inventory.getStackInSlot(1), inventory.getStackInSlot(2), inventory.getStackInSlot(3)});
    }

    @Nullable private int[] allocate(ItemStack[] stacks)
    {
        int nodes = materials.size() + 5;
        int sink = nodes - 1;
        int[][] capacity = new int[nodes][nodes];
        int needed = 0;
        for (int raw = 0; raw < 3; raw++)
        {
            ItemStack stack = stacks[raw];
            if (stack.isEmpty()) continue;
            boolean relevant = false;
            capacity[0][1 + raw] = Math.min(stack.getCount(), 64);
            for (int material = 0; material < materials.size(); material++)
            {
                if (materials.get(material).ingredient().test(stack))
                {
                    capacity[1 + raw][4 + material] = 192;
                    relevant = true;
                }
            }
            if (!relevant) return null; // 多余同料可留，无关材料不能混进成功匹配。
        }
        for (int material = 0; material < materials.size(); material++)
        {
            int count = materials.get(material).count();
            capacity[4 + material][sink] = count;
            needed += count;
        }
        int[] initial = {capacity[0][1], capacity[0][2], capacity[0][3]};
        int flow = 0;
        while (flow < needed)
        {
            int[] previous = new int[nodes];
            Arrays.fill(previous, -1);
            previous[0] = 0;
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(0);
            while (!pending.isEmpty() && previous[sink] < 0)
            {
                int from = pending.remove();
                for (int to = 1; to < nodes; to++)
                    if (previous[to] < 0 && capacity[from][to] > 0)
                    {
                        previous[to] = from;
                        pending.add(to);
                    }
            }
            if (previous[sink] < 0) return null;
            int amount = needed - flow;
            for (int to = sink; to != 0; to = previous[to]) amount = Math.min(amount, capacity[previous[to]][to]);
            for (int to = sink; to != 0; to = previous[to])
            {
                capacity[previous[to]][to] -= amount;
                capacity[to][previous[to]] += amount;
            }
            flow += amount;
        }
        return new int[]{initial[0] - capacity[0][1], initial[1] - capacity[0][2], initial[2] - capacity[0][3]};
    }

    /** 完整数据指纹用于加载/资源重载验证，不把旧配方的进度套给改动后的配方。 */
    public String fingerprint()
    {
        StringBuilder value = new StringBuilder(tool.toJson().toString()).append('|').append(toolDamage).append('|').append(duration);
        if(researchNode!=null)value.append("|research:").append(researchNode);
        for (Material material : materials) value.append('|').append(material.ingredient().toJson()).append(':').append(material.count());
        if (container != null) value.append("|container:").append(container.ingredient().toJson()).append(':').append(container.count());
        value.append('|').append(result.save(new net.minecraft.nbt.CompoundTag()));
        value.append('|').append(returned.save(new net.minecraft.nbt.CompoundTag()));
        return value.append('|').append(byproduct.save(new net.minecraft.nbt.CompoundTag())).toString();
    }

    @Override public ItemStack assemble(Container inventory, RegistryAccess access) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return result.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return prescription_recipes.PROCESSING.get(); }
    @Override public RecipeType<?> getType() { return processing_registry.RECIPE_TYPE.get(); }
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients()
    {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(tool);
        for (Material material : materials) ingredients.add(material.ingredient());
        if (container != null) ingredients.add(container.ingredient());
        return ingredients;
    }

    public static final class Serializer implements RecipeSerializer<processing_recipe>
    {
        @Override public processing_recipe fromJson(ResourceLocation id, JsonObject json)
        {
            Ingredient tool = Ingredient.fromJson(json.get("tool"));
            if (tool.isEmpty()) throw new JsonParseException("Processing tool must not be empty");
            int damage = bounded(GsonHelper.getAsInt(json, "tool_damage", 1), 1, 128, "tool_damage");
            JsonArray ingredients = GsonHelper.getAsJsonArray(json, "ingredients");
            if (ingredients.isEmpty() || ingredients.size() > 16) throw new JsonParseException("Expected 1..16 processing ingredients");
            List<Material> materials = new ArrayList<>();
            int total = 0;
            for (var element : ingredients)
            {
                Material material = material(GsonHelper.convertToJsonObject(element, "ingredient"));
                materials.add(material);
                total += material.count();
            }
            if (total > 192) throw new JsonParseException("Processing ingredients exceed the three input slots");
            Material container = json.has("container") ? material(GsonHelper.getAsJsonObject(json, "container")) : null;
            if (container != null && container.count() > 64) throw new JsonParseException("Container count exceeds slot limit");
            ItemStack result = output(json, "result", true);
            ItemStack returned = output(json, "return", false);
            ItemStack byproduct = output(json, "byproduct", false);
            int duration = bounded(GsonHelper.getAsInt(json, "processing_time"), 1, MAX_PROCESSING_TIME, "processing_time");
            processing_recipe recipe=new processing_recipe(id, tool, damage, materials, container, result, returned, byproduct, duration);
            if(json.has("research")){recipe.researchNode=ResourceLocation.tryParse(GsonHelper.getAsString(json,"research"));if(recipe.researchNode==null)throw new JsonParseException("Invalid processing research ID");}
            return recipe;
        }

        private static Material material(JsonObject json)
        {
            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            if (ingredient.isEmpty()) throw new JsonParseException("Processing ingredient must not be empty");
            return new Material(ingredient, bounded(GsonHelper.getAsInt(json, "count", 1), 1, 192, "count"));
        }

        private static ItemStack output(JsonObject json, String key, boolean required)
        {
            if (!json.has(key))
            {
                if (required) throw new JsonParseException("Missing processing " + key);
                return ItemStack.EMPTY;
            }
            ItemStack stack = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, key), true);
            if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > Math.min(64, stack.getMaxStackSize()))
                throw new JsonParseException("Processing " + key + " cannot fit one output slot");
            return stack;
        }

        private static int bounded(int value, int minimum, int maximum, String key)
        {
            if (value < minimum || value > maximum) throw new JsonParseException("Invalid processing " + key + ": " + value);
            return value;
        }

        @Override public processing_recipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            Ingredient tool = Ingredient.fromNetwork(buffer);
            int damage = bounded(buffer.readVarInt(), 1, 128, "tool_damage");
            int count = bounded(buffer.readVarInt(), 1, 16, "ingredients");
            List<Material> materials = new ArrayList<>();
            for (int index = 0; index < count; index++) materials.add(readMaterial(buffer));
            Material container = buffer.readBoolean() ? readMaterial(buffer) : null;
            processing_recipe recipe=new processing_recipe(id, tool, damage, materials, container, buffer.readItem(), buffer.readItem(),
                    buffer.readItem(), bounded(buffer.readVarInt(), 1, MAX_PROCESSING_TIME, "processing_time"));
            recipe.researchNode=buffer.readBoolean()?buffer.readResourceLocation():null;return recipe;
        }

        private static Material readMaterial(FriendlyByteBuf buffer)
        { return new Material(Ingredient.fromNetwork(buffer), bounded(buffer.readVarInt(), 1, 192, "count")); }

        @Override public void toNetwork(FriendlyByteBuf buffer, processing_recipe recipe)
        {
            recipe.tool.toNetwork(buffer);
            buffer.writeVarInt(recipe.toolDamage);
            buffer.writeVarInt(recipe.materials.size());
            for (Material material : recipe.materials) writeMaterial(buffer, material);
            buffer.writeBoolean(recipe.container != null);
            if (recipe.container != null) writeMaterial(buffer, recipe.container);
            buffer.writeItem(recipe.result);
            buffer.writeItem(recipe.returned);
            buffer.writeItem(recipe.byproduct);
            buffer.writeVarInt(recipe.duration);
            buffer.writeBoolean(recipe.researchNode!=null);if(recipe.researchNode!=null)buffer.writeResourceLocation(recipe.researchNode);
        }

        private static void writeMaterial(FriendlyByteBuf buffer, Material material)
        { material.ingredient().toNetwork(buffer); buffer.writeVarInt(material.count()); }
    }
}
