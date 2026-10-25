package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.machine.*;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
public final class MachineRulesGameTests {
    private static ResourceLocation id(String path) { return ResourceLocation.parse(path.contains(":") ? path : "minecraft:" + path); }
    private static machine_rules.Rule rule(String device, String json) {
        var data = JsonParser.parseString(json).getAsJsonObject();
        data.addProperty("device", "minecraft:" + device);
        data.addProperty("adapter", "white_lily:vanilla_cooking");
        return machine_rules.parse(id("white_lily:test_" + device), data);
    }
    private static AbstractFurnaceBlockEntity furnace(GameTestHelper h, Block block, int x) {
        var pos = new BlockPos(x, 2, 1); h.setBlock(pos, block);
        return (AbstractFurnaceBlockEntity) h.getBlockEntity(pos);
    }
    private static void tick(GameTestHelper h, AbstractFurnaceBlockEntity f, int ticks) {
        for (int i = 0; i < ticks; i++) AbstractFurnaceBlockEntity.serverTick(h.getLevel(), f.getBlockPos(), f.getBlockState(), f);
    }
    @GameTest(template = "empty")
    public static void finalRecipeTablesAreStrict(GameTestHelper h) {
        var m = h.getLevel().getRecipeManager();
        h.assertTrue(machine_rules.recipes(m,id("furnace")).size() == 18
                && machine_rules.recipes(m,id("smoker")).size() == 9
                && machine_rules.recipes(m,id("blast_furnace")).size() == 9, "Exact cooking whitelist counts");
        h.assertTrue(m.byKey(id("iron_ingot_from_smelting_raw_iron")).isPresent()
                && m.byKey(id("gold_nugget_from_blasting")).isPresent()
                && m.byKey(id("netherite_scrap")).isPresent(), "Global mineral and recycling recipes remain available to unadapted devices");
        for (String metal : List.of("iron", "gold", "copper")) for (String input : List.of(metal + "_ore", "deepslate_" + metal + "_ore", "raw_" + metal))
            h.assertTrue(m.byKey(id(metal + "_ingot_from_blasting_" + input)).isPresent(), "Allowed mineral " + input);
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void realMachinesRejectOresAndKeepCookingSpeeds(GameTestHelper h) {
        var f = furnace(h, Blocks.FURNACE, 1);
        f.setItem(0, new ItemStack(Items.RAW_IRON)); f.setItem(1, new ItemStack(Items.COAL));
        tick(h, f, 220);
        h.assertTrue(f.getItem(0).is(Items.RAW_IRON) && f.getItem(1).getCount() == 1 && f.getItem(2).isEmpty(), "Forbidden input consumes neither ore nor fresh fuel");
        f.setItem(0, new ItemStack(Items.BEEF)); tick(h, f, 100);
        h.assertTrue(f.getItem(2).isEmpty(), "Furnace is not smoker speed");
        tick(h, f, 100); h.assertTrue(f.getItem(2).is(Items.COOKED_BEEF), "Furnace food cooks at 200 ticks");
        var s = furnace(h, Blocks.SMOKER, 2);
        s.setItem(0, new ItemStack(Items.BEEF)); s.setItem(1, new ItemStack(Items.COAL)); tick(h, s, 100);
        h.assertTrue(s.getItem(2).is(Items.COOKED_BEEF), "Smoker cooks at 100 ticks");
        var b = furnace(h, Blocks.BLAST_FURNACE, 3);
        b.setItem(0, new ItemStack(Items.RAW_COPPER)); b.setItem(1, new ItemStack(Items.COAL)); tick(h, b, 100);
        h.assertTrue(b.getItem(2).is(Items.COPPER_INGOT), "Blast furnace processes copper without any player owner");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void savedCookingProgressSurvivesBlockEntityReload(GameTestHelper h) {
        Block[] blocks = {Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE};
        Item[] inputs = {Items.BEEF, Items.BEEF, Items.RAW_IRON};
        Item[] outputs = {Items.COOKED_BEEF, Items.COOKED_BEEF, Items.IRON_INGOT};
        for (int index = 0; index < blocks.length; index++) {
            var f = furnace(h, blocks[index], index + 1);
            f.setItem(0, new ItemStack(inputs[index], 2));
            f.setItem(1, new ItemStack(Items.COAL));
            tick(h, f, 30);
            for (int reload = 0; reload < 2; reload++) {
                var saved = f.saveWithFullMetadata();
                int progress = saved.getInt("CookTime"), fuel = saved.getInt("BurnTime");
                h.assertTrue(progress > 0 && f.getItem(2).isEmpty(), "Save partially cooked input");
                var restored = (AbstractFurnaceBlockEntity) BlockEntity.loadStatic(f.getBlockPos(), f.getBlockState(), saved);
                h.assertTrue(restored != null, "Restore a new furnace block entity from saved NBT");
                h.getLevel().setBlockEntity(restored);
                f = restored;
                tick(h, f, 1);
                var after = f.saveWithoutMetadata();
                h.assertTrue(after.getInt("CookTime") == progress + 1 && after.getInt("BurnTime") == fuel - 1
                        && f.getItem(0).getCount() == 2 && f.getItem(2).isEmpty(),
                        "Reload resumes saved progress without duplicating fuel, input or output");
            }
            var saved = f.saveWithoutMetadata();
            tick(h, f, saved.getInt("CookTimeTotal") - saved.getInt("CookTime"));
            h.assertTrue(f.getItem(0).getCount() == 1 && f.getItem(2).is(outputs[index])
                    && f.getItem(2).getCount() == 1, "Finish exactly once using only the remaining cooking time");
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void savedProgressCannotCookForbiddenInput(GameTestHelper h) {
        var f = furnace(h, Blocks.FURNACE, 1);
        f.setItem(0, new ItemStack(Items.RAW_IRON));
        f.setItem(1, new ItemStack(Items.COAL));
        var saved = f.saveWithFullMetadata();
        saved.putShort("CookTime", (short) 199);
        saved.putShort("CookTimeTotal", (short) 200);
        f = (AbstractFurnaceBlockEntity) BlockEntity.loadStatic(f.getBlockPos(), f.getBlockState(), saved);
        h.getLevel().setBlockEntity(f);
        tick(h, f, 220);
        h.assertTrue(f.getItem(0).is(Items.RAW_IRON) && f.getItem(1).getCount() == 1
                && f.getItem(2).isEmpty() && f.saveWithoutMetadata().getInt("CookTime") == 0,
                "Invalid saved progress cannot bypass the device whitelist or consume fuel");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void copyMigrationAndPolicyErrors(GameTestHelper h) {
        var src = new SmeltingRecipe(id("white_lily:test_source"), "test", CookingBookCategory.MISC,
                Ingredient.of(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT, 2), .7F, 200);
        var r = rule("blast_furnace", "{\"copies\":[{\"source\":\"white_lily:test_source\",\"id\":\"white_lily:test_copy\",\"cooking_time\":100,\"remove_source\":true}]}");
        var recipes = machine_rules.build(List.of(src), List.of(r)).tables().get(id("blast_furnace"));
        var copied = (AbstractCookingRecipe) recipes.get(0);
        h.assertTrue(recipes.size() == 1 && copied.getType() == RecipeType.BLASTING && copied.getCookingTime() == 100
                && copied.getExperience() == .7F && copied.getResultItem(h.getLevel().registryAccess()).getCount() == 2
                && copied.getIngredients().get(0).test(new ItemStack(Items.RAW_IRON)), "Migration preserves recipe data");
        boolean rejected = false;
        try { machine_rules.build(List.of(src), List.of(rule("furnace", "{\"allow\":[\"white_lily:missing\"]}"))); }
        catch (IllegalArgumentException e) { rejected = true; }
        h.assertTrue(rejected, "Missing whitelist targets fail validation");
        rejected = false;
        try { machine_rules.build(List.of(src), List.of(r, r)); }
        catch (IllegalArgumentException e) { rejected = true; }
        h.assertTrue(rejected, "Conflicting device policies are rejected");
        var blacklist = rule("furnace", "{\"mode\":\"blacklist\",\"deny\":[\"white_lily:test_source\"]}");
        h.assertTrue(machine_rules.build(List.of(src), List.of(blacklist)).tables().get(id("furnace")).isEmpty(), "Blacklist removal");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void reloadResetsProgressAndCachedRecipeCannotSurviveRemoval(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        try {
            var f = furnace(h, Blocks.FURNACE, 1);
            f.setItem(0, new ItemStack(Items.BEEF)); f.setItem(1, new ItemStack(Items.COAL)); tick(h, f, 50);
            long before = ((recipe_epoch) manager).whiteLily$recipeEpoch();
            ((machine_state_access)manager).whiteLily$machines(machine_rules.reload(h.getLevel().getServer().getResourceManager(), saved));
            tick(h, f, 1);
            h.assertTrue(f.saveWithoutMetadata().getInt("CookTime") == 1
                    && ((recipe_epoch) manager).whiteLily$recipeEpoch() > before, "Reload resets old progress and changes recipe epoch");
            manager.replaceRecipes(saved.stream().filter(r -> !r.getId().equals(id("cooked_beef"))).toList());
            tick(h, f, 220);
            h.assertTrue(f.getItem(0).is(Items.BEEF) && f.getItem(2).isEmpty(), "Cached removed recipe cannot consume inputs or produce output");
        } finally { manager.replaceRecipes(saved); ((machine_state_access)manager).whiteLily$machines(machine_rules.reload(h.getLevel().getServer().getResourceManager(), saved)); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void blastFurnaceRecipeAndUnlockNeedNoIron(GameTestHelper h) {
        var grid = new TransientCraftingContainer(new CraftingMenu(1, h.makeMockPlayer().getInventory()), 3, 3);
        Item[] ingredients = {Items.STONE_BRICKS, Items.STONE_BRICKS, Items.STONE_BRICKS,
                Items.STONE_BRICKS, Items.FURNACE, Items.STONE_BRICKS,
                Items.SMOOTH_STONE, Items.COAL_BLOCK, Items.SMOOTH_STONE};
        for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(ingredients[i]));
        var recipe = (CraftingRecipe) h.getLevel().getRecipeManager().byKey(id("blast_furnace")).orElseThrow();
        h.assertTrue(recipe.matches(grid, h.getLevel()), "Five stone bricks, furnace, two smooth stone and coal block");
        grid.setItem(7, new ItemStack(Items.CHARCOAL));
        h.assertTrue(!recipe.matches(grid, h.getLevel()), "Charcoal cannot replace coal block");
        try (var scope = new ProcessingTestPlayers(h)) {
            var p = ProcessingTestPlayers.create(h);
            recipe_unlocks.acquired(p, new ItemStack(Items.FURNACE));
            h.assertTrue(recipe_unlocks.isUnlocked(p, id("blast_furnace")), "Actual furnace unlocks blast furnace without iron");
            recipe_unlocks.acquired(p, new ItemStack(Items.COAL));
            h.assertTrue(recipe_unlocks.isUnlocked(p, id("coal_block")), "Coal unlocks coal block");
        }
        h.succeed();
    }
}
