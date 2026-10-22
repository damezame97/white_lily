package com.damezame97.white_lily.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 永久进度只保存稳定节点ID；不存在的旧ID仍保留，绝不映射成其他节点。 */
@AutoRegisterCapability
public final class research_progress
{
    private final Set<ResourceLocation> completed = new LinkedHashSet<>();
    private long revision;
    public transient boolean handbookOpen;
    /** 限流期间的多次打开合并为一次；退出/关闭不保留这个界面请求。 */
    public transient boolean pendingOpen;
    public transient long lastRequestTick = Long.MIN_VALUE;
    public transient long lastReplyTick = Long.MIN_VALUE;
    public transient String lastSnapshot = "";

    public Set<ResourceLocation> completed() { return Collections.unmodifiableSet(completed); }
    public boolean has(ResourceLocation id) { return completed.contains(id); }
    public long revision() { return revision; }
    /** 可信Java管理/迁移入口，不向客户端提供任意解锁数据包。 */
    public boolean complete(ResourceLocation id)
    {
        if (id == null || !completed.add(id)) return false;
        revision++;
        return true;
    }
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        completed.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("Completed", list);
        tag.putLong("Revision", revision);
        return tag;
    }
    public void load(CompoundTag tag)
    {
        completed.clear();
        ListTag list = tag.getList("Completed", 8);
        for (int i = 0; i < Math.min(list.size(), 16384); i++)
        {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) completed.add(id);
        }
        revision = Math.max(0, tag.getLong("Revision"));
        handbookOpen = false;
        pendingOpen = false;
        lastRequestTick = Long.MIN_VALUE;
        lastReplyTick = Long.MIN_VALUE;
        lastSnapshot = "";
    }
}
