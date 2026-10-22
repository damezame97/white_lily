# 医疗、药草与 SAN 重生修复

适用：Minecraft 1.20.1、Forge 47.4.10、Java 17。“污染”完全复用已有整数十分位感染度（0～3000单位，即0.0～300.0），没有第二套污染数据。

## 注册与使用

下列ID均以 `white_lily:` 开头。

| ID | 名称 | 堆叠 | 使用及效果 |
| --- | --- | --- | --- |
| simple_bandage | 简易绷带 | 16 | 使用60刻；包扎Ⅰ6000刻，恢复间隔属性+30；有污染风险 |
| clean_fine_bandage | 洁净的精良绷带 | 16 | 使用60刻；包扎Ⅱ12000刻，恢复间隔属性+60；不增减污染 |
| bitterleaf | 苦叶草 | 64 | 使用32刻；主动治疗污染3.0，饥饿Ⅰ900刻；也是可放置植物物品 |
| bitterleaf_paste | 苦叶药膏 | 16 | 使用32刻；主动治疗污染8.0，饥饿Ⅰ200刻 |
| yarrow | 止血蓍草 | 64 | 使用32刻；渗血抑制400刻，虚弱Ⅰ600刻；也是植物物品 |
| yarrow_dressing | 蓍草敷料 | 16 | 使用32刻；渗血抑制1200刻，虚弱Ⅰ200刻 |
| calming_flower | 安神花 | 64 | 使用32刻；完成前有效SAN≥5000恢复10，否则恢复5；反胃Ⅰ400刻；也是植物物品 |
| calming_flower_tea | 安神花茶 | 1 | 饮用32刻；完成前有效SAN≥5000恢复30，否则恢复15；反胃Ⅰ200刻；饮后返瓶 |
| pollution_chip_small / medium / large | 小/中/大型污染片 | 64 | 立即直接增加10/30/100污染 |
| cleansing_chip_small / medium / large | 小/中/大型洁净片 | 64 | 立即直接减少10/30/100污染 |
| pollution_detector | 污染检测仪 | 64 | 立即在自己的聊天栏显示“污染度：X.X / 300.0”，不消耗 |

共新增15种物品、3种同名药草方块、2种辅助状态：`bandaged`（包扎）、`wound_suppression`（渗血抑制）。物品复用 `item/items.java` 注册；正式医疗品和药草放入原主栏，不改变隐藏饰品规则；七种测试工具放入仅管理员可见的“污染测试”创造栏。

药草对合适土壤点按放置；对空气按住右键使用。所有完成使用逻辑仅在服务端执行。中断不会消费、施加效果或抽污染。创造模式使用不消耗，也不额外产生玻璃瓶。普通背包满时，最后一杯茶的玻璃瓶直接成为手持结果；异常堆叠茶仍有余量时尝试入背包，满包则由服务端掉落。

### 包扎与渗血抑制

- 包扎Ⅰ明确映射+30，Ⅱ明确映射+60，没有通过等级线性公式推测持续时间。
- 高级替换低级；同级刷新。低级遇到高级时，使用开始前和完成时都校验，不消耗、不污染、不延长高级。
- 包扎和抑制使用 Forge 治疗物品空表，不能被牛奶解除；药草副作用使用正常原版效果，仍可喝奶解除。
- 包扎只修改恢复间隔，不直接回血、不增加回复量、不重置已累计的回血计时。5秒下限及原有无饱和度分支保留。
- 渗血抑制只暂停 `wound_seepage` 的间隔和回复量两项修正，不删除渗血，也不冻结其剩余时间。受击仍正常延长渗血；抑制结束后仅在渗血仍存在时恢复惩罚。重复用药保留较长抑制时间。
- 医疗辅助效果使用原版状态保存/同步。非死亡Clone显式保留两种医疗效果；死亡不继承。感染框架开关及创造/旁观的感染症状暂停约定保持不变，医疗物品仍可正常使用。

### 简易绷带污染

第一组一次抽取：`[0,0.15)` 为30，`[0.15,0.45)` 为15，其余为0。第二组独立抽取：`[0,0.5)` 加5，其余加0。

| 合并基础污染 | 概率 |
| --- | --- |
| 0 | 27.5% |
| 5 | 27.5% |
| 15 | 15% |
| 20 | 15% |
| 30 | 7.5% |
| 35 | 7.5% |

合并后以稳定来源ID `white_lily:simple_bandage` 调用一次已有来源入口，触发概率为1，基础量为合并值。统一计算伤势、耐力、抗性、四舍五入及上限；实际增加后才有一次随机症状判定。基础量0不调用来源。创造/旁观、关闭感染框架时，来源仍按现有规则暂停，不为绷带绕过该规则。

### 测试工具权限

六种增减片要求服务端权限等级2，创造不消耗，生存消耗1。名称按小/中/大使用白/蓝/红，修改结果限制0～300。调用 `addDirect/reduce`，不套倍率、不抽随机池、不触发主动治疗疲劳；数值变化才同步和重新检查固定症状。

检测仪只读自身，不要求管理员权限，也能在创造使用。工具均有10刻使用冷却，不增加客户端任意修改数据的网络接口。完整症状测试须由管理员切到生存；未改变所有创造玩家的症状暂停规则。

## 配方

全部无序，均有独立配方解锁进度。

| 配方ID | 原料 | 产出/余料 |
| --- | --- | --- |
| simple_bandage | 粗布片1+细布条1 | 简易绷带2 |
| clean_fine_bandage_honey | 简易绷带1+蓍草敷料1+蜂蜜瓶1 | 精良绷带1，返玻璃瓶1 |
| clean_fine_bandage_beeswax | 简易绷带1+蓍草敷料1+本模组蜂蜡块1 | 精良绷带1，无容器 |
| bitterleaf_paste | 苦叶草2+黏液球1 | 苦叶药膏1 |
| yarrow_dressing | 止血蓍草2+细布条1 | 蓍草敷料1 |
| calming_flower_tea | 安神花2+普通水瓶1 | 花茶1，合成不额外返瓶 |

水瓶复用当前项目的严格 `forge:nbt` 配料，仅接受普通 `{Potion:"minecraft:water"}` 水瓶，不接受其他药水、喷溅、滞留或额外自定义药效NBT。蜂蜜瓶返瓶复用原版 crafting remainder；自动测试实际执行合成台 Shift 批量点击，并确认茶合成不返空瓶。

资源路径：`src/main/resources/data/white_lily/recipes/`；进度在 `advancements/recipes/medical/`。

## 药草种植与自然生成

独立土壤标签 `data/white_lily/tags/blocks/herb_soils.json`，默认仅草方块、泥土、砂土、灰化土。拒绝水中或树叶上种植。植株没有碰撞，使用单格 cross 模型和 Forge 模型的 `minecraft:cutout` 渲染层。普通采集每株只掉对应物品1份，没有种子、时运倍增或自动蔓延。

苦叶草、止血蓍草使用一次有效骨粉额外产出1份，原株保留。安神花的骨粉目标检查为false，无效骨粉不消耗；它没有随机刻繁殖，也没有加入原版普通骨粉的花卉生成 Feature。

| 药草 | 每合适区块概率 | 目标/上限 | 默认群系 |
| --- | --- | --- | --- |
| 苦叶草 | 15% | 2～4，最多4 | plains、sunflower_plains、forest、birch_forest、old_growth_birch_forest、flower_forest |
| 止血蓍草 | 10% | 1～3，最多3 | plains、sunflower_plains、meadow、windswept_forest |
| 安神花 | 5% | 1，最多1 | forest、birch_forest、old_growth_birch_forest、dark_forest、flower_forest |

以上群系均为 `minecraft:`，使用三个独立 `*_habitats` 群系标签，不按名称模糊匹配第三方群系。仅在主世界正常新生区块装饰中运行，不补种旧区块，不在每刻或加载事件扫描世界。

实现为 `worldgen/herb_patch.java` 的自定义Feature，三个 configured/placed feature 和三个 Forge biome modifier；全部使用 `vegetal_decoration`，无跨边界 `random_patch`。

- 三种有独立配置及概率判定，同一区块可多种共存。
- 每种最多32次落点尝试；围绕区块内一个中心±3格选点，越界点直接跳过，不补到邻区块。
- 使用 `MOTION_BLOCKING_NO_LEAVES` 高度图寻找地表，位置必须为空气、无流体、下方为土壤、所在群系符合。
- 苦叶草和蓍草再与 `WORLD_SURFACE_WG` 比较，排除树冠等上方遮挡。安神花允许树荫下地表。没有随机选地下高度的分支。
- 特别避免在 FEATURES 阶段依赖尚未完成的天空光照，因此不调用 `canSeeSky` 判定露天。
- 核对当前 `ChunkGenerator.applyBiomeDecoration` 使用 feature 索引集合对多群系去重；另外用弱键区块装饰记录按药草ID防御同一 `ChunkAccess` 在多步骤重复调用，包含失败抽取也只判一次。该记录只保护生成过程，不限制玩家种植和骨粉产物。

配置：`data/white_lily/worldgen/configured_feature/*_patch.json`、`worldgen/placed_feature/*_patch.json`、`forge/biome_modifier/add_*.json`、`tags/worldgen/biome/*_habitats.json`。

## SAN 重生修复的实际原因与处理

源码确认：旧代码只在 `PlayerLoggedInEvent` 执行一次按世界档位的SAN初始化；死亡创建了新的属性实例，默认值10000，但旧Clone流程只有计时器/融毁罚值清理，没有给新SAN属性按档位赋值，也没有对应的死亡重生初始化。旧Clone读取能力时未显式 revive/invalidate，非死亡Clone也没有显式复制SAN属性。这些都是服务端生命周期问题，不能仅靠刷新属性面板解决。

修改位置：

- `capability/player_variables.java`：Clone读取旧能力前 revive，finally invalidate。其他进度仍按原方式复制。死亡调用 `san_procedures.resetForDeath`；非死亡复制SAN状态及属性实例，不重置SAN，并保留医疗效果。
- `procedures/san_procedures.java`：死亡按 `debug_file.loadOrCreateLevel(server)` 的世界权威档位恢复SAN；清零融毁罚值、受伤计时器并重建属性修正。Clone尚未设置最终客户端实体ID，感染效果重评延后至Respawn。Respawn重新核对状态，并以正确实体ID发送属性及活动效果。没有每刻重写SAN。
- 普通登录只在 initialized=false 时初始化，保存加载及普通换维度不重置已有SAN。不会因药草恢复而重新初始化SAN。
- 新统一 `restore(player, low, high)` 在完成使用时读取有效SAN决定一次恢复量，考虑完整属性修正后按有效值恢复并遵守SAN上限，刷新SAN与感染条件。

自动测试通过真实 `ServerPlayer.restoreFrom` / Forge Clone事件链，包含旧能力先失效的情形：

| 权威档位 | 首次初始化 | 死亡重生 | 重复死亡 | 修改为4321后非死亡Clone/保存加载/登录 |
| --- | --- | --- | --- | --- |
| 和平1 | 50000 | 50000 | 50000 | 保留4321 |
| 简单2 | 22000 | 22000 | 22000 | 保留4321 |
| 普通3 | 10000 | 10000 | 10000 | 保留4321 |
| 困难4 | 2000 | 2000 | 2000 | 保留4321 |

测试核对了重生实际属性包的SAN基础值。没有实际用启动器客户端检查面板或执行真实玩家的跨维度旅行；非死亡Clone和存档加载测试不能被表述为已人工联机实测。

## 占位素材与替换

未找到对应正式素材，没有调用图片生成，没有覆盖已有用户贴图。当前为从本机Minecraft资源中原样复制的16×16 PNG占位。

物品目录：`src/main/resources/assets/white_lily/textures/item/`。文件名就是上述15种物品的注册名加 `.png`。

| 文件 | 当前占位来源 |
| --- | --- |
| simple_bandage.png | 原版纸 |
| clean_fine_bandage.png | 原版书 |
| bitterleaf.png | 原版蒲公英 |
| bitterleaf_paste.png | 原版黏液球 |
| yarrow.png | 原版滨菊 |
| yarrow_dressing.png | 原版线 |
| calming_flower.png | 原版兰花 |
| calming_flower_tea.png | 原版蜂蜜瓶 |
| pollution_chip_small/medium/large.png | 原版白/淡蓝/红染料 |
| cleansing_chip_small/medium/large.png | 原版紫水晶碎片/海晶碎片/回响碎片 |
| pollution_detector.png | 原版末影之眼 |

植物地面模型另用 `textures/block/bitterleaf.png`、`textures/block/yarrow.png`、`textures/block/calming_flower.png`，当前复制同样三种原版花。更换药草时同时替换 item 和 block 两处，名称不变无需改JSON。

包扎、抑制状态目前使用13/14号颜色占位图标。正式18×18透明PNG可放到 `textures/mob_effect/bandaged.png`、`textures/mob_effect/wound_suppression.png`，自动优先使用。模型在 `models/item/`、`models/block/`；blockstates和loot_tables已补齐。中英文物品说明沿用Shift展开。

## 获取与调试

管理员权限2，测试完整感染症状时切到生存；创造模式感染症状暂停仍按原约定。

```mcfunction
/give @s white_lily:simple_bandage 16
/give @s white_lily:clean_fine_bandage 16
/give @s white_lily:bitterleaf 64
/give @s white_lily:bitterleaf_paste 16
/give @s white_lily:yarrow 64
/give @s white_lily:yarrow_dressing 16
/give @s white_lily:calming_flower 64
/give @s white_lily:calming_flower_tea 1
/give @s white_lily:pollution_chip_small 64
/give @s white_lily:pollution_chip_medium 64
/give @s white_lily:pollution_chip_large 64
/give @s white_lily:cleansing_chip_small 64
/give @s white_lily:cleansing_chip_medium 64
/give @s white_lily:cleansing_chip_large 64
/give @s white_lily:pollution_detector
/white_lily infection get @s
/white_lily infection set @s 80
/attribute @s white_lily:san base set 4995
/attribute @s white_lily:san get
```

自然分布请在新生成的、符合标签的主世界群系区块检查。不要通过反复 `/place feature` 的结果当作自然生成频率测试。

## 验证范围

新增 `MedicalGameTests`、`MedicalLifecycleGameTests`、`MedicalWorldgenGameTests` 共13项分组测试，使用可控随机数；检查概率边界、合并感染事件、取消使用、包扎等级与计时、真实合成台批量返瓶、严格水瓶、药草治疗及副作用、渗血抑制、SAN阈值和属性上限、测试片权限/精确值、骨粉/土壤/掉落，以及四档SAN生命周期和发包。

世界生成测试读取实际已加载 configured feature，核对已应用群系装饰表中每种feature只出现一次；用受控世界运行真正Feature主体，检查上限、有限尝试、不越界及水/叶土/沙漠/遮挡/洞穴排除。未使用脆弱的随机频率统计断言。

最终单独执行 `gradlew.bat build` 成功，日志 `build/medical-build.log`；完整测试 **194项中193项通过，新增13项均通过**，日志 `build/medical-final-verification.log`。唯一失败仍为原有 `phantomFragmentsResourcesAreShipped`：旧断言要求幻像碎片栏位名称为空，现有作者资源已有名称。未修改该无关规则，因此完整 GameTest 命令仍返回失败，不能声称全部通过。

检查了两种语言的15组名称/说明、物品模型与实际16×16 PNG、解锁进度格式、JAR内医疗类/世界生成/掉落/模型/贴图，`git diff --check` 无空白错误。成品为 `build/libs/white_lily-0.1.2.jar`；联机两端均须替换为本次新文件。

仍需人工验证：植物与物品图像/渲染、长按与中断的交互体验、实际新探索区块分布、多人背包同步、真实退出重进与维度旅行，以及启动器客户端复活后的SAN面板。以上均没有声称已经人工游戏实测。
