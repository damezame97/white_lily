package com.damezame97.white_lily.item;

import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilycurio.fate_compass;
import com.damezame97.white_lily.white_lilycurio.flesh_ring;
import com.damezame97.white_lily.white_lilycurio.improve_body;
import com.damezame97.white_lily.white_lilycurio.invalid_placeholder;
import com.damezame97.white_lily.white_lilycurio.stored_suffering;
import com.damezame97.white_lily.white_lilyfood.magic_apple;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的物品注册表（所有物品的唯一注册入口）。
 *
 * <p>所有食物、材料、工具和方块物品均复用本入口；公开展示由创造栏的明确清单决定。</p>
 * <ol>
 *     <li>{@link #MAGIC_APPLE}：「魔力苹果」，真正的食物类物品，类放在
 *         {@code com.damezame97.white_lily.white_lilyfood} 包里；</li>
 *     <li>{@link #TAB_ICON}：「歧途“珍馐”」创造模式物品栏的图标载体，玩家看不到它。</li>
 * </ol>
 *
 * <p>另有四个「空饰品」（{@link #FLESH_RING}、{@link #IMPROVE_BODY}、
 * {@link #INVALID_PLACEHOLDER}、{@link #FATE_COMPASS}），类都放在
 * {@code com.damezame97.white_lily.white_lilycurio} 包里：它们只实现 Curios 的
 * {@code ICurioItem} 并进 {@code curios:improvement} 标签，因此能被“改良”饰品栏接受；
 * 它们<b>不进任何创造模式物品栏页</b>，也不进原版“搜索”页（搜索页是从其它页汇总的），
 * 玩家只能通过首次进世界时那套难度发放拿到它们。</p>
 *
 * <p><b>为什么需要 TAB_ICON</b>：Minecraft 1.20.1 的 {@code CreativeModeTab.Builder#icon} 只接受
 * {@code Supplier<ItemStack>}，没有任何「直接塞一张贴图」的入口。所以想让自定义贴图出现在
 * 物品栏标签上，就必须有一个真实物品来承载它。</p>
 *
 * <p><b>它是隐藏的</b>：它不会被任何 {@code displayItems} 输出，也不进搜索页，
 * 玩家在游戏里看不到这件物品本身，只会看到它出现在标签图标位置。</p>
 */
public final class items
{
    /** 物品注册表，由主类 {@link white_lily} 挂到模组事件总线。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, white_lily.MODID);

    public static final RegistryObject<Item> WHITE_SLEEPING_BAG = ITEMS.register("white_sleeping_bag", () ->
            new net.minecraft.world.item.BedItem(com.damezame97.white_lily.block.medical_blocks.WHITE_SLEEPING_BAG.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> HEALTH_CAPACITY_PLUS_ONE = ITEMS.register("health_capacity_plus_one", () -> new capacity_test_item(true, 1));
    public static final RegistryObject<Item> HEALTH_CAPACITY_PLUS_TEN = ITEMS.register("health_capacity_plus_ten", () -> new capacity_test_item(true, 10));
    public static final RegistryObject<Item> HEALTH_CAPACITY_MINUS_ONE = ITEMS.register("health_capacity_minus_one", () -> new capacity_test_item(true, -1));
    public static final RegistryObject<Item> HEALTH_CAPACITY_MINUS_TEN = ITEMS.register("health_capacity_minus_ten", () -> new capacity_test_item(true, -10));
    public static final RegistryObject<Item> FOOD_CAPACITY_PLUS_ONE = ITEMS.register("food_capacity_plus_one", () -> new capacity_test_item(false, 1));
    public static final RegistryObject<Item> FOOD_CAPACITY_PLUS_TEN = ITEMS.register("food_capacity_plus_ten", () -> new capacity_test_item(false, 10));
    public static final RegistryObject<Item> FOOD_CAPACITY_MINUS_ONE = ITEMS.register("food_capacity_minus_one", () -> new capacity_test_item(false, -1));
    public static final RegistryObject<Item> FOOD_CAPACITY_MINUS_TEN = ITEMS.register("food_capacity_minus_ten", () -> new capacity_test_item(false, -10));
    public static final RegistryObject<Item> CAPACITY_DETECTOR = ITEMS.register("capacity_detector", () -> new capacity_test_item(true, 0));
    public static final RegistryObject<Item> HEALTH_RESTORE_TEST = ITEMS.register("health_restore_test",
            () -> new state_test_item(state_test_item.Action.HEALTH));
    public static final RegistryObject<Item> FOOD_RESTORE_TEST = ITEMS.register("food_restore_test",
            () -> new state_test_item(state_test_item.Action.FOOD));
    public static final RegistryObject<Item> INSTANT_DAMAGE_TEST = ITEMS.register("instant_damage_test",
            () -> new state_test_item(state_test_item.Action.DAMAGE));

    /**
     * 「歧途“珍馐”」物品栏的图标物品。
     *
     * <p>贴图：{@code assets/white_lily/textures/item/tab_icon.png}（即 white_lily_food.png）；
     * 模型：{@code assets/white_lily/models/item/tab_icon.json}。</p>
     */
    public static final RegistryObject<Item> TAB_ICON =
            ITEMS.register("tab_icon", () -> new Item(new Item.Properties()));

    /**
     * 隐藏的成就图标：只为「造血障碍」这条进度提供一个能用贴图的 item 壳子。
     *
     * <p>贴图直接用 {@code assets/white_lily/textures/item/delayed_healing_icon.png}（即效果图的副本），
     * 模型 {@code models/item/delayed_healing_icon.json}。它不进任何创造模式栏。</p>
     */
    public static final RegistryObject<Item> DELAYED_HEALING_ICON =
            ITEMS.register("delayed_healing_icon", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> GOLDEN_SWORD_RECIPE_BOOK =
            ITEMS.register("golden_sword_recipe_book", () -> new recipe_unlock_book(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "golden_sword")));

    /** Five independent makeshift iron tools; each reads its own real iron counterpart's durability. */
    public static final RegistryObject<Item> MAKESHIFT_IRON_PICKAXE =
            ITEMS.register(makeshift_iron_pickaxe.NAME, () -> new makeshift_iron_pickaxe(net.minecraft.world.item.Items.IRON_PICKAXE));
    public static final RegistryObject<Item> MAKESHIFT_IRON_AXE =
            ITEMS.register(makeshift_iron_axe.NAME, () -> new makeshift_iron_axe(net.minecraft.world.item.Items.IRON_AXE));
    public static final RegistryObject<Item> MAKESHIFT_IRON_SHOVEL =
            ITEMS.register(makeshift_iron_shovel.NAME, () -> new makeshift_iron_shovel(net.minecraft.world.item.Items.IRON_SHOVEL));
    public static final RegistryObject<Item> MAKESHIFT_IRON_HOE =
            ITEMS.register(makeshift_iron_hoe.NAME, () -> new makeshift_iron_hoe(net.minecraft.world.item.Items.IRON_HOE));
    public static final RegistryObject<Item> MAKESHIFT_IRON_SWORD =
            ITEMS.register(makeshift_iron_sword.NAME, () -> new makeshift_iron_sword(net.minecraft.world.item.Items.IRON_SWORD));

    /** Iron-stage knowledge books. Random books store only their server-side pool. */
    public static final RegistryObject<Item> BLACKSMITH_TOOLS_NOTES =
            ITEMS.register("blacksmith_tools_notes", () -> recipe_unlock_book.random(
                    "tooltip.white_lily.recipe_book.random.tools",
                    mc("iron_pickaxe"), mc("iron_axe"), mc("iron_shovel"), mc("iron_hoe"), mc("iron_sword")));
    public static final RegistryObject<Item> BLACKSMITH_ARMOR_NOTES =
            ITEMS.register("blacksmith_armor_notes", () -> recipe_unlock_book.random(
                    "tooltip.white_lily.recipe_book.random.armor",
                    mc("iron_helmet"), mc("iron_chestplate"), mc("iron_leggings"), mc("iron_boots")));
    public static final RegistryObject<Item> SHIELD_RECIPE_BOOK =
            ITEMS.register("shield_recipe_book", () -> recipe_unlock_book.copyable(mc("shield")));
    public static final RegistryObject<Item> STONECUTTER_NOTES =
            ITEMS.register("stonecutter_notes", () -> recipe_unlock_book.copyable(mc("stonecutter"), operation("stonecutter")));
    public static final RegistryObject<Item> BLACKSMITH_FORGING_NOTES =
            ITEMS.register("blacksmith_forging_notes", () -> recipe_unlock_book.copyable(mc("anvil"), operation("anvil")));
    public static final RegistryObject<Item> EQUIPMENT_UPGRADE_NOTES =
            ITEMS.register("equipment_upgrade_notes", () -> recipe_unlock_book.copyable(mc("smithing_table")));
    public static final RegistryObject<Item> TRANSPORT_NOTES =
            ITEMS.register("transport_notes", () -> recipe_unlock_book.copyable(mc("rail"), mc("minecart")));

    /**
     * 「魔力苹果」——见 {@link magic_apple}。
     *
     * <p>贴图 {@code assets/white_lily/textures/item/magic_apple.png}（即 Magic_Apple.png），
     * 模型 {@code assets/white_lily/models/item/magic_apple.json}。</p>
     */
    public static final RegistryObject<Item> MAGIC_APPLE =
            ITEMS.register(magic_apple.NAME, magic_apple::new);

    /** 低质量药剂：三维瓶身，饮用恢复魔力并可能附加副作用，返还玻璃瓶。 */
    public static final RegistryObject<Item> LOW_QUALITY_POTION =
            ITEMS.register(low_quality_potion.NAME, low_quality_potion::new);

    /** 可浸水显字的普通材料。 */
    public static final RegistryObject<Item> BLANK_PRESCRIPTION =
            ITEMS.register("blank_prescription", prescription_item::new);

    /** 后续药剂制作材料，暂不附加药效。 */
    public static final RegistryObject<Item> REVEALED_PRESCRIPTION =
            ITEMS.register("revealed_prescription", prescription_item::new);

    public static final RegistryObject<Item> STORED_SUFFERING =
            ITEMS.register(stored_suffering.NAME, stored_suffering::new);

    /** 基础制作材料，按 Shift 查看简介。 */
    // 以下基础材料只保留物品本身，不再提供氛围简介或无意义的 Shift 提示。
    public static final RegistryObject<Item> COARSE_CLOTH =
            ITEMS.register("coarse_cloth", basic_material_item::noDetails);
    public static final RegistryObject<Item> THIN_CLOTH_STRIP =
            ITEMS.register("thin_cloth_strip", basic_material_item::noDetails);
    public static final RegistryObject<Item> CORK_STOPPER =
            ITEMS.register("cork_stopper", basic_material_item::noDetails);
    public static final RegistryObject<Item> BEESWAX_BLOCK =
            ITEMS.register("beeswax_block", basic_material_item::noDetails);
    public static final RegistryObject<Item> CHARCOAL_POWDER =
            ITEMS.register("charcoal_powder", basic_material_item::noDetails);
    public static final RegistryObject<Item> BLANK_LABEL =
            ITEMS.register("blank_label", basic_material_item::noDetails);

    public static final RegistryObject<Item> HEMP_SEEDS = ITEMS.register("hemp_seeds", hemp_seeds::new);
    public static final RegistryObject<Item> HEMP_STALK = ITEMS.register("hemp_stalk", basic_material_item::new);

    // 以下加工材料只保留物品本身；研钵与布包仍保留各自的功能说明。
    public static final RegistryObject<Item> PLANT_FIBER = ITEMS.register("plant_fiber", basic_material_item::noDetails);
    public static final RegistryObject<Item> WOODEN_TRAY = ITEMS.register("wooden_tray", basic_material_item::noDetails);
    public static final RegistryObject<Item> STONE_MORTAR = ITEMS.register("stone_mortar", () ->
            new basic_material_item(new Item.Properties().durability(128), false, 2));
    public static final RegistryObject<Item> CLAY_BOWL = ITEMS.register("clay_bowl", basic_material_item::noDetails);
    public static final RegistryObject<Item> GLASS_TUBE = ITEMS.register("glass_tube", basic_material_item::noDetails);
    public static final RegistryObject<Item> FILTER_PAPER = ITEMS.register("filter_paper", basic_material_item::noDetails);
    public static final RegistryObject<Item> DESICCANT_PACKET = ITEMS.register("desiccant_packet", basic_material_item::noDetails);
    public static final RegistryObject<Item> HERBAL_RESIDUE = ITEMS.register("herbal_residue", basic_material_item::noDetails);
    public static final RegistryObject<Item> HERBAL_POUCH = ITEMS.register("herbal_pouch", () ->
            new basic_material_item(new Item.Properties().stacksTo(64), false, 1));
    public static final RegistryObject<Item> INVESTIGATION_HANDBOOK = ITEMS.register("investigation_handbook", investigation_handbook::new);
    public static final RegistryObject<Item> SIMPLE_PROCESSING_TABLE = ITEMS.register("simple_processing_table", () ->
            new processing_table_item(com.damezame97.white_lily.block.medical_blocks.SIMPLE_PROCESSING_TABLE.get()));

    public static final RegistryObject<Item> SIMPLE_BANDAGE = ITEMS.register("simple_bandage", () -> new medical_item(medical_actions.Kind.SIMPLE));
    public static final RegistryObject<Item> CLEAN_FINE_BANDAGE = ITEMS.register("clean_fine_bandage", () -> new medical_item(medical_actions.Kind.FINE));
    public static final RegistryObject<Item> BITTERLEAF = ITEMS.register("bitterleaf", () -> new medical_herb_item(
            com.damezame97.white_lily.block.medical_blocks.BITTERLEAF.get(), medical_actions.Kind.BITTERLEAF));
    public static final RegistryObject<Item> YARROW = ITEMS.register("yarrow", () -> new medical_herb_item(
            com.damezame97.white_lily.block.medical_blocks.YARROW.get(), medical_actions.Kind.YARROW));
    public static final RegistryObject<Item> CALMING_FLOWER = ITEMS.register("calming_flower", () -> new medical_herb_item(
            com.damezame97.white_lily.block.medical_blocks.CALMING_FLOWER.get(), medical_actions.Kind.FLOWER));
    public static final RegistryObject<Item> BITTERLEAF_PASTE = ITEMS.register("bitterleaf_paste", () -> new medical_item(medical_actions.Kind.PASTE));
    public static final RegistryObject<Item> YARROW_DRESSING = ITEMS.register("yarrow_dressing", () -> new medical_item(medical_actions.Kind.DRESSING));
    public static final RegistryObject<Item> CALMING_FLOWER_TEA = ITEMS.register("calming_flower_tea", () -> new medical_item(medical_actions.Kind.TEA));
    public static final RegistryObject<Item> POLLUTION_CHIP_SMALL = test("pollution_chip_small", 10, net.minecraft.ChatFormatting.WHITE);
    public static final RegistryObject<Item> POLLUTION_CHIP_MEDIUM = test("pollution_chip_medium", 30, net.minecraft.ChatFormatting.BLUE);
    public static final RegistryObject<Item> POLLUTION_CHIP_LARGE = test("pollution_chip_large", 100, net.minecraft.ChatFormatting.RED);
    public static final RegistryObject<Item> CLEANSING_CHIP_SMALL = test("cleansing_chip_small", -10, net.minecraft.ChatFormatting.WHITE);
    public static final RegistryObject<Item> CLEANSING_CHIP_MEDIUM = test("cleansing_chip_medium", -30, net.minecraft.ChatFormatting.BLUE);
    public static final RegistryObject<Item> CLEANSING_CHIP_LARGE = test("cleansing_chip_large", -100, net.minecraft.ChatFormatting.RED);
    public static final RegistryObject<Item> POLLUTION_DETECTOR = test("pollution_detector", 0, net.minecraft.ChatFormatting.WHITE);

    private static RegistryObject<Item> test(String name, int delta, net.minecraft.ChatFormatting color)
    { return ITEMS.register(name, () -> new pollution_test_item(delta, color)); }

    /**
     * 空饰品「血肉之环」——见 {@link flesh_ring}。
     *
     * <p>自身没有任何效果，只用于打通 Curios 流程与难度发放。</p>
     */
    public static final RegistryObject<Item> FLESH_RING =
            ITEMS.register(flesh_ring.NAME, flesh_ring::new);

    /**
     * 空饰品「改良之躯」——见 {@link improve_body}。
     */
    public static final RegistryObject<Item> IMPROVE_BODY =
            ITEMS.register(improve_body.NAME, improve_body::new);

    /**
     * 空饰品「无效占位符」——见 {@link invalid_placeholder}。
     *
     * <p>四种难度都会装备它（和平难度只有它）。</p>
     */
    public static final RegistryObject<Item> INVALID_PLACEHOLDER =
            ITEMS.register(invalid_placeholder.NAME, invalid_placeholder::new);

    /**
     * 空饰品「命运罗盘」——见 {@link fate_compass}。
     */
    public static final RegistryObject<Item> FATE_COMPASS =
            ITEMS.register(fate_compass.NAME, fate_compass::new);

    private static net.minecraft.resources.ResourceLocation mc(String path)
    { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", path); }

    private static net.minecraft.resources.ResourceLocation operation(String name)
    { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(white_lily.MODID, "operation/" + name); }

    private items()
    {
    }
}
