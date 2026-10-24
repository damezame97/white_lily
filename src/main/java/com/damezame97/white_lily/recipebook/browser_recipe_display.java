package com.damezame97.white_lily.recipebook;
import com.damezame97.white_lily.recipebook.browser_display.*;
import com.damezame97.white_lily.processing.processing_recipe;
import com.damezame97.white_lily.block.medical_blocks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.common.crafting.IShapedRecipe;
import java.util.*;
import java.util.function.Predicate;
/** Pure actual-recipe display builder; never grants permission or scans player inventory. */
public final class browser_recipe_display {
    private static final String KEY="screen.white_lily.recipe_browser.";
    public static Display build(Recipe<?> recipe, RegistryAccess access)
    {
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
            add(slots, processing.tool(), 1, 4, 36, "tool", false);
            if(processing.container()!=null)add(slots,processing.container().ingredient(),processing.container().count(),72,70,"container",false);
            int i=0;for(var material:processing.materials())
            {add(slots,material.ingredient(),material.count(),48+(i%3)*20,24+(i/3)*22,"material",false);i++;}
            int extra=Math.max(0,(i+2)/3-1)*22;
            // Explicit fields remain distinct even for datapack recipes requiring more than one row.
            if(extra>0){slots.replaceAll(slot->slot.label().getString().equals(Component.translatable(KEY+"container").getString())?new DisplaySlot(slot.ingredient(),slot.count(),72,70+extra,slot.label(),slot.output()):slot);}
            addOutput(slots,outputs,processing.result(),172,24,"result");
            addOutput(slots,outputs,processing.returned(),146,70+extra,"returned");
            addOutput(slots,outputs,processing.byproduct(),194,70+extra,"byproduct");
            contentHeight=104+extra;
            notes.add(Component.translatable(KEY + "tool_damage", processing.toolDamage()));
            notes.add(Component.translatable(KEY + "time", processing.duration() / 20.0));
        }
        else if (recipe instanceof SmithingRecipe smithing)
        {
            Ingredient template = candidates(smithing::isTemplateIngredient);
            Ingredient base = candidates(smithing::isBaseIngredient);
            Ingredient addition = candidates(smithing::isAdditionIngredient);
            add(slots, template, 1, 4, 28, "template", false);
            add(slots, base, 1, 34, 28, "base", false);
            add(slots, addition, 1, 64, 28, "addition", false);
            ItemStack result = recipe.getResultItem(access);
            if (result.isEmpty() && template.getItems().length > 0 && base.getItems().length > 0 && addition.getItems().length > 0)
                result = smithing.assemble(new SimpleContainer(template.getItems()[0], base.getItems()[0], addition.getItems()[0]), access);
            addOutput(slots, outputs, result, 132, 28, "result");
            notes.add(Component.translatable(KEY+"smithing_dynamic"));
        }
        else if (recipe instanceof CraftingRecipe)
        {
            List<Ingredient> ingredients = recipe.getIngredients();
            int recipeWidth = recipe instanceof IShapedRecipe<?> shaped ? shaped.getRecipeWidth() : 3;
            int recipeHeight = recipe instanceof IShapedRecipe<?> shaped ? shaped.getRecipeHeight() : (ingredients.size() + 2) / 3;
            int columns = Math.max(1, recipeWidth);
            for (int i = 0; i < ingredients.size(); i++)
                add(slots, ingredients.get(i), 1, 4 + (i % columns) * 20, 10 + (i / columns) * 20, "material", false);
            int resultX = Math.max(112,columns*20+52);
            addOutput(slots, outputs, recipe.getResultItem(access), resultX, 30, "result");
            contentHeight = Math.max(84, recipeHeight * 20 + 20);
            int returned=0;
            for(Ingredient ingredient:ingredients)if(ingredient.getItems().length>0&&ingredient.getItems()[0].hasCraftingRemainingItem())
            {addOutput(slots,new ArrayList<>(),ingredient.getItems()[0].getCraftingRemainingItem(),4+(returned++%6)*20,contentHeight,"returned");}
            if(recipe instanceof com.damezame97.white_lily.crafting.prescription_revealing_recipe)
                for(Ingredient ingredient:ingredients)if(Arrays.stream(ingredient.getItems()).anyMatch(stack->stack.is(Items.POTION)))
                    addOutput(slots,new ArrayList<>(),new ItemStack(Items.GLASS_BOTTLE),4+(returned++%6)*20,contentHeight,"returned");
            if(recipe instanceof com.damezame97.white_lily.crafting.template_copy_recipe copy) {
                ItemStack[] templates=copy.template().getItems();
                if(templates.length>0) addOutput(slots,new ArrayList<>(),templates[0].copyWithCount(1),
                        4+(returned++%6)*20,contentHeight,"returned");
                notes.add(Component.translatable(KEY+"template_return"));
            }
            if(returned>0)contentHeight+=24*((returned+5)/6);
            notes.add(Component.translatable(KEY + (recipe instanceof IShapedRecipe<?> ? "shaped" : "shapeless")));
            if(recipe instanceof com.damezame97.white_lily.crafting.durable_tool_recipe)
                notes.add(Component.translatable(KEY+"shears_return"));
        }
        else if(recipe instanceof AbstractCookingRecipe cooking)
        {
            if(!recipe.getIngredients().isEmpty())add(slots,recipe.getIngredients().get(0),1,24,12,"material",false);
            addOutput(slots,outputs,recipe.getResultItem(access),116,34,"result");
            contentHeight=98;
            notes.add(Component.translatable(KEY+"fuel_hint"));
            notes.add(Component.translatable(KEY+"time",cooking.getCookingTime()/20.0));
            notes.add(Component.translatable(KEY+"experience",cooking.getExperience()));
        }
        else if(recipe instanceof StonecutterRecipe)
        {
            if(!recipe.getIngredients().isEmpty())add(slots,recipe.getIngredients().get(0),1,20,24,"material",false);
            addOutput(slots,outputs,recipe.getResultItem(access),112,24,"result");contentHeight=70;
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
    private browser_recipe_display(){}
}
