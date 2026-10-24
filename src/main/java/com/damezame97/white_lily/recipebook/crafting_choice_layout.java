package com.damezame97.white_lily.recipebook;

/** GUI logical coordinates shared by drawing, hit testing and boundary tests. */
public final class crafting_choice_layout {
    public record Rect(int x,int y,int w,int h){public boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}}
    public record Popup(Rect bounds,int rows,int rowHeight){public int row(double mx,double my,int first){return bounds.contains(mx,my)&&my>=bounds.y+24&&my<bounds.y+24+rows*rowHeight?first+(int)(my-bounds.y-24)/rowHeight:-1;}}
    public static Rect button(int width,int height,int resultX,int resultY){int w=Math.min(60,Math.max(20,width-8));return new Rect(Math.max(4,Math.min(width-w-4,resultX+8-w/2)),Math.max(4,Math.min(height-16,resultY+20)),w,12);}
    public static Popup popup(int width,int height,Rect anchor,int count){
        int w=Math.min(300,Math.max(80,width-8)),rowHeight=48,rows=Math.max(1,Math.min(count,Math.max(1,(height-48)/rowHeight)));
        int h=Math.min(height-8,rows*rowHeight+40),x=anchor.x+w+4<=width?anchor.x:anchor.x+anchor.w-w;
        return new Popup(new Rect(Math.max(4,Math.min(width-w-4,x)),Math.max(4,Math.min(height-h-4,anchor.y+anchor.h)),w,h),rows,rowHeight);
    }
    private crafting_choice_layout() { }
}
