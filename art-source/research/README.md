# 调查手册像素资源与界面

本目录是可编辑美术源文件，不会打包进游戏。

- `draw_research.py`：使用 Pillow 在真实 16×16 画布逐像素绘制手册；生成可复用 GUI 图集及离线布局预览。
- `palette.json`：脚本中有限物品色板的导出记录；修改色板时编辑脚本内 `PALETTE` 后重新生成。
- `lang_zh_cn.json`、`lang_en_us.json`：本轮界面翻译键源记录，实际生效内容已合并到模组语言文件。
- `merge_language.py`：合并界面片段和手册/节点档案翻译，不重排已有语言文件；发现既有键冲突会要求人工审查。
- `package_checks.py`、`package_checks.json`：真实PNG尺寸/透明度、中英文键、配方、停用生成配置及正式JAR资源逐字节检查；不等同于游戏内测试。

运行脚本不会覆盖安神花、花茶或其他既有物品贴图。

```text
python art-source/research/draw_research.py
```

正式资源：

- `assets/white_lily/textures/item/investigation_handbook.png`：真实 16×16 RGBA；像素透明度仅 0/255；没有抗锯齿、背景或烘焙文字。
- `assets/white_lily/models/item/investigation_handbook.json`：普通 `item/generated` 模型。
- `assets/white_lily/textures/gui/research_atlas.png`：256×256 RGBA 图集。

图集坐标使用 GUI 逻辑像素：

| 区域 | 用途 |
| --- | --- |
| `(0,0)` 起横向四块 16×16 | 主边框、内容面板、悬停/选中、树背景；九宫格边缘 2 像素 |
| `(0,32)` 起横向三块 30×30，间距 32 | 未发现/锁定、可研究、已完成节点边框 |
| `(0,68)` 起横向三块 9×9，间距 10 | 独立锁、完成勾、可研究加号 |
| `(0,88)` 起横向三块 30×18，间距 32 | 可复用按钮普通、悬停、禁用资源；当前主要操作使用原版 Button |

文本、数量、节点连线、背包材料统计和物品图标由真实客户端代码动态绘制，不烘焙在正式 PNG 中。未发现图标使用当前已加载模型的粒子纹理生成灰度副本，资源重载或退出连接后释放；不改动原物品资源。

离线预览：`docs/previews/research_handbook_preview.png`（明确标注“非游戏截图”）；`research_handbook_item_24x.png` 为最近邻 24 倍预览。预览不能代替实际客户端 UI 验证。

实际客户端截图：`docs/previews/research_client_initial.png`，来自测试客户端原生F2截图，显示新玩家只有灰色安神花。后续用户按物理Esc停止界面操作，因此没有将可研究/已完成布局预览冒充实际截图，完整验证范围见 `WORK_REPORT.md`。

客户端实现：`client/research_client.java` 仅合并服务端裁剪过的 full/patch 快照，不加载完整研究 JSON；`client/InvestigationScreen.java` 缓存节点与文本布局，实现分栏、拖动、缩放、详情/栏目滚动及返回。正式节点配置位于服务端 `data/white_lily/research/handbook.json`。
