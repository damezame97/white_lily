package com.damezame97.white_lily.client;

/** 纯坐标数学，无客户端类依赖，供界面和确定性服务端测试共用。 */
public final class research_tree_view
{
    public double centerX,centerY,zoom=1;
    public boolean initialized;
    public static double clamp(double zoom) { return Math.max(0.1,Math.min(2,Double.isFinite(zoom)?zoom:1)); }

    /** 命中半径下限（屏幕像素）：再小的缩放也要点得到节点。 */
    public static final double HIT_RADIUS_MIN = 2.0;

    /** 命中半径上限基数（屏幕像素，缩放 1 时的节点本体半径）。 */
    public static final double HIT_RADIUS_MAX = 14.0;

    /** 只有一个节点时没有「最近邻近节点」，用它当默认半间距，别让间距变成无穷大。 */
    public static final double DEFAULT_SPACING = 48.0;

    /**
     * 节点的命中半径（屏幕像素）。
     *
     * <p>规则：先取节点本体半径 {@code 14*zoom}（缩小到 25% 以下时退化为 2px），
     * 再用「到最近节点的半间距 × 0.9」限制，避免两个节点的可点区域重叠；
     * 最后夹上下限：不小于 {@link #HIT_RADIUS_MIN}，不大于本体半径的两倍。</p>
     *
     * <p>{@code spacing} 非有限（只有一个节点，或间距算出无穷大）时视为「没有邻近节点」，
     * 只用本体半径——没有上限的话，点树里任何空白处都会被当成选中该节点。</p>
     */
    public static double hitRadius(double zoom, double spacing)
    {
        double z = clamp(zoom);
        double body = z < .25 ? HIT_RADIUS_MIN : HIT_RADIUS_MAX * z;
        double neighbour = Double.isFinite(spacing) && spacing > 0 ? spacing * z * .9 : Double.POSITIVE_INFINITY;
        double radius = Math.min(body, neighbour);
        return Math.max(HIT_RADIUS_MIN, Math.min(radius, HIT_RADIUS_MAX * z * 2));
    }
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
