package com.damezame97.white_lily.white_lilycurio;

/**
 * 饰品「改良之躯」（{@code improve_body}）。
 *
 * <p>继承 {@link improvement_curio}，所以自动带上三条统一特性：拿不下来、死了不掉、
 * 非创造玩家的物品栏里出现就删。自身没有别的效果。</p>
 *
 * <p>贴图 {@code assets/white_lily/textures/item/improve_body.png}，
 * 模型 {@code assets/white_lily/models/item/improve_body.json}；
 * 哪个难度会装备它见 {@link improvement_loadout}。</p>
 */
public class improve_body extends improvement_curio
{
    /** 注册名，同时也是贴图/模型/语言键的路径部分。 */
    public static final String NAME = "improve_body";
}
