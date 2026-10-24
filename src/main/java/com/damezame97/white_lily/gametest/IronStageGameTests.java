package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.crafting.template_copy_recipe;
import com.damezame97.white_lily.item.creative_tabs;
import com.damezame97.white_lily.item.detail_tooltip;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.item.makeshift_iron_tool;
import com.damezame97.white_lily.item.recipe_unlock_book;
import com.damezame97.white_lily.recipebook.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** Server-side coverage for the iron stage: tools, books, transcription, routes and device gates. */
@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class IronStageGameTests
{
    private static final List<ResourceLocation> TOOL_POOL = List.of(
            id("iron_pickaxe"), id("iron_axe"), id("iron_shovel"), id("iron_hoe"), id("iron_sword"));
    private static final List<ResourceLocation> ARMOR_POOL = List.of(
            id("iron_helmet"), id("iron_chestplate"), id("iron_leggings"), id("iron_boots"));
    private static final List<String> NEW_ITEM_NAMES = List.of(
            "makeshift_iron_pickaxe", "makeshift_iron_axe", "makeshift_iron_shovel",
            "makeshift_iron_hoe", "makeshift_iron_sword", "blacksmith_tools_notes",
            "blacksmith_armor_notes", "shield_recipe_book", "stonecutter_notes",
            "blacksmith_forging_notes", "equipment_upgrade_notes", "transport_notes");
    private record CopyDef(String book, String recipe)
    {
        ResourceLocation bookId() { return wl(book); }
        ResourceLocation recipeId() { return wl(recipe); }
    }
    private static final List<CopyDef> COPY_BOOKS = List.of(
            new CopyDef("blacksmith_tools_notes", "blacksmith_tools_notes_copy"),
            new CopyDef("blacksmith_armor_notes", "blacksmith_armor_notes_copy"),
            new CopyDef("shield_recipe_book", "shield_recipe_book_copy"),
            new CopyDef("stonecutter_notes", "stonecutter_notes_copy"),
            new CopyDef("blacksmith_forging_notes", "blacksmith_forging_notes_copy"),
            new CopyDef("equipment_upgrade_notes", "equipment_upgrade_notes_copy"),
            new CopyDef("transport_notes", "transport_notes_copy"));

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("minecraft", path); }
    private static ResourceLocation wl(String path) { return ResourceLocation.fromNamespaceAndPath("white_lily", path); }
    private static boolean unlocked(ServerPlayer player, ResourceLocation id) { return recipe_unlocks.isUnlocked(player, id); }
    private static void acquire(ServerPlayer player, Item... items) {
        for (Item item : items) recipe_unlocks.acquired(player, new ItemStack(item));
    }
    private static CraftingMenu table(GameTestHelper h, ServerPlayer player) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        CraftingMenu menu = new CraftingMenu(91, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        player.containerMenu = menu;
        return menu;
    }
    private static ItemStack read(ServerPlayer player, Item book) {
        player.getCooldowns().removeCooldown(book);
        ItemStack stack = new ItemStack(book, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        book.use(player.level(), player, InteractionHand.MAIN_HAND);
        return stack;
    }
    private static Set<ResourceLocation> known(ServerPlayer player, List<ResourceLocation> pool) {
        Set<ResourceLocation> result = new HashSet<>();
        for (ResourceLocation id : pool) if (unlocked(player, id)) result.add(id);
        return result;
    }
    private static double modifier(ItemStack stack, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        return stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(attribute)
                .stream().mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount).sum();
    }

    @GameTest(template = "empty")
    public static void twelveItemsModelsTexturesAndCreativeEntriesAreReal(GameTestHelper h)
    {
        for (String name : NEW_ITEM_NAMES)
        {
            Item item = ForgeRegistries.ITEMS.getValue(wl(name));
            yes(h, item != null && item != Items.AIR, "注册物品存在：" + name);
            String modelPath = "/assets/white_lily/models/item/" + name + ".json";
            String texturePath = "/assets/white_lily/textures/item/" + name + ".png";
            try (InputStream model = IronStageGameTests.class.getResourceAsStream(modelPath))
            {
                yes(h, model != null, "模型资源存在：" + name);
                String json = new String(model.readAllBytes(), StandardCharsets.UTF_8);
                yes(h, json.contains("\"white_lily:item/" + name + "\""), "模型引用实际贴图：" + name);
            }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
            try (InputStream texture = IronStageGameTests.class.getResourceAsStream(texturePath))
            {
                yes(h, texture != null, "贴图资源存在：" + name);
                BufferedImage image = ImageIO.read(texture);
                yes(h, image != null && image.getWidth() == 16 && image.getHeight() == 16, "正式贴图为16x16：" + name);
                boolean transparent = false;
                for (int y = 0; y < image.getHeight() && !transparent; y++)
                    for (int x = 0; x < image.getWidth(); x++)
                        if ((image.getRGB(x, y) >>> 24) == 0) { transparent = true; break; }
                yes(h, transparent, "正式贴图含透明背景：" + name);
            }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }

        CreativeModeTab books = creative_tabs.RECIPE_BOOKS.get();
        books.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        yes(h, books.getDisplayItems().size() == 8 && books.getDisplayItems().iterator().next().is(items.GOLDEN_SWORD_RECIPE_BOOK.get()),
                "7本新书进入现有配方书栏，金剑书仍居首");
        CreativeModeTab livelihood = creative_tabs.MISGUIDED_LIVELIHOOD.get();
        livelihood.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
        for (Item tool : List.of(items.MAKESHIFT_IRON_PICKAXE.get(), items.MAKESHIFT_IRON_AXE.get(),
                items.MAKESHIFT_IRON_SHOVEL.get(), items.MAKESHIFT_IRON_HOE.get(), items.MAKESHIFT_IRON_SWORD.get()))
            yes(h, livelihood.getDisplayItems().stream().anyMatch(stack -> stack.is(tool)), "简易工具进入既有生计栏");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customDetailsUseTheExistingStoneMortarShiftFold(GameTestHelper h)
    {
        List<Item> newItems = new ArrayList<>();
        for (String name : NEW_ITEM_NAMES) newItems.add(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(wl(name))));
        for (Item item : newItems)
        {
            List<Component> collapsed = new ArrayList<>();
            item.appendHoverText(new ItemStack(item), h.getLevel(), collapsed, TooltipFlag.Default.NORMAL);
            yes(h, collapsed.size() == 1 && collapsed.get(0).equals(detail_tooltip.hint()),
                    "未按Shift只显示统一提示且自定义说明全部折叠：" + item);
        }

        List<Map.Entry<Item, Item>> tools = List.of(
                Map.entry(items.MAKESHIFT_IRON_PICKAXE.get(), Items.IRON_PICKAXE),
                Map.entry(items.MAKESHIFT_IRON_AXE.get(), Items.IRON_AXE),
                Map.entry(items.MAKESHIFT_IRON_SHOVEL.get(), Items.IRON_SHOVEL),
                Map.entry(items.MAKESHIFT_IRON_HOE.get(), Items.IRON_HOE),
                Map.entry(items.MAKESHIFT_IRON_SWORD.get(), Items.IRON_SWORD));
        for (var pair : tools)
        {
            List<Component> expanded = new ArrayList<>();
            ItemStack stack = new ItemStack(pair.getKey());
            makeshift_iron_tool.appendHoverText(pair.getValue(), stack, expanded, true);
            yes(h, expanded.size() == 2
                            && expanded.get(0).equals(Component.translatable("tooltip.white_lily.makeshift_iron.lore")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                            && expanded.get(1).equals(Component.translatable("tooltip.white_lily.makeshift_iron.durability",
                            stack.getMaxDamage(), new ItemStack(pair.getValue()).getMaxDamage())),
                    "简易工具展开后按原顺序保留简介与三分之一耐久说明：" + pair.getKey());
        }

        Component use = Component.translatable("tooltip.white_lily.recipe_book.use").withStyle(ChatFormatting.AQUA);
        Component copy = Component.translatable("tooltip.white_lily.recipe_book.copy").withStyle(ChatFormatting.GRAY);
        for (String name : List.of("blacksmith_tools_notes", "blacksmith_armor_notes", "shield_recipe_book",
                "stonecutter_notes", "blacksmith_forging_notes", "equipment_upgrade_notes", "transport_notes"))
        {
            Item item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(wl(name)));
            recipe_unlock_book book = (recipe_unlock_book) item;
            List<Component> expanded = new ArrayList<>();
            book.appendDetails(new ItemStack(item), h.getLevel(), expanded, true);
            yes(h, expanded.size() >= 3 && expanded.get(0).equals(use)
                            && expanded.get(expanded.size() - 1).equals(copy),
                    "知识书展开后依次保留学习方式、目标内容与抄录规则：" + name);
        }
        ItemStack goldStack = new ItemStack(items.GOLDEN_SWORD_RECIPE_BOOK.get());
        List<Component> goldCollapsed = new ArrayList<>();
        items.GOLDEN_SWORD_RECIPE_BOOK.get().appendHoverText(goldStack, h.getLevel(), goldCollapsed, TooltipFlag.Default.NORMAL);
        yes(h, goldCollapsed.size() == 2 && goldCollapsed.get(0).equals(use)
                        && goldCollapsed.stream().noneMatch(line -> line.equals(copy)),
                "金剑书仍是原有常显说明，不套用本轮Shift折叠且不追加抄录说明");
        List<Component> gold = new ArrayList<>();
        ((recipe_unlock_book) items.GOLDEN_SWORD_RECIPE_BOOK.get()).appendDetails(goldStack, h.getLevel(), gold, true);
        yes(h, gold.size() == 2 && gold.get(0).equals(use)
                        && gold.stream().noneMatch(line -> line.equals(copy)),
                "金剑书展开内容保持原样");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void toolsUseOneThirdDurabilityAndRealIronAttributes(GameTestHelper h)
    {
        record Pair(Item simple, Item normal) { }
        List<Pair> pairs = List.of(
                new Pair(items.MAKESHIFT_IRON_PICKAXE.get(), Items.IRON_PICKAXE),
                new Pair(items.MAKESHIFT_IRON_AXE.get(), Items.IRON_AXE),
                new Pair(items.MAKESHIFT_IRON_SHOVEL.get(), Items.IRON_SHOVEL),
                new Pair(items.MAKESHIFT_IRON_HOE.get(), Items.IRON_HOE),
                new Pair(items.MAKESHIFT_IRON_SWORD.get(), Items.IRON_SWORD));
        for (Pair pair : pairs)
        {
            ItemStack simple = new ItemStack(pair.simple()), normal = new ItemStack(pair.normal());
            int expected = Math.max(1, normal.getMaxDamage() / 3);
            yes(h, simple.getMaxDamage() == expected, "耐久为对应正规铁工具三分之一并向下取整：" + pair.simple());
            yes(h, ((TieredItem) pair.simple()).getTier().getLevel() == ((TieredItem) pair.normal()).getTier().getLevel()
                            && ((TieredItem) pair.simple()).getEnchantmentValue() == ((TieredItem) pair.normal()).getEnchantmentValue(),
                    "采掘等级和附魔能力复用铁材料：" + pair.simple());
            yes(h, Math.abs(modifier(simple, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                            - modifier(normal, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) < 1e-6,
                    "攻击伤害对应正规铁工具：" + pair.simple());
            yes(h, Math.abs(modifier(simple, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)
                            - modifier(normal, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) < 1e-6,
                    "攻击速度对应正规铁工具：" + pair.simple());
            yes(h, pair.simple().isValidRepairItem(simple, new ItemStack(Items.IRON_INGOT)), "修复材料沿用铁锭：" + pair.simple());
        }
        yes(h, new ItemStack(items.MAKESHIFT_IRON_PICKAXE.get()).getDestroySpeed(Blocks.STONE.defaultBlockState())
                        == new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(Blocks.STONE.defaultBlockState()),
                "简易铁镐采掘速度与铁镐一致");
        yes(h, new ItemStack(items.MAKESHIFT_IRON_SWORD.get()).getDestroySpeed(Blocks.COBWEB.defaultBlockState()) == 15.0F,
                "简易铁剑保留剑类蛛网速度");

        // Anvil repair must keep the independent simple item, never replace it with a real iron tool.
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.create(h);
            ItemStack damaged = new ItemStack(items.MAKESHIFT_IRON_PICKAXE.get());
            damaged.setDamageValue(damaged.getMaxDamage() - 1);
            AnvilMenu menu = new AnvilMenu(5, p.getInventory(), ContainerLevelAccess.NULL);
            menu.getSlot(0).set(damaged);
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            menu.createResult();
            ItemStack result = menu.getSlot(2).getItem();
            yes(h, result.is(items.MAKESHIFT_IRON_PICKAXE.get()) && !result.is(Items.IRON_PICKAXE)
                            && result.getDamageValue() < damaged.getDamageValue() && result.getDamageValue() <= result.getMaxDamage(),
                    "铁砧修复仍是简易铁镐且不突破自身耐久/不升级为正规铁镐");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void makeshiftToolsRequireWoodStoneAndIronHistoryInAnyOrder(GameTestHelper h)
    {
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        List<ResourceLocation> simple = List.of(
                wl("makeshift_iron_pickaxe"), wl("makeshift_iron_axe"), wl("makeshift_iron_shovel"),
                wl("makeshift_iron_hoe"), wl("makeshift_iron_sword"));
        try (var scope = new ProcessingTestPlayers(h))
        {
            for (int[] order : orders)
            {
                ServerPlayer p = ProcessingTestPlayers.create(h);
                boolean wood = false, stone = false, iron = false;
                for (int step : order)
                {
                    if (step == 0) { acquire(p, Items.OAK_LOG); wood = true; }
                    else if (step == 1) { acquire(p, Items.COBBLESTONE); stone = true; }
                    else { acquire(p, Items.IRON_INGOT); iron = true; }
                    boolean all = wood && stone && iron;
                    for (ResourceLocation tool : simple)
                        yes(h, unlocked(p, tool) == all, "三条件顺序无关且只在最后一项满足时补发：" + tool.getPath());
                    if (iron && !(wood && stone))
                        yes(h, !unlocked(p, id("iron_pickaxe")), "仅获得铁锭不会提前开放正规铁工具");
                }
                yes(h, !unlocked(p, id("iron_pickaxe")) && !unlocked(p, id("iron_sword")),
                        "简易工具解锁不连带正规铁工具");
            }
            ServerPlayer ironOnly = ProcessingTestPlayers.create(h);
            acquire(ironOnly, Items.IRON_INGOT);
            for (ResourceLocation tool : simple) yes(h, !unlocked(ironOnly, tool), "仅铁锭不开放简易工具");
            for (ResourceLocation tool : TOOL_POOL) yes(h, !unlocked(ironOnly, tool), "仅铁锭不开放正规工具");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sameMaterialsChooseOnlyTheUnlockedVersion(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.create(h);
            acquire(p, Items.OAK_LOG, Items.COBBLESTONE, Items.IRON_INGOT);
            CraftingMenu menu = table(h, p);
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
            menu.getSlot(3).set(new ItemStack(Items.IRON_INGOT));
            menu.getSlot(5).set(new ItemStack(Items.STICK));
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            var view = crafting_choice.open(p, menu.containerId);
            yes(h, view.candidates().size() == 1 && view.candidates().get(0).id().equals(wl("makeshift_iron_pickaxe")),
                    "仅掌握简易版时同材料只出现简易铁镐");
            yes(h, browser_transfer.apply(p, menu.containerId, id("iron_pickaxe"), browser_transfer.fingerprint(
                    h.getLevel().getRecipeManager().byKey(id("iron_pickaxe")).orElseThrow()), false) != transfer_reason.OK,
                    "浏览器填充不能绕过未掌握的正规铁镐");
            yes(h, recipe_unlocks.unlock(p, id("iron_pickaxe")), "掌握正规铁镐配方");
            view = crafting_choice.open(p, menu.containerId);
            yes(h, view.candidates().size() == 2 && view.candidates().stream().anyMatch(c -> c.id().equals(wl("makeshift_iron_pickaxe")))
                            && view.candidates().stream().anyMatch(c -> c.id().equals(id("iron_pickaxe"))),
                    "掌握正规版后两种稳定ID均可切换");
            yes(h, crafting_choice.select(p, view.menu(), view.session(), view.version(), 1, id("iron_pickaxe")) == transfer_reason.OK,
                    "可显式选择正规铁镐");
            yes(h, menu.getSlot(0).getItem().is(Items.IRON_PICKAXE), "切换后结果槽显示正规铁镐");
            view = crafting_choice.open(p, menu.containerId);
            yes(h, crafting_choice.select(p, view.menu(), view.session(), view.version(), 2, wl("makeshift_iron_pickaxe")) == transfer_reason.OK
                            && menu.getSlot(0).getItem().is(items.MAKESHIFT_IRON_PICKAXE.get()),
                    "切回简易铁镐仍保留自有配方");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedBooksGrantExactRecipesAndOperationsOnly(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer shield = ProcessingTestPlayers.create(h);
            yes(h, !unlocked(shield, id("shield")), "初始未掌握盾牌");
            ItemStack shieldBook = read(shield, items.SHIELD_RECIPE_BOOK.get());
            yes(h, shieldBook.getCount() == 0 && unlocked(shield, id("shield")), "盾牌书生存首次学习消耗一本并授予盾牌");
            ItemStack shieldAgain = read(shield, items.SHIELD_RECIPE_BOOK.get());
            yes(h, shieldAgain.getCount() == 1 && unlocked(shield, id("shield")), "已掌握后不消耗");

            ServerPlayer stone = ProcessingTestPlayers.create(h);
            ItemStack stoneBook = read(stone, items.STONECUTTER_NOTES.get());
            yes(h, stoneBook.getCount() == 0 && unlocked(stone, id("stonecutter"))
                            && unlocked(stone, recipe_menu_gate.operation("stonecutter")),
                    "切石机书授予设备制作与操作权限");
            ResourceLocation stoneRecipe = stonecuttingRecipe(h, Items.STONE);
            yes(h, !unlocked(stone, stoneRecipe), "切石机书不授予所有石材切石配方");

            ServerPlayer anvil = ProcessingTestPlayers.create(h);
            ItemStack anvilBook = read(anvil, items.BLACKSMITH_FORGING_NOTES.get());
            yes(h, anvilBook.getCount() == 0 && unlocked(anvil, id("anvil"))
                            && unlocked(anvil, recipe_menu_gate.operation("anvil")),
                    "锻造书授予铁砧制作与操作权限");

            ServerPlayer equipment = ProcessingTestPlayers.create(h);
            read(equipment, items.EQUIPMENT_UPGRADE_NOTES.get());
            yes(h, unlocked(equipment, id("smithing_table")), "装备改造书授予锻造台制作");
            for (Recipe<?> recipe : h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.SMITHING))
                yes(h, !unlocked(equipment, recipe.getId()), "装备改造书不授予具体锻造升级");

            ServerPlayer transport = ProcessingTestPlayers.create(h);
            read(transport, items.TRANSPORT_NOTES.get());
            yes(h, unlocked(transport, id("rail")) && unlocked(transport, id("minecart")), "运输书授予普通铁轨和矿车");
            for (String other : List.of("detector_rail", "powered_rail", "activator_rail", "hopper_minecart", "tnt_minecart"))
                yes(h, !unlocked(transport, id(other)), "运输书不扩大到其他运输路线：" + other);

            ServerPlayer creative = ProcessingTestPlayers.create(h);
            creative.getAbilities().instabuild = true;
            ItemStack creativeBook = read(creative, items.SHIELD_RECIPE_BOOK.get());
            yes(h, creativeBook.getCount() == 1 && unlocked(creative, id("shield")), "创造阅读成功且不消耗");

            ServerPlayer gold = ProcessingTestPlayers.create(h);
            ItemStack goldBook = read(gold, items.GOLDEN_SWORD_RECIPE_BOOK.get());
            yes(h, goldBook.getCount() == 0 && unlocked(gold, id("golden_sword")), "金剑书原有行为保留");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void randomBooksGrantOneUnlearnedValidTargetAndKeepCreativeCopies(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer tools = ProcessingTestPlayers.create(h);
            Set<ResourceLocation> learned = new HashSet<>();
            for (int i = 0; i < TOOL_POOL.size(); i++)
            {
                ItemStack book = read(tools, items.BLACKSMITH_TOOLS_NOTES.get());
                Set<ResourceLocation> now = known(tools, TOOL_POOL);
                yes(h, book.getCount() == 0 && now.size() == i + 1 && learned.stream().allMatch(now::contains),
                        "工具随机书每次只授予一种尚未掌握的目标");
                learned = now;
            }
            ItemStack exhausted = read(tools, items.BLACKSMITH_TOOLS_NOTES.get());
            yes(h, exhausted.getCount() == 1 && known(tools, TOOL_POOL).size() == 5, "工具全部掌握后不消耗且不重复");

            ServerPlayer armor = ProcessingTestPlayers.create(h);
            Set<ResourceLocation> armorLearned = new HashSet<>();
            for (int i = 0; i < ARMOR_POOL.size(); i++)
            {
                ItemStack book = read(armor, items.BLACKSMITH_ARMOR_NOTES.get());
                Set<ResourceLocation> now = known(armor, ARMOR_POOL);
                yes(h, book.getCount() == 0 && now.size() == i + 1 && armorLearned.stream().allMatch(now::contains),
                        "防具随机书每次只授予一种尚未掌握的目标");
                armorLearned = now;
            }
            yes(h, known(armor, ARMOR_POOL).size() == 4 && known(armor, TOOL_POOL).isEmpty(),
                    "防具池4类且不会串到工具池");

            ServerPlayer creative = ProcessingTestPlayers.create(h);
            creative.getAbilities().instabuild = true;
            ItemStack creativeBook = read(creative, items.BLACKSMITH_TOOLS_NOTES.get());
            yes(h, creativeBook.getCount() == 1 && known(creative, TOOL_POOL).size() == 1,
                    "创造模式随机书保留一本且只授予一种");

            ServerPlayer cooldown = ProcessingTestPlayers.create(h);
            cooldown.getCooldowns().removeCooldown(items.BLACKSMITH_TOOLS_NOTES.get());
            ItemStack first = new ItemStack(items.BLACKSMITH_TOOLS_NOTES.get());
            cooldown.setItemInHand(InteractionHand.MAIN_HAND, first);
            items.BLACKSMITH_TOOLS_NOTES.get().use(cooldown.level(), cooldown, InteractionHand.MAIN_HAND);
            int afterFirst = known(cooldown, TOOL_POOL).size();
            ItemStack immediate = new ItemStack(items.BLACKSMITH_TOOLS_NOTES.get());
            cooldown.setItemInHand(InteractionHand.MAIN_HAND, immediate);
            items.BLACKSMITH_TOOLS_NOTES.get().use(cooldown.level(), cooldown, InteractionHand.MAIN_HAND);
            yes(h, immediate.getCount() == 1 && known(cooldown, TOOL_POOL).size() == afterFirst,
                    "使用冷却阻止主副手式重复请求");
            cooldown.getCooldowns().removeCooldown(items.BLACKSMITH_TOOLS_NOTES.get());
            ItemStack later = new ItemStack(items.BLACKSMITH_TOOLS_NOTES.get());
            cooldown.setItemInHand(InteractionHand.MAIN_HAND, later);
            items.BLACKSMITH_TOOLS_NOTES.get().use(cooldown.level(), cooldown, InteractionHand.MAIN_HAND);
            yes(h, later.getCount() == 0 && known(cooldown, TOOL_POOL).size() == afterFirst + 1,
                    "冷却结束后仍可正常继续随机学习");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allSevenCopyRecipesReturnOneTemplateAndDoNotReadTheBook(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.create(h);
            for (CopyDef def : COPY_BOOKS)
            {
                Item book = ForgeRegistries.ITEMS.getValue(def.bookId());
                Recipe<?> raw = h.getLevel().getRecipeManager().byKey(def.recipeId()).orElse(null);
                yes(h, raw instanceof template_copy_recipe && raw.getResultItem(h.getLevel().registryAccess()).is(book),
                        "抄录配方存在且产出同种书：" + def.recipe());
                yes(h, raw.getIngredients().size() == 4
                                && ingredientMatches(raw, book) && ingredientMatches(raw, Items.BOOK)
                                && ingredientMatches(raw, Items.INK_SAC) && ingredientMatches(raw, Items.FEATHER),
                        "抄录配料为原书+书+墨囊+羽毛：" + def.recipe());
                template_copy_recipe synced = networkCopy(raw);
                yes(h, synced.template().test(new ItemStack(book))
                                && synced.getResultItem(h.getLevel().registryAccess()).is(book),
                        "抄录配方网络同步后仍保留模板与产物：" + def.recipe());
                yes(h, !unlocked(p, def.recipeId()), "未持有模板前抄录配方不提前开放");
                recipe_unlocks.acquired(p, new ItemStack(book));
                yes(h, unlocked(p, def.recipeId()), "持有模板即可查看并执行对应抄录：" + def.recipe());
                yes(h, new ItemStack(book).getCraftingRemainingItem().isEmpty(), "书物品没有全局错误返还行为");
            }
            yes(h, !unlocked(p, id("shield")), "获得盾牌模板不会自动读取书中知识");

            ItemStack template = new ItemStack(items.SHIELD_RECIPE_BOOK.get());
            template.setHoverName(net.minecraft.network.chat.Component.literal("带名字的模板"));
            template.getOrCreateTag().putString("white_lily_test", "keep");
            CraftingMenu menu = table(h, p);
            ItemStack templateInput = template.copy();
            menu.getSlot(1).set(templateInput);
            menu.getSlot(2).set(new ItemStack(Items.BOOK));
            menu.getSlot(3).set(new ItemStack(Items.INK_SAC));
            menu.getSlot(4).set(new ItemStack(Items.FEATHER));
            var view = crafting_choice.open(p, menu.containerId);
            yes(h, view.candidates().stream().anyMatch(c -> c.id().equals(wl("shield_recipe_book_copy"))),
                    "持有模板时抄录进入真实候选");
            var display = browser_recipe_display.build(
                    h.getLevel().getRecipeManager().byKey(wl("shield_recipe_book_copy")).orElseThrow(), h.getLevel().registryAccess());
            yes(h, display.slots().stream().anyMatch(slot -> slot.output()
                            && slot.ingredient().test(template)), "浏览器展示原书模板返还物");
            menu.clicked(0, 0, ClickType.PICKUP, p);
            ItemStack crafted = menu.getCarried().copy();
            yes(h, crafted.is(items.SHIELD_RECIPE_BOOK.get()) && crafted.getCount() == 1 && crafted.getTag() == null,
                    "普通取出得到无额外状态的同种知识书");
            yes(h, ItemStack.matches(template, menu.getSlot(1).getItem()) && menu.getSlot(1).getItem().getCount() == 1,
                    "原书模板名称与NBT原样返还且只返还一份");
            yes(h, menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty() && menu.getSlot(4).getItem().isEmpty(),
                    "普通书、墨囊与羽毛正常消耗");
            yes(h, !unlocked(p, id("shield")), "抄录不触发阅读解锁");
            menu.setCarried(ItemStack.EMPTY);

            // Shift/batch transcription: template persists once; the three consumables are consumed.
            menu.getSlot(1).set(template.copy());
            menu.getSlot(2).set(new ItemStack(Items.BOOK, 3));
            menu.getSlot(3).set(new ItemStack(Items.INK_SAC, 3));
            menu.getSlot(4).set(new ItemStack(Items.FEATHER, 3));
            menu.clicked(0, 0, ClickType.QUICK_MOVE, p);
            yes(h, p.getInventory().countItem(items.SHIELD_RECIPE_BOOK.get()) == 3
                            && menu.getSlot(1).getItem().getCount() == 1
                            && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty() && menu.getSlot(4).getItem().isEmpty(),
                    "Shift批量抄录产出3本、消耗3套材料且模板不复制");

            p.getInventory().clearContent();
            for (int slot = 0; slot < 36; slot++) p.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            menu.getSlot(1).set(template.copy());
            menu.getSlot(2).set(new ItemStack(Items.BOOK));
            menu.getSlot(3).set(new ItemStack(Items.INK_SAC));
            menu.getSlot(4).set(new ItemStack(Items.FEATHER));
            menu.quickMoveStack(p, 0);
            yes(h, menu.getSlot(1).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1
                            && menu.getSlot(3).getItem().getCount() == 1 && menu.getSlot(4).getItem().getCount() == 1,
                    "满背包时Shift不吞模板或材料");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ironDailyRoutesAreOrderIndependentAndDoNotOvergrant(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer iron = ProcessingTestPlayers.create(h);
            acquire(iron, Items.IRON_INGOT);
            for (String conversion : List.of("iron_nugget", "iron_ingot_from_nuggets", "iron_block", "iron_ingot_from_iron_block"))
                yes(h, unlocked(iron, id(conversion)), "铁锭开放四向正常压缩/拆分：" + conversion);
            for (String blocked : List.of("iron_pickaxe", "iron_sword", "shears", "flint_and_steel", "bucket",
                    "iron_door", "iron_trapdoor", "lantern", "compass", "hopper", "piston", "powered_rail"))
                yes(h, !unlocked(iron, id(blocked)), "铁锭不提前开放其他路线：" + blocked);

            requireBothOrders(h, Items.IRON_INGOT, Items.STRING, id("shears"));
            requireBothOrders(h, Items.IRON_INGOT, Items.FLINT, id("flint_and_steel"));
            requireBothOrders(h, Items.IRON_INGOT, Items.BOWL, id("bucket"));
            requireBothOrders(h, Items.IRON_INGOT, Items.TORCH, id("lantern"));
            requireBothOrders(h, Items.IRON_INGOT, Items.REDSTONE, id("compass"));

            ServerPlayer bucket = ProcessingTestPlayers.create(h);
            acquire(bucket, Items.IRON_INGOT, Items.BOWL);
            yes(h, unlocked(bucket, id("bucket")), "铁锭+碗开放桶");
            acquire(bucket, Items.BUCKET);
            yes(h, unlocked(bucket, id("cauldron")), "获得桶后开放炼药锅");

            ServerPlayer wood = ProcessingTestPlayers.create(h);
            acquire(wood, Items.IRON_INGOT);
            yes(h, !unlocked(wood, id("iron_bars")) && !unlocked(wood, id("chain")), "仅铁锭不开放铁栏杆/锁链");
            acquire(wood, Items.OAK_PLANKS);
            yes(h, unlocked(wood, id("iron_bars")) && unlocked(wood, id("chain")), "基础木作+铁锭开放铁栏杆和锁链");

            ServerPlayer door = ProcessingTestPlayers.create(h);
            acquire(door, Items.IRON_INGOT);
            yes(h, recipe_unlocks.unlock(door, id("oak_door")), "先掌握一种木门");
            yes(h, unlocked(door, id("iron_door")), "任意一种已掌握木门即可开放铁门");
            ServerPlayer trapdoor = ProcessingTestPlayers.create(h);
            acquire(trapdoor, Items.IRON_INGOT);
            yes(h, recipe_unlocks.unlock(trapdoor, id("oak_trapdoor")), "先掌握一种木活板门");
            yes(h, unlocked(trapdoor, id("iron_trapdoor")), "任意一种已掌握木活板门即可开放铁活板门");

            ServerPlayer soul = ProcessingTestPlayers.create(h);
            acquire(soul, Items.IRON_INGOT, Items.TORCH);
            yes(h, unlocked(soul, id("lantern")), "铁锭+火把开放普通灯笼");
            acquire(soul, Items.SOUL_TORCH);
            yes(h, unlocked(soul, id("soul_lantern")), "普通灯笼+灵魂火把开放灵魂灯笼");

            ServerPlayer cart = ProcessingTestPlayers.create(h);
            yes(h, recipe_unlocks.unlock(cart, id("minecart")), "先掌握普通矿车");
            acquire(cart, Items.CHEST);
            yes(h, unlocked(cart, id("chest_minecart")), "普通矿车+箱子开放运输矿车");
            for (String blocked : List.of("hopper_minecart", "tnt_minecart", "powered_rail", "detector_rail", "activator_rail"))
                yes(h, !unlocked(cart, id(blocked)), "箱子矿车不扩大到其他运输路线：" + blocked);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stonecutterAndAnvilNoLongerRequireUnlocks(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.create(h);
            ResourceLocation specific = stonecuttingRecipe(h, Items.STONE);
            yes(h, !unlocked(p, specific), "玩家没有这条切石配方");
            BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
            h.getLevel().setBlockAndUpdate(pos, Blocks.STONECUTTER.defaultBlockState());
            p.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            StonecutterMenu menu = new StonecutterMenu(6, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
            p.containerMenu = menu;
            menu.getSlot(0).set(new ItemStack(Items.STONE));
            int index = menu.getRecipes().indexOf(h.getLevel().getRecipeManager().byKey(specific).orElseThrow());
            yes(h, index >= 0, "切石机加载具体配方");
            menu.clickMenuButton(p, index);
            yes(h, !recipe_unlocks.requiresUnlock(p, specific), "切石配方不在解锁范围内");
            yes(h, recipe_menu_gate.canTake(menu, menu.getSlot(1), p), "切石机不再要求任何解锁");
            ItemStack result = menu.getSlot(1).getItem().copy();
            yes(h, !result.isEmpty(), "切石机照常产出结果");
            yes(h, !menu.quickMoveStack(p, 1).isEmpty() && p.getInventory().countItem(result.getItem()) > 0,
                    "未解锁也能直接取走切石结果");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilIsVanillaAndBookStillGrantsItsCraftingRecipe(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.create(h);
            p.experienceLevel = 50;
            yes(h, !unlocked(p, id("anvil")), "尚未掌握铁砧的制作配方");
            AnvilMenu menu = new AnvilMenu(7, p.getInventory(), ContainerLevelAccess.NULL);
            p.containerMenu = menu;
            ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
            damaged.setDamageValue(100);
            menu.getSlot(0).set(damaged);
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            menu.createResult();
            ItemStack repaired = menu.getSlot(2).getItem();
            yes(h, !repaired.isEmpty() && repaired.getDamageValue() < 100, "铁砧照常产出修复结果");
            yes(h, recipe_menu_gate.canTake(menu, menu.getSlot(2), p), "铁砧结果不再需要解锁");
            menu.clicked(2, 0, ClickType.PICKUP, p);
            yes(h, menu.getCarried().is(Items.IRON_PICKAXE) && menu.getSlot(0).getItem().isEmpty(), "铁砧结果可以正常取走");
            menu.setCarried(ItemStack.EMPTY);
            read(p, items.BLACKSMITH_FORGING_NOTES.get());
            yes(h, unlocked(p, id("anvil")), "锻造书仍授予铁砧的制作配方");
            for (String fake : List.of("chipped_anvil", "damaged_anvil"))
                yes(h, h.getLevel().getRecipeManager().byKey(id(fake)).isEmpty(), "不存在虚构铁砧制作配方：" + fake);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldSeenHistoryReconcilesNewIronAndBookRoutes(GameTestHelper h)
    {
        try (var scope = new ProcessingTestPlayers(h))
        {
            ServerPlayer p = ProcessingTestPlayers.createWithoutInitialize(h);
            CompoundTag root = p.getPersistentData();
            CompoundTag persisted = root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            CompoundTag data = new CompoundTag();
            net.minecraft.nbt.ListTag seen = new net.minecraft.nbt.ListTag();
            for (String item : List.of("minecraft:iron_ingot", "minecraft:oak_log", "minecraft:cobblestone",
                    "minecraft:string", "white_lily:shield_recipe_book"))
                seen.add(net.minecraft.nbt.StringTag.valueOf(item));
            data.put("seen_items", seen);
            data.putLong("generation", 1L);
            persisted.put(recipe_unlocks.DATA_KEY, data);
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
            recipe_unlocks.initialize(p);
            yes(h, unlocked(p, wl("makeshift_iron_pickaxe")) && unlocked(p, id("shears"))
                            && unlocked(p, wl("shield_recipe_book_copy")),
                    "旧获得历史在配置内容变化后补评估简易工具、日用品与抄录路线");
        }
        h.succeed();
    }

    private static void requireBothOrders(GameTestHelper h, Item a, Item b, ResourceLocation recipe)
    {
        ServerPlayer first = ProcessingTestPlayers.create(h);
        acquire(first, a);
        yes(h, !unlocked(first, recipe), "缺少第二条件不开放：" + recipe.getPath());
        acquire(first, b);
        yes(h, unlocked(first, recipe), "条件A后B可开放：" + recipe.getPath());
        ServerPlayer second = ProcessingTestPlayers.create(h);
        acquire(second, b);
        yes(h, !unlocked(second, recipe), "反向缺少第一条件不开放：" + recipe.getPath());
        acquire(second, a);
        yes(h, unlocked(second, recipe), "条件B后A可开放：" + recipe.getPath());
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static template_copy_recipe networkCopy(Recipe<?> recipe)
    {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try
        {
            RecipeSerializer serializer = recipe.getSerializer();
            serializer.toNetwork(buffer, recipe);
            return (template_copy_recipe) serializer.fromNetwork(recipe.getId(), buffer);
        }
        finally { buffer.release(); }
    }
    private static boolean ingredientMatches(Recipe<?> recipe, Item item)
    {
        return recipe.getIngredients().stream()
                .anyMatch(ingredient -> Arrays.stream(ingredient.getItems()).anyMatch(stack -> stack.is(item)));
    }
    private static ResourceLocation stonecuttingRecipe(GameTestHelper h, Item input)
    {
        for (Recipe<?> recipe : h.getLevel().getRecipeManager().getRecipes())
            if (recipe instanceof StonecutterRecipe && !recipe.getIngredients().isEmpty()
                    && recipe.getIngredients().get(0).test(new ItemStack(input))) return recipe.getId();
        throw new IllegalStateException("No stonecutting recipe for " + input);
    }
    @Nullable private static ResourceLocation stonecuttingRecipeOtherThan(GameTestHelper h, ResourceLocation first)
    {
        for (Recipe<?> recipe : h.getLevel().getRecipeManager().getRecipes())
            if (recipe instanceof StonecutterRecipe && !recipe.getId().equals(first)) return recipe.getId();
        return null;
    }
}
