package com.damezame97.white_lily.recipebook;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.ArrayDeque;

/** Navigation holds identifiers, never permission snapshots or nested Screen instances. */
public final class browser_history
{
    public record Query(ItemStack subject,boolean uses,boolean all,@Nullable ResourceLocation category,
                        @Nullable ResourceLocation recipe,int scroll,String search,int listPage,boolean favorites)
    { public Query { subject=subject.copy(); } }
    private final ArrayDeque<Query> entries=new ArrayDeque<>();
    public void push(Query from,ItemStack target,boolean uses,boolean all)
    {
        if(from.uses()==uses&&from.all()==all&&ItemStack.isSameItemSameTags(from.subject(),target))return;
        if(entries.size()>=32)entries.removeFirst();entries.addLast(from);
    }
    public boolean isEmpty(){return entries.isEmpty();}
    @Nullable public Query pop(){return entries.pollLast();}
    public int size(){return entries.size();}
    public void clear(){entries.clear();}
}
