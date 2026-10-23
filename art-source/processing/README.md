# 简易加工台本轮美术与数据源

- `draw_processing_assets.py`：Pillow 逐像素绘制 9 件物品与 7 张方块材质，生成原生 Java cuboid 模型、GUI 背景和离线预览。物品/方块正式纹理始终为真实 16×16 RGBA，透明度只有 0/255，不使用概念图缩小替代绘制。
- `simple_processing_table.editable.json`：与正式方块模型一致的可编辑原生 JSON，含各 cuboid 名称，可用 Blockbench Java Block/Item 模式导入。没有专门伪装成 `.bbmodel`。
- `palette_and_layout.json`：绘制尺寸、UI 槽位及箭头 UV、参考图来源。
- `install_processing_data.py`：本轮普通合成配方、解锁、土料标签、掉落和新增语言键的数据源。再次执行时，如果已有同名翻译被改成不同内容，将报冲突而不覆盖。
- `check_processing_assets.py`：静态检查尺寸、透明边距、UV、模型、语言键、普通配方与旧路线迁移，结果写入 `static_checks.json`。这不替代游戏内测试。

三个脚本均从自身位置寻找项目根目录，需要 Python 与 Pillow，可在任意工作目录运行。请在修改美术源后重跑绘图脚本；正式资源位于 `src/main/resources/assets/white_lily/`。

实际查看过的用户概念图：

- `D:/bb/png/livelihood_tab_concept.png`
- `D:/bb/png/herbal_workbench_concepts/ui.png`
- `D:/bb/png/herbal_workbench_concepts/model.png`

用户原图未覆盖。全部像素由程序中的有限色板、折线、硬边多边形绘制，未调用图片生成。

预览位于 `docs/previews/processing_items_16x.png`、`processing_model.png`、`processing_ui.png` 和 `processing_overview.png`。模型预览由正式模型 cuboid、UV 和纹理离线正交投影而成；UI 预览仅在预览阶段添加文字与示例物品。**这些图片不是游戏内实测截图**，正式 GUI 背景没有烘焙文字或示例数值。

药草布包与药草残渣没有本轮普通合成配方。药草布包配方待定，当前由创造模式或 `/give` 获取；药草残渣由加工台配方产生。
