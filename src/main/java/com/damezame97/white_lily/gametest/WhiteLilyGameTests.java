package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.item.creative_tabs;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.procedures.damage_mitigation_procedures;
import com.damezame97.white_lily.procedures.archery_procedures;
import com.damezame97.white_lily.procedures.rifle_procedures;
import com.damezame97.white_lily.procedures.spell_damage_procedures;
import com.damezame97.white_lily.procedures.spell_power_procedures;
import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilybuff.mob_effects;
import com.damezame97.white_lily.white_lilydamage.abnormal_mana;
import com.damezame97.white_lily.white_lilydamage.depletion;
import com.damezame97.white_lily.white_lilydamage.nightmare;
import com.damezame97.white_lily.white_lilydamage.shadow_damage;
import com.damezame97.white_lily.white_lilycurio.curio_events;
import com.damezame97.white_lily.white_lilycurio.curio_slots;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.capability.progressive_tense;
import com.damezame97.white_lily.capability.hunger_state;
import com.damezame97.white_lily.debugging.debug_file;
import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.gamerule.happy_mode_food;
import com.damezame97.white_lily.gamerule.happy_mode_hunger;
import com.damezame97.white_lily.gamerule.hunger_activity;
import com.damezame97.white_lily.white_lilycurio.invalid_placeholder;
import com.damezame97.white_lily.white_lilycurio.improvement_loadout;
import com.damezame97.white_lily.white_lilyfood.magic_apple;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.init.ModDamageTypes;
import io.netty.buffer.Unpooled;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.ISlotType;

/**
 * White Lily 的 GameTest（由 {@code gradlew gameTestServer} 驱动）。
 *
 * <p>分三层验证：</p>
 * <ol>
 *     <li><b>公式层</b>：直接调 {@link damage_mitigation_procedures#applyMitigationAndReduction}，
 *         用 zh_cn.json 描述里的原例逐条核对。</li>
 *     <li><b>事件层</b>：构造真实的 {@link LivingDamageEvent} 并投递到
 *         {@code MinecraftForge.EVENT_BUS}，检查本模组监听器确实被调用、伤害确实被改写。
 *         这一层同时证明了「监听器注册成功」和「属性真的挂到了玩家身上」。</li>
 *     <li><b>资源层</b>：检查创造模式物品栏注册成功、标题语言键在 zh_cn/en_us 里都命中、
 *         图标物品与图标贴图/模型都真的打进了包。</li>
 * </ol>
 *
 * <p>模板用本模组自带的空结构 {@code white_lily:empty}
 * （{@code src/main/resources/data/white_lily/structures/empty.nbt}，3x3x3 全空气）。
 * Forge 47.4.10 没有提供内置空模板（{@code net.minecraftforge.gametest} 下只有
 * GameTestHolder/PrefixGameTestTemplate/ForgeGameTestHooks/GameTestMain），
 * 因此自带一个最省事。</p>
 */
@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
public final class WhiteLilyGameTests {

    /** 空结构模板路径（不能带命名空间，Forge 会自己拼上 {@code @GameTestHolder} 的命名空间）。 */
    private static final String TEMPLATE = "empty";

    /** 浮点比较容差。 */
    private static final double EPSILON = 1.0E-6D;

    /** float 版容差（食物属性、魔力值都是 float）。 */
    private static final float EPSILON_F = 1.0E-4F;

    private WhiteLilyGameTests() {
    }

    // ==================================================================
    // 一、公式层
    // ==================================================================

    /** 与 zh_cn.json 描述里的例子逐条对齐。 */
    @GameTest(template = TEMPLATE)
    public static void formulaFollowsDescription(GameTestHelper helper) {
        expect(helper, 4.0D, damage_mitigation_procedures.applyMitigationAndReduction(1.0D, -3.0D, 0.0D),
                "伤害 1、抵抗 -3 应当变成 4（描述原例）");
        expect(helper, 7.0D, damage_mitigation_procedures.applyMitigationAndReduction(10.0D, 3.0D, 0.0D),
                "伤害 10、抵抗 3 应当变成 7");
        expect(helper, 9.0D, damage_mitigation_procedures.applyMitigationAndReduction(10.0D, 0.0D, 0.1D),
                "伤害 10、减免 0.1 应当变成 9（描述原例）");
        expect(helper, 8.91D, damage_mitigation_procedures.applyMitigationAndReduction(9.0D, 0.0D, 0.012D),
                "减免 0.012 只按 0.01 即 1% 计算（描述原例）");
        expect(helper, 6.3D, damage_mitigation_procedures.applyMitigationAndReduction(10.0D, 3.0D, 0.1D),
                "必须先生效伤害抵抗、后生效伤害减免");
        expect(helper, 10.5D, damage_mitigation_procedures.applyMitigationAndReduction(10.0D, 0.0D, -0.05D),
                "负减免应当按增伤处理");
        expect(helper, 71.0D, damage_mitigation_procedures.applyMitigationAndReduction(100.0D, 0.0D, 0.29D),
                "减免 0.29 应当正好按 29% 计算（0.29*100 的浮点截断陷阱）");
        helper.succeed();
    }

    /** 硬性下限：0.05，且绝不会变成 0 或负数。 */
    @GameTest(template = TEMPLATE)
    public static void formulaNeverGoesBelowHardFloor(GameTestHelper helper) {
        expect(helper, 0.05D, damage_mitigation_procedures.applyMitigationAndReduction(10.0D, 10000.0D, 0.0D),
                "抵抗远大于伤害时应当被压到 0.05");
        expect(helper, 0.05D, damage_mitigation_procedures.applyMitigationAndReduction(10.0D, 0.0D, 1.0D),
                "减免 100% 时应当得到 0.05，而不是 0");
        expect(helper, 0.05D, damage_mitigation_procedures.applyMitigationAndReduction(0.05D, 0.0D, 0.0D),
                "本来就是下限的伤害不应当被继续压低");
        helper.succeed();
    }

    // ==================================================================
    // 二、事件层
    // ==================================================================

    /** 10 点伤害：先减固定值 3，再吃 10% 减免 → 6.3。 */
    @GameTest(template = TEMPLATE)
    public static void eventAppliesMitigationThenReduction(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();

        AttributeInstance mitigation = player.getAttribute(attributes.DAMAGE_MITIGATION.get());
        AttributeInstance reduction = player.getAttribute(attributes.DAMAGE_REDUCTION.get());
        helper.assertTrue(mitigation != null, "玩家身上应当存在 white_lily:damage_mitigation");
        helper.assertTrue(reduction != null, "玩家身上应当存在 white_lily:damage_reduction");

        mitigation.setBaseValue(3.0D);
        reduction.setBaseValue(0.1D);

        expect(helper, 6.3D, postLivingDamage(player, helper.getLevel(), 10.0F),
                "事件层：10 先减 3、再吃 10% 减免");
        helper.succeed();
    }

    /** 事件层同样要守住 0.05 硬性下限。 */
    @GameTest(template = TEMPLATE)
    public static void eventKeepsHardFloor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAttribute(attributes.DAMAGE_MITIGATION.get()).setBaseValue(10000.0D);
        player.getAttribute(attributes.DAMAGE_REDUCTION.get()).setBaseValue(1.0D);

        expect(helper, 0.05D, postLivingDamage(player, helper.getLevel(), 10.0F),
                "事件层也必须是 0.05，不能更少");
        helper.succeed();
    }

    /** 没有这两个属性的实体（本模组只把它们加在 PLAYER 上）必须原样放行。 */
    @GameTest(template = TEMPLATE)
    public static void eventIgnoresEntitiesWithoutTheAttributes(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        helper.assertTrue(zombie.getAttribute(attributes.DAMAGE_MITIGATION.get()) == null,
                "僵尸不应当带有 white_lily:damage_mitigation");

        expect(helper, 10.0D, postLivingDamage(zombie, helper.getLevel(), 10.0F),
                "实体没有这两个属性时监听器必须完全不介入");
        helper.succeed();
    }

    /** 原伤害为 0 时不能凭空造出伤害（哪怕抵抗值很大）。 */
    @GameTest(template = TEMPLATE)
    public static void eventKeepsZeroDamageAtZero(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAttribute(attributes.DAMAGE_MITIGATION.get()).setBaseValue(10000.0D);
        player.getAttribute(attributes.DAMAGE_REDUCTION.get()).setBaseValue(1.0D);

        expect(helper, 0.0D, postLivingDamage(player, helper.getLevel(), 0.0F),
                "原伤害为 0 时结果必须仍是 0");
        helper.succeed();
    }

    // ==================================================================
    // 三、魔力苹果（white_lilyfood 包里的第一件食物）
    // ==================================================================

    /** 注册、食物数值、进食动画、贴图与模型。 */
    @GameTest(template = TEMPLATE)
    public static void magicAppleIsARealFoodItem(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation(white_lily.MODID, magic_apple.NAME);
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "应当注册了 " + id);

        Item apple = items.MAGIC_APPLE.get();
        helper.assertTrue(apple.isEdible(), "魔力苹果必须是食物（FoodProperties 不能为 null）");

        ItemStack stack = new ItemStack(apple);
        FoodProperties food = stack.getFoodProperties(null);
        helper.assertTrue(food != null, "应当有食物属性");
        helper.assertTrue(food.getNutrition() == 0, "饱食度必须是 0，实测 " + food.getNutrition());
        helper.assertTrue(Math.abs(food.getSaturationModifier() - 0.0F) < EPSILON_F,
                "饱和度必须是 0，实测 " + food.getSaturationModifier());

        helper.assertTrue(apple.getUseDuration(stack) == magic_apple.USE_DURATION_TICKS,
                "进食耗时应当是 " + magic_apple.USE_DURATION_TICKS + " 刻，实测 " + apple.getUseDuration(stack));

        // 原版进食动作与音效都靠 UseAnim.EAT 驱动（触发点在 LivingEntity#triggerItemUseEffects）。
        helper.assertTrue(stack.getUseAnimation() == UseAnim.EAT,
                "必须走原版进食动画 UseAnim.EAT，实测 " + stack.getUseAnimation());

        helper.assertTrue(existsResource("/assets/white_lily/textures/item/magic_apple.png"),
                "缺少贴图 assets/white_lily/textures/item/magic_apple.png");
        helper.assertTrue(existsResource("/assets/white_lily/models/item/magic_apple.json"),
                "缺少模型 assets/white_lily/models/item/magic_apple.json");
        helper.succeed();
    }

    /** 物品名必须走语言键，中英各一份，不再硬编码。 */
    @GameTest(template = TEMPLATE)
    public static void magicAppleNameIsTranslated(GameTestHelper helper) {
        String key = items.MAGIC_APPLE.get().getDescriptionId();
        String expectedKey = "item." + white_lily.MODID + "." + magic_apple.NAME;
        helper.assertTrue(expectedKey.equals(key), "描述键应当是 " + expectedKey + "，实测 " + key);

        String zh = langValue("zh_cn", key);
        String en = langValue("en_us", key);
        helper.assertTrue("“魔力苹果”".equals(zh), "中文名应当逐字等于 “魔力苹果”，实测 " + zh);
        helper.assertTrue("Magic apple".equals(en), "英文名应当是 Magic apple，实测 " + en);

        // getName 覆盖成「取语言键再上色」。服务端默认语言是 en_us，所以实测值是译文而不是键名。
        Component name = items.MAGIC_APPLE.get().getName(new ItemStack(items.MAGIC_APPLE.get()));
        helper.assertTrue(name.getContents() instanceof TranslatableContents,
                "物品名应当是可翻译组件，实测 " + name.getContents().getClass().getName());
        helper.assertTrue("Magic apple".equals(name.getString()),
                "getName 在 en_us 下应当解析成 Magic apple，实测 " + name.getString());
        helper.succeed();
    }

    /**
     * Shift 悬浮提示必须全部走语言键（中英各一份）；效果条目内容由作者自填，所以这里只断言「键在」。
     *
     * <p>注意：{@code appendHoverText} 里用的 {@code Screen} 是纯客户端类（工具提示本来也只在客户端构建），
     * 服务端碰不得，所以这里改为直接核对 lang 文件的内容 + 运行时语言表。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void magicAppleTooltipIsLocalized(GameTestHelper helper) {
        // 1) 表头与提示语：中英各一份，逐字对齐。
        helper.assertTrue("§b=== 详细效果 ===".equals(langValue("zh_cn", magic_apple.TOOLTIP_HEADER_KEY)),
                "zh_cn 的详细效果表头实测 " + langValue("zh_cn", magic_apple.TOOLTIP_HEADER_KEY));
        helper.assertTrue("§b=== Detailed Effects ===".equals(langValue("en_us", magic_apple.TOOLTIP_HEADER_KEY)),
                "en_us 的详细效果表头实测 " + langValue("en_us", magic_apple.TOOLTIP_HEADER_KEY));

        helper.assertTrue("§8按住 §eShift §8查看详细效果".equals(langValue("zh_cn", magic_apple.TOOLTIP_HINT_KEY)),
                "zh_cn 的 Shift 提示实测 " + langValue("zh_cn", magic_apple.TOOLTIP_HINT_KEY));
        helper.assertTrue("§8 Hold down §eShift §8 View detailed effect".equals(langValue("en_us", magic_apple.TOOLTIP_HINT_KEY)),
                "en_us 的 Shift 提示实测 " + langValue("en_us", magic_apple.TOOLTIP_HINT_KEY));

        // 2) 效果条目：lang 里按 effect.1..N 预留，内容由作者自填，所以这里只断言「键在」。
        for (int i = 1; i <= magic_apple.TOOLTIP_EFFECT_LINE_COUNT; i++) {
            String key = magic_apple.TOOLTIP_EFFECT_KEY_PREFIX + i;
            helper.assertTrue(langValue("zh_cn", key) != null, "zh_cn.json 缺少效果条目 " + key);
            helper.assertTrue(langValue("en_us", key) != null, "en_us.json 缺少效果条目 " + key);
        }

        // 3) 运行时语言表里确实查得到（证明 lang 真的被加载，而不只是文件存在）。
        String resolvedHeader = Component.translatable(magic_apple.TOOLTIP_HEADER_KEY).getString();
        helper.assertTrue("§b=== Detailed Effects ===".equals(resolvedHeader),
                "运行时应当能从 en_us 语言表查到表头，实测 " + resolvedHeader);

        // 4) 没填的条目会被 appendHoverText 里的 hasTranslatedText() 跳过；
        //    它靠的是「键不存在时 getString() 返回键名本身」，这条得锁住。
        String missingKey = magic_apple.TOOLTIP_EFFECT_KEY_PREFIX + 999;
        helper.assertTrue(Component.translatable(missingKey).getString().equals(missingKey),
                "不存在的语言键应当解析成键名本身，实测 " + Component.translatable(missingKey).getString());

        helper.succeed();
    }

    /**
     * 堆叠上限：数量必须能无损地过一遍网络、过一遍存档 NBT，并且背包装得下。
     *
     * <p>原版用有符号 byte 存/传数量（上限 127），所以 128 同时踩到三处：
     * 网络 {@code writeItemStack}/{@code readItem}、存档 {@code ItemStack#save}/{@code ItemStack#of}、
     * 以及玩家背包槽位上限（{@code Container#getMaxStackSize} 默认 64）。
     * 三条都由本模组的 mixin 撑开，这条测试把三者一并锁住。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void magicAppleStackSizeSurvivesNetwork(GameTestHelper helper) {
        ItemStack stack = new ItemStack(items.MAGIC_APPLE.get(), magic_apple.MAX_STACK_SIZE);
        helper.assertTrue(stack.getMaxStackSize() == magic_apple.MAX_STACK_SIZE,
                "物品上限应当是 " + magic_apple.MAX_STACK_SIZE + "，实测 " + stack.getMaxStackSize());

        // 网络往返。
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeItem(stack);
            ItemStack read = buf.readItem();
            helper.assertTrue(read.getCount() == magic_apple.MAX_STACK_SIZE,
                    "网络往返后数量应当是 " + magic_apple.MAX_STACK_SIZE + "，实测 " + read.getCount());
            helper.assertTrue(ItemStack.isSameItemSameTags(stack, read), "往返前后应当是同一件物品");
        } finally {
            buf.release();
        }

        // 存档 NBT 往返（save -> of 就是存盘/读盘那条路）。
        CompoundTag saved = stack.save(new CompoundTag());
        ItemStack loaded = ItemStack.of(saved);
        helper.assertTrue(loaded.getCount() == magic_apple.MAX_STACK_SIZE,
                "存档往返后数量应当是 " + magic_apple.MAX_STACK_SIZE + "，实测 " + loaded.getCount());

        // 容器槽位也得装得下。
        Inventory inventory = helper.makeMockPlayer().getInventory();
        helper.assertTrue(inventory.getMaxStackSize() >= magic_apple.MAX_STACK_SIZE,
                "玩家背包上限应当 >= " + magic_apple.MAX_STACK_SIZE + "，实测 " + inventory.getMaxStackSize());

        inventory.add(new ItemStack(items.MAGIC_APPLE.get(), magic_apple.MAX_STACK_SIZE));
        helper.assertTrue(inventory.getItem(0).getCount() == magic_apple.MAX_STACK_SIZE,
                "应当能在同一个背包槽里放满 " + magic_apple.MAX_STACK_SIZE + "，实测 " + inventory.getItem(0).getCount());
        helper.succeed();
    }

    /** 吃完后：获得「不洁魔力」10 秒 0 级（不显示粒子），并获得 5 点 ISS 魔力。 */
    @GameTest(template = TEMPLATE)
    public static void magicAppleOnConsumeAppliesImpureMagicAndMana(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();

        MagicData data = MagicData.getPlayerMagicData(player);
        helper.assertTrue(data != null,
                "应当能取到 ISS 的 MagicData —— ISS 是本模组的强制前置（mods.toml 里 mandatory=true）");
        data.setMana(0.0F);

        magic_apple.applyOnConsume(player);

        MobEffectInstance impure = player.getEffect(mob_effects.IMPURE_MAGIC.get());
        helper.assertTrue(impure != null, "吃完魔力苹果应当获得 white_lily:impure_magic");
        helper.assertTrue(impure.getDuration() == magic_apple.IMPURE_MAGIC_DURATION_TICKS,
                "不洁魔力应当是 " + magic_apple.IMPURE_MAGIC_DURATION_TICKS + " 刻（10 秒），实测 " + impure.getDuration());
        helper.assertTrue(impure.getAmplifier() == magic_apple.IMPURE_MAGIC_AMPLIFIER,
                "不洁魔力等级应当是 " + magic_apple.IMPURE_MAGIC_AMPLIFIER + "，实测 " + impure.getAmplifier());
        helper.assertTrue(!impure.isVisible(), "不洁魔力不应当显示粒子");

        // 原来那口原版中毒已经被顶掉。
        helper.assertTrue(player.getEffect(MobEffects.POISON) == null,
                "不应当再有原版中毒——它已经被不洁魔力替换");

        helper.assertTrue(Math.abs(data.getMana() - magic_apple.MANA_GAIN) < EPSILON_F,
                "魔力应当增加 " + magic_apple.MANA_GAIN + "，实测 " + data.getMana());
        helper.succeed();
    }

    // ==================================================================
    // 四、不洁魔力（white_lilybuff 包）与异常魔力伤害（white_lilydamage 包）
    // ==================================================================

    /** 效果本身：注册、中立类别、名字/贴图、以及「牛奶消不掉」。 */
    @GameTest(template = TEMPLATE)
    public static void impureMagicIsRegisteredAndUncurable(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation(white_lily.MODID, com.damezame97.white_lily.white_lilybuff.impure_magic.NAME);
        helper.assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(id), "应当注册了状态效果 " + id);

        MobEffect effect = mob_effects.IMPURE_MAGIC.get();
        helper.assertTrue(effect.getCategory() == MobEffectCategory.NEUTRAL,
                "不洁魔力必须是中立类别，实测 " + effect.getCategory());
        helper.assertTrue("effect.white_lily.impure_magic".equals(effect.getDescriptionId()),
                "描述键实测 " + effect.getDescriptionId());
        helper.assertTrue(langValue("zh_cn", effect.getDescriptionId()) != null, "zh_cn.json 缺少效果名");
        helper.assertTrue(langValue("en_us", effect.getDescriptionId()) != null, "en_us.json 缺少效果名");
        helper.assertTrue(existsResource("/assets/white_lily/textures/mob_effect/impure_magic.png"),
                "缺少效果图标 assets/white_lily/textures/mob_effect/impure_magic.png");

        // 治愈物品清单为空 —— 这就是「牛奶消不掉 / 蜂蜜也消不掉」的实现。
        helper.assertTrue(effect.getCurativeItems().isEmpty(),
                "不洁魔力的可治愈物品清单必须是空的，实测 " + effect.getCurativeItems());

        // 真刀真枪喝一次牛奶：效果必须还在。
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        zombie.addEffect(new MobEffectInstance(effect, 200, 0));
        helper.assertTrue(zombie.hasEffect(effect), "应当挂上了不洁魔力");
        zombie.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(zombie.hasEffect(effect), "喝牛奶之后不洁魔力必须还在");

        // 对照组：原版中毒吃牛奶会消失，证明上面的前提是「效果不可治愈」而不是机制失效。
        Zombie control = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 0, 0));
        control.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        control.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(!control.hasEffect(MobEffects.POISON), "对照组：喝牛奶后原版中毒应当消失");

        helper.succeed();
    }

    /** 每 10 刻一跳，伤害类型是 white_lily:abnormal_mana，且无来源。 */
    @GameTest(template = TEMPLATE)
    public static void impureMagicTicksEveryTenTicksWithAbnormalManaDamage(GameTestHelper helper) {
        MobEffect effect = mob_effects.IMPURE_MAGIC.get();

        // 节流：第 10、20 刻结算，第 9 刻不结算。
        helper.assertTrue(effect.isDurationEffectTick(10, 0), "duration=10 时应当结算");
        helper.assertTrue(effect.isDurationEffectTick(20, 0), "duration=20 时应当结算");
        helper.assertTrue(!effect.isDurationEffectTick(9, 0), "duration=9 时不应当结算");

        // 真打一下：等级 III（amplifier=3）→ 3 点伤害。
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        zombie.invulnerableTime = 0;
        float before = zombie.getHealth();
        effect.applyEffectTick(zombie, 3);

        helper.assertTrue(Math.abs(zombie.getHealth() - (before - 3.0F)) < EPSILON_F,
                "等级 III 每跳应当是 3 点伤害，实测掉了 " + (before - zombie.getHealth()));
        DamageSource source = zombie.getLastDamageSource();
        helper.assertTrue(source != null, "应当记录到最后一次伤害来源");
        helper.assertTrue(source.is(abnormal_mana.KEY),
                "伤害类型应当是 " + abnormal_mana.KEY.location() + "，实测 " + source.getMsgId());
        helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null,
                "异常魔力伤害必须是无来源的（不挂任何实体）");

        // 等级 I（amplifier=0）→ 0 点，等于空跳。
        Zombie dummy = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 0, 0));
        dummy.invulnerableTime = 0;
        float dummyBefore = dummy.getHealth();
        effect.applyEffectTick(dummy, 0);
        helper.assertTrue(Math.abs(dummy.getHealth() - dummyBefore) < EPSILON_F,
                "等级 I 时伤害是 0，血量不应当变化");
        helper.succeed();
    }

    /** 伤害类型注册与字段，以及致死提示的接线（内容留给作者填）。 */
    @GameTest(template = TEMPLATE)
    public static void abnormalManaDamageTypeIsRegistered(GameTestHelper helper) {
        Holder<DamageType> holder = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(abnormal_mana.KEY);
        DamageType type = holder.value();

        helper.assertTrue(abnormal_mana.NAME.equals(type.msgId()), "message_id 实测 " + type.msgId());
        helper.assertTrue(type.scaling() == DamageScaling.NEVER, "scaling 应当是 never，实测 " + type.scaling());
        helper.assertTrue(Math.abs(type.exhaustion() - 0.0F) < EPSILON_F,
                "exhaustion 应当是 0，实测 " + type.exhaustion());

        // 死亡提示：键要在（内容目前是空的，作者自己填）。
        helper.assertTrue(langValue("zh_cn", abnormal_mana.DEATH_MESSAGE_KEY) != null,
                "zh_cn.json 缺少 " + abnormal_mana.DEATH_MESSAGE_KEY);
        helper.assertTrue(langValue("en_us", abnormal_mana.DEATH_MESSAGE_KEY) != null,
                "en_us.json 缺少 " + abnormal_mana.DEATH_MESSAGE_KEY);
        helper.assertTrue(langValue("zh_cn", abnormal_mana.DEATH_MESSAGE_PLAYER_KEY) != null,
                "zh_cn.json 缺少 " + abnormal_mana.DEATH_MESSAGE_PLAYER_KEY);
        helper.assertTrue(langValue("en_us", abnormal_mana.DEATH_MESSAGE_PLAYER_KEY) != null,
                "en_us.json 缺少 " + abnormal_mana.DEATH_MESSAGE_PLAYER_KEY);

        // 真的造一次致死提示，确认 message_id → 语言键 这条路是通的。
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        DamageSource source = abnormal_mana.source(helper.getLevel());
        String shown = source.getLocalizedDeathMessage(victim).getString();

        // 原版默认死亡提示会把模板里的 %1$s 换成死者显示名，所以要和「替换后的结果」比，
        // 不能直接和 lang 里的模板比。
        String template = langValue("en_us", abnormal_mana.DEATH_MESSAGE_KEY);
        helper.assertTrue(template != null, "en_us.json 缺少 " + abnormal_mana.DEATH_MESSAGE_KEY);
        String expected = template.replace("%1$s", victim.getDisplayName().getString());
        helper.assertTrue(shown.equals(expected),
                "致死提示应当把 " + abnormal_mana.DEATH_MESSAGE_KEY + " 的 %1$s 换成死者名，期望 ["
                        + expected + "]，实测 [" + shown + "]");
        helper.succeed();
    }

    /** 梦魇伤害标签：shadow_damage 本身 + 它蕴含的五个 bypasses_* 全部命中。 */
    @GameTest(template = TEMPLATE)
    public static void shadowDamageTagCarriesAllBypasses(GameTestHelper helper) {
        Holder<DamageType> holder = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(abnormal_mana.KEY);

        helper.assertTrue(holder.is(shadow_damage.TAG),
                abnormal_mana.NAME + " 应当在标签 " + shadow_damage.TAG.location() + " 里");

        for (TagKey<DamageType> bypass : shadow_damage.IMPLIED_BYPASSES) {
            helper.assertTrue(holder.is(bypass),
                    abnormal_mana.NAME + " 缺少 " + bypass.location() + "（记得同步改那 6 个 tag json）");
        }

        // 蕴涔清单本身也得是 5 条，防止以后有人删掉一条还不报错。
        helper.assertTrue(shadow_damage.IMPLIED_BYPASSES.size() == 5,
                "蕴涔清单应当是 5 条，实测 " + shadow_damage.IMPLIED_BYPASSES.size());
        helper.succeed();
    }

    // ==================================================================
    // 五、创造模式物品栏
    // ==================================================================

    /**「歧途“珍馐”」必须真的进了 built-in 注册表，且标题/图标/资源都对得上。 */
    @GameTest(template = TEMPLATE)
    public static void creativeTabIsRegistered(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation(white_lily.MODID, creative_tabs.MISGUIDED_DELICACIES_NAME);

        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id),
                "应当注册了创造模式物品栏 " + id);

        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(id);
        helper.assertTrue(tab != null, "注册表里应当取得到 " + id);

        // 标题必须走语言键；不设 title 的话 Forge 默认给 Component.empty()，客户端会显示空白。
        // 注意：getString() 会查语言表，服务端默认语言是 en_us，所以实测值是译文而不是键名。
        Component title = tab.getDisplayName();
        ComponentContents contents = title.getContents();
        helper.assertTrue(contents instanceof TranslatableContents,
                "标题应当是可翻译组件，实测 " + contents.getClass().getName());
        helper.assertTrue(creative_tabs.MISGUIDED_DELICACIES_TITLE_KEY.equals(((TranslatableContents) contents).getKey()),
                "标题语言键应当是 " + creative_tabs.MISGUIDED_DELICACIES_TITLE_KEY
                        + "，实测 " + ((TranslatableContents) contents).getKey());
        helper.assertTrue("Misguided Delicacies".equals(title.getString()),
                "en_us 下标题应当解析为 Misguided Delicacies，实测 " + title.getString());

        // 图标是承载 white_lily_food 贴图的那个隐藏物品。
        helper.assertTrue(tab.getIconItem().getItem() == items.TAB_ICON.get(),
                "图标应当是 " + items.TAB_ICON.getId() + "，实测 " + tab.getIconItem());

        // 贴图与模型必须真的打进包里，否则标签上会渲染成紫黑格。
        helper.assertTrue(existsResource("/assets/white_lily/textures/item/tab_icon.png"),
                "缺少物品栏图标贴图 assets/white_lily/textures/item/tab_icon.png");
        helper.assertTrue(existsResource("/assets/white_lily/models/item/tab_icon.json"),
                "缺少物品栏图标模型 assets/white_lily/models/item/tab_icon.json");

        // 这里是最容易踩的坑：CATEGORY 类型的物品栏只要一件物品都没有，
        // shouldDisplay() 就是 false，客户端任何一页都不会画它——看起来像“根本没注册”。
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        helper.assertTrue(tab.hasAnyItems(), "物品栏里至少要有一件物品，否则它不会被显示");
        helper.assertTrue(tab.shouldDisplay(), "shouldDisplay() 必须为 true，否则客户端任何一页都看不到它");

        // 占位物品 TAB_ICON 应当已经撤出这一栏（它只保留作标签图标），换成了真正的魔力苹果。
        helper.assertTrue(tab.getDisplayItems().contains(new ItemStack(items.MAGIC_APPLE.get())),
                "物品栏里应当有 " + items.MAGIC_APPLE.getId());
        helper.assertTrue(!tab.getDisplayItems().contains(new ItemStack(items.TAB_ICON.get())),
                "占位物品 " + items.TAB_ICON.getId() + " 应当已经从物品栏内容里移除");

        helper.succeed();
    }

    /**
     * 最关键的一条：客户端渲染标签栏用的是 {@link CreativeModeTabRegistry#getSortedCreativeModeTabs()}，
     * 而不是注册表本身。只要它不在这个列表里，界面上就永远看不到。
     */
    @GameTest(template = TEMPLATE)
    public static void creativeTabIsInSortedList(GameTestHelper helper) {
        ResourceLocation id = new ResourceLocation(white_lily.MODID, creative_tabs.MISGUIDED_DELICACIES_NAME);
        List<CreativeModeTab> sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs();

        StringBuilder dump = new StringBuilder("\n排序表共 " + sorted.size() + " 栏：");
        int position = -1;
        for (int i = 0; i < sorted.size(); i++) {
            ResourceLocation name = CreativeModeTabRegistry.getName(sorted.get(i));
            dump.append("\n  ").append(i + 1).append(". ").append(name);
            if (id.equals(name)) {
                position = i + 1;
            }
        }

        helper.assertTrue(position > 0, "排序表里没有 " + id + dump);
        helper.assertTrue(position > 10,
                "Forge 每页 10 栏，它排在第 " + position + " 位，也就是第 " + ((position - 1) / 10 + 1) + " 页" + dump);
        helper.succeed();
    }

    /** 物品栏标题的中英翻译必须齐全，否则客户端只能看到原始语言键。 */
    @GameTest(template = TEMPLATE)
    public static void creativeTabTitleIsTranslated(GameTestHelper helper) {
        String key = creative_tabs.MISGUIDED_DELICACIES_TITLE_KEY;

        String zh = langValue("zh_cn", key);
        String en = langValue("en_us", key);

        helper.assertTrue(zh != null, "zh_cn.json 缺少 " + key);
        helper.assertTrue(en != null, "en_us.json 缺少 " + key);

        // 中文名必须是「歧途“珍馐”」，逐字对齐，防止写入时编码出错。
        helper.assertTrue("歧途“珍馐”".equals(zh), "中文名应当逐字等于 歧途“珍馐”，实测 " + zh);

        // 隐藏图标物品的名字走 Item#getDescriptionId（item.white_lily.tab_icon，用点不用冒号）。
        String iconKey = items.TAB_ICON.get().getDescriptionId();
        helper.assertTrue(langValue("zh_cn", iconKey) != null, "zh_cn.json 缺少隐藏图标物品的名字 " + iconKey);
        helper.assertTrue(langValue("en_us", iconKey) != null, "en_us.json 缺少隐藏图标物品的名字 " + iconKey);

        helper.succeed();
    }

    /**
     * Curios 饰品栏「Improvement / 改良」必须真的被数据包加载器读进栏位注册表。
     *
     * <p>栏位定义是数据包（{@code data/white_lily/curios/slots/improvement.json}），
     * 服务端那份由 {@code CuriosSlotManager.SERVER} 加载，所以这里查服务端。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void curiosImprovementSlotIsRegistered(GameTestHelper helper) {
        Optional<ISlotType> found = CuriosApi.getSlot(curio_slots.IMPROVEMENT, false);
        helper.assertTrue(found.isPresent(),
                "Curios 没加载栏位 " + curio_slots.IMPROVEMENT
                        + "（检查 data/white_lily/curios/slots/improvement.json）");

        ISlotType improvement = found.get();
        helper.assertTrue(curio_slots.IMPROVEMENT.equals(improvement.getIdentifier()),
                "栏位 id 应为 " + curio_slots.IMPROVEMENT + "，实测 " + improvement.getIdentifier());
        helper.assertTrue(improvement.getSize() == 1,
                "栏位数量应为 1，实测 " + improvement.getSize());
        helper.assertTrue(curio_slots.IMPROVEMENT_ICON.equals(improvement.getIcon()),
                "栏位贴图应为 " + curio_slots.IMPROVEMENT_ICON + "，实测 " + improvement.getIcon());
        helper.assertTrue(
                improvement.getValidators().contains(new ResourceLocation("curios", "tag")),
                "栏位校验器应含 curios:tag，实测 " + improvement.getValidators());

        // 贴图文件与图集声明都必须真的打进包：
        // 前者是贴图本身，后者决定它会不会被拼进 blocks 图集（Curios 用 BLOCK_ATLAS + sprite 画栏位底图）。
        helper.assertTrue(existsResource("/assets/white_lily/textures/slot/improvement.png"),
                "缺少贴图 assets/white_lily/textures/slot/improvement.png");
        helper.assertTrue(existsResource("/assets/minecraft/atlases/blocks.json"),
                "缺少图集声明 assets/minecraft/atlases/blocks.json，贴图不会被拼进 blocks 图集");

        // 栏位显示名走 lang：curios.identifier.<栏位 id>
        helper.assertTrue("改良".equals(langValue("zh_cn", curio_slots.IMPROVEMENT_NAME_KEY)),
                "zh_cn.json 的 " + curio_slots.IMPROVEMENT_NAME_KEY + " 应为「改良」，实测 "
                        + langValue("zh_cn", curio_slots.IMPROVEMENT_NAME_KEY));
        helper.assertTrue("Improvement".equals(langValue("en_us", curio_slots.IMPROVEMENT_NAME_KEY)),
                "en_us.json 的 " + curio_slots.IMPROVEMENT_NAME_KEY + " 应为 Improvement，实测 "
                        + langValue("en_us", curio_slots.IMPROVEMENT_NAME_KEY));

        helper.succeed();
    }

    // ==================================================================
    // Curios 饰品栏：档位 → 栏位数量 / 初始饰品（档位来自世界文件夹的调试文件）
    // ==================================================================

    /** 四档难度对应的栏位数量必须是 1 / 2 / 3 / 4。 */
    @GameTest(template = TEMPLATE)
    public static void improvementSlotCountFollowsDifficulty(GameTestHelper helper) {
        helper.assertTrue(improvement_loadout.slotsForDifficulty(Difficulty.PEACEFUL) == 1,
                "和平难度应当是 1 个栏位，实测 " + improvement_loadout.slotsForDifficulty(Difficulty.PEACEFUL));
        helper.assertTrue(improvement_loadout.slotsForDifficulty(Difficulty.EASY) == 2,
                "简单难度应当是 2 个栏位，实测 " + improvement_loadout.slotsForDifficulty(Difficulty.EASY));
        helper.assertTrue(improvement_loadout.slotsForDifficulty(Difficulty.NORMAL) == 3,
                "普通难度应当是 3 个栏位，实测 " + improvement_loadout.slotsForDifficulty(Difficulty.NORMAL));
        helper.assertTrue(improvement_loadout.slotsForDifficulty(Difficulty.HARD) == 4,
                "困难难度应当是 4 个栏位，实测 " + improvement_loadout.slotsForDifficulty(Difficulty.HARD));
        helper.succeed();
    }

    /** 四档难度对应的装备清单必须逐项对得上，顺序也要对。 */
    @GameTest(template = TEMPLATE)
    public static void improvementLoadoutFollowsDifficulty(GameTestHelper helper) {
        expectLoadout(helper, Difficulty.PEACEFUL, List.of(
                items.INVALID_PLACEHOLDER.get()));
        expectLoadout(helper, Difficulty.EASY, List.of(
                items.INVALID_PLACEHOLDER.get(),
                items.FATE_COMPASS.get()));
        expectLoadout(helper, Difficulty.NORMAL, List.of(
                items.INVALID_PLACEHOLDER.get(),
                items.FATE_COMPASS.get(),
                items.IMPROVE_BODY.get()));
        expectLoadout(helper, Difficulty.HARD, List.of(
                items.INVALID_PLACEHOLDER.get(),
                items.FATE_COMPASS.get(),
                items.IMPROVE_BODY.get(),
                items.FLESH_RING.get()));
        helper.succeed();
    }

    /**
     * 四个空饰品必须真的被 Curios 认成「能进 improvement 栏」的物品。
     *
     * <p>走 Curios 自己的 {@code getItemStackSlots}，也就是它会去查
     * {@code curios:improvement} / {@code curios:curio} 标签——所以这条测试同时证明了
     * 物品实现了 {@code ICurioItem} 和 {@code data/curios/tags/items/improvement.json} 生效。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void improvementCurioItemsBelongToSlot(GameTestHelper helper) {
        for (Item item : improvementCurioItems()) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(
                    CuriosApi.getItemStackSlots(stack, false).containsKey(curio_slots.IMPROVEMENT),
                    item + " 应当能装进 " + curio_slots.IMPROVEMENT + " 栏（检查 curios:improvement 物品标签）");
            helper.assertTrue(CuriosApi.getCurio(stack).isPresent(),
                    item + " 应当带 Curios 饰品能力（类要实现 ICurioItem）");
        }
        helper.succeed();
    }

    /** 四个空饰品不能出现在任何创造模式物品栏页里（原版搜索页是从其它页汇总的，一并覆盖）。 */
    @GameTest(template = TEMPLATE)
    public static void improvementCurioItemsAreHiddenFromCreativeTab(GameTestHelper helper) {
        List<Item> hidden = improvementCurioItems();
        int checked = 0;

        // 创造模式物品栏的内容是服务器启动后期才建的，测试可能跑在它前面。
        // 这里主动催一次（参数没变时它会自己跳过），否则会在“所有页都是空的”上白挂一次。
        MinecraftServer server = helper.getLevel().getServer();
        CreativeModeTabs.tryRebuildTabContents(
                server.getWorldData().enabledFeatures(), true, server.registryAccess());

        for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            for (ItemStack stack : tab.getDisplayItems()) {
                checked++;
                helper.assertTrue(!hidden.contains(stack.getItem()),
                        "空饰品 " + stack.getItem() + " 不应出现在物品栏 "
                                + CreativeModeTabRegistry.getName(tab));
            }
        }

        // 防止「所有页都是空的」这种假阳性
        helper.assertTrue(checked > 0, "所有创造模式物品栏都是空的，这条测试没意义");
        helper.succeed();
    }

    // ==================================================================
    // Curios 饰品栏：「改良」系列饰品的统一特性
    // ==================================================================

    /** 统一特性之一：四件饰品都拿不下来（真去 extractItem 一次，拿不出来）。 */
    @GameTest(template = TEMPLATE)
    public static void improvementCuriosCannotBeTakenOff(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        improvement_loadout.apply(player, 2);

        SlotContext context = new SlotContext(curio_slots.IMPROVEMENT, player, 0, false, true);
        for (Item item : improvementCurioItems()) {
            ICurio curio = CuriosApi.getCurio(new ItemStack(item)).orElse(null);
            helper.assertTrue(curio != null, item + " 没有 Curios 饰品能力");
            helper.assertTrue(!curio.canUnequip(context), item + " 应当拿不下来");
        }

        // 光看 canUnequip 不够，真去取一次：Curios 的格子走 IItemHandler#extractItem
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        helper.assertTrue(handler != null, "假玩家拿不到 Curios 库存能力");
        ICurioStacksHandler stacksHandler = handler.getStacksHandler(curio_slots.IMPROVEMENT).orElse(null);
        helper.assertTrue(stacksHandler != null, "玩家身上没有 " + curio_slots.IMPROVEMENT + " 栏");

        ItemStack extracted = stacksHandler.getStacks().extractItem(0, 1, false);
        helper.assertTrue(extracted.isEmpty(), "第 0 格应当拿不出来，实测 " + extracted);
        helper.assertTrue(equippedStack(player, 0).getItem() == items.INVALID_PLACEHOLDER.get(),
                "被拒绝之后那一格应当原封不动");
        helper.succeed();
    }

    /** 统一特性之二：死了不掉（掉落规则是 ALWAYS_KEEP，掉落物还会再被筛一遍）。 */
    @GameTest(template = TEMPLATE)
    public static void improvementCuriosNeverDropOnDeath(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        SlotContext context = new SlotContext(curio_slots.IMPROVEMENT, player, 0, false, true);

        for (Item item : improvementCurioItems()) {
            ICurio curio = CuriosApi.getCurio(new ItemStack(item)).orElse(null);
            helper.assertTrue(curio != null, item + " 没有 Curios 饰品能力");
            helper.assertTrue(
                    curio.getDropRule(context, helper.getLevel().damageSources().generic(), 0, true)
                            == ICurio.DropRule.ALWAYS_KEEP,
                    item + " 的掉落规则应当是 ALWAYS_KEEP（死了留着）");
        }

        // 再兜一层：掉落物里出现这四件就直接剔除
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(items.FLESH_RING.get())));
        drops.add(new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.STONE)));

        LivingDropsEvent event = new LivingDropsEvent(
                victim, helper.getLevel().damageSources().generic(), drops, 0, false);
        curio_events.onLivingDrops(event);

        helper.assertTrue(drops.size() == 1,
                "饰品应当被剔除，实测还剩 " + drops.size() + " 件掉落物");
        helper.assertTrue(drops.get(0).getItem().getItem() == Items.STONE, "剩下的应当是石头");
        helper.succeed();
    }

    /** 统一特性之三：非创造玩家的物品栏里出现这四件就立刻删；创造玩家不受影响。 */
    @GameTest(template = TEMPLATE)
    public static void improvementCuriosArePurgedFromNonCreativeInventory(GameTestHelper helper) {
        // 注意：Forge 的 makeMockPlayer() 默认是创造模式，要非创造得用 makeMockSurvivalPlayer()
        Player survival = helper.makeMockSurvivalPlayer();
        helper.assertTrue(!survival.isCreative(), "前置条件：这个假玩家应当是非创造模式");

        survival.getInventory().setItem(0, new ItemStack(items.FLESH_RING.get()));
        survival.getInventory().setItem(5, new ItemStack(items.FATE_COMPASS.get()));
        survival.getInventory().setItem(9, new ItemStack(Items.STONE));

        curio_events.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, survival));

        helper.assertTrue(survival.getInventory().getItem(0).isEmpty(), "第 0 格应当被删掉");
        helper.assertTrue(survival.getInventory().getItem(5).isEmpty(), "第 5 格应当被删掉");
        helper.assertTrue(survival.getInventory().getItem(9).getItem() == Items.STONE,
                "普通物品不能被牵连");

        // 创造模式玩家：留着
        Player creative = helper.makeMockPlayer();
        helper.assertTrue(creative.isCreative(), "前置条件：makeMockPlayer() 应当是创造模式");
        creative.getInventory().setItem(0, new ItemStack(items.FLESH_RING.get()));
        curio_events.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, creative));

        helper.assertTrue(creative.getInventory().getItem(0).getItem() == items.FLESH_RING.get(),
                "创造玩家的物品栏不该被清理");
        helper.succeed();
    }

    /**
     * 真拿一个（假的）玩家跑一遍 {@code improvement_loadout.apply}：
     * 逐档断言栏位数量与每一格里的物品，最后回到和平难度验证「缩减」也走得通。
     *
     * <p>这是唯一能证明「栏位数量真的改得动」的测试：它不读代码常量，而是去问
     * Curios 要这个玩家当前的栏位数与库存内容。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void improvementLoadoutChangesSlotCount(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();

        for (Difficulty difficulty : Difficulty.values()) {
            improvement_loadout.apply(player, difficulty);
            assertSlotState(helper, player, difficulty);
        }

        // 再从困难缩回和平：验证 shrink 那条路
        improvement_loadout.apply(player, Difficulty.PEACEFUL);
        assertSlotState(helper, player, Difficulty.PEACEFUL);

        helper.succeed();
    }

    /**
     * 每次进世界都要按 json 重设：玩家自己动过的手脚会被抹掉，改了 json 下次进来就生效。
     */
    @GameTest(template = TEMPLATE)
    public static void improvementLoadoutIsReappliedOnEveryJoin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = debug_file.path(server);
        byte[] backup = readOrNull(file);

        try {
            debug_file.write(server, 2);
            Player player = helper.makeMockPlayer();

            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 2,
                    "第一次进世界应当按文件里的 2 档设置");
            assertSlotState(helper, player, 2);

            // 模拟玩家把第 2 格抠空
            CuriosApi.getCuriosHelper().setEquippedCurio(
                    player, curio_slots.IMPROVEMENT, 1, ItemStack.EMPTY);
            helper.assertTrue(equippedStack(player, 1).isEmpty(),
                    "前置条件：那一格应当已经被抠空");

            // 再进一次世界 → 按 json 填回来
            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 2,
                    "第二次进世界也应当按文件重设");
            assertSlotState(helper, player, 2);

            // 把 json 改成 4 档，再进一次 → 按新档位重设
            debug_file.write(server, 4);
            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 4,
                    "改了 json 之后再进世界应当按新档位重设");
            assertSlotState(helper, player, 4);

            // 再改回 1 档 → 往回收（缩格 + 只留一件）
            debug_file.write(server, 1);
            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 1,
                    "改回 1 档之后再进世界应当缩回 1 格");
            assertSlotState(helper, player, 1);
        } finally {
            restoreFile(file, backup);
        }

        helper.succeed();
    }

    /**
     * 世界级调试文件 {@code Debugging_whitelilymod.json}：缺了就按当时难度生成，
     * 在了就以里面的 {@code difficulty_level} 为准（跟当前难度无关），坏了当没有。
     */
    @GameTest(template = TEMPLATE)
    public static void improvementDebugFileIsAuthoritative(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = debug_file.path(server);
        byte[] backup = readOrNull(file);
        int byDifficulty = improvement_loadout.levelForDifficulty(
                helper.getLevel().getLevelData().getDifficulty());

        try {
            // 1) 文件不在 → 按当前世界难度生成
            Files.deleteIfExists(file);
            int created = debug_file.loadOrCreateLevel(server);
            helper.assertTrue(Files.isRegularFile(file),
                    "世界加载后应当生成 " + debug_file.FILE_NAME + "（路径 " + file + "）");
            helper.assertTrue(created == byDifficulty,
                    "生成出来的档位应当等于当时世界难度对应的档位，实测 " + created);
            helper.assertTrue(debug_file.readLevel(server).orElse(-1) == created,
                    "刚写出来的文件应当能读回来");

            // 2) 文件在且合法 → 只认文件里的值
            debug_file.write(server, 1);
            helper.assertTrue(debug_file.readLevel(server).orElse(-1) == 1,
                    "文件里写 1，读回来就该是 1");
            helper.assertTrue(debug_file.loadOrCreateLevel(server) == 1,
                    "文件合法时必须只认文件里的值");

            // 3) 越界 / 坏 JSON → 当没有，重新生成
            // （越界值用裸文本写，模拟作者手改成 99 的情况；write() 本身会把档位夹到 1~4）
            Files.writeString(file, "{\"" + debug_file.LEVEL_KEY + "\": 99}", StandardCharsets.UTF_8);
            helper.assertTrue(debug_file.readLevel(server).isEmpty(),
                    "difficulty_level = 99 应当被当成非法（合法范围 1~4）");
            Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
            helper.assertTrue(debug_file.readLevel(server).isEmpty(),
                    "坏 JSON 应当被当成没有这份文件");
            helper.assertTrue(debug_file.loadOrCreateLevel(server) == byDifficulty,
                    "坏文件应当按当前难度重新生成");
        } catch (IOException e) {
            helper.assertTrue(false, "读写调试文件失败：" + e);
        } finally {
            restoreFile(file, backup);
        }

        helper.succeed();
    }

    /**
     * 「以 json 内的参数为准」：先故意制造矛盾——文件写 4 档、世界难度调成简单——看它跟谁。
     *
     * <p>验证三件事：</p>
     * <ol>
     *     <li>进世界按文件里的 4 档设置（4 格 4 件），<b>无视</b>当时的简单难度；</li>
     *     <li>把世界难度改成普通再进一次，还是 4 档（文件才是准的）；</li>
     *     <li>文件改成 2 档，同一名玩家再进一次就变成 2 格 2 件。</li>
     * </ol>
     *
     * <p>全程只在 EASY / NORMAL 之间改难度（避开 PEACEFUL，切入和平会顺带清敌对生物），
     * 最后无论成败都恢复原难度与原文件。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void improvementLoadoutFollowsDebugFile(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = debug_file.path(server);
        byte[] backup = readOrNull(file);
        Difficulty original = server.getWorldData().getDifficulty();

        try {
            debug_file.write(server, 4);
            server.setDifficulty(Difficulty.EASY, true);

            Player player = helper.makeMockPlayer();
            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 4,
                    "应当按文件里的 4 档设置，而不是当时的简单难度");
            assertSlotState(helper, player, 4);

            // 改世界难度：文件没变，结果就不该变
            server.setDifficulty(Difficulty.NORMAL, true);
            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 4,
                    "世界难度变了但文件没变，仍然应当按 4 档");
            assertSlotState(helper, player, 4);

            // 改文件：下一次进世界就跟着变
            debug_file.write(server, 2);
            helper.assertTrue(improvement_loadout.syncFromDebugFile(player) == 2,
                    "文件改成 2 档后应当按 2 档重设");
            assertSlotState(helper, player, 2);
        } finally {
            restoreFile(file, backup);
            server.setDifficulty(original, true);
        }

        helper.succeed();
    }

    /** 读玩家 improvement 栏第 index 格的内容（读不到就给空）。 */
    private static ItemStack equippedStack(Player player, int index) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ICurioStacksHandler stacksHandler = handler.getStacksHandler(curio_slots.IMPROVEMENT).orElse(null);
        if (stacksHandler == null || index >= stacksHandler.getStacks().getSlots()) {
            return ItemStack.EMPTY;
        }
        return stacksHandler.getStacks().getStackInSlot(index);
    }

    /** 读文件原始字节；不存在或读不了返回 {@code null}。 */
    private static byte[] readOrNull(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** 把文件恢复成备份的样子；备份是 {@code null} 就删掉。 */
    private static void restoreFile(Path path, byte[] backup) {
        try {
            if (backup == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, backup);
            }
        } catch (IOException e) {
            // 测试收尾，尽力而为
        }
    }

    /** 四个空饰品的清单（测试与断言共用）。 */
    private static List<Item> improvementCurioItems() {
        return List.of(
                items.FLESH_RING.get(),
                items.IMPROVE_BODY.get(),
                items.INVALID_PLACEHOLDER.get(),
                items.FATE_COMPASS.get());
    }

    /** 断言难度对应的装备清单逐项一致。 */
    private static void expectLoadout(GameTestHelper helper, Difficulty difficulty, List<Item> expected) {
        List<Item> actual = improvement_loadout.loadoutForDifficulty(difficulty);
        helper.assertTrue(expected.size() == actual.size(),
                difficulty + " 应当装备 " + expected.size() + " 件，实测 " + actual.size());
        for (int i = 0; i < expected.size(); i++) {
            helper.assertTrue(expected.get(i) == actual.get(i),
                    difficulty + " 第 " + i + " 件应当是 " + expected.get(i) + "，实测 " + actual.get(i));
        }
    }

    /** 去 Curios 那边问这个玩家当前的栏位数量与每一格的内容，和档位规则对一遍。 */
    private static void assertSlotState(GameTestHelper helper, Player player, int level) {
        int expectedSlots = improvement_loadout.slotsForLevel(level);
        List<Item> expectedItems = improvement_loadout.loadoutForLevel(level);

        int slots = CuriosApi.getSlotHelper().getSlotsForType(player, curio_slots.IMPROVEMENT);
        helper.assertTrue(slots == expectedSlots,
                level + " 档应当有 " + expectedSlots + " 个 " + curio_slots.IMPROVEMENT
                        + " 栏位，实测 " + slots);

        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        helper.assertTrue(handler != null, "假玩家拿不到 Curios 库存能力");
        ICurioStacksHandler stacksHandler = handler.getStacksHandler(curio_slots.IMPROVEMENT).orElse(null);
        helper.assertTrue(stacksHandler != null,
                "玩家身上没有 " + curio_slots.IMPROVEMENT + " 栏");

        IDynamicStackHandler stacks = stacksHandler.getStacks();
        helper.assertTrue(stacks.getSlots() == expectedSlots,
                "底层库存应当也有 " + expectedSlots + " 格，实测 " + stacks.getSlots());

        for (int i = 0; i < expectedSlots; i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            Item expected = expectedItems.get(i);
            helper.assertTrue(stack.getItem() == expected,
                    level + " 档第 " + i + " 格应当是 " + expected + "，实测 " + stack);
        }
    }

    /** 难度版断言（内部换算成档位），给只关心难度的测试用。 */
    private static void assertSlotState(GameTestHelper helper, Player player, Difficulty difficulty) {
        assertSlotState(helper, player, improvement_loadout.levelForDifficulty(difficulty));
    }

    // ==================================================================
    // 「无效占位符」的名字渐变 + 玩家变量能力 progressive_tense
    // ==================================================================

    /** 名字渐变的纯函数 + lang 键齐全（名字现在是空例子，所以只断言键存在）。 */
    @GameTest(template = TEMPLATE)
    public static void invalidPlaceholderNameIsGradientAnimated(GameTestHelper helper) {
        List<String> keys = List.of(
                invalid_placeholder.NAME_KEY,
                invalid_placeholder.NAME_COLOR_FROM_KEY,
                invalid_placeholder.NAME_COLOR_TO_KEY,
                invalid_placeholder.TOOLTIP_HINT_KEY,
                invalid_placeholder.TOOLTIP_HEADER_KEY);
        for (String key : keys) {
            helper.assertTrue(langValue("zh_cn", key) != null, "zh_cn.json 缺少 " + key);
            helper.assertTrue(langValue("en_us", key) != null, "en_us.json 缺少 " + key);
        }
        for (int text = 1; text <= 2; text++) {
            for (int line = 1; line <= invalid_placeholder.TOOLTIP_LINE_COUNT; line++) {
                String key = invalid_placeholder.TOOLTIP_TEXT_KEY_PREFIX + text + "." + line;
                helper.assertTrue(langValue("zh_cn", key) != null, "zh_cn.json 缺少 " + key);
                helper.assertTrue(langValue("en_us", key) != null, "en_us.json 缺少 " + key);
            }
        }

        // 每个字是「§ + 颜色代码 + 字本身」三个字符，而且代码都在原版 16 色里
        String sample = "INVALIDPLACEHOLDER";
        String atStart = invalid_placeholder.colorize(sample, 0L, "§b", "§d");
        String later = invalid_placeholder.colorize(
                sample, invalid_placeholder.NAME_GRADIENT_PERIOD_MS / 4, "§b", "§d");

        helper.assertTrue(atStart.length() == sample.length() * 3,
                "每个字应当占「§ + 颜色代码 + 字」三个字符，实测长度 " + atStart.length());
        Set<Character> codes = new HashSet<>();
        for (int i = 0; i < atStart.length(); i += 3) {
            int index = i / 3;
            helper.assertTrue(atStart.charAt(i) == '\u00a7',
                    "第 " + index + " 个字前面应当是 §");
            char code = atStart.charAt(i + 1);
            helper.assertTrue("0123456789abcdef".indexOf(code) >= 0, "非法颜色代码 §" + code);
            helper.assertTrue(atStart.charAt(i + 2) == sample.charAt(index),
                    "第 " + index + " 个字的内容被改动了");
            codes.add(code);
        }
        helper.assertTrue(codes.size() >= 2,
                "一条长名字上应当看得到不止一种颜色，实测 " + codes);
        helper.assertTrue(!atStart.equals(later),
                "渐变应当随时间流动（两个不同时刻的结果不该一模一样）");

        // 名字为空（lang 里现在是空例子）时不产生任何字符
        helper.assertTrue(invalid_placeholder.colorize("", 0L, "§b", "§d").isEmpty(),
                "空名字应当原样返回空串");
        helper.succeed();
    }

    /** progressive_tense 能力已经注册，而且只挂在玩家身上。 */
    @GameTest(template = TEMPLATE)
    public static void progressiveTenseOnlyExistsOnPlayers(GameTestHelper helper) {
        helper.assertTrue(player_variables.PROGRESSIVE_TENSE.isRegistered(),
                "progressive_tense 能力应当已经被注册（@AutoRegisterCapability）");

        Player player = helper.makeMockPlayer();
        helper.assertTrue(player_variables.get(player).isPresent(),
                "玩家身上应当挂着 progressive_tense");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        helper.assertTrue(!zombie.getCapability(player_variables.PROGRESSIVE_TENSE).isPresent(),
                "怪物身上不该有这个能力（它只能存在玩家实体上）");
        helper.succeed();
    }

    /** 它的值会随实体数据（玩家的 playerdata）一起存：序列化出来就在 ForgeCaps 段里。 */
    @GameTest(template = TEMPLATE)
    public static void progressiveTenseIsSavedIntoPlayerData(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        progressive_tense vars = player_variables.get(player).orElse(null);
        helper.assertTrue(vars != null, "玩家身上应当挂着 progressive_tense");

        vars.setStage(7);
        vars.setTense(true);

        String id = player_variables.PROGRESSIVE_TENSE_ID.toString();
        CompoundTag root = player.serializeNBT();
        helper.assertTrue(root.contains("ForgeCaps"), "实体数据里应当有 ForgeCaps 段");

        CompoundTag caps = root.getCompound("ForgeCaps");
        helper.assertTrue(caps.contains(id), "ForgeCaps 里应当有 " + id + "，实测 " + caps.getAllKeys());

        CompoundTag saved = caps.getCompound(id);
        helper.assertTrue(saved.getInt("stage") == 7, "stage 应当存成 7，实测 " + saved.getInt("stage"));
        helper.assertTrue(saved.getBoolean("tense"), "tense 应当存成 true");

        // round-trip：读回来还是原值
        progressive_tense restored = new progressive_tense();
        restored.deserializeNBT(saved);
        helper.assertTrue(restored.getStage() == 7 && restored.isTense(),
                "读回来应当还是 stage=7/tense=true，实测 " + restored);
        helper.succeed();
    }

    /** 重生（换玩家实体）时值要跟着走。 */
    @GameTest(template = TEMPLATE)
    public static void progressiveTenseSurvivesRespawn(GameTestHelper helper) {
        Player oldPlayer = helper.makeMockPlayer();
        progressive_tense oldVars = player_variables.get(oldPlayer).orElse(null);
        helper.assertTrue(oldVars != null, "旧玩家身上应当有能力");
        oldVars.setStage(3);
        oldVars.setTense(true);

        Player newPlayer = helper.makeMockPlayer();
        // 注意 Forge 的构造参数顺序是（新玩家, 旧玩家, 是否死亡）
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(newPlayer, oldPlayer, true));

        progressive_tense newVars = player_variables.get(newPlayer).orElse(null);
        helper.assertTrue(newVars != null, "新玩家身上应当有能力");
        helper.assertTrue(newVars.getStage() == 3 && newVars.isTense(),
                "重生后值应当跟着走，实测 " + newVars);
        helper.succeed();
    }

    /**
     * 需求的核心：{@code progressive_tense} 一旦被设成某个值，只要没人手动改，
     * <b>死亡 / 退出重进游戏都不会变</b>。
     *
     * <p>三条路径：① 死亡重生（换玩家实体 → {@code PlayerEvent.Clone}）；
     * ② 存档/读档（{@code serializeNBT()} 出来再 {@code load()} 回去，就是 playerdata 的来回路）；
     * ③ 原玩家自己不受影响。</p>
     *
     * <p>维度切换不需要单独测：玩家实体要么被复用（能力原封不动），要么被重建（那就走 ① 的 Clone 路径），
     * 两条路都不会丢值。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void progressiveTenseStaysSetUntilChanged(GameTestHelper helper) {
        Player original = helper.makeMockPlayer();
        progressive_tense vars = player_variables.get(original).orElse(null);
        helper.assertTrue(vars != null, "玩家身上应当挂着 progressive_tense");
        vars.setStage(1);

        // ① 死亡重生
        Player respawned = helper.makeMockPlayer();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, original, true));
        progressive_tense respawnedVars = player_variables.get(respawned).orElse(null);
        helper.assertTrue(respawnedVars != null, "重生后的玩家身上应当有能力");
        helper.assertTrue(respawnedVars.getStage() == 1,
                "重生后应当还是 1，实测 " + respawnedVars.getStage());

        // ② 存档 → 读档
        CompoundTag saved = original.serializeNBT();
        Player reloaded = helper.makeMockPlayer();
        progressive_tense reloadedVars = player_variables.get(reloaded).orElse(null);
        helper.assertTrue(reloadedVars != null, "读档的玩家身上应当有能力");
        reloadedVars.setStage(-99);   // 先弄脏，确保那个 1 真的是从存档里读回来的

        reloaded.load(saved);
        progressive_tense afterLoad = player_variables.get(reloaded).orElse(null);
        helper.assertTrue(afterLoad != null, "读档后能力应当还在");
        helper.assertTrue(afterLoad.getStage() == 1,
                "读档后应当把 1 读回来，实测 " + afterLoad.getStage());

        // ③ 原玩家没被动过
        helper.assertTrue(vars.getStage() == 1, "原玩家身上应当还是 1，实测 " + vars.getStage());
        helper.succeed();
    }

    /**
     * 指令能读能写：用真的 {@link ServerPlayer}（{@code EntityArgument.players()} 要求执行实体是
     * 服务端玩家，{@code GameTestHelper} 那两个假玩家不是，所以这里直接 new 一个）。
     */
    @GameTest(template = TEMPLATE)
    public static void progressiveTenseCommandReadsAndWrites(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-server-player"));
        CommandSourceStack source = server.createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position());

        helper.assertTrue(server.getCommands().performPrefixedCommand(source,
                        "white_lily tense set @s stage 1") == 1,
                "set @s stage 1 应当改到 1 名玩家");
        helper.assertTrue(player_variables.of(player).getStage() == 1,
                "stage 应当被指令改成 1，实测 " + player_variables.of(player).getStage());

        helper.assertTrue(server.getCommands().performPrefixedCommand(source,
                        "white_lily tense set @s tense true") == 1,
                "set @s tense true 应当改到 1 名玩家");
        helper.assertTrue(player_variables.of(player).isTense(), "tense 应当被指令改成 true");

        helper.assertTrue(server.getCommands().performPrefixedCommand(source,
                        "white_lily tense get @s") == 1,
                "get @s 应当读到 1 名玩家");

        // @a 解析与执行都没问题；GameTestServer 的玩家列表是空的，所以选到 0 个
        // （游戏里 @a 就是正常的"所有玩家"，得在游戏里验）
        helper.assertTrue(server.getCommands().performPrefixedCommand(source,
                        "white_lily tense set @a stage 9") == 0,
                "@a 在这个测试环境里应当选到 0 名玩家（玩家列表为空）");
        helper.assertTrue(player_variables.of(player).getStage() == 1,
                "既然没人被选中，stage 应当还是 1，实测 " + player_variables.of(player).getStage());
        helper.succeed();
    }

    /**
     * 游戏规则 {@code Happy_mode}：注册了、值会落进世界 json、<b>游戏内改不动</b>、建世界界面能改。
     *
     * <p>判定依据：原版游戏规则界面（建世界/编辑存档）调的是 {@code set(值, null)}，
     * 而 {@code /gamerule} 命令传的是真的服务器实例——所以测试里就用这两种调用区分「界面」与「游戏内」。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void happyModeIsFrozenInGame(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = debug_file.path(server);
        byte[] backup = readOrNull(file);
        GameRules.BooleanValue rule = server.getGameRules().getRule(happy_mode.RULE);
        boolean originalValue = rule.get();

        try {
            // 摆一个「json 说 true」的局面
            Files.writeString(file,
                    "{\n  \"" + debug_file.LEVEL_KEY + "\": 2,\n  \""
                            + debug_file.HAPPY_MODE_KEY + "\": true\n}",
                    StandardCharsets.UTF_8);
            helper.assertTrue(debug_file.readHappyMode(server).orElse(false),
                    "json 里应当读到 happy_mode = true");
            helper.assertTrue(debug_file.loadOrCreateLevel(server) == 2, "档位仍应当是 2");

            // ① 游戏内改（server != null）→ 被拉回 json 里的 true
            rule.set(false, server);
            helper.assertTrue(rule.get(),
                    "游戏内把 Happy_mode 改成 false 应当被拉回 true，实测 " + rule.get());

            // ①b 走真实指令（玩家在游戏里就是这条）：/gamerule 的路径是
            // updateFromArgument（直接写字段）+ onChanged(真 server)，和上面走的是同一个回调
            CommandSourceStack console = server.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(console, "gamerule Happy_mode false");
            helper.assertTrue(rule.get(),
                    "/gamerule Happy_mode false 应当被拉回 json 里的 true，实测 " + rule.get());

            // ② 建世界界面那条路（server == null）→ 放行
            rule.set(false, null);
            helper.assertTrue(!rule.get(), "创建界面那条路（set 的 server 为 null）应当能改");

            // ③ 再在游戏内改一次 → 又被拉回
            rule.set(false, server);
            helper.assertTrue(rule.get(), "游戏内再改一次应当又被拉回 true");

            // ④ 老 json 缺 happy_mode 时会被补上
            Files.writeString(file, "{\n  \"" + debug_file.LEVEL_KEY + "\": 3\n}",
                    StandardCharsets.UTF_8);
            helper.assertTrue(debug_file.readHappyMode(server).isEmpty(),
                    "前置条件：这份 json 里没有 happy_mode");
            debug_file.loadOrCreateLevel(server);
            helper.assertTrue(debug_file.readHappyMode(server).isPresent(),
                    "缺少 happy_mode 的老 json 应当被补上");

            // ⑤ 模组自己写的那份也带着 happy_mode
            debug_file.write(server, 3);
            helper.assertTrue(debug_file.readHappyMode(server).isPresent(),
                    "write() 写出来的 json 应当带 happy_mode");
        } catch (IOException e) {
            helper.assertTrue(false, "读写世界 json 失败：" + e);
        } finally {
            restoreFile(file, backup);
            rule.set(originalValue, null);   // 用 null 绕过「游戏内禁止改」的限制还原
        }

        helper.succeed();
    }

    /** Happy_mode 的三条文本（名字 / 说明 / 彩蛋）中英都齐全，彩蛋颜色也按需求。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeTextIsLocalized(GameTestHelper helper) {
        for (String key : List.of(happy_mode.TITLE_KEY, happy_mode.INFO_KEY, happy_mode.PARTY_KEY)) {
            helper.assertTrue(langValue("zh_cn", key) != null, "zh_cn.json 缺少 " + key);
            helper.assertTrue(langValue("en_us", key) != null, "en_us.json 缺少 " + key);
        }

        helper.assertTrue("快乐模式！！！".equals(langValue("zh_cn", happy_mode.TITLE_KEY)),
                "中文名应当是「快乐模式！！！」，实测 " + langValue("zh_cn", happy_mode.TITLE_KEY));
        helper.assertTrue("HAPPY MODE!!!".equals(langValue("en_us", happy_mode.TITLE_KEY)),
                "英文名应当是 HAPPY MODE!!!，实测 " + langValue("en_us", happy_mode.TITLE_KEY));

        String zhParty = langValue("zh_cn", happy_mode.PARTY_KEY);
        String enParty = langValue("en_us", happy_mode.PARTY_KEY);
        helper.assertTrue(zhParty != null && zhParty.startsWith("\u00a74"),
                "中文彩蛋应当以 §4 开头，实测 " + zhParty);
        helper.assertTrue(enParty != null && enParty.startsWith("\u00a7c"),
                "英文彩蛋应当以 §c 开头，实测 " + enParty);
        helper.succeed();
    }

    // ==================================================================
    // 四、Happy_mode 的回血规则（mixin 接管 FoodData#tick）
    //
    // 这些测试直接调 FoodData#tick(player)：开发环境里 mixin 是真生效的，
    // 所以走的就是游戏里那条路（不是把规则单独拎出来测）。
    // ==================================================================

    /** 默认属性值：180 秒到点回 1 点血、扣 1 点饱和度。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeRegenUsesDefaultInterval(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(10.0F);
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(20.0F);
            food.setExhaustion(0.0F);

            for (int i = 0; i < happy_mode_food.BASE_INTERVAL_TICKS - 1; i++) {
                food.tick(player);
            }
            expect(helper, 10.0D, player.getHealth(), "间隔没到之前不该回血");
            expect(helper, 20.0D, food.getSaturationLevel(), "间隔没到之前不该扣饱和度");

            food.tick(player);
            expect(helper, 11.0D, player.getHealth(), "180 秒到点应当回 1 点血");
            expect(helper, 19.0D, food.getSaturationLevel(), "每次恢复应当扣 1 点饱和度");
        });
        helper.succeed();
    }

    /** Recovery_interval / Restore_health / stamina 三个属性都按需求生效（含负数用法）。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeRegenFollowsItsAttributes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(10.0F);
            // Recovery_interval = -60 → 间隔 180 + 60 = 240 秒
            player.getAttribute(attributes.RECOVERY_INTERVAL.get()).setBaseValue(-60.0D);
            // Restore_health = 10 → 一次回 10 点
            player.getAttribute(attributes.RESTORE_HEALTH.get()).setBaseValue(10.0D);
            // stamina = -1 → 每次扣 2 点饱和度
            player.getAttribute(attributes.STAMINA.get()).setBaseValue(-1.0D);

            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(20.0F);
            food.setExhaustion(0.0F);

            int ticks = happy_mode_food.intervalTicks(-60.0D, false);
            expect(helper, 240.0D * 20.0D, ticks, "Recovery_interval = -60 应当是 240 秒");

            for (int i = 0; i < ticks - 1; i++) {
                food.tick(player);
            }
            expect(helper, 10.0D, player.getHealth(), "240 秒没到之前不该回血");

            food.tick(player);
            expect(helper, 20.0D, player.getHealth(), "Restore_health = 10 应当一次回 10 点");
            expect(helper, 18.0D, food.getSaturationLevel(), "stamina = -1 应当一次扣 2 点饱和度");
        });
        helper.succeed();
    }

    /** stamina 大于 0：把「每次扣 1 点」摊成「每 N 次扣 1 点」。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeRegenSpreadsSaturationCostWithStamina(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(10.0F);
            player.getAttribute(attributes.STAMINA.get()).setBaseValue(1.0D);   // 每 2 次才扣 1 点

            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(20.0F);
            food.setExhaustion(0.0F);

            for (int i = 0; i < happy_mode_food.BASE_INTERVAL_TICKS; i++) {
                food.tick(player);
            }
            expect(helper, 11.0D, player.getHealth(), "第 1 次恢复应当已经生效");
            expect(helper, 20.0D, food.getSaturationLevel(), "stamina = 1 时第 1 次恢复不该扣饱和度");

            for (int i = 0; i < happy_mode_food.BASE_INTERVAL_TICKS; i++) {
                food.tick(player);
            }
            expect(helper, 12.0D, player.getHealth(), "第 2 次恢复也应当生效");
            expect(helper, 19.0D, food.getSaturationLevel(), "stamina = 1 时第 2 次恢复应当扣 1 点");
        });
        helper.succeed();
    }

    /**
     * 饥饿值不满（但 ≥ 6）且有饱和度：按需求原话「使 Recovery_interval 减少 120」——
     * 也就是把属性值 -120 之后再算，等价于间隔 +120 秒。
     */
    @GameTest(template = TEMPLATE)
    public static void happyModeRegenExtendsIntervalWhenHungerIsNotFull(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(10.0F);
            FoodData food = player.getFoodData();
            food.setFoodLevel(10);
            food.setSaturation(20.0F);
            food.setExhaustion(0.0F);

            int ticks = happy_mode_food.intervalTicks(0.0D, true);
            expect(helper, 300.0D * 20.0D, ticks,
                    "Recovery_interval 减 120 之后应当是 300 秒（180 - (-120)）");

            for (int i = 0; i < ticks - 1; i++) {
                food.tick(player);
            }
            expect(helper, 10.0D, player.getHealth(), "300 秒没到之前不该回血");

            food.tick(player);
            expect(helper, 11.0D, player.getHealth(), "300 秒到点应当回 1 点血");
            expect(helper, 19.0D, food.getSaturationLevel(), "这次扣的还是饱和度");
        });
        helper.succeed();
    }

    /** 饱和度为 0 时把饥饿值当饱和度用：固定 180 秒、固定扣 1 点饥饿值、且不吃属性。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeRegenFallsBackToHungerWhenSaturationIsZero(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(10.0F);
            // 这两个属性在「只剩饥饿值」那条路上必须被无视
            player.getAttribute(attributes.RECOVERY_INTERVAL.get()).setBaseValue(60.0D);
            player.getAttribute(attributes.RESTORE_HEALTH.get()).setBaseValue(50.0D);
            player.getAttribute(attributes.STAMINA.get()).setBaseValue(-5.0D);

            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(0.0F);
            food.setExhaustion(0.0F);

            for (int i = 0; i < happy_mode_food.BASE_INTERVAL_TICKS - 1; i++) {
                food.tick(player);
            }
            expect(helper, 10.0D, player.getHealth(), "固定间隔没到之前不该回血");
            expect(helper, 20.0D, food.getFoodLevel(), "固定间隔没到之前不该扣饥饿值");

            food.tick(player);
            expect(helper, 11.0D, player.getHealth(), "这条固定回 1 点（无视 Restore_health）");
            expect(helper, 19.0D, food.getFoodLevel(), "这条每次扣 1 点饥饿值");
            expect(helper, 0.0D, food.getSaturationLevel(), "没有饱和度时不该凭空产生饱和度");
        });
        helper.succeed();
    }

    /** 饥饿值低于 6：不管有没有饱和度，都不再消耗任何东西换血。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeRegenStopsWhenHungerIsBelowSix(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(10.0F);
            FoodData food = player.getFoodData();

            // ① 只剩饥饿值（5 点）
            food.setFoodLevel(5);
            food.setSaturation(0.0F);
            food.setExhaustion(0.0F);
            for (int i = 0; i < happy_mode_food.BASE_INTERVAL_TICKS * 2; i++) {
                food.tick(player);
            }
            expect(helper, 10.0D, player.getHealth(), "饥饿值 5 时不该回血");
            expect(helper, 5.0D, food.getFoodLevel(), "饥饿值 5 时不该被扣");

            // ② 有饱和度但饥饿值只有 5
            food.setFoodLevel(5);
            food.setSaturation(20.0F);
            for (int i = 0; i < happy_mode_food.BASE_INTERVAL_TICKS * 2; i++) {
                food.tick(player);
            }
            expect(helper, 10.0D, player.getHealth(), "饥饿值 5 时也不该拿饱和度换血");
            expect(helper, 20.0D, food.getSaturationLevel(), "饥饿值 5 时不该被扣饱和度");
        });
        helper.succeed();
    }

    /**
     * Building_endurance：每 1 点把「储存饱和度」的上限比饥饿值抬高 1——
     * <b>只在 Happy_mode 开启时生效</b>（关掉的世界里上限就该和原版一样是饥饿值）。
     */
    @GameTest(template = TEMPLATE)
    public static void buildingEnduranceRaisesSaturationCap(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        FoodData food = player.getFoodData();
        player.getAttribute(attributes.BUILDING_ENDURANCE.get()).setBaseValue(5.0D);

        // 开着 Happy_mode：上限被抬到 20 + 5 = 25
        withHappyMode(helper, player, () -> {
            food.setFoodLevel(20);
            food.setSaturation(19.0F);
            player.eat(helper.getLevel(), new ItemStack(Items.COOKED_BEEF));
            expect(helper, 25.0D, food.getSaturationLevel(),
                    "Building_endurance = 5 时上限应当是 25（19 + 12.8 = 31.8 夹到 25）");
        });

        // 关掉 Happy_mode：即使属性是 5，也该和原版一样夹在饥饿值 20
        GameRules.BooleanValue rule =
                helper.getLevel().getServer().getGameRules().getRule(happy_mode.RULE);
        boolean original = rule.get();
        try {
            rule.set(false, null);
            food.setFoodLevel(20);
            food.setSaturation(19.0F);
            player.eat(helper.getLevel(), new ItemStack(Items.COOKED_BEEF));
            expect(helper, 20.0D, food.getSaturationLevel(),
                    "关掉 Happy_mode 时上限应当回到饥饿值 20");
        } finally {
            rule.set(original, null);
        }

        helper.succeed();
    }

    /** 关掉 Happy_mode 时，原版自然恢复必须原样不动（10 刻回 1 点）。 */
    @GameTest(template = TEMPLATE)
    public static void vanillaRegenIsUntouchedWhileHappyModeIsOff(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        GameRules.BooleanValue rule =
                helper.getLevel().getServer().getGameRules().getRule(happy_mode.RULE);
        boolean original = rule.get();
        try {
            rule.set(false, null);
            player.setHealth(10.0F);
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(20.0F);
            food.setExhaustion(0.0F);

            for (int i = 0; i < 10; i++) {
                food.tick(player);
            }
            expect(helper, 11.0D, player.getHealth(),
                    "关掉 Happy_mode 时应当走原版：10 刻回 1 点");
        } finally {
            rule.set(original, null);
        }
        helper.succeed();
    }

    // ==================================================================
    // 五、Happy_mode 的「消耗计数器」（hunger_activity + hunger_state）
    //
    // 纯数值那几条直接测 happy_mode_hunger；行为那几条调 hunger_activity.tick(player)
    // （就是我们挂在 PlayerTickEvent 上的那个方法，GameTest 里不走事件）。
    // 计数器是玩家能力，所以用「生存假玩家」（创造假玩家的 abilities 不一样）。
    // ==================================================================

    /** 计数器上限：默认 40，每 +1 stamina +30，每 -1 stamina -10，但不会低于 10。 */
    @GameTest(template = TEMPLATE)
    public static void hungerCounterMaxFollowsStamina(GameTestHelper helper) {
        helper.assertTrue(happy_mode_hunger.counterMax(0.0D) == 40,
                "stamina=0 时上限应当是 40，实测 " + happy_mode_hunger.counterMax(0.0D));
        helper.assertTrue(happy_mode_hunger.counterMax(1.0D) == 70,
                "stamina=1 时上限应当是 70，实测 " + happy_mode_hunger.counterMax(1.0D));
        helper.assertTrue(happy_mode_hunger.counterMax(2.0D) == 100,
                "stamina=2 时上限应当是 100，实测 " + happy_mode_hunger.counterMax(2.0D));
        helper.assertTrue(happy_mode_hunger.counterMax(-1.0D) == 30,
                "stamina=-1 时上限应当是 30，实测 " + happy_mode_hunger.counterMax(-1.0D));
        helper.assertTrue(happy_mode_hunger.counterMax(-4.0D) == 10,
                "stamina=-4 时算出来是 0，应当被下限 10 兜住，实测 " + happy_mode_hunger.counterMax(-4.0D));
        helper.assertTrue(happy_mode_hunger.counterMax(-100.0D) == 10,
                "stamina 再低上限也不会低于 10，实测 " + happy_mode_hunger.counterMax(-100.0D));
        helper.succeed();
    }

    /** 移动消耗按状态三选一：游泳 3/米、疾跑 1/米、普通移动 0.5/4 米。 */
    @GameTest(template = TEMPLATE)
    public static void hungerMovementCostPicksOneMode(GameTestHelper helper) {
        expect(helper, 3.0D, happy_mode_hunger.movementCostPerMeter(true, false), "游泳每米 3");
        expect(helper, 3.0D, happy_mode_hunger.movementCostPerMeter(true, true), "游泳优先于疾跑");
        expect(helper, 1.0D, happy_mode_hunger.movementCostPerMeter(false, true), "疾跑每米 1");
        expect(helper, 0.125D, happy_mode_hunger.movementCostPerMeter(false, false),
                "普通移动每米 0.125（每 4 米 0.5）");
        helper.succeed();
    }

    /** 跳跃：普通 2 点，疾跑中 4 点（替代，不是叠加）。 */
    @GameTest(template = TEMPLATE)
    public static void hungerJumpCostFollowsSprint(GameTestHelper helper) {
        expect(helper, 2.0D, happy_mode_hunger.jumpCost(false), "普通跳跃 2 点");
        expect(helper, 4.0D, happy_mode_hunger.jumpCost(true), "疾跑跳 4 点");
        helper.succeed();
    }

    /** 状态效果：饥饿每等级每秒 -2、饱和每等级每秒 +4（换算成每刻）。 */
    @GameTest(template = TEMPLATE)
    public static void hungerEffectCostsFollowLevels(GameTestHelper helper) {
        expect(helper, 0.1D, happy_mode_hunger.hungerEffectCostPerTick(0), "饥饿 I 每刻 -0.1");
        expect(helper, 0.2D, happy_mode_hunger.hungerEffectCostPerTick(1), "饥饿 II 每刻 -0.2");
        expect(helper, 0.2D, happy_mode_hunger.saturationEffectGainPerTick(0), "饱和 I 每刻 +0.2");
        expect(helper, 0.4D, happy_mode_hunger.saturationEffectGainPerTick(1), "饱和 II 每刻 +0.4");
        helper.succeed();
    }

    /** 计数器见底：扣 1 点饱和度，然后填回上限。 */
    @GameTest(template = TEMPLATE)
    public static void hungerCounterPaysSaturationWhenDepleted(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        withHappyMode(helper, player, () -> {
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(5.0F);

            hunger_state state = player_variables.hunger(player).orElse(null);
            helper.assertTrue(state != null, "玩家身上应当挂着消耗计数器");
            state.setCounter(0.4F);

            hunger_activity.tick(player);          // 第一刻：只采样位置
            player.setPos(player.getX() + 4.0D, player.getY(), player.getZ());
            hunger_activity.tick(player);          // 走 4 米 → 普通移动 0.5 → 见底

            expect(helper, 4.0D, food.getSaturationLevel(), "见底应当扣 1 点饱和度");
            expect(helper, 40.0D, state.getCounter(), "扣完应当填回上限 40");
        });
        helper.succeed();
    }

    /** 没有饱和度时，见底改扣 1 点饥饿值。 */
    @GameTest(template = TEMPLATE)
    public static void hungerCounterFallsBackToFoodWhenSaturationIsZero(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        withHappyMode(helper, player, () -> {
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(0.0F);

            hunger_state state = player_variables.hunger(player).orElse(null);
            helper.assertTrue(state != null, "玩家身上应当挂着消耗计数器");
            state.setCounter(0.2F);

            hunger_activity.tick(player);
            player.setPos(player.getX() + 4.0D, player.getY(), player.getZ());
            hunger_activity.tick(player);

            expect(helper, 19.0D, food.getFoodLevel(), "没有饱和度时应当扣 1 点饥饿值");
            expect(helper, 40.0D, state.getCounter(), "扣完应当填回上限 40");
        });
        helper.succeed();
    }

    /** 没开 Happy_mode 时，这套计数器什么都不做（原版疲劳值那一套照旧）。 */
    @GameTest(template = TEMPLATE)
    public static void hungerCounterIsIgnoredWhenHappyModeIsOff(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        GameRules.BooleanValue rule =
                helper.getLevel().getServer().getGameRules().getRule(happy_mode.RULE);
        boolean original = rule.get();
        try {
            rule.set(false, null);

            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(5.0F);

            hunger_state state = player_variables.hunger(player).orElse(null);
            helper.assertTrue(state != null, "玩家身上应当挂着消耗计数器");
            state.setCounter(0.2F);

            hunger_activity.tick(player);
            player.setPos(player.getX() + 4.0D, player.getY(), player.getZ());
            hunger_activity.tick(player);

            expect(helper, 5.0D, food.getSaturationLevel(), "关掉 Happy_mode 时不该扣饱和度");
            expect(helper, 0.2D, state.getCounter(), "关掉 Happy_mode 时计数器不该动");
        } finally {
            rule.set(original, null);
        }
        helper.succeed();
    }

    /** 饥饿归零：属性修正挂上、每 2 秒掉 1 点意志值、每 12 秒扣 20% 最大生命值。 */
    @GameTest(template = TEMPLATE)
    public static void starvingAppliesDebuffsAndDrains(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        withHappyMode(helper, player, () -> {
            player.setHealth(20.0F);
            FoodData food = player.getFoodData();
            food.setFoodLevel(0);
            food.setSaturation(0.0F);

            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            double normalSpeed = speed.getValue();
            AttributeInstance spirit = player.getAttribute(attributes.STEADFAST_IN_SPIRIT.get());
            double normalSpirit = spirit.getBaseValue();

            hunger_activity.tick(player);   // 第 1 刻：挂修正、起计时
            expect(helper, normalSpeed * 0.4D, speed.getValue(), "饿到归零时移动速度应当 -60%");

            for (int i = 0; i < happy_mode_hunger.spiritLossIntervalTicks(); i++) {
                hunger_activity.tick(player);
            }
            expect(helper, normalSpirit - 1.0D, spirit.getBaseValue(), "每 2 秒应当掉 1 点意志坚定值");

            for (int i = 0; i < happy_mode_hunger.healthDrainIntervalTicks(); i++) {
                hunger_activity.tick(player);
            }
            expect(helper, 16.0D, player.getHealth(), "每 12 秒应当扣最大生命值的 20%（20 → 16）");
        });
        helper.succeed();
    }

    /** 饿回来（饥饿值 > 0）时，四条属性修正要摘掉。 */
    @GameTest(template = TEMPLATE)
    public static void starvingDebuffsAreRemovedWhenHungerReturns(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        withHappyMode(helper, player, () -> {
            FoodData food = player.getFoodData();
            food.setFoodLevel(0);
            hunger_activity.tick(player);

            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            expect(helper, 0.04D, speed.getValue(), "饿到归零时速度应当是 0.1 × 0.4 = 0.04");

            food.setFoodLevel(10);
            hunger_activity.tick(player);
            expect(helper, 0.1D, speed.getValue(), "吃饱后速度修正应当被摘掉");
        });
        helper.succeed();
    }

    /** 开 Happy_mode 时必须取消原版「饥饿值归零每 80 刻扣 1 血」的饿死伤害；关掉时照旧。 */
    @GameTest(template = TEMPLATE)
    public static void happyModeCancelsVanillaStarvationDamage(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        player.setHealth(20.0F);
        FoodData food = player.getFoodData();
        food.setFoodLevel(0);
        food.setSaturation(0.0F);

        withHappyMode(helper, player, () -> {
            for (int i = 0; i < 400; i++) {
                food.tick(player);
            }
            expect(helper, 20.0D, player.getHealth(), "开 Happy_mode 时原版饿死伤害应当被取消");
        });

        GameRules.BooleanValue rule =
                helper.getLevel().getServer().getGameRules().getRule(happy_mode.RULE);
        boolean original = rule.get();
        try {
            rule.set(false, null);
            player.setHealth(20.0F);
            food.setFoodLevel(0);
            food.setSaturation(0.0F);
            for (int i = 0; i < 80; i++) {
                food.tick(player);
            }
            expect(helper, 19.0D, player.getHealth(), "关掉时原版每 80 刻应当扣 1 点血");
        } finally {
            rule.set(original, null);
        }
        helper.succeed();
    }

    /** 枯竭伤害类型注册了、绕过盔甲/附魔，且随机播报确实是 17 条。 */
    @GameTest(template = TEMPLATE)
    public static void depletionDamageTypeIsRegisteredAndBypassesArmor(GameTestHelper helper) {
        Holder<DamageType> holder = depletion.holder(helper.getLevel());
        helper.assertTrue(holder != null, "white_lily:depletion 伤害类型应当已经注册");
        helper.assertTrue(holder.is(DamageTypeTags.BYPASSES_ARMOR), "枯竭伤害应当无视盔甲");
        helper.assertTrue(holder.is(DamageTypeTags.BYPASSES_ENCHANTMENTS), "枯竭伤害应当无视附魔");
        helper.assertTrue(depletion.DEATH_MESSAGE_COUNT == 17,
                "随机死亡播报应当是 17 条，实测 " + depletion.DEATH_MESSAGE_COUNT);
        helper.succeed();
    }

    /** 17 条死亡播报的语言键在两份 lang 里都存在（内容是空的，等作者自己填）。 */
    @GameTest(template = TEMPLATE)
    public static void depletionDeathMessagesAreLocalized(GameTestHelper helper) {
        for (String locale : List.of("zh_cn", "en_us")) {
            for (int i = 1; i <= depletion.DEATH_MESSAGE_COUNT; i++) {
                String key = depletion.deathMessageKey(i);
                helper.assertTrue(langValue(locale, key) != null,
                        locale + " 里应当有 " + key + " 这个键（留空等作者填）");
            }
        }
        helper.succeed();
    }

    /**
     * 临时打开 Happy_mode 跑一段断言，跑完还原。
     *
     * <p>Happy_mode 是「游戏内改不动」的（值会被世界 json 拉回），所以必须用
     * {@code set(值, null)} 那条「建世界界面」的路才改得动。</p>
     */
    // ==================================================================
    // 十、全局法强 / 全局法抗（procedures 包）
    // ==================================================================

    /**
     * 「元素类属性」的界定：命名空间 irons_spellbooks、注册名以 _spell_power / _magic_resist 结尾。
     * ISS 没给学派属性打过 tag，所以按注册名枚举；顺带确认 ISS 自己的全局 spell_power/spell_resist
     * 不会被误纳（否则同一次加成会算两遍）。
     */
    @GameTest(template = TEMPLATE)
    public static void allSpellPowerTargetsAreEveryIssSchoolAttribute(GameTestHelper helper) {
        List<Attribute> power = spell_power_procedures.schoolPowerAttributes();
        List<Attribute> resist = spell_power_procedures.schoolResistAttributes();

        helper.assertTrue(power.size() == 9, "ISS 应当有 9 条学派法强，实测 " + power.size());
        helper.assertTrue(resist.size() == 9, "ISS 应当有 9 条学派法抗，实测 " + resist.size());

        for (String school : List.of("fire", "ice", "lightning", "holy", "ender", "blood",
                "evocation", "nature", "eldritch")) {
            helper.assertTrue(containsIssAttribute(power, school + "_spell_power"),
                    "学派法强不该漏掉 " + school);
            helper.assertTrue(containsIssAttribute(resist, school + "_magic_resist"),
                    "学派法抗不该漏掉 " + school);
        }

        helper.assertTrue(!containsIssAttribute(power, "spell_power"),
                "ISS 自己的全局 spell_power 不该被纳入");
        helper.assertTrue(!containsIssAttribute(resist, "spell_resist"),
                "ISS 自己的全局 spell_resist 不该被纳入");
        helper.succeed();
    }

    /** all_spell_power = 0.10 → 9 条学派法强全部 +10%；不影到法抗、也不改写 ISS 的全局法强。 */
    @GameTest(template = TEMPLATE)
    public static void allSpellPowerFeedsEveryIssSchoolPower(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();

        expect(helper, 1.0D, issValue(player, "fire_spell_power"),
                "没加任何东西时学派法强应当就是默认 1.0");

        player.getAttribute(attributes.ALL_SPELL_POWER.get()).setBaseValue(0.10D);
        spell_power_procedures.sync(player);

        for (Attribute target : spell_power_procedures.schoolPowerAttributes()) {
            expect(helper, 1.10D, player.getAttributeValue(target),
                    "学派法强 " + attributeId(target) + " 应当 +10%");
        }

        expect(helper, 1.0D, issValue(player, "fire_magic_resist"),
                "全局法强不该动到法抗");
        expect(helper, 1.0D, issValue(player, "spell_power"),
                "ISS 自己的全局 spell_power 不该被本模组改写（否则加成会算两遍）");
        expect(helper, 0.10D, player.getAttributeValue(attributes.ALL_SPELL_POWER.get()),
                "全局法强自身数值不该被自己改掉");
        helper.succeed();
    }

    /** all_spell_resis = 0.10 → 9 条学派法抗全部 +10%；不影到法强。 */
    @GameTest(template = TEMPLATE)
    public static void allSpellResisFeedsEveryIssSchoolResist(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();

        expect(helper, 1.0D, issValue(player, "fire_magic_resist"),
                "没加任何东西时学派法抗应当也是默认 1.0（ISS 用 2 - softCap(值) 当伤害系数，1.0 = 无抗）");

        player.getAttribute(attributes.ALL_SPELL_RESIS.get()).setBaseValue(0.10D);
        spell_power_procedures.sync(player);

        for (Attribute target : spell_power_procedures.schoolResistAttributes()) {
            expect(helper, 1.10D, player.getAttributeValue(target),
                    "学派法抗 " + attributeId(target) + " 应当 +10%");
        }

        expect(helper, 1.0D, issValue(player, "fire_spell_power"),
                "全局法抗不该动到法强");
        expect(helper, 1.0D, issValue(player, "spell_resist"),
                "ISS 自己的全局 spell_resist 不该被本模组改写");
        helper.succeed();
    }

    /** 两条属性都可以为负数：−0.10 → 学派值变成 0.90（法强变相降幅、法抗变相成为负抗）。 */
    @GameTest(template = TEMPLATE)
    public static void allSpellAttributesAcceptNegativeValues(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAttribute(attributes.ALL_SPELL_POWER.get()).setBaseValue(-0.10D);
        player.getAttribute(attributes.ALL_SPELL_RESIS.get()).setBaseValue(-0.25D);
        spell_power_procedures.sync(player);

        for (Attribute target : spell_power_procedures.schoolPowerAttributes()) {
            expect(helper, 0.90D, player.getAttributeValue(target),
                    "全局法强 = -0.10 时 " + attributeId(target) + " 应当是 0.90");
        }
        for (Attribute target : spell_power_procedures.schoolResistAttributes()) {
            expect(helper, 0.75D, player.getAttributeValue(target),
                    "全局法抗 = -0.25 时 " + attributeId(target) + " 应当是 0.75");
        }
        helper.succeed();
    }

    /** 值回到 0（或来源属性消失）时，修饰符要被摘掉，不留残留。 */
    @GameTest(template = TEMPLATE)
    public static void allSpellPowerZeroRemovesTheDerivedModifiers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Attribute school = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation(spell_power_procedures.ISS_NAMESPACE, "fire_spell_power"));
        AttributeInstance instance = player.getAttribute(school);
        helper.assertTrue(instance != null, "假玩家应当被挂上了 ISS 学派法强属性");

        player.getAttribute(attributes.ALL_SPELL_POWER.get()).setBaseValue(0.10D);
        spell_power_procedures.sync(player);
        helper.assertTrue(instance.getModifier(spell_power_procedures.POWER_MODIFIER_ID) != null,
                "加了值之后应当挂上了派生修饰符");

        player.getAttribute(attributes.ALL_SPELL_POWER.get()).setBaseValue(0.0D);
        spell_power_procedures.sync(player);
        helper.assertTrue(instance.getModifier(spell_power_procedures.POWER_MODIFIER_ID) == null,
                "值回到 0 之后派生修饰符应当被摘掉");
        expect(helper, 1.0D, instance.getValue(), "摘掉修饰符后应当退回默认 1.0");
        helper.succeed();
    }

    /**
     * 端到端：真正走 ISS 的 {@code SchoolType#getPowerFor/getResistanceFor}，
     * 确认本模组两条属性确实流到了 ISS 的学派结算入口。
     */
    @GameTest(template = TEMPLATE)
    public static void allSpellAttributesReachIssSpellSchools(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getAttribute(attributes.ALL_SPELL_POWER.get()).setBaseValue(0.25D);
        player.getAttribute(attributes.ALL_SPELL_RESIS.get()).setBaseValue(-0.20D);
        spell_power_procedures.sync(player);

        IForgeRegistry<SchoolType> schools = SchoolRegistry.REGISTRY.get();
        helper.assertTrue(schools != null && schools.getValues().size() >= 9,
                "ISS 学派注册表应当至少有 9 个学派");

        for (SchoolType school : schools.getValues()) {
            expect(helper, 1.25D, school.getPowerFor(player),
                    "学派 " + school.getId() + " 的 getPowerFor 应当是 1.25");
            expect(helper, 0.80D, school.getResistanceFor(player),
                    "学派 " + school.getId() + " 的 getResistanceFor 应当是 0.80");
        }
        helper.succeed();
    }

    // ==================================================================
    // 十一、铳械精通 / 铳械增幅（procedures 包，TaCZ 枪械伤害）
    // ==================================================================

    /**
     * 公式层：照 zh_cn desc 的原例逐条核对——增幅是固定值、精通是百分比，
     * 先固定后百分比，负数照算，压到 0 就停下。
     */
    @GameTest(template = TEMPLATE)
    public static void rifleFormulaFollowsDescription(GameTestHelper helper) {
        // desc 原例：最终伤害 5、增幅 -1 → 4
        expect(helper, 4.0D, rifle_procedures.applyToGunDamage(5.0D, -1.0D, 0.0D),
                "增幅 -1 应当是 5 - 1 = 4");
        expect(helper, 5.0D, rifle_procedures.applyToGunDamage(5.0D, 0.0D, 0.0D),
                "两条都为 0 时不该改伤害");
        // 精通每 0.01 = +1% → 0.10 = +10%
        expect(helper, 11.0D, rifle_procedures.applyToGunDamage(10.0D, 0.0D, 0.10D),
                "精通 0.10 应当是 +10%");
        // 先固定后百分比：(10 + 2) × 1.10 = 13.2
        expect(helper, 13.2D, rifle_procedures.applyToGunDamage(10.0D, 2.0D, 0.10D),
                "应当先加固定值再乘百分比");
        // 负向衰减：精通 -0.10 → ×0.90
        expect(helper, 9.0D, rifle_procedures.applyToGunDamage(10.0D, 0.0D, -0.10D),
                "精通 -0.10 应当衰减 10%");
        // 压穿就钳在 0（不能让伤害变负数，那会变成反向治疗）
        expect(helper, 0.0D, rifle_procedures.applyToGunDamage(1.0D, -5.0D, 0.0D),
                "增幅把伤害压穿后应当钳在 0");
        expect(helper, 0.0D, rifle_procedures.applyToGunDamage(10.0D, 0.0D, -1.0D),
                "精通 -1（倍率 0）应当钳在 0");
        helper.succeed();
    }

    /** 非枪械类型的伤害不该被两条属性动到。 */
    @GameTest(template = TEMPLATE)
    public static void rifleHandlerIgnoresNonGunDamage(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        setRifleAttributes(shooter, 10.0D, 1.0D);

        LivingHurtEvent event = new LivingHurtEvent(
                shooter, helper.getLevel().damageSources().generic(), 10.0F);
        rifle_procedures.onLivingHurt(event);
        expect(helper, 10.0D, event.getAmount(), "普通伤害不该被铳械属性改写");
        helper.succeed();
    }

    /** 枪械伤害：按射手的属性结算（(10 + 2) × 1.10 = 13.2）。 */
    @GameTest(template = TEMPLATE)
    public static void rifleHandlerAppliesToGunDamage(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        setRifleAttributes(shooter, 0.10D, 2.0D);

        // TaCZ 建伤害源的方式：类型 tacz:bullet，直接实体=子弹，造成实体=枪的主人
        DamageSource bullet = ModDamageTypes.Sources.bullet(
                helper.getLevel().registryAccess(), shooter, shooter, false);
        helper.assertTrue(bullet.is(ModDamageTypes.BULLETS_TAG),
                "构造出来的伤害源应当属于 tacz:bullets 标签");

        LivingHurtEvent event = new LivingHurtEvent(victim, bullet, 10.0F);
        rifle_procedures.onLivingHurt(event);
        expect(helper, 13.2D, event.getAmount(), "枪械伤害应当按射手的属性结算");
        helper.succeed();
    }

    /** 看的是射手的属性，不是被打者的——被打者挂再多也不行。 */
    @GameTest(template = TEMPLATE)
    public static void rifleHandlerReadsShooterNotVictim(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        setRifleAttributes(victim, 10.0D, 1.0D);

        DamageSource bullet = ModDamageTypes.Sources.bullet(
                helper.getLevel().registryAccess(), shooter, shooter, false);
        LivingHurtEvent event = new LivingHurtEvent(victim, bullet, 10.0F);
        rifle_procedures.onLivingHurt(event);
        expect(helper, 10.0D, event.getAmount(), "应当看射手的属性，而不是被打者的");
        helper.succeed();
    }

    /** 射手身上没挂这两条属性（例如僵尸开的枪）时完全不介入。 */
    @GameTest(template = TEMPLATE)
    public static void rifleHandlerIgnoresShootersWithoutTheAttributes(GameTestHelper helper) {
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        helper.assertTrue(shooter.getAttribute(attributes.RIFLE_EXPERTISE.get()) == null,
                "前置：僵尸身上不该有本模组的属性（它只挂在玩家上）");

        DamageSource bullet = ModDamageTypes.Sources.bullet(
                helper.getLevel().registryAccess(), shooter, shooter, false);
        LivingHurtEvent event = new LivingHurtEvent(shooter, bullet, 10.0F);
        rifle_procedures.onLivingHurt(event);
        expect(helper, 10.0D, event.getAmount(), "拿不到射手属性时不该改伤害");
        helper.succeed();
    }

    /** 处理器确实注册到了 Forge 总线上（投递一次真实事件）。 */
    @GameTest(template = TEMPLATE)
    public static void rifleHandlerIsRegisteredOnTheBus(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        setRifleAttributes(shooter, 0.0D, 0.5D);

        DamageSource bullet = ModDamageTypes.Sources.bullet(
                helper.getLevel().registryAccess(), shooter, shooter, false);
        LivingHurtEvent event = new LivingHurtEvent(victim, bullet, 4.0F);
        MinecraftForge.EVENT_BUS.post(event);
        // (4 + 0.5) × 1 = 4.5（TaCZ 的子弹抗性默认 0，不参与）
        expect(helper, 4.5D, event.getAmount(), "注册到总线上的处理器应当生效");
        helper.succeed();
    }

    /** 给玩家设上铳械精通 / 铳械增幅。 */
    private static void setRifleAttributes(Player player, double expertise, double enhancement) {
        player.getAttribute(attributes.RIFLE_EXPERTISE.get()).setBaseValue(expertise);
        player.getAttribute(attributes.RIFLE_ENHANCEMENT.get()).setBaseValue(enhancement);
    }

    // ==================================================================
    // 十二、法术增幅 / 法术暴击（procedures 包，ISS 法术伤害）
    // ==================================================================

    /**
     * 公式层：照 zh_cn desc 逐条核对——增幅固定值且截断到 1 位小数、暴击每 1 点 4% 区间且小数不算、
     * 先增幅后暴击、负数负向衰减、压穿钳 0。
     */
    @GameTest(template = TEMPLATE)
    public static void spellFormulaFollowsDescription(GameTestHelper helper) {
        // 增幅：属性 1 → +1
        expect(helper, 6.0D, spell_damage_procedures.applyToSpellDamage(5.0D, 1.0D, 0.0D, 0.0D),
                "增幅 1 应当 +1");
        // 增幅截断到小数点后一位：1.22 → 1.2
        expect(helper, 1.2D, spell_damage_procedures.applyToSpellDamage(0.0D, 1.22D, 0.0D, 0.0D),
                "增幅 1.22 应当只算 1.2");
        expect(helper, 8.8D, spell_damage_procedures.applyToSpellDamage(10.0D, -1.22D, 0.0D, 0.0D),
                "增幅 -1.22 应当只算 -1.2");

        // 暴击：1.83 → 只算 1 点 → 4% 区间
        expect(helper, 10.0D, spell_damage_procedures.applyToSpellDamage(10.0D, 0.0D, 1.83D, 0.0D),
                "roll=0 时不该有暴击加成");
        expect(helper, 10.4D, spell_damage_procedures.applyToSpellDamage(10.0D, 0.0D, 1.83D, 1.0D),
                "1 点暴击吃满区间应当是 +4%");
        expect(helper, 10.8D, spell_damage_procedures.applyToSpellDamage(10.0D, 0.0D, 2.0D, 2.0D),
                "roll 超过 1 应当按 1 算（2 点暴击最多 +8%）");

        // 先增幅后暴击：(10 + 2) × 1.04 = 12.48
        expect(helper, 12.48D, spell_damage_procedures.applyToSpellDamage(10.0D, 2.0D, 1.0D, 1.0D),
                "应当先加固定值再乘暴击区间");

        // 负数：暴击 -1.83 → 只算 -1 点 → -4%
        expect(helper, 9.6D, spell_damage_procedures.applyToSpellDamage(10.0D, 0.0D, -1.83D, 1.0D),
                "负暴击应当负向衰减");

        // 钳 0：决不允许负伤害
        expect(helper, 0.0D, spell_damage_procedures.applyToSpellDamage(1.0D, -5.0D, 0.0D, 0.0D),
                "增幅压穿后应当钳在 0");
        expect(helper, 0.0D, spell_damage_procedures.applyToSpellDamage(10.0D, 0.0D, -100.0D, 1.0D),
                "暴击压穿后应当钳在 0");
        helper.succeed();
    }

    /** 随机暴击跑很多次，结果必须一直落在 [基础, 基础 × 1.04]。 */
    @GameTest(template = TEMPLATE)
    public static void spellCritRangeStaysWithinItsBand(GameTestHelper helper) {
        for (int i = 0; i < 200; i++) {
            double out = spell_damage_procedures.applyToSpellDamage(10.0D, 0.0D, 1.0D);
            helper.assertTrue(out >= 10.0D - EPSILON && out <= 10.4D + EPSILON,
                    "1 点暴击的结果应当落在 [10, 10.4]，实测 " + out);
        }
        helper.succeed();
    }

    /** 法术伤害：按施法者的属性结算（10 + 2 = 12）。 */
    @GameTest(template = TEMPLATE)
    public static void spellDamageHandlerAppliesToCasterAttributes(GameTestHelper helper) {
        Player caster = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        setSpellAttributes(victim, 0.0D, 0.0D);
        setSpellAttributes(caster, 0.0D, 2.0D);

        SpellDamageEvent event = newSpellDamageEvent(caster, victim, 10.0F);
        spell_damage_procedures.onSpellDamage(event);
        expect(helper, 12.0D, event.getAmount(), "法术伤害应当按施法者的法术增幅结算");
        helper.succeed();
    }

    /** 两条都是 0 时不该改伤害。 */
    @GameTest(template = TEMPLATE)
    public static void spellDamageHandlerIgnoresZeroAttributes(GameTestHelper helper) {
        Player caster = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        setSpellAttributes(victim, 0.0D, 0.0D);
        setSpellAttributes(caster, 0.0D, 0.0D);

        SpellDamageEvent event = newSpellDamageEvent(caster, victim, 10.0F);
        spell_damage_procedures.onSpellDamage(event);
        expect(helper, 10.0D, event.getAmount(), "两条都是 0 时不该改伤害");
        helper.succeed();
    }

    /** 看的是施法者的属性，不是被打者的。 */
    @GameTest(template = TEMPLATE)
    public static void spellDamageHandlerReadsCasterNotVictim(GameTestHelper helper) {
        Player caster = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        helper.assertTrue(caster != victim, "makeMockPlayer() 每次应当返回不同实例，否则这个测试没意义");

        // 两边都显式写满，避开默认值与残留。
        setSpellAttributes(victim, 10.0D, 10.0D);
        setSpellAttributes(caster, 0.0D, 0.0D);

        SpellDamageEvent event = newSpellDamageEvent(caster, victim, 10.0F);
        spell_damage_procedures.onSpellDamage(event);
        expect(helper, 10.0D, event.getAmount(),
                "应当看施法者的属性（施法者 增幅=" + caster.getAttributeValue(attributes.SPELL_ENHANCEMENT.get())
                        + " 暴击=" + caster.getAttributeValue(attributes.SPELL_CRITICAL_STRIKE.get())
                        + "；被打者 增幅=" + victim.getAttributeValue(attributes.SPELL_ENHANCEMENT.get())
                        + " 暴击=" + victim.getAttributeValue(attributes.SPELL_CRITICAL_STRIKE.get()) + "）");
        helper.succeed();
    }

    /** 处理器确实注册到了 Forge 总线上（投递一次真实事件）。 */
    @GameTest(template = TEMPLATE)
    public static void spellDamageHandlerIsRegisteredOnTheBus(GameTestHelper helper) {
        Player caster = helper.makeMockPlayer();
        Player victim = helper.makeMockPlayer();
        setSpellAttributes(victim, 0.0D, 0.0D);
        setSpellAttributes(caster, 0.0D, 3.0D);

        SpellDamageEvent event = newSpellDamageEvent(caster, victim, 2.0F);
        MinecraftForge.EVENT_BUS.post(event);
        expect(helper, 5.0D, event.getAmount(), "注册到总线上的处理器应当生效");
        helper.succeed();
    }

    /** 给玩家设上法术暴击 / 法术增幅。 */
    private static void setSpellAttributes(Player player, double criticalStrike, double enhancement) {
        player.getAttribute(attributes.SPELL_CRITICAL_STRIKE.get()).setBaseValue(criticalStrike);
        player.getAttribute(attributes.SPELL_ENHANCEMENT.get()).setBaseValue(enhancement);
    }

    /** 造一个真实参数的法术伤害事件（用 ISS 的火焰球法术建伤害源）。 */
    private static SpellDamageEvent newSpellDamageEvent(Player caster, Player victim, float amount) {
        SpellDamageSource source = SpellDamageSource.source(
                caster, caster, SpellRegistry.FIREBALL_SPELL.get());
        return new SpellDamageEvent(victim, amount, source);
    }

    // ==================================================================
    // 十三、箭术精通 / 箭矢暴击 / 箭矢暴击伤害（procedures 包）
    // ==================================================================

    /** 公式层：精通固定值+钳 0、暴击概率按 desc 原例截断、暴伤按比例。 */
    @GameTest(template = TEMPLATE)
    public static void archeryFormulaFollowsDescription(GameTestHelper helper) {
        // 箭术精通：属性 1 → +1；负数衰减；压穿钳 0
        expect(helper, 6.0D, archery_procedures.expertInArcheryDamage(5.0D, 1.0D),
                "箭术精通 1 应当 +1");
        expect(helper, 8.0D, archery_procedures.expertInArcheryDamage(10.0D, -2.0D),
                "箭术精通 -2 应当衰减 2 点");
        expect(helper, 0.0D, archery_procedures.expertInArcheryDamage(1.0D, -5.0D),
                "压穿后应当钳在 0");

        // 箭矢暴击概率：desc 原例 0.0018 → 0.1%
        expect(helper, 0.001D, archery_procedures.arrowStrikeChance(0.0018D),
                "0.0018 应当截成 0.001（0.1%）");
        expect(helper, 0.05D, archery_procedures.arrowStrikeChance(0.05D),
                "0.05 应当还是 0.05");
        expect(helper, 0.123D, archery_procedures.arrowStrikeChance(0.1234D),
                "0.1234 应当截成 0.123");
        expect(helper, 1.0D, archery_procedures.arrowStrikeChance(3.0D),
                "超过 1 应当钳到 100%");
        expect(helper, 0.0D, archery_procedures.arrowStrikeChance(0.0009D),
                "0.0009 截成 0 就是永不触发");

        // 箭矢暴击伤害：每 0.01 = 1%
        expect(helper, 15.0D, archery_procedures.nightmareAmount(10.0D, 1.5D),
                "暴伤 1.50 应当是 150%");
        expect(helper, 0.5D, archery_procedures.nightmareAmount(10.0D, 0.05D),
                "暴伤 0.05 应当是 5%");
        helper.succeed();
    }

    /** 箭矢伤害：箭术精通按固定值结算（10 + 2 = 12）。 */
    @GameTest(template = TEMPLATE)
    public static void archeryHandlerAppliesExpertiseToArrowDamage(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        setArcheryAttributes(shooter, 2.0D, 0.0D, 0.0D);

        LivingHurtEvent event = new LivingHurtEvent(victim, arrowSource(helper, shooter), 10.0F);
        archery_procedures.onLivingHurt(event);
        expect(helper, 12.0D, event.getAmount(), "箭术精通 2 应当把箭矢伤害算成 12");
        helper.succeed();
    }

    /** 箭术精通为负时负向衰减，压穿后钳 0。 */
    @GameTest(template = TEMPLATE)
    public static void archeryHandlerClampsExpertiseAtZero(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        setArcheryAttributes(shooter, -100.0D, 0.0D, 0.0D);

        LivingHurtEvent event = new LivingHurtEvent(victim, arrowSource(helper, shooter), 10.0F);
        archery_procedures.onLivingHurt(event);
        expect(helper, 0.0D, event.getAmount(), "箭术精通压穿后应当钳在 0");
        helper.succeed();
    }

    /** 非箭矢伤害不该被动到。 */
    @GameTest(template = TEMPLATE)
    public static void archeryHandlerIgnoresNonArrowDamage(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        setArcheryAttributes(shooter, 10.0D, 1.0D, 1.0D);

        LivingHurtEvent event = new LivingHurtEvent(
                victim, helper.getLevel().damageSources().generic(), 10.0F);
        archery_procedures.onLivingHurt(event);
        expect(helper, 10.0D, event.getAmount(), "普通伤害不该被箭矢属性改写");
        helper.succeed();
    }

    /** 处理器确实注册到了 Forge 总线上（投递一次真实事件）。 */
    @GameTest(template = TEMPLATE)
    public static void archeryHandlerIsRegisteredOnTheBus(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));
        setArcheryAttributes(shooter, 3.0D, 0.0D, 0.0D);

        LivingHurtEvent event = new LivingHurtEvent(victim, arrowSource(helper, shooter), 4.0F);
        MinecraftForge.EVENT_BUS.post(event);
        expect(helper, 7.0D, event.getAmount(), "注册到总线上的处理器应当生效");
        helper.succeed();
    }

    /**
     * 触发暴击时真的多打一份梦魇伤害。
     *
     * <p>用「同一箭打两次、只变属性」的差值来判断：因为僵尸自带护甲，绝对掉血量会混进原版减免，
     * 而梦魇伤害走的是 bypasses_armor，所以差值恰好应该是「未减免的箭伤 × 暴伤」。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void archeryCritDealsNightmareDamage(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));

        // 对照组：不触发暴击。
        setArcheryAttributes(shooter, 0.0D, 0.0D, 0.0D);
        float plain = hitWithArrow(helper, shooter, victim, 4.0F);

        // 实验组：必然触发、暴伤 100% → 额外 4 点梦魇伤害。
        setArcheryAttributes(shooter, 0.0D, 1.0D, 1.0D);
        float crit = hitWithArrow(helper, shooter, victim, 4.0F);

        helper.assertTrue(Math.abs((crit - plain) - 4.0F) < 0.01F,
                "触发暴击应当比不触发多掉 4 点梦魇伤害，实测多掉 " + (crit - plain)
                        + "（对照 " + plain + "，触发 " + crit + "）");
        helper.succeed();
    }

    /** 额外梦魇伤害按「箭矢暴击伤害」的比例缩放：暴伤 0.5 → 额外 2 点。 */
    @GameTest(template = TEMPLATE)
    public static void archeryCritExtraScalesWithCritDamage(GameTestHelper helper) {
        Player shooter = helper.makeMockPlayer();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 0, 0));

        setArcheryAttributes(shooter, 0.0D, 0.0D, 0.0D);
        float plain = hitWithArrow(helper, shooter, victim, 4.0F);

        setArcheryAttributes(shooter, 0.0D, 1.0D, 0.5D);
        float crit = hitWithArrow(helper, shooter, victim, 4.0F);

        helper.assertTrue(Math.abs((crit - plain) - 2.0F) < 0.01F,
                "暴伤 0.5 时应当多掉 2 点梦魇伤害，实测多掉 " + (crit - plain));
        helper.succeed();
    }

    /** 梦魇伤害类型已接进 shadow_damage 标签体系（无视盔甲等 5 项）。 */
    @GameTest(template = TEMPLATE)
    public static void nightmareDamageTypeBypassesEverything(GameTestHelper helper) {
        Holder<DamageType> holder = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(nightmare.KEY);

        helper.assertTrue(holder.is(shadow_damage.TAG),
                nightmare.NAME + " 应当在 " + shadow_damage.TAG.location() + " 里");
        for (TagKey<DamageType> bypass : shadow_damage.IMPLIED_BYPASSES) {
            helper.assertTrue(holder.is(bypass),
                    nightmare.NAME + " 缺少 " + bypass.location() + "（记得同步改那 6 个 tag json）");
        }
        helper.assertTrue(nightmare.NAME.equals(holder.value().msgId()),
                "message_id 实测 " + holder.value().msgId());
        helper.assertTrue(langValue("zh_cn", nightmare.DEATH_MESSAGE_KEY) != null,
                "zh_cn.json 缺少 " + nightmare.DEATH_MESSAGE_KEY);
        helper.assertTrue(langValue("en_us", nightmare.DEATH_MESSAGE_KEY) != null,
                "en_us.json 缺少 " + nightmare.DEATH_MESSAGE_KEY);
        helper.assertTrue(langValue("zh_cn", nightmare.DEATH_MESSAGE_PLAYER_KEY) != null,
                "zh_cn.json 缺少 " + nightmare.DEATH_MESSAGE_PLAYER_KEY);
        helper.assertTrue(langValue("en_us", nightmare.DEATH_MESSAGE_PLAYER_KEY) != null,
                "en_us.json 缺少 " + nightmare.DEATH_MESSAGE_PLAYER_KEY);
        helper.succeed();
    }

    /** 给玩家设上箭术精通 / 箭矢暴击 / 箭矢暴击伤害。 */
    private static void setArcheryAttributes(Player player, double expertise, double strike, double critDamage) {
        player.getAttribute(attributes.EXPERT_IN_ARCHERY.get()).setBaseValue(expertise);
        player.getAttribute(attributes.ARROW_STRIKE.get()).setBaseValue(strike);
        player.getAttribute(attributes.ARROW_CRIT_DAMAGE.get()).setBaseValue(critDamage);
    }

    /** 造一个 {@code minecraft:arrow} 的伤害源（直接/造成实体都填射手）。 */
    private static DamageSource arrowSource(GameTestHelper helper, LivingEntity shooter) {
        Holder<DamageType> holder = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW);
        return new DamageSource(holder, shooter, shooter);
    }

    /** 真的打一箭，返回真实掉血量（含原版护甲等一切减免）。 */
    private static float hitWithArrow(GameTestHelper helper, Player shooter, Zombie victim, float amount) {
        victim.invulnerableTime = 0;
        victim.setHealth(victim.getMaxHealth());
        float before = victim.getHealth();
        helper.assertTrue(victim.hurt(arrowSource(helper, shooter), amount), "这一箭应当打中");
        return before - victim.getHealth();
    }

    /** 读 ISS 某条属性的当前值（属性不存在时返回 NaN）。 */
    private static double issValue(LivingEntity entity, String path) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation(spell_power_procedures.ISS_NAMESPACE, path));
        return attribute == null ? Double.NaN : entity.getAttributeValue(attribute);
    }

    /** 列表里有没有这条 ISS 属性。 */
    private static boolean containsIssAttribute(List<Attribute> list, String path) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation(spell_power_procedures.ISS_NAMESPACE, path));
        return attribute != null && list.contains(attribute);
    }

    /** 属性的注册名，只为让断言信息好读。 */
    private static String attributeId(Attribute attribute) {
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        return id == null ? String.valueOf(attribute) : id.toString();
    }

    private static void withHappyMode(GameTestHelper helper, Player player, Runnable body) {
        GameRules.BooleanValue rule =
                helper.getLevel().getServer().getGameRules().getRule(happy_mode.RULE);
        boolean original = rule.get();
        try {
            rule.set(true, null);
            helper.assertTrue(rule.get(), "前置条件：Happy_mode 应当被打开");
            body.run();
        } finally {
            rule.set(original, null);
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 读 lang 文件里某个键的值，键不存在或文件缺失时返回 null。 */
    private static String langValue(String locale, String key) {
        try (InputStream in = WhiteLilyGameTests.class
                .getResourceAsStream("/assets/white_lily/lang/" + locale + ".json")) {
            if (in == null) {
                return null;
            }
            JsonObject lang = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return lang.has(key) ? lang.get(key).getAsString() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 类路径上是否存在某个资源（用来证明贴图/模型真的打进了包）。 */
    private static boolean existsResource(String classpathPath) {
        try (InputStream in = WhiteLilyGameTests.class.getResourceAsStream(classpathPath)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** 投递一次 LivingDamageEvent，返回结算后的伤害值。 */
    private static double postLivingDamage(LivingEntity entity, ServerLevel level, float amount) {
        LivingDamageEvent event = new LivingDamageEvent(entity, level.damageSources().generic(), amount);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getAmount();
    }

    /** 断言实测值与期望值一致（带容差）。 */
    private static void expect(GameTestHelper helper, double expected, double actual, String what) {
        helper.assertTrue(Math.abs(expected - actual) < EPSILON,
                what + "：期望 " + expected + "，实测 " + actual);
    }
}
