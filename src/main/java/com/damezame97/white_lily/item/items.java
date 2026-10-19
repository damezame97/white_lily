package com.damezame97.white_lily.item;

import com.damezame97.white_lily.white_lily;
import com.damezame97.white_lily.white_lilycurio.fate_compass;
import com.damezame97.white_lily.white_lilycurio.flesh_ring;
import com.damezame97.white_lily.white_lilycurio.improve_body;
import com.damezame97.white_lily.white_lilycurio.invalid_placeholder;
import com.damezame97.white_lily.white_lilyfood.magic_apple;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的物品注册表（所有物品的唯一注册入口）。
 *
 * <p>目前有两个条目：</p>
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

    /**
     * 「歧途“珍馐”」物品栏的图标物品。
     *
     * <p>贴图：{@code assets/white_lily/textures/item/tab_icon.png}（即 white_lily_food.png）；
     * 模型：{@code assets/white_lily/models/item/tab_icon.json}。</p>
     */
    public static final RegistryObject<Item> TAB_ICON =
            ITEMS.register("tab_icon", () -> new Item(new Item.Properties()));

    /**
     * 「魔力苹果」——见 {@link magic_apple}。
     *
     * <p>贴图 {@code assets/white_lily/textures/item/magic_apple.png}（即 Magic_Apple.png），
     * 模型 {@code assets/white_lily/models/item/magic_apple.json}。</p>
     */
    public static final RegistryObject<Item> MAGIC_APPLE =
            ITEMS.register(magic_apple.NAME, magic_apple::new);

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

    private items()
    {
    }
}
