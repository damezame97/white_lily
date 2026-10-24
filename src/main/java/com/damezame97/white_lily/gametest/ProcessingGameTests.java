package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.medical_blocks;
import com.damezame97.white_lily.crafting.prescription_recipes;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.processing.processing_block_entity;
import com.damezame97.white_lily.processing.processing_recipe;
import com.damezame97.white_lily.processing.processing_registry;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class ProcessingGameTests
{
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("white_lily", path); }
    private static processing_block_entity table(GameTestHelper h)
    {
        h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        h.setBlock(new BlockPos(1, 2, 1), medical_blocks.SIMPLE_PROCESSING_TABLE.get());
        processing_block_entity table=(processing_block_entity) h.getBlockEntity(new BlockPos(1, 2, 1));
        var researcher=ProcessingTestPlayers.create(h);
        var progress=com.damezame97.white_lily.research.research_capability.get(researcher).orElseThrow();
        progress.complete(id("bitterleaf_paste"));progress.complete(id("yarrow_dressing"));
        table.registerLearned(researcher);
        for (var recipe : h.getLevel().getRecipeManager().getAllRecipesFor(processing_registry.RECIPE_TYPE.get()))
            com.damezame97.white_lily.recipebook.recipe_unlocks.unlock(researcher,recipe.getId());
        return table;
    }
    private static void tick(processing_block_entity table, int count)
    {
        for (int i = 0; i < count; i++) processing_block_entity.serverTick(table.getLevel(), table.getBlockPos(), table.getBlockState(), table);
    }
    private static void fill(processing_block_entity table, ItemStack raw)
    {
        table.getInventory().setStackInSlot(0, new ItemStack(items.STONE_MORTAR.get()));
        table.getInventory().setStackInSlot(1, raw);
    }
    private static processing_recipe recipe(GameTestHelper h, String path)
    { return (processing_recipe) h.getLevel().getRecipeManager().byKey(id(path)).orElseThrow(); }

    @GameTest(template="empty")
    public static void quantitiesDistributionTimingsAndNoUnrelatedInputs(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            processing_block_entity t = table(h);
            yes(h,t.getDisplayName().getString().equals("Simple Processing Table"),"菜单标题使用已有方块翻译，不显示原始语言键");
            fill(t, new ItemStack(items.HEMP_STALK.get(), 3));
            t.getInventory().setStackInSlot(6, new ItemStack(Items.DIRT, 64));
            t.getInventory().setStackInSlot(7, new ItemStack(Items.DIRT, 64));
            tick(t, 39);
            yes(h, t.getData().get(0) == 39 && t.getInventory().getStackInSlot(5).isEmpty(), "40刻前不扣料或产出");
            tick(t, 81);
            yes(h, t.getInventory().getStackInSlot(5).is(items.PLANT_FIBER.get())
                    && t.getInventory().getStackInSlot(5).getCount() == 6
                    && t.getInventory().getStackInSlot(0).getDamageValue() == 3, "三批120刻，六纤维，三耐久；无对应输出不堵塞");
            for (var herb : List.of(items.BITTERLEAF.get(), items.YARROW.get()))
            {
                t = table(h);
                fill(t, new ItemStack(herb));
                t.getInventory().setStackInSlot(3, new ItemStack(herb, 3));
                tick(t, 99);
                yes(h, t.getInventory().getStackInSlot(5).isEmpty() && t.getInventory().getStackInSlot(1).getCount() == 1, "100刻前不预扣");
                tick(t, 1);
                yes(h, t.getInventory().getStackInSlot(5).is(herb == items.BITTERLEAF.get() ? items.BITTERLEAF_PASTE.get() : items.YARROW_DRESSING.get())
                        && t.getInventory().getStackInSlot(5).getCount() == 2 && t.getInventory().getStackInSlot(7).getCount() == 1
                        && t.getInventory().getStackInSlot(1).isEmpty() && t.getInventory().getStackInSlot(3).getCount() == 1,
                        "跨槽分摊三原料，余量保留，药品两份和药渣一份");
            }
            t.getInventory().setStackInSlot(2, new ItemStack(Items.STICK));
            yes(h, !recipe(h, "processing/yarrow_dressing").matchesInputs(t.getInventory()), "无关原料拒绝匹配");
            yes(h, !t.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "首版不公开自动化能力");
            h.succeed();
        }
    }

    @GameTest(template="empty")
    public static void blockedOutputsAreAtomicAndFinalDurabilityStillProduces(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            processing_block_entity t = table(h);
            fill(t, new ItemStack(items.BITTERLEAF.get(), 3));
            ItemStack mortar = t.getInventory().getStackInSlot(0);
            mortar.setDamageValue(127);
            mortar.setHoverName(Component.literal("Last mortar"));
            mortar.enchant(Enchantments.UNBREAKING, 3);
            mortar.getOrCreateTag().putString("CustomMarker", "preserved");
            tick(t, 50);
            t.getInventory().setStackInSlot(7, new ItemStack(items.HERBAL_RESIDUE.get(), 64));
            tick(t, 100);
            yes(h, t.getData().get(0) == 50 && t.getData().get(2) == 2 && mortar.getDamageValue() == 127
                    && t.getInventory().getStackInSlot(1).getCount() == 3 && t.getInventory().getStackInSlot(5).isEmpty(), "副产物堵塞暂停并保留50刻，无部分扣料");
            t.getInventory().setStackInSlot(7, ItemStack.EMPTY);
            ItemStack named = new ItemStack(items.BITTERLEAF_PASTE.get()); named.setHoverName(Component.literal("different"));
            t.getInventory().setStackInSlot(5, named);
            tick(t, 3);
            yes(h, t.getData().get(0) == 50, "同种但不同NBT输出不能混叠");
            t.getInventory().setStackInSlot(5, ItemStack.EMPTY);
            tick(t, 50);
            yes(h, t.getInventory().getStackInSlot(0).isEmpty() && t.getInventory().getStackInSlot(1).isEmpty()
                    && t.getInventory().getStackInSlot(5).getCount() == 2 && t.getInventory().getStackInSlot(7).getCount() == 1,
                    "最后1耐久正常结算后破碎；耐久附魔不免损耗");
            tick(t, 100);
            yes(h, t.getInventory().getStackInSlot(5).getCount() == 2, "破碎不继续加工或重复发放");
            t = table(h); fill(t, new ItemStack(items.HEMP_STALK.get(), 2));
            ItemStack tool = t.getInventory().getStackInSlot(0); tool.setHoverName(Component.literal("Kept"));
            tool.getOrCreateTag().putString("Marker", "kept"); tool.enchant(Enchantments.UNBREAKING, 3);
            CompoundTag original = tool.getTag().copy(); tick(t, 40);
            ItemStack after = t.getInventory().getStackInSlot(0); CompoundTag actual = after.getTag().copy(); actual.putInt("Damage", 0);
            yes(h, after.getDamageValue() == 1 && actual.equals(original), "只改真实耐久，名称附魔及其他NBT保留");
            h.succeed();
        }
    }

    @GameTest(template="empty")
    public static void progressInputChangesAndPersistentResume(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            processing_block_entity t = table(h); fill(t, new ItemStack(items.HEMP_STALK.get(), 2)); tick(t, 10);
            t.getInventory().setStackInSlot(1, new ItemStack(items.HEMP_STALK.get(), 8)); tick(t, 1);
            yes(h, t.getData().get(0) == 11, "增加同种原料保留进度");
            t.getInventory().setStackInSlot(0, t.getInventory().getStackInSlot(0).copy());
            yes(h, t.getData().get(0) == 0, "外部换上相同NBT工具也清零");
            tick(t, 17); CompoundTag saved = t.saveWithFullMetadata();
            processing_block_entity loaded = new processing_block_entity(t.getBlockPos(), t.getBlockState());
            loaded.load(saved); loaded.setLevel(h.getLevel());
            yes(h, loaded.getData().get(0) == 17, "加载不按现实时间补算");
            tick(loaded, 22); yes(h, loaded.getInventory().getStackInSlot(5).isEmpty(), "恢复后仍需完整剩余刻数");
            tick(loaded, 1); yes(h, loaded.getInventory().getStackInSlot(5).getCount() == 2, "40刻仅结算一批");
            tick(loaded, 5); loaded.getInventory().setStackInSlot(1, ItemStack.EMPTY);
            yes(h, loaded.getData().get(0) == 0, "取走必要原料立即清零");
            loaded.setRemoved(); tick(loaded, 100); yes(h, loaded.getData().get(0) == 0, "移除/卸载的实体不推进");
            h.succeed();
        }
    }

    @GameTest(template="empty")
    public static void serializerOptionalOutputsReloadAndStableSelection(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            var serializer = prescription_recipes.PROCESSING.get();
            var json = JsonParser.parseString("{\"tool\":{\"item\":\"white_lily:stone_mortar\"},\"tool_damage\":1,"
                    + "\"ingredients\":[{\"ingredient\":{\"item\":\"white_lily:hemp_stalk\"},\"count\":1}],"
                    + "\"container\":{\"ingredient\":{\"item\":\"minecraft:potion\"},\"count\":1},"
                    + "\"result\":{\"item\":\"white_lily:plant_fiber\",\"count\":2},"
                    + "\"return\":{\"item\":\"minecraft:glass_bottle\"},\"byproduct\":{\"item\":\"white_lily:herbal_residue\"},\"processing_time\":40}").getAsJsonObject();
            processing_recipe fixture = serializer.fromJson(id("aaa_fixture"), json);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { serializer.toNetwork(buffer, fixture); yes(h, fixture.fingerprint().equals(serializer.fromNetwork(fixture.getId(), buffer).fingerprint()), "配方全字段网络往返"); }
            finally { buffer.release(); }
            for (String key : List.of("tool_damage", "processing_time"))
            {
                var bad = json.deepCopy(); bad.addProperty(key, 0); boolean rejected = false;
                try { serializer.fromJson(id("invalid"), bad); } catch (RuntimeException expected) { rejected = true; }
                yes(h, rejected, "拒绝非法配方字段：" + key);
            }
            var manager = h.getLevel().getRecipeManager(); List<Recipe<?>> original = new ArrayList<>(manager.getRecipes());
            try
            {
                List<Recipe<?>> all = new ArrayList<>(original); all.add(fixture); all.add(serializer.fromJson(id("zzz_fixture"), json)); manager.replaceRecipes(all);
                processing_block_entity t = table(h); fill(t, new ItemStack(items.HEMP_STALK.get(), 2));
                t.getInventory().setStackInSlot(4, new ItemStack(Items.POTION));
                tick(t, 10);
                yes(h, t.getData().get(0) == 10 && t.saveWithFullMetadata().getString("Recipe").equals(fixture.getId().toString()), "多个可匹配配方按ID稳定选择");
                for (int slot : new int[]{5,6,7})
                {
                    t.getInventory().setStackInSlot(slot, new ItemStack(Items.DIRT,64)); tick(t, 1);
                    yes(h, t.getData().get(0) == 10 && t.getInventory().getStackInSlot(1).getCount() == 2
                            && t.getInventory().getStackInSlot(4).getCount() == 1, "三输出逐一堵塞都不能部分提交");
                    t.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                }
                tick(t,30);
                yes(h,t.getInventory().getStackInSlot(1).getCount()==1 && t.getInventory().getStackInSlot(4).isEmpty()
                        && t.getInventory().getStackInSlot(5).getCount()==2 && t.getInventory().getStackInSlot(6).is(Items.GLASS_BOTTLE)
                        && t.getInventory().getStackInSlot(7).is(items.HERBAL_RESIDUE.get()), "带容器与三个输出的一批同时提交");
                t = table(h); fill(t,new ItemStack(items.HEMP_STALK.get(),2));
                t.getInventory().setStackInSlot(4,new ItemStack(Items.POTION)); tick(t,10);
                var identical = serializer.fromJson(fixture.getId(), json); all = new ArrayList<>(original); all.add(identical); manager.replaceRecipes(all); tick(t,1);
                yes(h, t.getData().get(0) == 11, "兼容重载保留进度");
                var changed = json.deepCopy(); changed.addProperty("processing_time", 41);
                all = new ArrayList<>(original); all.add(serializer.fromJson(fixture.getId(), changed)); manager.replaceRecipes(all); tick(t,1);
                yes(h, t.getData().get(0) == 1, "同ID配方数据改变安全重新计时");
                manager.replaceRecipes(original); tick(t,1);
                yes(h, t.getData().get(0) == 0, "旧配方移除且容器与其他配方不兼容时清零");
            }
            finally { manager.replaceRecipes(original); }
            h.succeed();
        }
    }

    @GameTest(template="empty")
    public static void overlappingIngredientsAndBreakDropsOnlyOnce(GameTestHelper h)
    {
        try (ProcessingTestPlayers scope = new ProcessingTestPlayers(h))
        {
            processing_recipe overlap = new processing_recipe(id("overlap"), Ingredient.of(items.STONE_MORTAR.get()), 1,
                    List.of(new processing_recipe.Material(Ingredient.of(Items.OAK_LOG,Items.BIRCH_LOG),1),
                            new processing_recipe.Material(Ingredient.of(Items.OAK_LOG),1)), null,
                    new ItemStack(items.PLANT_FIBER.get()), ItemStack.EMPTY, ItemStack.EMPTY, 1);
            ItemStackHandler inv = new ItemStackHandler(8); inv.setStackInSlot(1,new ItemStack(Items.OAK_LOG)); inv.setStackInSlot(2,new ItemStack(Items.BIRCH_LOG));
            int[] used = overlap.allocateInputs(inv);
            yes(h, used != null && used[0] == 1 && used[1] == 1, "重叠配料使用完整分配，不能被贪心顺序误判");
            processing_block_entity t = table(h); fill(t,new ItemStack(items.HEMP_STALK.get(),7));
            t.getInventory().setStackInSlot(5,new ItemStack(items.PLANT_FIBER.get(),6));
            var area = new net.minecraft.world.phys.AABB(t.getBlockPos()).inflate(0.5);
            int before = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().mapToInt(e->e.getItem().getCount()).sum();
            var old = t.getBlockState(); h.getLevel().setBlockAndUpdate(t.getBlockPos(),Blocks.AIR.defaultBlockState());
            t.dropContents(); old.getBlock().onRemove(old,h.getLevel(),t.getBlockPos(),Blocks.AIR.defaultBlockState(),false);
            int after = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().mapToInt(e->e.getItem().getCount()).sum();
            yes(h,after-before == 14,"破坏与重复回调仅掉一次8槽物品，不复制");
            t = table(h); fill(t,new ItemStack(items.HEMP_STALK.get(),5));
            before = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.getLevel().destroyBlock(t.getBlockPos(),true);
            after = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().mapToInt(e->e.getItem().getCount()).sum();
            yes(h,after-before == 7,"正常破坏掉落一个工作台和各槽物品各一次");
            h.succeed();
        }
    }
}
