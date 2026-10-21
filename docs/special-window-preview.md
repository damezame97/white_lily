# 特殊窗口 · 节点交互预览（v5）

## 配置与入口

在游戏主菜单进入「模组 → White Lily → 配置」，切换 **Enable special window**。
也可以编辑 `config/white_lily-common.toml`：

```toml
"Enable special window" = true
```

该选项默认 `false`，快乐模式开关保留在创建世界的「世界」页。
改成 `true` 后重新进入创建世界，右下边缘会出现动态蓝色噪域，使用隐藏入口进入特殊窗口。

- 连续点击入口三次进入；相邻两次点击超过 1.6 秒则重新计数。
- `+` / `-` 或滚轮缩放，左键/中键拖拽空白区域平移。
- `Home` / `0` 回到原点并重置缩放。
- 点击 `(0, 0)` 根节点切换快乐模式；空格也可以切换根节点。
- 长按 `Esc` 约 0.9 秒开始收束动画，松开后返回原版创建世界页面。

打开、返回、切页和调整窗口大小不会创建世界。根节点读写原有创建世界草稿中的
`Happy_mode` 游戏规则，与默认「世界」页的快乐模式开关使用同一份状态。

## 主菜单背景彩蛋

每次真正进入主菜单时有 **20%** 概率显示黑白噪域背景；一次停留中的选择固定，
窗口缩放、语言切换和资源重载不会反复抽取。离开主菜单再返回时重新抽取。
三击入口打开特殊界面后，即使尚未开启原点，之后在**本次客户端启动内**返回主菜单
也必定显示黑白噪域；长按 Esc 仍先返回原创建世界草稿，再按原流程返回主菜单。
重启客户端后重新从 20% 概率开始，访问记录不写配置或世界存档。

背景使用黑底、无规则黑色孔洞和灰白噪粒碎边，保持原版标题、按钮和菜单输入。
它的随机出现不依赖 `Enable special window`；该配置仍只控制创建世界中的特殊入口。

- `TitleNoiseState.java`：`RANDOM_CHANCE = 0.20` 调概率，`visitSpecialWindow()` 锁定本次启动。
- `NoiseTitleEasterEgg.java`：维护客户端会话与背景动画时钟，`FADE_SECONDS` 调噪粒淡入。
- `mixin/client/MixinTitleScreen.java`：`added` / `removed` 驱动真实进入/离开，
  在全景遮罩之后、Logo 绘制之前插入背景，避免覆盖标题和按钮。仅在 mixin 的 `client` 列表注册。
- `SpecialWindowScreen.init()`：实际显示后记录已访问；构造但未打开不触发。
- `NoiseTitleRenderer.java` / `noise_title.fsh`：调灰白粒子密度、黑色块域、碎边羽化和活动速率。
  黑底始终不透明，淡入只控制噪粒亮度；资源失败时使用同样黑白的备用绘制。

`title-noise-preview.png` / `title-noise-preview.webp` 是实际 shader 的独立背景预览。
`tools/TitleNoiseShaderPreview.java` 验证灰度、不透明覆盖和活动帧；
`tools/TitleNoiseStateTest.java` 验证概率边界、每次进入抽一次和特殊界面访问锁定。

## 节点与依赖行为

目前有 25 个可交互节点：原点 `happy_mode`，以及东、南、西、北各 6 个示例节点。
未开启节点呈蓝色噪团，开启节点呈红色曝光噪团。节点主体由更小、更细密的等向方粒和曝光点
混乱重排组成，模糊小块作为底层，不使用横向白条。逻辑选择立即生效，蓝红视觉在约 **1 秒**内爆散、逐粒替换并
重新聚合；过渡途中反向点击会从当前进度继续，不会突然跳色，也不是整团混成紫色。

连线是**白色噪点组成的粗带**，颗粒各自向不同方向局部游移，不沿整段连线统一流动。
一条真实依赖两端都开启后，随两端较低的视觉激活进度
收束成带细白光晕的静态、稳定细白线；完全稳定后核心与光晕都不再随时间或悬停闪动。关闭任一端时，连线随节点
过渡反向散开。依赖规则不等待动画完成，级联关闭仍然立即处理。

鼠标经过蓝色噪场时留下逐渐退回蓝色的红粒轨迹，不绘制实心红圆或额外的鼠标线条。
历史约 **1.3 秒**、影响半径 **22 GUI 像素**（上一版为 42），最多保留 **20 点**，并用实时端点连接真实移动路径。
红色曝光、局部增亮和微扰共享此半径，避免缩小尾迹后仍扰动一大片背景。
静止且窗口有焦点时，鼠标当前位置保持活跃；失焦会断段，调整窗口或 GUI 坐标系时清空旧轨迹。

非悬停时不显示常驻文字 HUD、节点名称或操作提示。悬停节点时，黑底描述文件从中心
用黑白噪点碎边展开，约 **0.42 秒**完全展开；文字在展开进度超过 **60%** 后才从中心渐显。
它列出标题、说明、状态、全部前置与点击操作，不使用完整的白色矩形边框。
离开后约 **0.18 秒**向中心收拢；切换到另一节点时先收好旧文件，再展开新内容。
同一份文件的锚点保持固定，节点内轻微移动鼠标不会让说明跟随抖动。

每个方向的节点后缀和依赖相同，`east` 可替换为 `south`、`west`、`north`：

| 节点 ID | 全部前置节点 |
| --- | --- |
| `happy_mode` | 无 |
| `east_main` | `happy_mode` |
| `east_left` | `east_main` |
| `east_right` | `east_main` |
| `east_left_leaf` | `east_left` |
| `east_right_leaf` | `east_right` |
| `east_merge` | `east_left_leaf` **并且** `east_right_leaf` |

单前置必须已经开启。多前置使用 **AND**，数组中的所有节点都开启后，后继才可以开启。
点击尚未满足前置的节点不会改变状态。点击已开启节点可以将其关闭，并级联关闭失去
必要前置的所有后继；即使汇合节点还保留另一个开启前置，也会关闭。
再次开启父节点不会自动恢复之前被关闭的后继，需要重新选择。

普通节点仅用于验证分支选择、前置限制和视觉反馈，当前没有技能加成、消耗或其他游戏效果。
这些选择在当前创建世界草稿内维护，不写入客户端配置，也没有新增世界存档格式。
只有根节点对应现有的 `Happy_mode` 规则；子节点的存档方式和游戏效果仍待定义。

## 修改节点数据

实现位置：`src/main/java/com/damezame97/white_lily/client/SpecialWindowTree.java`。
类内已有对应的中文注释，数据定义和可变选择状态分开维护：

- `NodeSpec(id, x, y, visualSeed, titleKey, descriptionKey, prerequisites)` 定义一个节点。
  `id` 必须稳定且唯一；`visualSeed` 仅控制噪团外观，不能作为身份标识。
- `x/y` 是相对于原点、缩放之前的树坐标。向右是 `+x`，向下是 `+y`。
  移动节点时修改这些坐标，缩放与平移由屏幕统一处理。
- `prerequisites` 是真实前置 ID 数组：`List.of()` 无前置，
  `List.of("east_main")` 是单前置，
  `List.of("east_left_leaf", "east_right_leaf")` 是 AND 多前置。
- `titleKey` 和 `descriptionKey` 指向中英文语言文件；
  默认命名为 `white_lily.special_window.node.<id>.title` 和同前缀的 `.description`。
- `Connection(fromId, toId, bends)` 定义「前置 → 后继」的可见路径。
  完整折线按 `from` 节点坐标 → `bends` 中的 `Point(x, y)` 顺序 → `to` 节点坐标绘制。
  路由折点只改变画线，不改变前置条件。

默认布局集中在 `createDefaultDefinition()`：先写以东向为基准的局部坐标，
再由 `rotate()` 依次旋转为南、西、北。调整四个方向的共同形状时修改局部坐标；
只修改某一方向时应按对应方向单独设置坐标和折点。
当前局部节点坐标为主节点 `(110, 0)`、两侧分支 `(220, ±85)`、
两侧末端 `(330, ±125)`、汇合节点 `(420, 0)`。

依赖与连线必须一一对应。增加一个前置时，也要增加一条以它为起点的 `Connection`；
不要仅靠画线表达依赖，也不要用前置数组填充装饰线路。
`Definition` 会检查重复 ID、未知前置、自依赖、循环依赖、重复前置，
以及遗漏、重复或多余连线，发现错误立即抛出异常。
折点应避开其他节点及其点击区域，确保线的终点能清楚辨认。

`Selection` 保存每份草稿的可变状态，使用 `isEnabled()` 查询开启状态、
`canEnable()` 查询全部前置、`toggle()` 尝试切换、`setRootEnabled()` 同步游戏规则。
`enabledIds()` 返回不可变快照。不要把可变选择写进静态的默认 `Definition`。
以后接入真实效果时，应在成功修改选择后统一处理，并覆盖级联关闭产生的所有状态变化。

## 修改视觉与悬停文字

- `SpecialWindowScreen.java`：处理鼠标命中、缩放/平移、节点点击、视觉进度和描述排版。
  `NODE_TRANSITION_SECONDS = 1.0F` 控制节点切换时长；`rebuildNodes()` 将逻辑状态交给
  每个节点的 `NoiseTransition`，`buildBranches()` 取连线两端激活进度的较小值作为 `stability`。
  `TOOLTIP_OPEN_SECONDS = 0.42F`、`TOOLTIP_CLOSE_SECONDS = 0.18F` 控制描述显隐；
  `TOOLTIP_MAX_WIDTH = 240`、`TOOLTIP_PADDING = 10`、`TOOLTIP_MARGIN = 5` 控制换行与边距。
  `renderNodeDescription()` 中的 `textReveal` 使用进度 `0.60` 到 `1.00` 延迟显露文字，
  文字在屏幕坐标中绘制，不随树一起缩放。修改时保留非悬停收束逻辑，不能变成常驻 HUD。
- `NoiseTransition.java`：`target()` / `value()` 使用界面打开后的单调秒数，
  修改目标前先保存当前显示值，因此快速开关也保持连续；反复提交同一目标不会重启动画。
  不要用逐帧固定增量替代，否则切换时长会随帧率改变。
- `NoiseDocumentMotion.java`：维护 `visibleId`、展开量和固定锚点。
  `update()` 先消费旧文件收束所需的时间，再把剩余帧时间用于新文件展开，避免卡顿时切换变慢。
  这里不排版文字；屏幕根据它保留的节点 ID 渲染旧内容，直到旧文件完全收起。
- `src/main/resources/assets/white_lily/shaders/core/noise_tree.fsh`：
  `LINK_WHITE` 控制连线颜色；`LINK_LOOSE_HALF_WIDTH` / `LINK_STABLE_HALF_WIDTH` /
  `LINK_STABLE_BLUR` 控制粗噪带与稳定细线的尺寸；`LINK_STABLE_HALO_RADIUS = 1.8` /
  `LINK_STABLE_HALO_ALPHA = 0.16` / `LINK_STABLE_HALO_CUTOFF = 5.0` 控制细白光晕。
  `LINK_NOISE_CELL` 控制白粒间距，`LINK_NOISE_SPEED` / `LINK_LOCAL_MOTION` 控制各粒局部游移。
  `localParticleMotion()` 按粒子种子分配双轴频率和相位，不再对整列或整段施加统一位移。
  `branch()` 对完全稳定的连线直接返回静态细线与光晕，不再读取时间扰动。
  `BLUE_*` / `RED_*` 控制节点关闭与开启颜色；
  `NODE_RADIUS` / `ORIGIN_RADIUS` 控制核心尺寸；
  `NODE_HALO_SPREAD` / `NODE_HALO_ALPHA` 控制光晕；
  `NODE_BLOCK_CELL` / `NODE_BLOCK_BLUR` / `NODE_BLOCK_RATE` 控制模糊方块的尺寸、软化和重排；
  `NODE_BLOCK_CELL = 3.2`、`NODE_POINT_CELL = 1.25`、`NODE_FINE_CELL = 0.78` 控制三层粒子间距；
  粒径在各层 `blockSize` / `squareOrPoint()` 中同步设置，避免只加密而不缩小颗粒。
  各粒的亮灭频率分别从种子计算，`NODE_BURST_SPREAD` 控制过渡中段的径向爆散。
- `NoiseTreeRenderer.java`：`LINK_PAD`、`LINK_HALF_WIDTH`、
  `NODE_HALF_SIZE` 控制绘制边界；同名 `BLUE_*` / `RED_*` 是 shader 不可用时的备用颜色，
  `FALLBACK_*` 调整备用碎粒、粗带和稳定线宽；备用节点使用 420 细粒、90 个模糊小块，
  `fallbackStableLine()` 分层绘制同样静态的细白光晕。`Link.stability` 与 `Node.activation` 都是
  `0..1` 的连续视觉进度，`Node.enabled` 保留逻辑目标；renderer 不单独启动动画时钟。
  shader 的 `Color.b` 低范围编码线段稳定度，节点用 `Color.g` 的低 7 位编码激活进度；
  修改任一侧编码时必须同步 shader 与离屏预览工具。`noise_tree` 当前没有全局 `Enabled` uniform。
  调整效果尺寸时，同时确认绘制边界足够容纳光晕和过渡爆散。
- `NoiseCursorTrail.java`：`DURATION_SECONDS = 1.30F`、`RADIUS_GUI_PIXELS = 22.0F`、
  `MAX_SAMPLES = 20` 控制轨迹生命周期、半径与上限。历史约每 72 毫秒保存一点，另保留
  每帧更新的实时端点，因此高帧率不会把一秒历史压缩成最近 20 帧。
  `sample(..., active=false)` 停止刷新并断段，已有点继续衰减；`clear()` 用于坐标系变化。
- `NoiseFieldRenderer.java`、`noise_field.fsh` 与 `SpecialWindowPortal.java`：
  处理整屏噪场与右下入口。`noise_field` 使用 `TrailPoints` / `TrailCount` /
  `TrailRadius` / `TrailBounds` 接收轨迹；`trailInfluence()` 计算点和相邻有效线段的影响，
  颜色逐粒替换并随历史强度退回蓝色。背景 `proximity` 也按 GUI 像素距离除以 `TrailRadius`，
  入口的局部悬停范围独立保留。轨迹坐标属于屏幕 GUI 坐标，不使用树的平移或缩放。
- `NoiseTooltipRenderer.java` 与 `noise_tooltip.fsh`：处理描述文件底层、中心扩散和黑白噪边。
  shader 的 `Reveal` 接收 `NoiseDocumentMotion` 的进度，`FieldSize` 是描述文件 GUI 尺寸；
  `EDGE_CELL` / `EDGE_SOFTNESS` / `FRAGMENT_RATE` 控制碎边尺度、软化和刷新频率。
  正文由 Screen 延迟绘制，shader 不画字体。备用绘制同样中心展开并使用碎白点，
  不退回完整的矩形白边框。
- `src/main/resources/assets/white_lily/lang/zh_cn.json` 与 `en_us.json`：
  修改每个节点的标题与说明；`white_lily.special_window.tooltip.*` 是共用状态和操作文字。
  `requires_all` 是无占位符的列表标题，后面逐项显示真实前置节点名称。

所有视觉均由程序化 shader 生成，没有引入外部游戏贴图。
`special-window-preview-v5.png` 与 `special-window-portal-preview-v5.png` 是本轮离屏 shader 预览；
它们验证实际 shader 的视觉，不代替游戏内输入、描述文字排版和世界草稿同步检查。
`special-window-preview-v5.webp` 展示调整后的节点切换、连线聚散和小范围鼠标尾迹；
`special-window-document-v4.webp` 单独展示描述文件的中心展开与快速收束（离屏不绘制游戏字体）。
`v2` / `v3` / `v4` 预览保留历史外观，节点、连线与鼠标范围以 `v5` 为准。
`tools/NoiseAnimationTest.java` 可用纯 JDK 17 验证过渡、描述显隐和轨迹生命周期，
`tools/SpecialWindowTreeTest.java` 验证前置、AND 汇合、级联关闭与路由避让。

## 手动核对

1. 配置关闭时，默认「世界」页能直接切换快乐模式。
2. 配置开启时，三击右下噪域进入，原点状态与同一份创建世界草稿一致。
3. 未悬停时无常驻文字；悬停根节点、锁定节点和汇合节点时，描述从中心展开，内容各自正确。
4. 只开启汇合节点的一个前置时仍无法开启；两个前置全开后可以开启。
5. 关闭任一必要前置后，其后继和汇合节点级联关闭，重新开启前置后不会自动恢复。
6. 缩放、拖拽、窗口大小变化和退出再返回后，点击位置与可见节点一致。
7. 节点蓝红切换约一秒，途中连续反向点击不跳色；两端开启后粗白噪带收束为真正静止的细白线，
   关闭任一端后反向散开。粗带颗粒无统一流向，节点细密；稳定白线有细白光晕且不闪动。
8. 快速从节点 A 划到 B，先收旧文件再开新文件；离开时不会瞬消，节点内微动不抖锚点，
   靠近屏幕边缘时描述仍能换行并保持可见。
9. 快速移动鼠标留下连续红粒尾迹，约 1.3 秒逐渐退回蓝色；静止鼠标仍有局部活跃点，
   失焦再返回不跨空白连接，窗口缩放后不保留旧坐标轨迹。
10. 检查进入与退出动画，以及无 shader 时的碎粒、连线和描述备用绘制。
