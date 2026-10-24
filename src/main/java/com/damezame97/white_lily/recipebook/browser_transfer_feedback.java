package com.damezame97.white_lily.recipebook;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Correlated S2C result; a reply never contains ingredients or grants recipe knowledge. */
public final class browser_transfer_feedback
{
    public record Reply(int menu,ResourceLocation recipe,int request,transfer_reason reason) { }
    private static final ArrayDeque<Reply> REPLIES=new ArrayDeque<>();
    public static synchronized void accept(Reply reply)
    {
        if(reply==null)return;
        if(REPLIES.size()>=16)REPLIES.removeFirst();
        REPLIES.addLast(reply);
    }
    /**
     * Consume only the reply that belongs to the current menu/request/recipe.
     * Unrelated late replies stay bounded and can never close a newer detail screen.
     */
    @Nullable public static synchronized Reply take(int request,int menu,ResourceLocation recipe)
    {
        for(Iterator<Reply> it=REPLIES.iterator();it.hasNext();)
        {
            Reply reply=it.next();
            if(reply.request()==request&&reply.menu()==menu&&reply.recipe().equals(recipe))
            {it.remove();return reply;}
        }
        return null;
    }
    @Nullable public static synchronized Reply last(){return REPLIES.peekLast();}
    public static synchronized void clear(){REPLIES.clear();}
    private browser_transfer_feedback() { }
}
