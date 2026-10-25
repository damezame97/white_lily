package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.effect_bar_layout;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class EffectBarLayoutGameTests
{
    @GameTest(template = "empty")
    public static void railCompactsOnlyWhenNeededAndKeepsIconsFullSize(GameTestHelper h)
    {
        effect_bar_layout.Layout four = effect_bar_layout.layout(4, 10, 166);
        yes(h, four.step() == 20 && !four.footer() && four.contentHeight() == 78,
                "1-4个效果正常竖排，不压缩也不显示计数");

        effect_bar_layout.Layout eight = effect_bar_layout.layout(8, 10, 166);
        yes(h, eight.footer() && eight.step() <= effect_bar_layout.OVERLAP_STEP
                        && 10 + eight.contentHeight() <= eight.footerY(),
                "5个以上固定形成重叠，底部保留计数空间且不缩短图标");

        effect_bar_layout.Layout shortRail = effect_bar_layout.layout(4, 10, 60);
        yes(h, shortRail.footer() && shortRail.step() < 20
                        && 10 + shortRail.contentHeight() <= shortRail.footerY(),
                "高度不足时压缩间距并让最后图标留在计数上方");

        int top = 10, step = effect_bar_layout.OVERLAP_STEP;
        yes(h, effect_bar_layout.topmost(top + 15, top, step, 3) == 1,
                "重叠命中取后绘制的上层图标，而非先绘制的下层");
        yes(h, effect_bar_layout.topmost(top + 2, top, step, 3) == 0
                        && effect_bar_layout.topmost(top + 40, top, step, 3) == 2,
                "非重叠区域仍能命中对应图标");
        yes(h, effect_bar_layout.topmost(top - 1, top, step, 3) == -1,
                "效果栏外不响应点击");
        h.succeed();
    }
}
