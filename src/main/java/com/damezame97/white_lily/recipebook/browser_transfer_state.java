package com.damezame97.white_lily.recipebook;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Request/response state for one recipe-detail screen.
 *
 * <p>The screen must keep waiting until the server replies for this exact menu,
 * request id and recipe. A late reply for an older request is ignored, and the
 * timeout is finite so a lost packet can never leave the plus button disabled.</p>
 */
public final class browser_transfer_state
{
    public enum Result { NONE, SUCCESS, FAILURE }

    private boolean waiting;
    private int request, menu, ticks;
    @Nullable private ResourceLocation recipe;
    @Nullable private transfer_reason reason;

    public boolean begin(int request, int menu, ResourceLocation recipe)
    {
        if (waiting || recipe == null) return false;
        this.request = request;
        this.menu = menu;
        this.recipe = recipe;
        this.ticks = 0;
        this.reason = null;
        this.waiting = true;
        return true;
    }

    public Result accept(browser_transfer_feedback.Reply reply)
    {
        if (!waiting || reply == null || reply.request() != request || reply.menu() != menu
                || recipe == null || !recipe.equals(reply.recipe()))
            return Result.NONE;
        waiting = false;
        reason = reply.reason();
        return reason == transfer_reason.OK ? Result.SUCCESS : Result.FAILURE;
    }

    /** Called once per client tick; returns true exactly when the wait expires. */
    public boolean tick()
    {
        if (!waiting) return false;
        if (++ticks <= 80) return false;
        waiting = false;
        reason = null;
        return true;
    }

    public void cancel()
    {
        waiting = false;
        reason = null;
    }

    public boolean isWaiting() { return waiting; }
    public int request() { return request; }
    public int menu() { return menu; }
    @Nullable public ResourceLocation recipe() { return recipe; }
    @Nullable public transfer_reason reason() { return reason; }
}
