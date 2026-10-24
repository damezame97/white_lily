package com.damezame97.white_lily.recipebook;

import java.text.Collator;
import java.util.*;

/** Locale keys are built once per index generation, never during frame rendering. */
public final class browser_order
{
    public enum Mode { DEFAULT, NAME, MOD; public Mode next(){return values()[(ordinal()+1)%values().length];} }
    public record Key(String name,String namespace,String modName,String stableId,String variant,String category,String categoryId) {
        public Key(String name,String namespace,String modName,String stableId,String variant){this(name,namespace,modName,stableId,variant,name,"");}
        public Key { name=Objects.requireNonNullElse(net.minecraft.ChatFormatting.stripFormatting(name),"");category=Objects.requireNonNullElse(net.minecraft.ChatFormatting.stripFormatting(category),name);if(modName==null||modName.isBlank())modName=namespace; }
    }
    public static Comparator<Key> comparator(Mode mode,String language)
    {
        Collator collator=Collator.getInstance(language.startsWith("zh")?Locale.SIMPLIFIED_CHINESE:Locale.forLanguageTag(language.replace('_','-')));
        collator.setStrength(Collator.PRIMARY);
        Comparator<Key> names=(a,b)->collator.compare(a.name(),b.name());
        Comparator<Key> tie=Comparator.comparing(Key::stableId).thenComparing(Key::variant);
        Comparator<Key> grouped=Comparator.<Key,String>comparing(Key::category,collator).thenComparing(Key::categoryId).thenComparing(names).thenComparing(tie);
        if(mode==Mode.NAME)return names.thenComparing(tie);
        if(mode==Mode.DEFAULT)return grouped;
        return Comparator.<Key,Boolean>comparing(k->!k.namespace().equals("minecraft"))
                .thenComparing((a,b)->collator.compare(a.modName(),b.modName()))
                .thenComparing(Key::namespace).thenComparing(grouped);
    }
    public record Cell<T>(int position,T item,String header) { }
    /** Only module headings consume cells; primary sort categories never create UI placeholders. */
    public static <T> List<List<Cell<T>>> grid(List<T> items,int columns,int rows,int cellSize,boolean modules,
                                              java.util.function.Function<T,String> namespace,java.util.function.Function<T,String> moduleName) {
        columns=Math.max(1,columns);rows=Math.max(1,rows);
        List<List<Cell<T>>> pages=new ArrayList<>();List<Cell<T>> cells=new ArrayList<>();
        int cursor=0,capacity=columns*rows,headerRows=Math.max(1,(12+Math.max(1,cellSize)-1)/Math.max(1,cellSize));String group="";
        for(T item:items){String mod=namespace.apply(item);boolean header=modules&&rows>headerRows&&!mod.equals(group);
            if(header){cursor=((cursor+columns-1)/columns)*columns;if(cursor+columns*headerRows>=capacity){pages.add(List.copyOf(cells));cells.clear();cursor=0;}cells.add(new Cell<>(cursor,null,moduleName.apply(item)));cursor+=columns*headerRows;group=mod;}
            if(cursor>=capacity){pages.add(List.copyOf(cells));cells.clear();cursor=0;if(modules&&rows>headerRows){cells.add(new Cell<>(0,null,moduleName.apply(item)));cursor=columns*headerRows;}}
            cells.add(new Cell<>(cursor++,item,""));}
        if(!cells.isEmpty()||pages.isEmpty())pages.add(List.copyOf(cells));return List.copyOf(pages);
    }
    public static int page(int page,int size,int capacity){return Math.max(0,Math.min(page,Math.max(0,(size-1)/Math.max(1,capacity))));}
    private browser_order() { }
}
