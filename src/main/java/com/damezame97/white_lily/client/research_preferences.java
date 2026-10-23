package com.damezame97.white_lily.client;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;

/** 只有客户端显示偏好，不进入服务器Capability或物品NBT。 */
public final class research_preferences
{
    private static final research_preferences INSTANCE=new research_preferences();
    public boolean icons;
    public boolean overlay;
    private final Path path;
    private research_preferences()
    { this(FMLPaths.CONFIGDIR.get().resolve("white_lily-research-ui.json")); }
    /** 独立路径支持确定性读写测试；生产仅使用get()的客户端个人配置。 */
    public research_preferences(Path path)
    {
        this.path=path;
        if(!Files.exists(path)) return;
        try(var reader=Files.newBufferedReader(path))
        {
            JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
            icons=json.has("columnIcons") && json.get("columnIcons").getAsBoolean();
            overlay=json.has("detailOverlay") && json.get("detailOverlay").getAsBoolean();
        }
        catch(Exception error) { LogUtils.getLogger().warn("White Lily: invalid research UI preferences {}",path,error); }
    }
    public static research_preferences get() { return INSTANCE; }
    public void save()
    {
        JsonObject json=new JsonObject(); json.addProperty("version",1);
        json.addProperty("columnIcons",icons); json.addProperty("detailOverlay",overlay);
        try
        {
            Files.createDirectories(path.getParent());
            Path temporary=path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(json));
            Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
        }
        catch(Exception error) { LogUtils.getLogger().warn("White Lily: cannot save research UI preferences {}",path,error); }
    }
}
