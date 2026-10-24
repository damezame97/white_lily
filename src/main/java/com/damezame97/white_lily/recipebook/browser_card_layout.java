package com.damezame97.white_lily.recipebook;

import com.damezame97.white_lily.processing.processing_recipe;
import com.damezame97.white_lily.recipebook.browser_display.Display;
import com.damezame97.white_lily.recipebook.browser_display.DisplaySlot;
import net.minecraft.world.item.crafting.*;

import java.util.Comparator;

/**
 * Compact recipe-card arithmetic shared by the client renderer and the GameTests.
 *
 * <p>A card is only the real device diagram plus one readable metadata line. It never
 * reserves a right-hand explanation column and never includes an ingredient alternative
 * as another card. Keeping this independent of {@code Screen} lets the capacity and
 * plus-button coordinates be checked without a client.</p>
 */
public final class browser_card_layout
{
    public static final int META_HEIGHT = 10;
    public static final int MIN_WIDTH = 140;

    public static int diagramHeight(Display display)
    {
        int bottom = 0;
        for (DisplaySlot slot : display.slots()) bottom = Math.max(bottom, slot.y() + 18);
        if (display.recipe() instanceof CraftingRecipe) bottom = Math.max(bottom, 70);
        else if (display.recipe() instanceof AbstractCookingRecipe) bottom = Math.max(bottom, 78);
        else if (display.recipe() instanceof StonecutterRecipe) bottom = Math.max(bottom, 52);
        else if (display.recipe() instanceof SmithingRecipe) bottom = Math.max(bottom, 52);
        else if (display.recipe() instanceof processing_recipe) bottom = Math.max(bottom, 88);
        return Math.max(24, bottom + 2);
    }

    public static int plusX(Display display)
    {
        DisplaySlot output = display.slots().stream()
                .filter(DisplaySlot::output)
                .min(Comparator.comparingInt(DisplaySlot::y).thenComparingInt(DisplaySlot::x))
                .orElse(null);
        if (output != null) return output.x() + 24;
        int right = display.slots().stream().mapToInt(slot -> slot.x() + 18).max().orElse(24);
        return right + 6;
    }

    public static int plusY(Display display)
    {
        return display.slots().stream()
                .filter(DisplaySlot::output)
                .min(Comparator.comparingInt(DisplaySlot::y).thenComparingInt(DisplaySlot::x))
                .map(DisplaySlot::y)
                .orElse(30);
    }

    public static int diagramWidth(Display display)
    {
        int right = display.slots().stream().mapToInt(slot -> slot.x() + 18).max().orElse(0);
        return Math.max(MIN_WIDTH, Math.max(right, plusX(display) + 18) + 4);
    }

    /** Height of one complete card at the actual (possibly fitted) scale. */
    public static int cardHeight(int diagramHeight, float scale)
    {
        return Math.max(12, Math.round(Math.max(1, diagramHeight) * Math.max(.1F, scale)) + META_HEIGHT);
    }

    public static int cardHeight(Display display, float scale)
    {
        return cardHeight(diagramHeight(display), scale);
    }

    private browser_card_layout() { }
}
