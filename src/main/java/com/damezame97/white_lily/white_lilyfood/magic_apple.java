package com.damezame97.white_lily.white_lilyfood;

import com.damezame97.white_lily.white_lilybuff.mob_effects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 「魔力苹果」——本模组的第一件食物类物品。
 *
 * <p>本类住在 {@code white_lilyfood} 包里；这个包专门用来收纳之后所有的食物类物品。</p>
 *
 * <p>行为：</p>
 * <ol>
 *     <li>食物：回复 0 饱食度、0 饱和度；进食耗时 20 刻；走 Minecraft 原版的进食动作与音效；</li>
 *     <li>吃完后：获得 10 秒 0 级的「不洁魔力」（见
 *         {@link com.damezame97.white_lily.white_lilybuff.impure_magic}，不显示粒子），
 *         并获得 5 点《Iron's Spells 'n Spellbooks》的魔力；</li>
 *     <li>物品名走语言键（中/英各一份），颜色 #c300ff；</li>
 *     <li>按住 Shift 展开「详细效果」——表头、提示语、效果条目全部走语言键（中/英各一份），
 *         效果内容在 lang 里填。</li>
 * </ol>
 *
 * <h2>关于堆叠上限：128 是怎么撑开的</h2>
 * <p>原版把物品数量当<b>有符号 byte</b> 存/传，上限只有 127；玩家背包的槽位上限又固定是 64。
 * 所以 128 靠三个 mixin 合力实现：</p>
 * <ol>
 *     <li>{@code MixinFriendlyByteBuf}：网络传输的数量从 {@code writeByte}/{@code readByte} 换成
 *         {@code writeVarInt}/{@code readVarInt}。其中 {@code writeItemStack} 是 Forge 补的方法、
 *         没有 SRG 映射，所以写 {@code @Overwrite(remap = false)}（详见该类注释）；</li>
 *     <li>{@code MixinItemStack}：存档 NBT 里数量 &gt; 127 时改写成 IntTag（≤ 127 保持原样的
 *         ByteTag，不破坏兼容）；</li>
 *     <li>{@code MixinInventory}：把玩家背包的 {@code getMaxStackSize()} 从 64 抬到 128，
 *         否则背包一格仍会被拆成两个 64。</li>
 * </ol>
 * <p>限制：因为改了 NBT 与网络格式，<b>服务端与客户端都必须装本模组</b>（mods.toml 里
 * {@code displayTest="MATCH_VERSION"} 已强制两端版本一致）。</p>
 */
public class magic_apple extends Item
{
    /** 注册名，同时也是贴图/模型的文件名与语言键尾段。 */
    public static final String NAME = "magic_apple";

    /**
     * 最大堆叠上限。
     *
     * <p>128。原版把数量当有符号 byte 存/传（上限 127），所以这里靠三个 mixin 一起撑开：
     * {@code MixinFriendlyByteBuf}（网络用 VarInt）、{@code MixinItemStack}（存档 NBT 超 127 用 int）、
     * {@code MixinInventory}（玩家背包槽位上限抬到 128）。详见类注释。</p>
     */
    public static final int MAX_STACK_SIZE = 128;

    /** 物品名的颜色（原写法里的 #c300ff）。 */
    private static final int NAME_COLOR = 0Xdf2369;

    /** 进食耗时：20 刻（原版普通食物是 32 刻）。 */
    public static final int USE_DURATION_TICKS = 20;

    /** 「不洁魔力」的持续时长：10 秒 = 200 刻。 */
    public static final int IMPURE_MAGIC_DURATION_TICKS = 200;

    /** 「不洁魔力」的等级：1 级（游戏内显示为「不洁魔力 II」）。 */
    public static final int IMPURE_MAGIC_AMPLIFIER = 1;

    /** 食用后获得的 ISS 魔力点数。 */
    public static final float MANA_GAIN = 5.0F;

    /** Shift 展开时第一行的标题——语言键。 */
    public static final String TOOLTIP_HEADER_KEY = "item.white_lily.magic_apple.tooltip.detail_header";

    /** 没按 Shift 时那一行提示——语言键。 */
    public static final String TOOLTIP_HINT_KEY = "item.white_lily.magic_apple.tooltip.detail_hint";

    /** 效果条目的语言键前缀，后面直接拼 1、2、3……与 lang 里预留的空例子一一对应。 */
    public static final String TOOLTIP_EFFECT_KEY_PREFIX = "item.white_lily.magic_apple.tooltip.effect.";

    /** 最多显示几条效果：要和 lang 里预留的 effect.N 数量一致（加条数时两边一起加）。 */
    public static final int TOOLTIP_EFFECT_LINE_COUNT = 3;

    public magic_apple()
    {
        super(new Item.Properties()
                .stacksTo(MAX_STACK_SIZE)
                .food(new FoodProperties.Builder()
                        // 0 饱食度、0 饱和度：这件物品存在的意义是魔力与负面效果，不是填肚子。
                        .nutrition(0)
                        .saturationMod(0.0F)
                        // 注意：这里故意没有 .alwaysEat()。原版规则是「饱食度满了就不能再吃」，
                        // 而本物品饱食度是 0，所以满饱食度时吃不了它。
                        // 如果你希望它随时都能吃，在 .build() 前面补一个 .alwaysEat() 就行。
                        .alwaysEat()
                        .build()));
    }

    // ==================================================================
    // 物品名：走语言键，这样中/英客户端各显示各的
    // ==================================================================

    /**
     * 原来的写法是 {@code Component.literal("“魔力苹果”")}，那是硬编码，英文客户端也只能看到中文。
     * 这里改成「用 {@link #getDescriptionId(ItemStack)} 取语言键再上色」：
     * 中文读 zh_cn.json 的 {@code item.white_lily.magic_apple}，英文读 en_us.json 的同名键。
     */
    @Override
    public Component getName(ItemStack stack)
    {
        return Component.translatable(this.getDescriptionId(stack))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(NAME_COLOR)));
    }

    // ==================================================================
    // 进食
    // ==================================================================

    /** 进食耗时 20 刻。返回值 > 0 时才算「可进食」，动画与音效由原版按 {@code UseAnim.EAT} 自动播放。 */
    @Override
    public int getUseDuration(ItemStack stack)
    {
        return USE_DURATION_TICKS;
    }

    /** 吃完时调用。先让原版把食物结算掉（含吞食音效与数量 -1），再挂上中毒与魔力。 */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        // 只在服务端结算，避免客户端本地预测把效果/魔力加两次。
        if (!level.isClientSide)
        {
            applyOnConsume(entity);
        }

        return result;
    }

    /**
     * 食用后的全部结算逻辑，独立成一个静态方法是为了让 GameTest 能直接调它。
     *
     * @param entity 吃下这件物品的实体
     */
    public static void applyOnConsume(LivingEntity entity)
    {
        // 自定义「不洁魔力」：10 秒、0 级——它已经替换掉原来的原版中毒。
        // 构造参数 (效果, 持续刻数, 等级, ambient, 是否显示粒子=false, 是否显示 HUD 图标=true)
        // —— visible 传 false 就是「不显示粒子」，图标仍保留，方便玩家看见它挂在自己身上。
        entity.addEffect(new MobEffectInstance(
                mob_effects.IMPURE_MAGIC.get(),
                IMPURE_MAGIC_DURATION_TICKS,
                IMPURE_MAGIC_AMPLIFIER,
                false,
                false,
                true));

        grantMana(entity, MANA_GAIN);
    }

    /**
     * 给实体加 ISS 魔力，并同步给客户端。
     *
     * <p>{@code MagicData#addMana} 内部只是 {@code setMana(当前 + 增量)}，会自动钳到玩家的
     * {@code MAX_MANA} 属性上限，但它<b>不会</b>自己发包——ISS 自己在改魔力之后都会补一句
     * {@code PacketDistributor.sendToPlayer(player, new SyncManaPacket(data))}，
     * 少这一步客户端魔力条不会动。</p>
     *
     * @return 结算后的魔力值；取不到 MagicData 时返回 {@code -1}
     */
    public static float grantMana(LivingEntity entity, float amount)
    {
        MagicData data = MagicData.getPlayerMagicData(entity);
        if (data == null)
        {
            return -1.0F;
        }

        data.addMana(amount);

        // 只有真实玩家才需要（也才能）同步。
        if (entity instanceof ServerPlayer serverPlayer)
        {
            PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(data));
        }

        return data.getMana();
    }

    // ==================================================================
    // 悬浮提示（全部走语言键，中/英客户端各显示各的）
    // ==================================================================

    /**
     * 按住 Shift 展开「详细效果」。
     *
     * <p>表头、提示语、效果条目全部是语言键，分别写在 zh_cn.json / en_us.json 里，
     * 英文客户端不会再看到中文。要加一条效果，做两件事：</p>
     * <ol>
     *     <li>在 lang 里给 {@code item.white_lily.magic_apple.tooltip.effect.N} 填上内容，例如
     *         {@code "§4• 负重值 +1"}；</li>
     *     <li>如果用的是新的 {@code N}，把 {@link #TOOLTIP_EFFECT_LINE_COUNT} 相应调大。</li>
     * </ol>
     * <p>值为空字符串的条目会被跳过，不会在提示里留下一行空白——所以 lang 里那几个
     * 空例子填之前什么也不显示。</p>
     */
    @Override
    public void appendHoverText(ItemStack itemstack, Level level, List<Component> list, TooltipFlag flag)
    {
        super.appendHoverText(itemstack, level, list, flag);

        if (Screen.hasShiftDown())
        {
            list.add(Component.translatable(TOOLTIP_HEADER_KEY));

            for (int i = 1; i <= TOOLTIP_EFFECT_LINE_COUNT; i++)
            {
                String key = TOOLTIP_EFFECT_KEY_PREFIX + i;
                if (hasTranslatedText(key))
                {
                    list.add(Component.translatable(key));
                }
            }
        }
        else
        {
            list.add(Component.translatable(TOOLTIP_HINT_KEY));
        }
    }

    /**
     * 该语言键在「当前语言」里有没有非空内容。
     *
     * <p>用 {@link Component#getString()} 让 Minecraft 自己去查语言表：键存在但值是空串时返回空串，
     * 键压根不存在时返回键名本身——两种都算「没内容」，于是那一行不显示。</p>
     */
    private static boolean hasTranslatedText(String key)
    {
        String text = Component.translatable(key).getString();
        return !text.equals(key) && !text.isBlank();
    }
}
