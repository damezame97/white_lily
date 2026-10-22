package com.damezame97.white_lily.client;

/** 纯坐标数学，无客户端类依赖，供界面和确定性服务端测试共用。 */
public final class research_tree_view
{
    public double centerX,centerY,zoom=1;
    public boolean initialized;
    public static double clamp(double zoom) { return Math.max(0.1,Math.min(2,Double.isFinite(zoom)?zoom:1)); }
    public double worldX(double local,double width) { return centerX+(local-width/2)/zoom; }
    public double worldY(double local,double height) { return centerY+(local-height/2)/zoom; }
    public void zoomAt(double value,double mouseX,double mouseY,double width,double height)
    {
        double x=worldX(mouseX,width),y=worldY(mouseY,height);
        zoom=clamp(value);
        centerX=x-(mouseX-width/2)/zoom; centerY=y-(mouseY-height/2)/zoom;
    }
    public void fit(double minX,double minY,double maxX,double maxY,double width,double height)
    {
        centerX=(minX+maxX)/2; centerY=(minY+maxY)/2;
        zoom=clamp(Math.min((width-32)/Math.max(64,maxX-minX+64),(height-28)/Math.max(64,maxY-minY+64)));
        initialized=true;
    }
}
