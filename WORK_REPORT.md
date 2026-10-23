# White Lily —— 构建验证工作报告（交接给下一位代码助手）

> **最新记录：2026-10-23 第一批9节点研究路线、个人普通合成权限与加工台共享配方登记见第13节。前文构建次数、测试计数及“源码零改动”均属各轮历史记录，最终状态以第13节为准。**

- 仓库路径：`D:\cao的mod\white_lily`
- 分支 / HEAD：`mapple` @ `3d1aa02`（2026-10-22 17:47:52 +0800，提交信息「修复感染系统与兼容问题，重绘九件物品的16×16贴图」）
- 报告生成时间：2026-10-22 20:52:11 +08:00（Asia/Shanghai）
- 本报告只记录**实际执行过并有输出支撑**的事实；计划、推测、未执行的验证一律放在第 6 节并明确标注。

---

## 0. 摘要（30 秒版）

> **两轮结果必须分开看**：第 1 轮只构建；第 2 轮（2026-10-22 20:53–20:57）做了全量重编译 + 真正跑 GameTest。**最新状态以第 2 轮为准。**

| 轮次 | 内容 | 结果 |
| --- | --- | --- |
| 第 1 轮 | `.\gradlew.bat build`（增量） | ✅ 成功，14s；`compileJava` UP-TO-DATE（未真正重编译） |
| 第 2 轮 | 备份复制 + `build --rerun-tasks` | ✅ 成功，22s，**11/11 任务实际执行**，编译 185 个 class，46 条弃用警告 |
| 第 2 轮 | `runGameTestServer` | ❌ **207 个测试执行、206 通过、1 失败、0 跳过**；Gradle 退出码 1 |

- **唯一失败测试**：`phantomfragmentsresourcesareshipped` —— 断言 `zh_cn` 的栏位名必须为空字符串，实测为 `「幻像碎片」`。测试与 lang 值来自**同一个提交** `a0c125fe`，属提交内自相矛盾，不是本次改动造成。
- **源码零改动**：两轮都没有改任何 `.java` / 资源 / 构建脚本；`git status` 只显示 `WORK_REPORT.md`、`backups/` 和两个既有 `docs/*.json`。
- **本轮未改任何文件夹权限**。构建仍需要写 `C:\Users\John\.gradle\wrapper\dists\...\gradle-8.8-bin.zip.lck`，被会话的写入范围拒绝（具体路径与报错见 7.2）；随后会话策略由用户改为完全权限，`runGameTestServer` 因此无需再申请。
- 仍未做：`clean build`、客户端启动、生产（reobf）jar 的实际加载。详见 7.7。

---

## 0.1 当前已知失败项登记（**保持红，不得强行通过**）

> 登记时间：2026-10-22 21:02:03 +08:00；基线 `mapple` @ `3d1aa02`。
> 状态：**未修复、故意保持失败**。此条是后续开发的固定红线，除作者明确指示外不得动。

| 字段 | 内容 |
| --- | --- |
| 测试名（报告里的写法） | `phantomfragmentsresourcesareshipped` |
| 测试方法 | `WhiteLilyGameTests.phantomFragmentsResourcesAreShipped(GameTestHelper)`，`src/main/java/com/damezame97/white_lily/gametest/WhiteLilyGameTests.java:3076` |
| 失败断言 | 同文件 `3107–3108`：`assertTrue(value.isEmpty(), locale + " 的栏位名应当留空，实测「" + value + "」")` |
| 失败原文 | `phantomfragmentsresourcesareshipped failed! zh_cn 的栏位名应当留空，实测「幻像碎片」` |
| 实际值 | `assets/white_lily/lang/zh_cn.json:74` = `"幻像碎片"`；`en_us.json:74` = `"Phantom Fragments"`（`en_us` 尚未被执行到，但同样非空） |
| 期望值 | 空字符串（`curio_slots.java:65` 注释：按需求留空，内容由作者自己填） |
| 复现方式 | `.\gradlew.bat runGameTestServer`，日志 `build\gametest-run.log`（GBK 编码） |
| 唯一性 | 207 个测试中仅此 1 个失败，其余 206 通过，0 跳过 |

**冻结规则（本轮起生效）：**

1. 不得为了让测试变绿而清空 `zh_cn` / `en_us` 的 `curios.identifier.phantom_fragments`；
2. 不得修改、放宽、跳过或删除该断言（含 `@GameTest` 的参数、`helper.succeed()` 位置）；
3. 不得用排除命名空间、改 `forge.enabledGameTestNamespaces`、加 `@Disabled` 之类手段绕过；
4. 修复方向（清空 lang 值 vs 改测试预期）属产品意图，**必须先由作者确认**；
5. 任何改动后重跑 `runGameTestServer`，如实报告此条是否仍为失败——它**预期保持失败**，直到作者决定方向。

**当前工作区状态（登记时实测）：** `git status` 仍只有 `?? WORK_REPORT.md`、`?? backups/` 与两个既有 `docs/*.json`；`src/` 下文件最后修改时间仍是 2026-10-22 17:41:56（= HEAD 提交时间），说明自上一次 GameTest 之后无人改过源码。

---

## 1. 任务是什么，已经完成了什么

### 1.1 本次任务（用户原始要求）

1. 不修改代码；
2. 确认工作区是 `D:\cao的mod\white_lily`；
3. 读取项目说明，检查 Java 版本与 Gradle Wrapper；
4. 用 PowerShell 在项目根目录运行 `.\gradlew.bat build`；
5. 报告构建是否成功，失败则先说明实际错误；
6. 不安装额外插件，不修改玩法数值。

### 1.2 已完成

| # | 事项 | 结果 |
| --- | --- | --- |
| 1 | 工作区确认 | `Get-Location` 返回 `D:\cao的mod\white_lily` ✅ |
| 2 | 阅读项目说明 | 已读 `README.md`（104 行）、`build.gradle`（268 行）、`gradle.properties`、`settings.gradle`、`mods.toml` ✅ |
| 3 | Java 版本检查 | Temurin `17.0.20.1`，与 `build.gradle` 的 toolchain 17 一致 ✅ |
| 4 | Gradle Wrapper 检查 | 指向 `gradle-8.8-bin`，本地 `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.8-bin` 已缓存 ✅ |
| 5 | 执行 `.\gradlew.bat build` | **BUILD SUCCESSFUL in 14s**，退出码 0 ✅（中途一次沙箱拦截至失败，见第 5 节） |
| 6 | 产物核对 | `build\libs\white_lily-0.1.2.jar`，622,331 字节，2026-10-22 20:48 生成，条目 463 个 ✅ |
| 7 | 打包结构核对 | jar 内 `white_lily.mixins.json`、`white_lily.refmap.json`、10 个 mixin 类 + 1 个 `InfectionMixinPlugin`、MANIFEST 的 `MixinConfigs` 均在 ✅ |

**没有做任何功能开发或数值调整。** 本次工作范围内没有新增/修改任何 Java 或资源文件（证据见第 2 节）。

### 1.3 环境事实

| 项目 | 实际值 | 来源 |
| --- | --- | --- |
| OS 架构 | `amd64` | 构建日志首行 `Java: 17.0.20.1, JVM: 17.0.20.1+1 (Eclipse Adoptium), Arch: amd64` |
| JVM | Eclipse Adoptium Temurin 17.0.20.1+1 | 同上 |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\` | `$env:JAVA_HOME` |
| Gradle | 8.8（`distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip`） | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle 其他版本缓存 | `gradle-9.6.0-bin` 也在 dists 里存在，但本构建未使用 | `%USERPROFILE%\.gradle\wrapper\dists` 列表 |
| `org.gradle.jvmargs` | `-Xmx3G`；`org.gradle.daemon=false` | `gradle.properties` 第 3–4 行 |
| 构建时实际行为 | 因 daemon 关闭，「single-use Daemon process will be forked」 | 构建日志 |

---

## 2. 修改、新增了哪些文件，以及各自用途

### 2.1 源码 / 构建脚本：**零改动（可验证）**

```
$ git -c core.quotepath=false status --porcelain=v1
?? WORK_REPORT.md                                  ← 本报告（本次新增）
?? docs/基础材料处方戒指贴图重绘记录.json            ← 既有，不是本次产生
?? docs/贴图绘制记录.json                          ← 既有，不是本次产生
```

- 两个 `docs/*.json` 是**此前就存在**的未跟踪文档，**不是本次工作产生的**；
- 除此之外没有任何 `M`/`A`/`D` 状态，即所有被跟踪的源码、资源、`build.gradle`、`gradle.properties` 均未改动；第 1 轮唯一新增的文本文件就是这份 `WORK_REPORT.md`（第 2 轮又新增了 `backups/`，见 7.6）。

### 2.2 被重新生成的构建产物（非源码，`build/` 下）

| 文件 | 用途 / 变化 |
| --- | --- |
| `build\libs\white_lily-0.1.2.jar` | 第 1 轮 `jar` + `reobfJar` 重新产出的模组 jar；622,331 B / 463 条目（第 2 轮 `--rerun-tasks` 后为 **622,376 B**，见 7.2） |
| `build\libs\white_lily-0.1.1.jar` | **旧产物，未删除**（4,694,573 B，2026-10-21 20:52，192 条目），仅供对比 |
| `build\classes\java\main\**` | 编译输出（本次 `compileJava` 为 UP-TO-DATE，未重新编译） |
| `build\downloadMcpConfig\`、`build\extractSrg\`、`build\createSrgToMcp\` | ForgeGradle 的映射中间产物（部分 UP-TO-DATE） |
| `build\resources\main\**` | `processResources` 展开占位符后的资源 |
| `build\texture-backup-20261022\` | **用户既有的贴图备份，本次未触碰**；正因为它在 `build/` 下，所以刻意没有执行 `clean` |

### 2.3 工作区之外的文件（沙箱权限修复，非项目文件）

| 路径 | 用途 |
| --- | --- |
| `D:\cao的mod\dsh-acl-report-20261022\acl-backup-bcba95996774435d940e6e22b8f039e0.json` | `D:\cao的mod\white_lily` 文件夹权限的**修改前**备份记录 |
| `D:\cao的mod\dsh-acl-report-20261022\acl-backup-bcba95996774435d940e6e22b8f039e0.json.ps1` | 独立回滚脚本 |
| `D:\cao的mod\dsh-acl-report-20261022\acl-report-1e131a7c4bc44604893288649b1d71a4.jsonl` | 完整诊断日志（每条权限变更与验证） |
| `WORK_REPORT.md`（本文件，工作区根目录） | 交接报告；**未加入 git**，提交前请自行决定是否保留/移动 |

权限变更内容：为当前用户（SID `S-1-5-21-1240274777-2905147200-165758857-1001`）在 `D:\cao的mod\white_lily` 上补了一条「完全控制」允许项。原因是该文件夹原先**缺少 WRITE_OWNER（取得所有者）**，导致 DSH 沙箱无法为工作区做写入授权。所有者未变、文件内容未变、未删除任何权限项。

回滚命令（**需要时**由人工执行）：

```powershell
pwsh -NoProfile -File 'D:\cao的mod\dsh-acl-report-20261022\acl-backup-bcba95996774435d940e6e22b8f039e0.json.ps1' -Path 'D:\cao的mod\white_lily' -AllowRoot 'D:\cao的mod\white_lily' -Restore 'D:\cao的mod\dsh-acl-report-20261022\acl-backup-bcba95996774435d940e6e22b8f039e0.json'
```

---

## 3. 当前实现的关键规则和数值（**本次未改动**，仅供下一位助手建立基线）

> 说明：以下均从代码中直接读出并标注了出处。仓库共 **129** 个 `.java` 文件，本节只覆盖与「构建 / 堆叠 / 属性 / 载重 / 理智 / 感染」直接相关且已逐行核对的部分；其余模块（医疗、处方、枪械/弓箭、Curios、世界生成等）**未逐行核对**，不要当作已确认。

### 3.1 构建与依赖规则

| 规则 | 数值 / 说明 | 出处 |
| --- | --- | --- |
| MC / Forge | `1.20.1` / `47.4.10`，范围 `[47,)` | `gradle.properties` |
| Java | 编译 target 17（`java.toolchain.languageVersion = JavaLanguageVersion.of(17)`） | `build.gradle` 第 18 行 |
| 映射 | `official` / `1.20.1` | `gradle.properties` 第 35–38 行 |
| 模组版本 | `0.1.2`，`mod_id=white_lily`，`mod_group_id=com.damezame97.white_lily` | `gradle.properties` |
| 插件 | ForgeGradle `[6.0,6.2)`、MixinGradle `0.7.38`、`foojay-resolver-convention 0.7.0`（均为**既有**配置） | `build.gradle` 第 5–7 行、`settings.gradle` |
| 第三方依赖 | 11 个 jar 走 `flatDir dir 'libs'` + `fg.deobf`；其中 Curios / ISS / TaCZ 额外 `compileOnly` | `build.gradle` 第 161–208 行 |
| Mixin APT | `annotationProcessor 'org.spongepowered:mixin:0.8.5'`；AP 路径拼接主编译类路径（借 gson/guava） | `build.gradle` 第 140–142、169 行 |
| 别名任务 | `jar` 由 `reobfJar` 收尾；`jarJar`/`reobfJarJar` 本次 **SKIPPED**（无 jar-in-jar 打包） | `build.gradle` 第 244 行 + 构建日志 |
| Manifest | `MixinConfigs: white_lily.mixins.json`、`Implementation-Version: 0.1.2` 已写入 | 实测读取 jar 的 `META-INF/MANIFEST.MF` |
| 配置项 | `debugLogging`(false)、`Enable special window`(false)、`infection.enabled`(true)、`infection.keepOnDeath`(true) | `Config.java` 第 18–34 行 |

### 3.2 超大堆叠 = 128

- `MixinInventory`：开发名 `getMaxStackSize()` 与 SRG 名 `m_6893_()` **各写一份，都返回 128**。原因见类注释：Mixin 给 `Inventory` **新增**的方法不会被重映射，只写官方名会「开发能跑、打包失效」。影响范围仅玩家背包，箱子/熔炉不受影响。
- `MixinItemStack`：仅当 `count > 127` 时把 NBT `"Count"` 从 ByteTag 改写成 IntTag；读取统一用 `getInt`。≤127 时完全保留原版 Byte 格式以保持跨模组兼容。
- 注意：**未改** `ItemStack#getMaxStackSize`；夹 64 的是 `Container#getMaxStackSize`。

### 3.3 自定义属性（共 **19** 条，全部挂到 `EntityType.PLAYER`）

`attribute/attributes.java` 中 `register("...")` 实测计数 = 19：`damage_mitigation`、`damage_reduction`、`load_capacity`、`all_spell_power`、`all_spell_resis`、`san`、`willpower_strength`、`rifle_expertise`、`rifle_enhancement`、`spell_enhancement`、`spell_critical_strike`、`expert_in_archery`、`arrow_strike`、`arrow_crit_damage`、`recovery_interval`、`restore_health`、`stamina`、`infection_resistance`、`building_endurance`。

关键默认值 / 边界（`[默认, 最小, 最大]`）：

| 属性 | 默认 | 范围 | 语义 |
| --- | --- | --- | --- |
| `damage_mitigation` | 0.0 | 0 … 1038576 | 固定值加减，先于减免结算 |
| `damage_reduction` | 0.0 | 0 … 1.0 | 每 0.01 = 1% 减免，后于抵抗结算 |
| `load_capacity` | 100.0 | 0 … 10024 | 登录时按难度覆盖（见 3.4） |
| `all_spell_power` / `all_spell_resis` | 0.00 | −10024 … 10024 | 0.10 = 每学派 +10% |
| `san` | 10000.0 | 0 … 100000024 | 登录时按难度覆盖（见 3.5） |
| `willpower_strength` | 0.05 | −10024 … 10024 | 意志坚韧度 |
| `spell_critical_strike` | 1.00 | −10024 … 10024 | 法术暴击 |
| `expert_in_archery` | 1.00 | −10024 … 10024 | 箭术精通 |
| `arrow_strike` | 0.05 | 0 … 10024 | 箭矢暴击率 |
| `arrow_crit_damage` | 1.50 | 0 … 10024 | 箭矢暴击伤害 |
| `restore_health` | 1.0 | −10086 … 10086 | Happy_mode 每次回血量（可负） |
| `recovery_interval` | 0.0 | −10086 … 10086 | 每 +1 间隔 −1 秒，下限 5 秒 |
| `stamina` | 0.0 | −10086 … 10086 | 每次恢复扣除的饱和度 |
| `infection_resistance` | 0.0 | **0 … 0.8** | 0.01 = 1%，只降新增、不改已有 |
| `building_endurance` | 0.0 | 0 … 10086 | 每 1 点让储存饱和度上限 +1 |

> ⚠️ `attributes.java` 第 59 行注释仍写「目前 14 条数值相同」，与实际 19 条不符，属**过期注释**。

### 3.4 载重（`procedures/load_capacity_procedures.java`）

- 按难度默认：和平 `150` / 简单 `140` / 普通 `120` / 困难 `100`（常量 `CAPACITY_*`，第 83–92 行；`case 1..4 ->` 映射在第 141 行附近）。
- 仅在 `v < 100` 时生效：`n = clamp(floor(100 − v), 0, 10)`。
- 承受伤害 ×`(1 + 0.20 × n)`（n=10 → 3 倍）；造成伤害 ×`(1 + 0.10 × n)`（n=10 → +100%）。
- 反噬 `white_lily:load_burden` = 生命上限 × `m × 100%`，`m = clamp(floor(90 − v), 0, 50)`，封顶 **5000%**（`MAX_BURDEN_FRACTION = 50.0`）；`v < 90` 才触发，同一玩家两次反噬至少隔 **5 游戏刻**（`BURDEN_COOLDOWN_TICKS`），单次数值硬上限 **1e9**（`MAX_LOAD_BURDEN_DAMAGE`），且反噬伤害不再触发反噬（防递归）。GameTest 断言：20 血 × 5000% = 1000。
- 挂载点：`LivingDamageEvent` + `EventPriority.LOWEST`（护甲/附魔/伤害吸收之后），是最后一个改数值的人。
- 注意：默认值来源是 `debug_file#loadOrCreateLevel` 的 json 参数，**不是**当前难度；难度只在首次/缺失时兜底。

### 3.5 理智 SAN（`procedures/san_procedures.java`）

- 按难度默认：和平 `50000` / 简单 `22000` / 普通 `10000` / 困难 `2000`（`SAN_*` 与 `sanForLevel/sanForDifficulty`）。
- 状态边界（`san_procedures.State.of`）：`OVERFLOW_ABOVE = 100000.0`、`NORMAL_MIN = 5000.0`、`COLLAPSE_MIN = 1000.0` → 过盛 >100000、正常 [5000, 100000]、崩溃 [1000, 5000)、融毁 <1000。
- 崩溃档附带修正：`COLLAPSE_STAMINA_DELTA = -10.0`（耐力每次多扣 10）、`COLLAPSE_RESTORE_HEALTH_DELTA = -1.0`（每次恢复血量 −1）；生命上限归 0 时立刻死亡，致死播报用 `white_lily:meltdown`。

### 3.6 感染（`infection/infection_math.java`、`capability/infection_state.java`）

- 内部整数单位为 0.1，`MAX_UNITS = 3000`（即上限 300.0），被动周期 `PERIOD = 1200` tick；夹值后才转 int，禁止 NaN/Infinity（非有限值归 0）。
- 伤势档位（按生命比 `ratio`）：`>0.70` HEALTHY（×1.0 / 被动 0.0）、`>0.40` WOUNDED（×1.2 / 0.5）、`>0.20` SEVERE（×1.8 / 1.0）、其余 CRITICAL（×2.8 / 2.0）。
- 风险系数：`1 − stamina × 0.1`（stamina<0 时用 0.25），夹在 `[0.5, 2.0]`。
- 抗性：`clamp(raw, 0, 0.8)`，与属性 `infection_resistance` 上限 0.8 一致。
- 结算：外部感染 `= round1(base × (1 + (multiplier−1) × risk) × (1 − resistance))`；被动 `= round1(passive × risk × (1 − resistance))`；每次独立 HALF_UP，**不累计小数余量**。
- 死亡处理：`keepOnDeath=false` 时清零感染；无论取值如何，`passive_ticks` 与症状均清零。

### 3.7 Mixin 清单（已随 jar 打包）

`white_lily.mixins.json`：`required=true`、`compatibilityLevel=JAVA_17`、`defaultRequire=1`、`plugin=...infection.InfectionMixinPlugin`、`refmap=white_lily.refmap.json`。
- 通用 8 个：`MixinDamageSource`、`MixinFoodData`、`MixinFriendlyByteBuf`、`MixinInventory`、`MixinItemStack`、`MixinPlayer`、`MixinSlot`、`infection.MixinInfectionSpellCost`
- 客户端 2 个：`client.MixinTitleScreen`、`client.MixinInfectionBlindness`
- 目标类均真实存在（glob 已确认 11 个文件）；`InfectionMixinPlugin` 会在 ISS 的 `AbstractSpell` 三个注入点不全时**整体关闭**扣费 hooks 并打 error 日志（测试版本 ISS `1.20.1-3.16.3`），不使游戏崩溃。

---

## 4. 执行了哪些构建/测试命令，结果如何

| # | 命令 | 工作目录 | 结果 |
| --- | --- | --- | --- |
| 1 | `java -version` / `javac -version` | 任意 | ✅ `openjdk version "17.0.20.1" 2026-08-18`（Temurin-17.0.20.1+1）；`javac 17.0.20.1` |
| 2 | `cmd /c ".\gradlew.bat build 2>&1"`（第 1 次，沙箱 workspace-write） | `D:\cao的mod\white_lily` | ❌ 退出码 1，**启动阶段**失败（详见第 5.1 节） |
| 3 | `cmd /c ".\gradlew.bat build 2>&1"`（第 2 次，一次更宽权限） | 同上 | ✅ **BUILD SUCCESSFUL in 14s**，退出码 0 |
| 4 | `git status --porcelain=v1` | 同上 | ✅ 仅 2 个既有未跟踪 `docs/*.json`（本报告生成后才多出 `WORK_REPORT.md`） |
| 5 | jar 条目 / MANIFEST 读取（`System.IO.Compression.ZipFile`） | 同上 | ✅ 463 条目；`MixinConfigs: white_lily.mixins.json`；`Implementation-Version: 0.1.2` |

### 4.1 成功的构建日志（完整）

```
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.8/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 

> Configure project :
MixinGradle did not locate the diffplug APT plugin, skipping eclipse task configuration
Java: 17.0.20.1, JVM: 17.0.20.1+1 (Eclipse Adoptium), Arch: amd64

> Task :downloadMcpConfig
> Task :extractSrg UP-TO-DATE
> Task :createMcpToSrg UP-TO-DATE
> Task :compileJava UP-TO-DATE
> Task :addMixinsToJar
> Task :processResources
> Task :classes
> Task :jar
> Task :configureReobfTaskForReobfJar
> Task :reobfJar
> Task :configureReobfTaskForReobfJarJar
> Task :addMixinsToJarJar
> Task :jarJar SKIPPED
> Task :reobfJarJar SKIPPED
> Task :assemble
> Task :compileTestJava NO-SOURCE
> Task :processTestResources NO-SOURCE
> Task :testClasses UP-TO-DATE
> Task :test NO-SOURCE
> Task :check UP-TO-DATE
> Task :build

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, refer to https://docs.gradle.org/8.8/userguide/command_line_interface.html#sec:command_line_warnings

BUILD SUCCESSFUL in 14s
11 actionable tasks: 8 executed, 3 up-to-date
```

**关于 `compileJava UP-TO-DATE` 的诚实说明**：本次没有重新编译任何 `.java`（输入未变），真正重跑的是 `processResources` → `jar` → `addMixinsToJar` → `reobfJar` → `assemble`。因此这次成功证明的是「**当前源码树能通过增量编译并被完整打包**」，而不是「一次从零的全新编译」。`git status` 显示源码无改动，所以 `UP-TO-DATE` 对应的正是 HEAD 的源码。

### 4.2 产物核对

```
Name                  Length LastWriteTime      
----                  ------ -------------      
white_lily-0.1.1.jar 4694573 2026/10/21 20:52:35
white_lily-0.1.2.jar  622331 2026/10/22 20:48:13
```

| jar | 条目 | .class | .json | .png | 说明 |
| --- | --- | --- | --- | --- | --- |
| 0.1.1（旧） | 192 | 74 | 44 | 21 | 体积主要来自大尺寸物品贴图：最大条目 `assets/white_lily/textures/item/revealed_prescription.png` 达 979,168 B（其后 `blank_prescription.png` 893,045 B） |
| 0.1.2（本次） | 463 | 185 | 133 | 61 | 同名贴图仍在但已缩到 16×16（对应 HEAD 提交「重绘九件物品的16×16贴图」），故条目变多而总体积变小；最大条目变成 `WhiteLilyGameTests.class`（126,247 B）。第 2 轮 `--rerun-tasks` 重新生成后为 622,376 B |

### 4.3 **没有执行**的验证（重要）

> 以下是**第 1 轮**的情况。第 2 轮已补跑 `runGameTestServer`（见第 7 节）；`clean build`、`runClient`、`runServer` 仍未执行。

- ❌ `.\gradlew.bat runGameTestServer` —— 第 1 轮未运行（第 2 轮已跑，1 个失败）。
- ❌ `.\gradlew.bat clean build` —— 刻意未运行，见 5.2。
- ❌ `runClient` / `runServer` —— 未运行，因此**运行期 Mixin 是否真正生效未经验证**（第 2 轮通过 GameTest 启动日志间接证实，见 7.4）。
- ❌ `--warning-mode all` —— 未运行，Gradle 9 弃用项未逐条查明。

---

## 5. 失败时的完整报错与日志

### 5.1 第 1 次 `.\gradlew.bat build` 失败（**环境/沙箱问题，不是项目代码问题**）

退出码 1。完整 stderr（原文为 GBK 输出，中文「拒绝访问」在采集时出现乱码，已在括号内注明）：

```
Exception in thread "main" java.io.FileNotFoundException: C:\Users\John\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8-bin.zip.lck (�ܾ����ʡ�)
	at java.base/java.io.RandomAccessFile.open0(Native Method)
	at java.base/java.io.RandomAccessFile.open(RandomAccessFile.java:344)
	at java.base/java.io.RandomAccessFile.<init>(RandomAccessFile.java:259)
	at java.base/java.io.RandomAccessFile.<init>(RandomAccessFile.java:213)
	at org.gradle.wrapper.ExclusiveFileAccessManager.access(ExclusiveFileAccessManager.java:53)
	at org.gradle.wrapper.Install.createDist(Install.java:68)
	at org.gradle.wrapper.WrapperExecutor.execute(WrapperExecutor.java:109)
	at org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:66)
[status: completed, exit code: 1]
```

**根因**：Gradle Wrapper 必须在 `%USERPROFILE%\.gradle\wrapper\dists\...` 创建 `.lck` 锁文件，该路径在工作区之外，被「只允许写工作区」的沙箱拒绝（`�ܾ����ʡ�` = 「拒绝访问」）。与此同时工作区文件夹本身也缺 `WRITE_OWNER`，导致沙箱连工作区写入授权都无法建立：

```
Error: SetNamedSecurityInfoW failed (Win32 5): grantWrite(D:\cao的mod\white_lily)
```

**处置**：先用随附的 ACL 诊断脚本检查并修复了 `D:\cao的mod\white_lily` 的权限（验证结果 `writeDac:true → writeOwner:true`，`GRANTED`），再**原样重跑同一条命令**并给予一次更宽权限，构建成功（第 4.1 节）。

**遗留影响**：这台机器上，若会话再次被限制为「仅写工作区」，`gradlew` 会在启动阶段就失败。**要让 Gradle 正常工作，必须允许写 `%USERPROFILE%\.gradle`**（或者把 `GRADLE_USER_HOME` 指到一个可写目录，但那会触发重新下载依赖，本次未采用）。

### 5.2 为什么没跑 `clean build`

`build\texture-backup-20261022\` 里存有用户此前的贴图备份，实测 **39 个 PNG**（`block/` 8、`item/` 17、`mob_effect/` 14）。`clean` 会把整个 `build\` 删掉，**包括这份备份**。因此本次只做增量构建。若需要从零验证，应先把该备份移出 `build\`。

### 5.3 其他非致命提示（非失败）

- `MixinGradle did not locate the diffplug APT plugin, skipping eclipse task configuration` —— Eclipse 专用配置被跳过，与 `build` 无关。
- `Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.` —— 升级 Gradle 9 前需处理。
- `jarJar SKIPPED` / `reobfJarJar SKIPPED` —— 配置无 jar-in-jar，属预期。
- `test NO-SOURCE` —— 本项目 GameTest 走 `runGameTestServer`，不是 JUnit，故 `test` 任务无源码，属预期。

---

## 6. 未完成、未验证、需要确认的内容

### 6.1 本次任务范围内未验证

1. ~~**GameTest 全部未运行**~~ **→ 第 2 轮已运行**：实际执行 207、通过 206、失败 1、跳过 0，见 7.3。代码中 `@GameTest` 注解数也是 207，但执行数是从 log 的 batch 大小（100+100+7）独立得出的，不是拿注解数当执行数。分布：`WhiteLilyGameTests` 107、`StoredSufferingGameTests` 24、`InfectionGameTests` 18、`InfectionSymptomsGameTests` 13、`PrescriptionGameTests` 13、`MedicalGameTests` 8、`HempGameTests` 6、`ReviewRegressionGameTests` 6、`LowQualityPotionGameTests` 5、`MedicalLifecycleGameTests` 3、`MedicalWorldgenGameTests` 2、`BasicMaterialsGameTests` 1、`HempWorldgenGameTests` 1。
2. ~~**运行期 Mixin 未验证**~~ **→ 第 2 轮已间接验证**：`runGameTestServer` 启动日志显示 8 个通用 mixin 全部成功应用到目标类（含 `infection.MixinInfectionSpellCost` → ISS `AbstractSpell`），且 `required=true` 未触发崩溃，见 7.4。但**客户端 2 个 mixin 仍未验证**（专用服务器不加载 client 配置），生产（reobf）环境也未验证，并且日志有一条 `Reference map 'white_lily.refmap.json' ... could not be read` 的 dev 警告需要留意。
3. **未验证客户端/服务端启动**（`runClient` / `runServer`）：第 2 轮只启动了 GameTest 专用服务器，客户端渲染/特殊窗口/音效等路径仍未跑过。
4. **未跑 `clean build`**：第 2 轮用 `--rerun-tasks` 强制所有任务重跑（11/11 executed，编译出 185 个 class），但 `build/` 目录本身没有清空；「从空目录全新构建」仍未验证。
5. Gradle 9 弃用警告未逐条定位（第 2 轮实测 46 条 javac 弃用警告 + Gradle 自身的 Gradle 9 兼容提示）。

### 6.2 文档与实际代码不一致（建议下一位助手核对后再改，**本次未动**）

| 位置 | 文档写的 | 实际 |
| --- | --- | --- |
| `README.md` 第 56–62 行 | 「6 个 GameTest」「输出 `All 6 required tests passed :)`」 | 实际 **207** 个 `@GameTest`、13 个测试类；第 2 轮实测输出是 `207 GAME TESTS COMPLETE` + `1 required tests failed :(` |
| `README.md` 第 64–83 行「项目结构」 | 只列 13 个包 | 实际含 `block/`、`crafting/`、`debugging/`、`infection/`、`worldgen/`，以及 `client/`、`mixin/client/`、`mixin/infection/` 下大量新类 |
| `README.md` 第 85–89 行「配置」 | 只写 `debugLogging` | `Config.java` 还有 `Enable special window`、`infection.enabled`、`infection.keepOnDeath`（后两项在 server 配置） |
| `attribute/attributes.java` 第 59 行注释 | 「目前 14 条数值相同」 | 实际 19 条，且数值早已各不相同 |
| `src/main/resources/META-INF/mods.toml` 第 26 行 | 「install this mod and its dependencies simultaneously on both the client side and **the mobile device**」 | 中文原意是「客户端与服务端」；英文把服务端误写成 mobile device，建议改成 `server side` |

### 6.3 仓库状态需要人工决定

1. `docs/基础材料处方戒指贴图重绘记录.json`、`docs/贴图绘制记录.json` 是**既有的未跟踪文件**（不是本次产生），是否提交需仓库维护者决定。
2. `backups/texture-backup-20261022/` 是第 2 轮新建的备份副本（39 文件），当前未跟踪；是否纳入版本库由维护者决定。
3. 本报告 `WORK_REPORT.md` 位于工作区根目录、未加入 git；不需要时可直接删除。
4. `build\libs\` 同时存在 `0.1.1` 与 `0.1.2` 两个 jar，发布前注意别拿错。
5. `run\` 目录被第 2 轮的 `runGameTestServer` 创建/改写过（`world/`、`logs/`、`config/`、`tacz/`、`saves/asdfg/` 等）。`run\world\Debugging_whitelilymod.json` 与 `run\saves\asdfg\...json` 里还留有旧的调试数据（本次日志显示曾读到 `difficulty_level = 99` 和一段语法错误的 JSON，随后被模组按当前难度改写成 2）。**这不是干净环境**，重跑 GameTest 前建议先确认 `run/` 状态。
6. **唯一失败测试的处理方式需要作者决定**：是把 `lang` 值清空（让测试通过、栏位名暂时显示为空），还是修改测试预期（承认栏位名应有文字）。两种做法都涉及产品意图，助手不应擅自选择；本次两者都没动。
7. `D:\cao的mod\white_lily` 的文件夹权限曾在第 1 轮被追加一条「完全控制」（备份与回滚脚本见 2.3 节）。**第 2 轮没有再改任何文件夹权限**；如果这台机器是共享/多用户环境，仍请确认第 1 轮那条授权是否可接受。

### 6.4 给下一位助手的建议顺序

1. `git log --oneline -5` + `git status` 确认起点（本报告基线为 `mapple` @ `3d1aa02`）；
2. 先读第 7 节拿到第 2 轮的真实测试结果，**不要**再假设「GameTest 没跑过」；
3. 唯一失败项 `phantomfragmentsresourcesareshipped` 属于产品意图问题（见 7.5），处理前先向作者确认，**不要为了绿灯去删测试或放宽断言**；
4. 若要补全验证：先备份并移出 `build\texture-backup-20261022\`，再跑 `clean build`；随后 `runClient` / `runServer` 验证客户端 mixin 与生产 reobf 路径；
5. 最后按 6.2 修正文档；改动 `mods.toml` 的英文描述属于文案修正，不涉及数值。

---

## 7. 第二轮验证记录（2026-10-22 20:53–20:57）

> 本轮目标：备份核对 → 真正重新编译 → 真正跑 GameTest。**未新增功能、未调整任何玩法数值、未改任何文件夹权限。**

### 7.1 备份复制与校验

| 项目 | 结果 |
| --- | --- |
| 源 | `build\texture-backup-20261022` |
| 目标 | `backups\texture-backup-20261022`（新建，此前不存在） |
| 文件数 | 源 **39** → 目标 **39**（`block/` 8、`item/` 17、`mob_effect/` 14） |
| 总字节 | 源 **13,193** → 目标 **13,193** |
| 逐文件 SHA-256 | `COPIED=39 SAME=0 CONFLICT=0 HASH_MISMATCH=0` |
| 整体树哈希 | `4921A77766E7FC212FA8D051855B0E01832E93FA471D2C9EA9DB8B8A0A0E5D38`，`TREE_EQUAL=True` |
| 原备份 | **未删除**（`build\texture-backup-20261022` 原样保留） |
| 覆盖行为 | 目标文件不存在 → 只做新增；实现里对已存在文件先比哈希，相同跳过、不同则记录冲突且**不覆盖**（本次冲突数为 0） |

树哈希算法：按相对路径排序，拼成 `相对路径:SHA-256` 的多行文本，再对整段文本取 SHA-256。可用它快速判断副本是否被改动。

### 7.2 `.\gradlew.bat build --rerun-tasks`

**第 1 次（受限）失败 —— 与第 1 轮同一个原因，不是代码问题：**

```
Exception in thread "main" java.io.FileNotFoundException: C:\Users\John\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8-bin.zip.lck (拒绝访问)
	at java.base/java.io.RandomAccessFile.open0(Native Method)
	...
	at org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:66)
```

被拒路径：`C:\Users\John\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8-bin.zip.lck`，原因：该路径在工作区之外，会话只允许写工作区。**本轮按要求没有改任何文件夹权限**，而是申请一次性更宽的执行范围后原样重跑。

**第 2 次（成功）：**

```
> Task :downloadMcpConfig
> Task :extractSrg
> Task :createMcpToSrg

> Task :compileJava
注: SpongePowered MIXIN Annotation Processor Version=0.8.5 (MixinGradle Version=0.7.38)
注: Loading searge mappings from D:\cao的mod\white_lily\build\createMcpToSrg\output.tsrg
注: Writing refmap to D:\cao的mod\white_lily\build\tmp\compileJava\compileJava-refmap.json
...（共 46 条警告，全部是 javac 的 [removal]/deprecation 提示）...
46 个警告

> Task :addMixinsToJar
> Task :processResources
> Task :classes
> Task :jar
> Task :reobfJar
> Task :jarJar SKIPPED
> Task :reobfJarJar SKIPPED
> Task :assemble
> Task :test NO-SOURCE
> Task :build

BUILD SUCCESSFUL in 22s
11 actionable tasks: 11 executed
```

- **11/11 任务全部 executed**，没有任何 `UP-TO-DATE` —— 这才算真正重编译（与第 1 轮的 3 个 UP-TO-DATE 不同）。
- `compileJava` 实际产出 **185 个 class**（`build\classes\java\main`），Mixin APT 重新生成了 refmap 与 searge 映射。
- 46 条警告的类别：`ResourceLocation(String,String)` / `ResourceLocation(String)` 已弃用（占大多数）、`ModLoadingContext.get()`、Curios 的 `ISlotHelper` / `getSlotHelper()` / `setEquippedCurio`。**均为警告，不影响构建产物**。
- 产物：`build\libs\white_lily-0.1.2.jar`，**622,376 B**，2026-10-22 20:55:49（比第 1 轮 622,331 B 多 45 B，差异来自 manifest 的 `Implementation-Timestamp`）。
- 说明：本轮 build 的控制台输出**没有落盘**（只有下面的 GameTest 日志落了盘）；上表为任务列表与结论的完整摘录，警告全文可用同一条命令重跑复现。

### 7.3 `.\gradlew.bat runGameTestServer`

执行方式（输出重定向到文件，日志编码为 **GBK / CP936**，用 UTF-8 读会乱码）：

```powershell
cmd /c ".\gradlew.bat runGameTestServer > build\gametest-run.log 2>&1"
# 正确读法：
[System.Text.Encoding]::GetEncoding(936).GetString([System.IO.File]::ReadAllBytes('build\gametest-run.log'))
```

**实际计数（来自日志，不是注解数）**

| 指标 | 数值 | 依据 |
| --- | --- | --- |
| 执行 | **207** | 三个批次 `defaultBatch:1`(100) + `:2`(100) + `:3`(7) = 207；汇总行 `207 GAME TESTS COMPLETE` |
| 通过 | **206** | 207 − 1 |
| 失败 | **1**（required） | `1 required tests failed :(` 后仅列 1 项 |
| 跳过 | **0** | 日志中无任何 skip/skipped 测试行；无 `optional tests failed` 行 |
| Gradle 结果 | ❌ `BUILD FAILED in 49s`，退出码 1 | `> Task :runGameTestServer FAILED` |

时间线：20:56:12 服务器启动 → 20:56:36 批次 1（100）→ 20:56:38 批次 2（100）→ 20:56:39 批次 3（7）→ 20:56:39 汇总 → 20:56:44 关服 → `BUILD FAILED in 49s`。

**失败日志（GBK 解码后原文）**

```
[20:56:37] [Server thread/ERROR] [minecraft/LogTestReporter]: phantomfragmentsresourcesareshipped failed! zh_cn 的栏位名应当留空，实测「幻像碎片」
...
[20:56:39] [Server thread/INFO] [minecraft/GameTestServer]: [+++++++...+++++++X+++++++...+++++++]
[20:56:39] [Server thread/INFO] [minecraft/GameTestServer]: ========= 207 GAME TESTS COMPLETE ======================
[20:56:39] [Server thread/INFO] [minecraft/GameTestServer]: 1 required tests failed :(
[20:56:39] [Server thread/INFO] [minecraft/GameTestServer]:    - phantomfragmentsresourcesareshipped
[20:56:39] [Server thread/INFO] [minecraft/GameTestServer]: ====================================================
[20:56:39] [Server thread/INFO] [minecraft/MinecraftServer]: Stopping server
...
> Task :runGameTestServer FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':runGameTestServer'.
> Process 'command 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'' finished with non-zero exit value 1

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.

BUILD FAILED in 49s
11 actionable tasks: 3 executed, 8 up-to-date
```

失败进度条里的 `X` 恰好 1 个，与「1 个失败」一致。

**运行期非致命警告（不影响测试判定）**

- `Can't keep up! Is the server overloaded? Running 2343ms or 46 ticks behind` —— 批次 2 期间服务器跟不上 tick，属性能提示。
- `White Lily: 读不了 Debugging_whitelilymod.json：JsonSyntaxException ... Expected ':' at line 1 column 9 path $.this`（出现 2 次）
- `White Lily: Debugging_whitelilymod.json 的 difficulty_level = 99 不在 1-4 之内，忽略`
- 后两条来自 `run/` 里**既有的脏调试文件**（见 6.3 第 5 条）。模组随后按当前难度（EASY = 2）重写了该文件，测试继续正常进行。

### 7.4 失败原因（证据链，**未做任何修改**）

1. 测试代码 `WhiteLilyGameTests.java:3076` 的 `phantomFragmentsResourcesAreShipped`，在 3103–3109 行要求：
   ```java
   // lang 按需求留空，但键必须在，而且内容必须是空的。
   for (String locale : new String[]{"zh_cn", "en_us"}) {
       String value = langValue(locale, curio_slots.PHANTOM_FRAGMENTS_NAME_KEY);
       helper.assertTrue(value != null, locale + " 缺少 " + ...);
       helper.assertTrue(value.isEmpty(), locale + " 的栏位名应当留空，实测「" + value + "」");
   }
   ```
2. 设计注释 `curio_slots.java:65` 同样写「（**按需求留空**，内容由作者自己填）」。
3. 实际资源：`assets/white_lily/lang/zh_cn.json:74` = `"curios.identifier.phantom_fragments": "幻像碎片"`；`en_us.json:74` = `"Phantom Fragments"`。两者都**非空**。
4. `git blame` 显示：lang 的两行与测试的 3103–3109 行**都来自同一个提交 `a0c125fe`（2026-10-21，"Update to 0.1.2: SAN/load systems, special window and noise shaders"）**。也就是说提交里同时写了「必须为空」的测试和「非空的」语言值，属于**提交内自相矛盾**，与本次两轮验证无关。
5. 为什么只报 `zh_cn`：循环先查 `zh_cn`，断言失败即抛；`en_us` 根本没被执行。按代码判断，`en_us` 也是非空，**一旦修好 zh_cn 它大概率会接着失败**。
6. 附带发现（非失败原因）：注释统一写「幻**影**碎片」，而 `zh_cn.json` 写的是「幻**像**碎片」，用字不一致。

结论：这是**真实的产品意图冲突**，不是构建/环境问题。修复只有两条路——清空 lang 值，或修改测试预期——都需要作者决定（见 6.3 第 6 条）。**我没有改代码、没改测试、没改 lang。**

### 7.5 运行期 Mixin 证据（补充第 1 轮的空缺）

`runGameTestServer` 启动日志里，8 个通用 mixin 全部成功应用：

```
Mixing MixinPlayer from white_lily.mixins.json into net.minecraft.world.entity.player.Player
Mixing MixinItemStack from white_lily.mixins.json into net.minecraft.world.item.ItemStack
Mixing MixinFriendlyByteBuf from white_lily.mixins.json into net.minecraft.network.FriendlyByteBuf
Mixing MixinInventory from white_lily.mixins.json into net.minecraft.world.entity.player.Inventory
Mixing MixinSlot from white_lily.mixins.json into net.minecraft.world.inventory.Slot
Mixing MixinDamageSource from white_lily.mixins.json into net.minecraft.world.damagesource.DamageSource
Mixing infection.MixinInfectionSpellCost from white_lily.mixins.json into io.redspace.ironsspellbooks.api.spells.AbstractSpell
Mixing MixinFoodData from white_lily.mixins.json into net.minecraft.world.food.FoodData
```

其中 `MixinInfectionSpellCost` 成功注入说明 `InfectionMixinPlugin.compatible()` 判定通过（ISS 的 `AbstractSpell` 三个注入点都在）。

一条 dev 警告需要留意（不致命）：

```
[20:56:15] [main/WARN] [mixin/]: Reference map 'white_lily.refmap.json' for white_lily.mixins.json could not be read. If this is a development environment you can ignore this message
```

原因是 dev 运行用的是 `build\classes` 而不是 jar，refmap 只生成在 `build\tmp\compileJava\`；jar 里是有的（第 1 轮已验证 `white_lily.refmap.json` 在包内）。**生产环境（reobf + SRG 名）的 remap 是否真的生效仍未验证。**

客户端两个 mixin（`MixinTitleScreen`、`MixinInfectionBlindness`）在专用服务器上按设计不加载，因此**仍未验证**。

### 7.6 本轮产生的文件与副作用

| 路径 | 说明 |
| --- | --- |
| `backups\texture-backup-20261022\**` | **新增 39 个文件**（13,193 B），已验证与源一致 |
| `build\libs\white_lily-0.1.2.jar` | 重新生成，622,376 B |
| `build\gametest-run.log` | **新增**，410,866 B（GBK 编码），本次 GameTest 的完整控制台日志 |
| `run\**` | 由服务器创建/改写：`world\`（GameTest 世界）、`logs\latest.log`、`logs\debug.log`、`logs\debug-1.log.gz`、`config\`、`tacz\`、`saves\asdfg\` 等 |
| `WORK_REPORT.md` | 本文件，已更新 |

`git status`（本轮结束）：

```
?? WORK_REPORT.md
?? backups/
?? docs/基础材料处方戒指贴图重绘记录.json   ← 既有
?? docs/贴图绘制记录.json                 ← 既有
```

**没有任何 `M`/`A`/`D`**：源码、资源、`build.gradle`、`gradle.properties`、测试代码全部未改。`run/` 与 `build/` 不在 git 跟踪范围内。

### 7.7 本轮仍未完成/未验证

1. `clean build`：仍没做（`--rerun-tasks` 强制重跑任务，但没有清空 `build/`；且 `clean` 会删掉 `build\texture-backup-20261022\`）。现已在 `backups\` 有一份校验过的副本，可以放心 clean 了——但这是下一步，不在本轮范围。
2. `runClient` / `runServer`：未跑，客户端 mixin、特殊窗口、噪声着色器等仍无运行期证据。
3. 生产 reobf jar 的实际加载：未验证（dev 用官方名，生产是 SRG 名；`MixinInventory` 的双名方案正是为这个场景写的）。
4. `run/` 不是干净环境：既有 `world\`、`saves\asdfg\` 与脏的 `Debugging_whitelilymod.json`，重跑结果是否完全可复现未确认。
5. 46 条 javac 弃用警告未逐条处理（尤其 `ResourceLocation(String,String)` 属 `[removal]`，将来升级 MC 会编译失败）。
6. 失败测试未修：等作者决定方向（7.4）。

---

## 8. 基础材料、简易加工台与生计创造栏扩展（2026-10-22～23）

### 8.1 基线与范围

- 起点为 `mapple` @ `3d1aa02`；适用目录和上级未发现 AGENTS.md。已阅读 README、开发建议、当前注册及本报告的已知失败登记。
- 开始时仅有既有未跟踪的 WORK_REPORT.md、backups/ 和两份贴图绘制 JSON；这些内容没有被清空或覆盖。
- 本轮实现必要加工台、基础材料、菜单、像素资源、模型、配方和创造栏，不改感染、SAN、医疗使用效果或原有基础材料配方数值。
- 本轮没有执行 Git 提交、推送或合并；所有改动均在本地。

### 8.2 注册 ID 与修改文件

全部使用 `white_lily:`：

| 类别 | 注册路径 |
| --- | --- |
| 9件物品 | plant_fiber、wooden_tray、stone_mortar、clay_bowl、glass_tube、filter_paper、desiccant_packet、herbal_residue、herbal_pouch |
| 方块及对应物品 | simple_processing_table |
| 方块实体、菜单 | simple_processing_table（各自注册表） |
| 配方类型、序列化器 | simple_processing |
| 新创造栏 | misguided_livelihood |

普通材料和加工台物品最大堆叠64；研钵堆叠1、真实耐久128。布包是真实公开普通物品，不提供收纳、食用或右键功能。

- 统一注册：修改 `item/items.java`、`block/medical_blocks.java`、`crafting/prescription_recipes.java` 和主类；新增 `processing/processing_registry.java`，不重复注册旧 ID。
- 公共逻辑：新增 `processing/processing_recipe.java`、`processing/processing_block_entity.java`、`processing/processing_menu.java`、`block/simple_processing_table.java`。
- 客户端：新增 `client/processing_screen.java`、`client/processing_client.java`，仅 Dist.CLIENT 事件绑定 Screen；公共代码不引用客户端类。
- 简介：复用 `basic_material_item.java`，允许额外客观提示；新增 `processing_table_item.java` 让方块物品沿用 Shift 展开。中英文本均在语言文件，普通灰色说明不继承绿色标题。
- 创造栏：修改 `item/creative_tabs.java` 的明确清单及相对排序，修正过期页码说明。
- 测试：新增 ProcessingGameTests（5组）、ProcessingMenuGameTests（2组）、LivelihoodGameTests（2组）；按已授权迁移更新 BasicMaterialsGameTests 与 MedicalGameTests。没有修改冻结的幻像碎片断言。

### 8.3 最终配方与制作路线

新增8条普通配方和对应解锁：

| 产物 | 配方 |
| --- | --- |
| 加工台×1 | 木板/托盘/木板；木板/空/木板；木棍/空/木棍 |
| 托盘×1 | 3木台阶水平一行，独立原版木台阶清单 |
| 研钵×1 | 石材/木棍/石材；空/石材/空，石材仅石头和圆石，可混用 |
| 陶土碗×4 | **3个红砖物品水平一行** |
| 玻璃细管×6 | 3普通玻璃方块水平一行 |
| 纸质滤片×4 | 纸＋植物纤维，无序 |
| 干燥剂包×2 | 纸＋普通沙子＋本模组炭粉，无序 |
| 粗布片×1 | 2植物纤维，无序 |

陶土碗原 V 形配方经实际已加载配方检查，与 `minecraft:flower_pot` 完全相同。本轮已询问并得到用户“调整陶土碗配方”的答复，随后仅改为水平一行，保留3个红砖与4产量。测试同时检查新配方无冲突、原版 V 形仍产出花盆。

保留麻秆×3水平一行→粗布片×2，以及原有细布条、软木塞、蜂蜡、炭粉、标签配方。加工路线3麻秆→6纤维→3粗布片，以120刻和3点研钵耐久获得比直接路线多50%的布。

三条加工配方位于 `data/white_lily/recipes/processing/`：

| ID尾段 | 原料 | 主产物 | 返还 | 副产物 | 时间/耐久 |
| --- | --- | --- | --- | --- | --- |
| hemp_fiber | 1麻秆 | 2植物纤维 | 无 | 无 | 40刻/1 |
| bitterleaf_paste | 3苦叶草 | 2苦叶药膏 | 无 | 1药草残渣 | 100刻/1 |
| yarrow_dressing | 3止血蓍草 | 2蓍草敷料 | 无 | 1药草残渣 | 100刻/1 |

删除旧普通 `bitterleaf_paste.json`、`yarrow_dressing.json` 及 `advancements/recipes/medical/` 下对应旧解锁，其他医疗配方不迁移。

**药草布包配方待定，当前可从创造模式或 /give 获取。** 药草残渣只作加工副产物，本轮没有普通配方或后续用途。

### 8.4 加工、安全边界与保存

- 八槽为工具0、原料1～3、容器4、成品5、返还6、副产物7。无空气分隔槽；三个输出禁止插入。
- 带数量的原料使用分配算法处理跨槽堆叠和重叠配料，拒绝无关原料；相同输入多配方按完整 ID 字典序选择。
- 仅服务端实体加工，关闭菜单不停止；两位观察者共享同一库存和进度。客户端只显示同步数据。
- 完成前不扣料，全部所需输出预检通过后一次提交。堵塞保留进度，不对应的输出不要求清空。机器不继承玩家128容量，取物品上限与64的较小值。
- 每批固定1点耐久，忽略耐久附魔，保留名称和NBT；最后一点完成本批后破碎。内部损耗不误认为换工具。
- 外部工具更换、材料不足、配方改变清零；增加同种且仍有效的原料保留。修复默认 Forge 槽容量查询会临时移走工具的副作用，并让 Shift 原地合并触发库存变更回调。
- 保存库存、配方ID、完整指纹、工具快照及进度；加载固定8槽并限量，首个服务端刻验证兼容性。资源重载丢失/改变配方安全清零，兼容配方可继续。
- 区块卸载暂停、不掉库存，不补现实时间。只有方块移除路径掉落，并先清空再生成物品以避免重复。
- 不公开 ITEM_HANDLER 能力，第一版不支持漏斗自动化。菜单按方块存在和8格距离验证。
- 为适配原版菜单属性，数据驱动加工时间明确限制1～32767刻；正式配方40/100/100不变。

### 8.5 创造栏与显示

采用已核实的 Forge 语义：`withTabsBefore(X)` 表示 X 在当前栏前面。

1. 歧途“珍馐”：原ID、标题和图标，内容仅魔力苹果、低质量药剂、暂存之苦。
2. 歧途“生计”：`misguided_livelihood`，Component.GREEN 标题；图标是公开布包本体，物品名称不继承绿色。
3. 原“创造测试工具”：原 medical_tests ID、图标、名称及7件工具，权限与功能不变。

生计28件依次为：药草布包、简易加工台、石制研钵、麻籽、麻秆、苦叶草、止血蓍草、安神花、植物纤维、粗布片、细布条、木制托盘、软木塞、蜂蜡块、炭粉、陶土碗、玻璃细管、纸质滤片、干燥剂包、空白标签、空白处方、显字处方、简易绷带、洁净的精良绷带、苦叶药膏、蓍草敷料、安神花茶、药草残渣。

本轮清单全部实现，无缺失待加入条目。三栏无重复、无假分隔，公开物品进入搜索汇总；隐藏图标和四件绑定改良饰品继续隐藏，不遍历全部注册物品曝光。

### 8.6 资源、源文件与预览

- 9张正式物品PNG：`src/main/resources/assets/white_lily/textures/item/`，同注册名；实际16×16 RGBA、有限色板、二值透明，至少一像素边距。
- 7张方块16×16材质：`textures/block/simple_processing_*.png`。
- 正式模型：`models/block/simple_processing_table.json`，29个Java cuboid；原生合法旋转、明确cutout、4方向blockstates与对应方块物品模型。选择与碰撞轮廓随方向旋转。
- 正式GUI：`textures/gui/simple_processing_table.png`，256×256画布、230×212逻辑尺寸，槽位与Screen坐标一致；文字、进度、状态和耐久没有烘焙进背景。
- 源脚本、布局、有限色板和可编辑原生模型：`art-source/processing/`；未伪装成.bbmodel，无新增美术或模型前置。
- 预览：`docs/previews/processing_items_16x.png`、`processing_model.png`、`processing_ui.png`、`processing_overview.png`，是离线像素绘制/模型投影，**不是客户端截图**。
- 原三张概念图已实际查看且原样保留，其他旧贴图未覆盖。
- 使用文档与全部获取命令：`docs/简易加工台与生计材料使用说明.md`。

### 8.7 本轮验证与限制

最终命令、计数及正式JAR检查见本节末追加结果。构建成功不能证明客户端界面已实测。

仍需人工：客户端启动后的模型烘焙与四方向观感、手持/背包显示、GUI不同缩放下点击及文字提示；真实联机两人同时打开、断线和区块卸载加载；启动器实际加载正式reobf JAR。

已有 `phantomfragmentsresourcesareshipped` 翻译断言按冻结规则保持原样；本轮另观察到原麻作物 `farmlandgrowthbonemealandwildsoilsareseparate` 等待光照超时。没有擅自改生长规则或旧测试。ISS依赖仍有已知两份战利品表解析错误，未修改前置。

### 8.8 最终实际执行结果

| 检查 | 实际结果 |
| --- | --- |
| `gradlew.bat build` | **BUILD SUCCESSFUL in 19s**，11项任务中8项执行、3项最新；日志 `build/processing-build-final.log` |
| `gradlew.bat runGameTestServer` | **216项执行、214通过、2失败**，退出码2；日志 `build/processing-gametest-final.log` |
| 本轮新增9组 GameTest | **全部通过**：分散堆叠/40与100刻边界；输出整体结算/堵塞/最后耐久与NBT；输入变化/保存恢复；序列化/可选容器与返还/资源重载；重叠配料/重复移除及真实破坏掉落；两组菜单转移与共享；两组创造栏、语言及真实配方冲突检查 |
| 两项失败 | `phantomfragmentsresourcesareshipped`：旧非空翻译与旧空值断言冲突；`farmlandgrowthbonemealandwildsoilsareseparate`：旧麻作物夹具等待照明就绪超时。均未修改 |
| 静态资源检查 | `art-source/processing/check_processing_assets.py`通过：9张16×16透明物品、7张16×16方块材质、29个合法模型元素与UV、四方向、8普通配方和解锁、中英文、旧路线迁移 |
| 正式JAR检查 | 51份本轮相关资源与源文件逐字节一致；9张正式物品均16×16 RGBA；新增公共/客户端类存在；旧药膏/敷料配方与旧解锁不在包中；art-source、docs、备份不入游戏包 |
| `git diff --check` | 通过；只有已有行尾转换提示 |
| 客户端人工测试 | **未执行**，没有声称UI、模型或GUI缩放已在游戏中验证 |
| 正式JAR实际启动器加载 | **未执行**，打包内容检查不等于生产运行验证 |

正式产物：`build/libs/white_lily-0.1.2.jar`，**708208字节**；SHA-256：`757534bb2f5cac9cc1deec2aba7bf2571f16dae833e38f927095ffe0b5d5c08f`。完整打包核对记录在 `art-source/processing/package_checks.json`。

中间轮次曾因新增冲突检查发现陶土碗与花盆重叠；已按用户同意调整并重跑验证。最终普通配方在当前已加载环境中没有该冲突。全套仍为失败状态，不能写“所有测试通过”。本轮没有运行clean，以保留既有build中的贴图备份；未安装额外插件，未修改工作区权限。

---

## 9. 2026-10-23 暂停植物自然生成（Codex）

用户要求关闭之前已写好的生成，为后续作者的新群系方案留出统一接入空间。当前没有污浊群系实现；本轮暂停现有苦叶草、止血蓍草、安神花、野生麻四种植物的自然生成。

- 修改四份 `src/main/resources/data/white_lily/forge/biome_modifier/add_*.json` 为 `{"type":"forge:none"}`。已核对本地 Forge 47.4.10 的 NoneBiomeModifier 源码，此类型为官方的空操作修饰器。
- 保留 configured_feature、placed_feature、栖息地标签、原概率/数量/放置逻辑、方块物品及种植规则。重启加载新包后对后续区块生效，不移除存档里已存在的植物；仍可人工种植和手动调用 feature。
- 更新 `MedicalWorldgenGameTests.java`、`HempWorldgenGameTests.java` 的自然挂载预期为零；前者额外检查全部已加载群系都未挂载这四种 feature。原配置与手动放置的测试继续执行。
- 新增 `docs/自然生成暂停说明.md`，记录生效范围和恢复方式。已有加工台等未提交改动未回退。

验证：

1. 最初 Gradle 默认落到不可写的 `C:\.gradle`；改为现有 `C:\Users\John\.gradle` 后受沙箱锁文件限制，按流程扩大本次命令权限后成功启动；未改 ACL。
2. 执行 `gradlew.bat build runGameTestServer --offline`，日志 `build/worldgen-pause-validation.log`。`compileJava` 真实执行，`build` 任务完成并产出 JAR；随后 GameTest 导致组合命令退出码 1（Java 测试进程退出码 2），Gradle 总结为 BUILD FAILED，耗时51秒。
3. 217 项测试执行、215 项通过、2 项失败。全部自然生成相关测试通过，包括全群系无挂载检查。两项失败仍是旧项 `phantomfragmentsresourcesareshipped` 和 `farmlandgrowthbonemealandwildsoilsareseparate`，本轮未改它们的断言、翻译或种植逻辑。
4. 逐项打开正式 `build/libs/white_lily-0.1.2.jar`（711543字节）核对四个 biome_modifier，全部为 `forge:none`。`git diff --check` 通过，只有已有的 CRLF 提示。

未执行客户端人工测试、启动器加载正式JAR；没有运行clean、提交或推送。上述JAR为本次新产物，上一节的JAR体积与哈希属于上一轮，不能用于本轮产物校验。

---

## 10. Bug复查与修复（2026-10-23）

用户要求检查并修复当前及此前功能中的Bug；随后明确本轮不处理作物自然生成。复查包括加工台、菜单、感染、SAN生命周期、暂存之苦、药剂消耗、处方与剪刀加工的现有实现和回归测试。保留此前尚未提交的全部扩展内容。

### 10.1 确认并修复的问题

1. **刚加载的加工台可以保留应清零的进度。** 保存进度后 `load()` 将 `activeRecipe` 置空；首个加工刻前取走必要原料再放回，原库存回调因配方对象为空而不检查。修改 `processing/processing_block_entity.java`，冷加载阶段按保存的配方ID、完整指纹、工具快照与当前原料校验。取走必要原料立即清零，追加有效同种原料仍保留。新增 `ProcessingBugRegressionGameTests`，覆盖保存17刻、加载后首刻前取出/放回、仅从1刻重新开始，以及追加原料17→18刻。
2. **原位重放工作台使旧菜单重新有效。** 原 `stillValid` 只核对方块类型和距离；旧Slot仍引用已经移除的方块实体，原位新台不会自动替换这些引用。修改 `processing/processing_menu.java`，服务端绑定原实体的存活、世界与当前位置实体身份，再保留原距离/方块检查；直接Shift调用也拒绝失效菜单。扩展 `ProcessingMenuGameTests` 验证旧菜单保持失效、不能移动库存，新菜单只绑定新实体。
3. **戒指提示仍存在无连接玩家空指针。** 前次已保护SAN/感染/魔力同步，但 `stored_suffering_events.sync` 仍直接调用 `ServerPlayer.displayClientMessage`，对没有真实网络连接的模拟玩家可能崩溃。增加连接保护，未发送时不清欠款或修改已发送缓存。扩展 `ReviewRegressionGameTests` 的无连接玩家回归，包含戒指提示、登录、重生路径。
4. **麻作物测试的异步光照等待不可靠。** 旧测试把作物放在3×3×3模板外的相对(2,30,2)，光源也越出模板；同时GameTest服务器不按真实50ms等待下一游戏刻，200模拟刻不代表异步照明完成。仅修改 `HempGameTests` 夹具：土壤/作物/光源移入模板，等待原版照明任务完成并泵送原版区块任务，以5秒真实超时防止死等。仍断言实际光照达到9，保留全部土壤、生长概率、阶段与骨粉断言。**没有修改生产作物生长或自然生成代码来使测试通过。**

### 10.2 此前功能复查

- 感染无限持续时间、缺失能力/注册、无效法术ID、重评节流及事件状态仍由完整回归覆盖；本轮未发现需要额外改公式的确定性问题，未改变感染数值或阈值。
- SAN四档死亡重生、普通登录/跨维度保留和恢复入口的现有回归通过；未更改其注册默认值、阈值或档位规则。
- 低质量药剂的服务端消耗、概率、返瓶和创造路径回归通过；未再改药效或产量。
- 暂存之苦伤害拆分、独立计时、卸下/破碎结算、改名与存档等现有回归通过；本次仅补提示同步的空连接保护。
- 处方水中转换/水瓶合成、剪刀耐久/NBT/批量加工等现有回归通过。自动测试通过只覆盖现有断言，不能证明所有前置版本或实际联机场景均无Bug。

### 10.3 自然生成的明确范围

检查过程中发现四份 `forge/biome_modifier/add_{hemp,bitterleaf,yarrow,calming_flower}.json` 及两份相关测试被调整为暂停生成。已询问用户，得到“**保留当前暂停生成状态**”的明确回复，因此保持四份 `forge:none` 配置，不恢复或继续扩展生成。相关生成配置已包含在最终包中。

本轮修复的是麻作物测试夹具，不代表完成了野外生成修复或真实新区块生成验证。此前已有的生成概率、群系、边界算法不在本轮修改范围。

### 10.4 最终验证

| 检查 | 实际结果 |
| --- | --- |
| 构建 | `gradlew.bat build`：**BUILD SUCCESSFUL in 18s**；日志 `build/bugfix-build.log` |
| 全套GameTest | **217项，216通过，1失败**；`runGameTestServer`退出码1，日志 `build/bugfix-gametest.log` |
| 本轮回归 | 新增冷加载输入回归通过；扩展旧菜单/重放测试通过；扩展无连接戒指提示回归通过；旧麻作物光照测试通过 |
| 唯一失败 | `phantomfragmentsresourcesareshipped`：当前“幻像碎片”非空名称与旧空值断言冲突，按第0.1节冻结要求未修改名称、断言或测试启用范围 |
| 正式JAR核对 | 51份相关资源逐字节核对通过；重映射后的菜单实体身份校验、冷加载校验方法、戒指连接空值保护已通过正式包字节码检查；四份自然生成停用配置确认在包内 |
| 差异检查 | `git diff --check`通过，存在既有CRLF提示 |
| 客户端/真实多人/启动器加载 | **未执行**，没有声称已在游戏内实测通过 |

产物仍为 `build/libs/white_lily-0.1.2.jar`，**712298字节**，SHA-256：`c3f6f35c0edac26c7c0e767842dac7a82c9660c427b5ba3b307761eeb1ff5254`。资源/包核对记录已更新到 `art-source/processing/package_checks.json`。生产JAR经过重映射，不能与开发class逐字节比较，因此使用重映射后的方法和分支检查。

ISS依赖中已知两份战利品表解析错误仍存在，未修改第三方资源。全套测试仍未全绿，不能把216通过写成全部通过。未提交、推送或覆盖既有未提交工作；后续仍需客户端GUI/模型、真实区块卸载与多人交互人工验证。

---

## 11. 调查手册与研究树第一版（2026-10-23）

### 11.1 开始状态与范围

实际工作目录为 `D:\cao的mod\white_lily`，分支 `mapple`，开始时HEAD为 `3d1aa02`。阅读README、适用项目开发说明和本报告；搜索范围内没有发现额外适用AGENTS.md。开始前已经存在加工台、材料、创造栏、贴图、先前Bug修复和自然生成暂停等大量未提交内容，本轮保留并在其上增量修改。没有提交、推送、恢复工作区、运行clean、更改权限或升级依赖。

使用当前Minecraft 1.20.1、Forge 47.4.10、Java 17。四份植物biome_modifier继续为 `forge:none`，没有新增世界自然生成。本轮没有修改SAN、感染、药物、作物生长或原加工台数值。

### 11.2 实际新增功能

- 真实物品 `white_lily:investigation_handbook`：堆叠1、无耐久、即时右键、不消耗、不把研究进度写进手册NBT；复用现有Shift简介。歧途“生计”中放在药草布包之后，其他既定相对顺序保持；不进入测试栏。
- 无序配方：书×1＋空白标签×1＋炭粉×1 → 调查手册×1，支持2×2；补普通解锁进度。花茶原配方只改为研究型Serializer，保留两朵花、Forge NBT普通水瓶及原产物。花茶旧普通解锁进度改为impossible，研究完成后由服务端授予真实配方书记录。
- 生存/创造背包左上角 `(6,6)` 的20×20按钮直接渲染手册物品图标，不要求持有手册。仅对这两种背包添加，不扩散到所有容器。背包进入时保留上一层界面引用；手持进入时返回游戏。重复初始化不重复插入按钮。
- 三栏动态UI：左栏目章节、中研究树、右滚动详情；上方关闭/恢复视图，下方图例与操作提示。只有“草药与医疗→基础草药”开放，其他四栏明确未开放。节点状态标记、选中框独立；隐藏节点与连线不参与显示/命中。空白拖动、0.5～2.0滚轮缩放、章节视图记忆、视口裁剪、文本缓存和窄窗口滚动均有实际实现，没有无效搜索框。
- 服务端配置两节点：`white_lily:calming_flower` 首次获得，`white_lily:calming_flower_tea` 提交研究。新玩家只收到灰色根节点；安神花真正进入自己的背包后完成发现，不消耗花，只揭示花茶；背包已有花也检查。附近掉落物、其他容器、浏览创造图标或仅获得成品茶不代替研究。
- 花茶研究提交安神花×1＋空白标签×1，材料跨格统计，先完整预检再一次性扣料；创造模式同样验证扣除。过期版本、无效ID、缺前置、缺材料和重复请求不重复扣除。完成后永久开放档案和原制作配方，且立即刷新已摆好的结果槽。
- `white_lily:research` 玩家Capability独立保存稳定完成ID与版本，未知旧ID仍保留；死亡和非死亡Clone正确revive/读取/复制旧能力。登录、重生、跨维度、重载同步。没有团队共享或客户端任意解锁包。
- 独立 `white_lily:research` SimpleChannel，明确C2S/S2C方向并主线程处理。初始/打开/重载发送裁剪快照，之后发送变化节点、移除ID和解锁配方差分。只有打开界面时每10刻比较展示变化，目标获得检测每20刻扫描个人背包索引；无变化不发送。
- 节点配置检查重复ID、重复配方归属、缺前置、循环、无效物品/配方、数量、栏目章节。错误日志定位文件/节点/配方，损坏配置及无效配方拒绝放行。本版展示器正式支持无序合成，不把其他配方假装成无序合成。

档案核对当前医疗源码：安神花SAN≥5000恢复10，否则5，反胃Ⅰ400刻，不可骨粉复制；花茶SAN≥5000恢复30，否则15，反胃Ⅰ200刻，饮用返瓶。没有修改这些效果或重开自然生成。

### 11.3 实际合成权限与修复

新增 `white_lily:research_crafting` Serializer，复用原版ShapelessRecipe及Forge Ingredient/NBT匹配。只将现有花茶接入该类型，其他普通、特殊、第三方及加工台配方不受本轮门槛限制。

六个独立研究Mixin分别接入网格菜单所有者、ResultSlot玩家/网格访问、结果取出、两种菜单Shift转移、服务端配方书授予/初始同步、配方书填充。使用真实菜单的玩家归属，不用全局“当前玩家”；没有改作者原有Mixin Java实现。已有旧配方书记录或 `/recipe give` 不能提前授予/制作；数据重载也清除不再有效的受限配方记录。

首轮测试揭露原生配方书按物品ID还原配料时丢失药水NBT，随后 `findSlotMatchingUnusedItem` 无法找到真实水瓶。新增的第六个研究Mixin只为研究型配方使用实际Ingredient例子的NBT，保留严格水瓶条件，不修改全局药水或普通配方。普通水瓶仍堆叠1；“全部填充”遵守其原上限，连续三次填充/Shift各做一份，不伪造三瓶同格或扩大堆叠。

另修两处真实审查/客户端发现：

1. 快速关闭再打开时，3刻限流曾直接丢掉打开请求。现在合并为一个待打开请求，窗口到达后补开；关闭取消排队但不重置限流，防止反复开关绕过限流。新增确定性边界/合并/取消/时间回退测试。
2. 只看过一个节点后关闭，发现花再重开，会复用旧居中位置使新节点靠边。View现在记录可见节点数；未手动移动时，节点数或窗口尺寸变化自动适配，手动拖动/缩放后的视图继续保留。

### 11.4 本轮文件

Java路径以下均相对 `src/main/java/com/damezame97/white_lily/`：

| 文件 | 用途 |
| --- | --- |
| `item/investigation_handbook.java` | 手持入口及复用说明 |
| `item/items.java`、`item/creative_tabs.java`、`white_lily.java` | 复用物品/创造栏注册入口及网络登记 |
| `research/research_catalog.java` | JSON加载、验证、发现目标索引 |
| `research/research_progress.java`、`research/research_capability.java` | 玩家进度NBT与Clone |
| `research/research_service.java` | 发现、材料提交、权限、裁剪与差分展示 |
| `research/research_events.java`、`research/research_network.java` | 生命周期、低频检测、请求/同步及限流排队 |
| `research/research_crafting_gate.java` | 当前玩家的真正制作权限及结果刷新 |
| `crafting/research_crafting_recipe.java`、`crafting/prescription_recipes.java` | 研究型无序Recipe及现有入口中的Serializer登记 |
| `mixin/research/ResearchCraftingContainerAccess.java`、`ResearchResultSlotAccess.java` | 网格与玩家访问器 |
| `mixin/research/MixinResearchResultPickup.java`、`MixinResearchQuickMove.java` | 真实取出/Shift前检查 |
| `mixin/research/MixinResearchRecipeBook.java`、`MixinResearchRecipePlacement.java` | 配方书权限与NBT填充 |
| `client/research_client.java`、`client/InvestigationScreen.java` | 背包按钮、缓存合并、返回与动态UI |
| `gametest/ResearchGameTests.java`、`ResearchCraftingGameTests.java`、`ResearchIntegrationGameTests.java` | 本轮12组测试 |
| `gametest/MedicalGameTests.java`、`LivelihoodGameTests.java` | 旧花茶水瓶夹具明确赋予权限；公开列表加入真实手册，保留其他测试目的 |

资源相对 `src/main/resources/`：

- `data/white_lily/research/handbook.json`：5栏目、1章节、2节点。
- `data/white_lily/recipes/investigation_handbook.json`、`recipes/calming_flower_tea.json`。
- `data/white_lily/advancements/recipes/processing/investigation_handbook.json`、`recipes/medical/calming_flower_tea.json`。
- `white_lily.mixins.json`：追加六个独立研究入口，保留原列表。
- `assets/white_lily/models/item/investigation_handbook.json`。
- `assets/white_lily/textures/item/investigation_handbook.png`：逐像素绘制正式16×16 RGBA，18色，alpha仅0/255；棕封皮、浅书脊、纸页、叶片标签及暗红书签。
- `assets/white_lily/textures/gui/research_atlas.png`：256×256硬边像素九宫格、边框、锁/勾/加号及按钮状态。动态文字、物品、连线没有画死在PNG里。
- `assets/white_lily/lang/{zh_cn,en_us}.json`：各新增59个手册、UI、栏目和档案键，不重排已有键。

交付文件：`docs/调查手册与研究树使用说明.md`（含节点示例、完整文件用途与操作）；`art-source/research/`保存绘制脚本、色板、语言片段/合并脚本和检查脚本/记录；`docs/previews/research_handbook_preview.png`明确标注非游戏截图，`research_handbook_item_24x.png`为最近邻预览。没有覆盖现有安神花、花茶或其他物品贴图。

### 11.5 验证结果

| 检查 | 实际结果 |
| --- | --- |
| 最终独立构建 | `gradlew.bat build --offline`：**BUILD SUCCESSFUL in 51s**，`compileJava`真实执行；日志 `build/research-build-final.log` |
| 最终全套GameTest | **229项，227通过，2失败**；日志 `build/research-gametest-final.log`，进程退出码2、Gradle退出码1 |
| 本轮新增 | **12组全部通过**：6组数据/发现/隐私/提交/保存Clone/配置/排队，4组合成真实菜单与配方书，2组手册物品与真实资源 |
| 权限覆盖 | 原生2×2/3×3、普通/Shift、过期结果、两玩家隔离、配方书提前授予/旧记录/真实填充、严格水瓶及网络读写、无玩家归属拒绝均覆盖 |
| 原子与隐私覆盖 | 缺材料/旧版本/重复包不扣料；跨格提交；根发现不消耗；灰根不发档案，隐藏花茶连ID都不发；单层揭示、多前置、变化节点差分；成品不授予研究 |
| 已有失败1 | `phantomfragmentsresourcesareshipped`：旧空名称断言与既有“幻像碎片”名称冲突，按第0.1节未改断言、翻译或测试范围 |
| 已有失败2 | `riflehandlerisregisteredonthebus`：预期4.5、实际6.75。另一轮旧 `sanmentalinjurystaysonedamageevent` 也出现过10→15，第二轮则只有翻译失败。两者均与前置Apothic默认5%暴击、1.5倍倍率吻合；源码查到旧测试未使用现有隔离辅助函数。此归因为源码证据推断，未捕获单次随机roll，不擅自改旧伤害代码或断言 |
| 静态/正式包检查 | 手册16×16透明、UI256×256、59对翻译、原水瓶NBT、四份停用生成检查通过；16份相关正式JAR资源逐字节匹配，27个相关类及六个Mixin/refmap存在；美术源和预览未打包 |
| 差异检查 | `git diff --check`通过，保留已有CRLF提示；旧战斗/SAN测试及伤害实现未改 |

正式产物：`D:\cao的mod\white_lily\build\libs\white_lily-0.1.2.jar`，**833215字节**，SHA-256：`d3cd06d9dcfd21704ef0c8bff7b491fa0a9c46befd024c00d277597b25c9ed56`。正式包检查记录 `art-source/research/package_checks.json`；这不等于在启动器中实际加载了生产JAR。

### 11.6 客户端、用户验证与剩余边界

已实际运行 `runClient`，日志 `build/research-client.log`；新建独立 `Handbook UI QA 2026-10-23`，没有进入或清除原 `asdfg` 存档。助手观察并操作了生存背包入口、关闭返回原背包、新玩家灰根/隐藏后续、成品花茶不代替研究、真正获得花后根完成且花茶出现、未研究无完整配方、缺标签禁用及持有3/0数量、详情滚动、滚轮缩放、恢复视图、窗口还原/最大化布局。创造背包入口和生计栏真实手册图标也实际出现，部分创造操作由用户手动进行，不能写成助手完整跑完了切页矩阵。

**实际客户端截图**：`docs/previews/research_client_initial.png`，由游戏F2原生保存后复制，854×480，显示灰色安神花和隐藏花茶；不是离线绘制预览。

用户按物理Esc停止Computer Use后，助手停止全部界面操作。用户随后针对“后续界面、手持打开和完整研究路线的客户端复验”明确回复“**我已经验证完成了**”，据此记录为**用户已完成人工验证**。助手没有冒称自己执行了这部分操作，也没有将离线预览当实测截图。

最后网络重开/重新适配视图修复已编译、相关调度自动测试通过；助手未在停止界面操作后重启客户端复验。仍打开的开发客户端需要重启才加载最终新类。所有GUI缩放档、F3+T、真实服务器重启、死亡/跨维度实机及两个真实客户端联机没有逐项执行记录；保存/Clone与双玩家权限有GameTest覆盖。没有宣称已完成启动器生产JAR加载或JEI/REI兼容：当前依赖中未发现这两者，未新增查看工具。

### 11.7 获取与后续扩展

```mcfunction
/give @s white_lily:investigation_handbook
/give @s white_lily:calming_flower 3
/give @s white_lily:blank_label
/give @s minecraft:potion{Potion:"minecraft:water"}
```

获得花 → 选择花茶 → 提交花1＋标签1 → 解锁后用剩余花2＋普通水瓶制作。发现不消耗花；研究与制作分开消耗。`/clear`不撤销研究，检查初始状态需使用新测试玩家，不删除已有玩家进度。

新增节点主要编辑 `data/<namespace>/research/*.json` 和语言键；`obtain`用target，`submit`用materials；prerequisites支持多ID，root_visible控制根。示例详见使用说明。新无序受限配方同时绑定节点recipes并采用 `white_lily:research_crafting`，不能只隐藏通知。未来观察、使用和处方辨识条件在catalog/service扩展，UI不硬编码目标物品。加工配方的ID结构可以绑定，但发布前还需补真实展示格式和明确机器玩家授权归属，本轮没有限制加工台。客户端展示裁剪不会阻止技术用户查看本地JAR资源或原版配方同步，不能宣称绝对保密。

---

## 12. 2026-10-23 调查手册档案风格UI、交互与管理员编辑

### 12.1 范围、基线及文件

沿用第11节已实现的调查手册框架，开始前实际查看批准样板 `docs/previews/handbook_archive_style_reference_v1.png`、说明、git状态及当前代码。没有发现适用AGENTS.md。保留既有加工台、手册和其他未提交修改；备份位于 `backups/research-ui-v2-20261023/`。未提交、推送、改权限或清理存档。本轮未调用子助手。

本轮没有改药效、研究材料、研究节点、受限配方或世界生成；四份植物biome modifier仍为 `forge:none`。沿用原六个独立研究兼容Mixin，没有新增或改写作者原有Mixin，没有新增依赖。

以下路径的Java前缀为 `src/main/java/com/damezame97/white_lily/`；资源前缀为 `src/main/resources/`：

| 修改/新增文件 | 用途 |
| --- | --- |
| `client/InvestigationScreen.java` | 重做木框/旧纸动态界面、响应布局、分区输入、滚动、缩放LOD、详情、菜单与节点命中 |
| `client/research_archive_button.java`（新增） | 图集按钮正常/悬停/按下/禁用状态与可读标签 |
| `client/research_preferences.java`（新增） | 客户端个人显示偏好持久化，独立于研究和物品NBT |
| `client/research_tree_view.java`（新增） | 10%～200%视图、鼠标锚点及居中计算；无客户端类依赖的数学对象 |
| `client/research_client.java` | 权限/编辑状态/通知及影响范围DTO，正常和编辑数据的安全替换 |
| `research/research_progress.java` | 持久重置抑制基线、临时编辑状态及版本 |
| `research/research_service.java` | 单次可见集合背包检查、权限验证、完成/重置依赖闭包、制作和配方书刷新 |
| `research/research_events.java` | 实际拾取/制作/熔炼入口，复用原登录和低频检查 |
| `research/research_network.java` | 协议版本2，背包检查和管理员操作请求，线程、方向、版本及限流检查 |
| `gametest/ResearchEditingGameTests.java`（新增） | 6组有状态回归测试 |
| `assets/white_lily/textures/gui/research_archive.png`（新增） | 正式256×256 RGBA可复用木框、旧纸、黄铜护角、按钮、节点状态和栏目图标 |
| `assets/white_lily/lang/zh_cn.json`、`en_us.json` | 新增25对相关翻译，累计84对手册文本 |
| `art-source/research/draw_archive_ui.py`、`archive_palette.json`（新增） | 正式逐像素绘制脚本与色板，不复制参考图像素 |
| `art-source/research/update_archive_language.py`、`archive_zh_cn.json`、`archive_en_us.json`（新增） | 可维护的新增中英文文本与更新工具 |
| `art-source/research/package_checks.py`、`package_checks.json` | 正式资源/JAR校验脚本及实际记录，更新检查新图集 |
| `docs/previews/research_archive_tiles.png`（新增） | 最近邻图集预览，非游戏截图 |
| `docs/调查手册界面与编辑模式说明.md`（新增）、本报告 | 使用方式、权限、资源、验证与边界 |

手册物品注册ID与贴图保持原样：`white_lily:investigation_handbook`，`assets/white_lily/textures/item/investigation_handbook.png`，16×16 RGBA、18色、透明边缘。没有覆盖花或花茶贴图。正式包不包含参考图、docs或美术源。

### 12.2 界面、偏好与输入规则

动态渲染木框、旧纸、护角、文字、实际物品图标、状态边框、方向线与操作控件，没有将整张参考图贴进游戏。按钮有四种状态，完成标记与暗红选中框分开显示。

客户端 `config/white_lily-research-ui.json` 保存栏目展开/图标与详情侧栏/覆盖两项偏好；默认展开与侧栏。小窗口临时切换图标/覆盖，不写回首选项，底部空间不足采用显示菜单或换行。布局变化保存章节、研究坐标中心、缩放和合理正文滚动，结束拖动并关闭失效菜单，不发送研究提交。每章节视图在本次客户端会话中记忆，不把视图或偏好放进服务端研究数据。

滚轮按树/正文/栏目分区；树10%～200%鼠标锚点缩放，百分比按钮回100%，恢复视图显式居中。≥50%显示名称；25%～不足50%隐藏常驻名称；<25%使用小状态标记。名称与悬停采用正常GUI字号避免分数字号导致中文糊成像素，标签宽度按节点间距及缩放更新缓存。节点/线有裁剪，命中半径受相邻距离限制，不读取每帧JSON/配方或扫描背包。

空白左右键单击关闭详情；移动4GUI像素后按拖动，不误关闭；侧栏/按钮/正文不算空白。右键菜单优先消费输入，点击外侧只关菜单，Esc先关菜单。编辑右键菜单位置限制在屏幕内，显示服务端计算的影响范围。

### 12.3 检查、权限及重置

“检查背包”只检查自身实际背包、检查开始时可见且满足前置的obtain节点，20刻冷却，不消耗、不检查箱子或掉落物、不代替submit、不在一次请求中递归揭示整条路线。保留原20刻自动发现和打开界面时刷新；不每刻扫描节点。

编辑权限使用服务端 `player.hasPermissions(2)`，非创造模式判断；每次修改验证权限、节点、会话版本及状态。默认只修改自己。重新打开默认退出编辑；撤销权限后的快照关闭编辑并移除正常不应看到的节点。正常玩家数据仍由服务端裁剪，完整配置仅在授权编辑显示时发送；编辑查看不开放档案、配方或进度。

直接完成在服务端算必要前置闭包；重置算因依赖失效的已完成后续闭包。全部变更在服务端线程完成后统一reconcile、同步、配方书增删及真实菜单结果刷新。操作不扣材料、不发奖励、不删除成品、不返还历史材料，不模拟正常获得回调；客户端不上传完成列表。旧包、无效ID、重复操作安全拒绝。两个玩家状态独立，沿用已有2×2/3×3、普通/Shift、配方书门控；未全局删除配方。

重置obtain节点在玩家能力NBT `ResetDiscoveries` 保存当时拥有数量。纯背包格、光标和自己合成格间移动用合计数量消除误判；旧库存、重登或打开手册不重新完成。实际拾取/制作/熔炼入口和之后净新增库存可以重新完成；主动检查明确允许重核旧库存。死亡Clone等保存路径沿用原能力实现，编辑状态/冷却属于临时会话，不保存为开启状态。未来新获得方式应接入 `research_service.acquired`；仅在两次轮询间先失去再通过无事件方式获得且总数不增长时，不能用数量差分判定该事件，玩家可主动检查补全，不声称覆盖任意第三方直接改库存方式。

### 12.4 实际验证与构建

| 检查 | 本轮最终结果 |
| --- | --- |
| 独立正式构建 | `gradlew.bat build --offline`：**BUILD SUCCESSFUL in 19s**；`compileJava`实际执行；11任务中8执行、3缓存。日志 `build/archive-build-final.log` |
| 最终完整GameTest | **235项，234通过、1失败**；日志 `build/archive-tests-final.log`。GameTest与Gradle退出码1，因此不能称全套测试通过 |
| 唯一最终失败 | `phantomfragmentsresourcesareshipped`，既有空名称断言与“幻像碎片”翻译冲突，遵守第0.1节未改测试、名称或排除项 |
| 新增6组 | 全部通过：服务端权限/隐藏数据；补齐前置与重置后真实结果槽/Shift/配方书撤销及双玩家独立；保存重登及内部移物不撤销重置/主动检查；新获得与无关物品；缩放阈值/鼠标锚点/尺寸中心；临时文件真正写入并重读偏好 |
| 原有研究测试 | 本轮完整测试中全部通过，保留原12组提交/保存/资源/真实合成与配方书回归 |
| 首次本轮全套 | `build/archive-validation.log`为235项233通过2失败，另一次旧SAN伤害断言10→15，符合已记录Apothic随机暴击问题；最终仅上述翻译失败。未改战斗实现或旧断言 |
| 正式包静态检查 | `package_checks.py`通过：17份相关资源与正式JAR逐字节一致，84对文本、手册16×16RGBA、图集256×256RGBA、严格水瓶、四份forge:none、必要新类及原六研究Mixin/refmap存在，参考和源文件未打包 |
| 差异检查 | `git diff --check`通过，仅既有CRLF提示；完整工作区含历史未提交内容，不能将其全计为本轮新增 |

正式JAR：`D:\cao的mod\white_lily\build\libs\white_lily-0.1.2.jar`，**866175字节**，SHA-256：`8f3145d2f6437c7b356d4c9093013440343800598ab2a80f852944fe9b028ce6`。服务端GameTest实际运行并加载类；没有把编译和静态包检查写成启动器/真实专用服完整实测。

### 12.5 客户端和用户验证

实际运行 `runClient` 并进入原独立QA世界 `Handbook UI QA 2026-10-23`，观察到新木框/纸张布局、节点和控件；日志 `build/archive-client.log`，正常结束，**BUILD SUCCESSFUL in 3m**。没有进入或清理用户其他存档。

用户按物理Esc停止Computer Use后，助手没有继续接管窗口。用户随后回复“**我以自己验证完成**”，因此记录为**用户已自行完成人工验证**，不冒称全部交互均由助手逐项执行。本轮请求的四张截图没有保存；`research_archive_tiles.png`明确只是非游戏图集预览，旧 `research_client_initial.png`属于上一轮灰色UI，不当作本轮新设计截图。

最后名称可读字号、菜单拖动/键盘优先级及无效配方校验的小修已真实编译并通过上述自动检查，助手未在用户停止后重新运行客户端复验。没有全窗口/GUI缩放矩阵、双真实客户端联机、真实服重启交互和启动器正式JAR加载记录；无JEI/REI依赖，未声称已验证相应查看工具兼容。所有这些与用户自己的确认分开登记。

### 12.6 获取与后续维护

`/give @s white_lily:investigation_handbook`。普通路线、材料及普通水瓶要求不变，见第11.7节；新增操作详见 `docs/调查手册界面与编辑模式说明.md`。管理员进入编辑后右键花茶直接完成可验证补齐安神花，重置安神花可验证花茶权限撤销；主动检查背包可以再次发现已有安神花，不返还或消耗材料。

后续节点仍编辑 `data/white_lily/research/handbook.json`，新增语言键，无需在Screen硬编码目标物品。美术修改重跑绘制脚本，翻译按新增源维护；正式包检查记录为静态证据，不能代替实机截图。生产客户端与服务端应同时更新本次JAR，因为研究网络协议已升级为2。

---

## 13. 2026-10-23 第一批基础研究路线与加工台配方登记

### 13.1 开始前与范围

阅读README、本报告、适用说明、当前研究/能力/制作门控、加工配方序列化、机器/菜单及方块生命周期；重新检查git状态。项目及父目录没有发现适用AGENTS.md。当前仍为 `mapple @ 3d1aa02`，工作区包含前轮大量未提交改动；本轮未提交、推送、改权限或清理。先列出预计文件后实施，相关修改前备份到 `backups/research-machine-v1-20261023/`。

复用原调查手册、永久研究能力、提交材料、个人配方门控、管理员编辑与重置抑制。没有新增研究系统、物品、贴图、药效、症状或自然生成。旧安神花和花茶节点ID与历史记录保留，没有默认给老玩家完成新增节点。

**最终规则已落地**：个人研究，普通合成按实际玩家权限；加工配方登记到具体机器后共享。没有每批开始按钮、最后放料者授权、在线授权玩家、当前查看者决定加工或离线暂停授权机制。

### 13.2 修改文件

Java前缀 `src/main/java/com/damezame97/white_lily/`；资源前缀 `src/main/resources/`：

| 文件 | 用途 |
| --- | --- |
| `research/research_catalog.java` | 可选reveal_after与合法性验证；保留旧Node构造入口 |
| `research/research_service.java` | 揭示与必要前置分开、加工绑定校验、真实加工档案、普通容器返还、多个配方显示数据及安全跳转线索；无绑定受限配方保持拒绝 |
| `research/research_network.java` | 研究协议升为3，要求两端同版本 |
| `processing/processing_recipe.java` | 可选research节点字段，JSON/网络往返与指纹包含绑定；旧基础配方指纹不因新增空字段改变 |
| `processing/processing_block_entity.java` | 每机登记集合、NBT保存加载、当前玩家已学配方合并、候选先过滤机器权限、未登记状态和等待期间进度显示，原子完成再检查权限 |
| `processing/processing_menu.java` | 使用原版菜单按钮包，校验当前菜单/实体身份/距离/按钮，10刻冷却，服务端计算新增配方 |
| `block/simple_processing_table.java` | 实际玩家放置时登记适用研究；无玩家不自动登记 |
| `item/processing_table_item.java` | 本机方块物品拒绝导入BlockEntityTag，不从旧物品恢复机器知识或库存 |
| `client/processing_screen.java` | 登记已学配方按钮、提示，未登记状态，进度文字避让按钮 |
| `client/research_client.java` | 解码多份真实配方、工具/耐久及安全前置跳转ID |
| `client/InvestigationScreen.java` | 多配方完整详情、真实工具/耗时/副产物、跨章节前置导航，保留原显示偏好/缩放/检查/编辑 |
| `data/white_lily/research/handbook.json` | 9个稳定节点、2个章节，精良绷带揭示触发与双前置 |
| `data/white_lily/recipes/simple_bandage.json`、`clean_fine_bandage_beeswax.json`、`clean_fine_bandage_honey.json` | 仅改为已有research_crafting序列化器，原材料/产量/容器处理不变 |
| `data/white_lily/recipes/processing/bitterleaf_paste.json`、`yarrow_dressing.json` | 仅增加research绑定，原工具/数量/100刻/副产物不变 |
| `assets/white_lily/lang/zh_cn.json`、`en_us.json` | 新节点档案、章节、跳转、工具/登记提示与反馈；旧药物说明未改 |
| `gametest/ResearchRoutesMachineGameTests.java`（新增） | 10组研究、真实结果槽、放置、NBT、共享、迁移、请求安全及拆除回归 |
| `gametest/ResearchGameTests.java`、`ResearchEditingGameTests.java` | 将旧仅2节点/1根的数量前提改成9节点/4根；新增绷带门槛，保留基础材料不受限检查 |
| `gametest/ProcessingGameTests.java`、`MedicalGameTests.java` | 原加工堵塞/耐久/批量蜂蜜返瓶测试先明确授予本轮新增的研究和机器授权；原行为断言保留 |
| `art-source/research/expand_routes.py`、`expand_route_ui.py`（新增） | 本轮一次性变更记录，重复运行检测已实施并拒绝，不作为游戏运行入口 |
| `art-source/research/route_checks.py`、`route_checks.json`（新增） | 真正读取配置、前轮备份和正式JAR核对数值、迁移与逐字节资源 |
| `art-source/research/package_checks.json` | 重新运行既有手册资源校验后的最新实际记录 |
| `docs/基础研究路线与加工台登记说明.md`（新增） | 完整节点/配方ID、研究与机器分工、迁移、安全和扩展方法 |
| `docs/调查手册与研究树使用说明.md`、`简易加工台与生计材料使用说明.md`、本报告 | 为旧历史说明标注新规则入口，记录最终结果 |

原作者Mixin、原六研究Mixin、药物动作、感染/SAN数值与全部正式物品贴图本轮均未改。原加工菜单8槽和库存掉落入口沿用；本轮没有改掉落表为copy_nbt或制造带记录方块物品。

### 13.3 九节点与配方绑定

以下均省略 `white_lily:` 命名空间；研究提交不是实际成品制作配方。

| 章节 / 节点 | 必要前置 | 研究提交 | 实际绑定配方 |
| --- | --- | --- | --- |
| 基础草药 / bitterleaf | 默认可见，获得苦叶草 | 不消耗 | 无 |
| bitterleaf_paste | bitterleaf | 苦叶草1＋空白标签1 | processing/bitterleaf_paste |
| yarrow | 默认可见，获得止血蓍草 | 不消耗 | 无 |
| yarrow_dressing | yarrow | 止血蓍草1＋空白标签1 | processing/yarrow_dressing |
| calming_flower | 默认可见，获得安神花 | 不消耗 | 无 |
| calming_flower_tea | calming_flower | 安神花1＋空白标签1 | calming_flower_tea |
| 包扎与敷料 / coarse_cloth | 默认可见，获得粗布片 | 不消耗 | 无 |
| simple_bandage | coarse_cloth | 粗布片1＋细布条1 | simple_bandage |
| clean_fine_bandage | simple_bandage＋yarrow_dressing | 简易绷带1＋蓍草敷料1＋空白标签1 | clean_fine_bandage_beeswax、clean_fine_bandage_honey |

`reveal_after:["white_lily:simple_bandage"]`使精良绷带只由简易绷带揭示，而不是完成蓍草敷料就提前出现。蓍草敷料未完成时锁定，详情有跨章节入口。若敷料仍完全隐藏，只允许跳到已揭示的止血蓍草发现线索；发现后可跳到敷料研究。不复制蓍草路线或透露隐藏档案。

必要前置/材料提交/版本/重复包仍由服务端原子预检。编辑精良绷带补齐粗布片、简易绷带、蓍草及敷料必要分支；不完成苦叶或安神花旁支。重置依赖只撤销相关个人进度和普通制作权限。既有ResetDiscoveries持久抑制保持，主动检查可重核旧库存，新增发现根首次接入可识别旧背包物品。

### 13.4 当前制作内容与权限

实际核对与用户预期一致，无制作数值差异。研钵均每批固定1耐久：麻秆1→纤维2为40刻、基础加工；苦叶草3→药膏2＋残渣1为100刻；止血蓍草3→敷料2＋残渣1为100刻。普通合成：安神花2＋严格普通水瓶1→花茶1；粗布片1＋细布条1→绷带2；简易绷带1＋敷料1＋蜂蜡块或蜂蜜瓶1→精良绷带1，蜂蜜返瓶保留。

花茶与三条绷带JSON使用既有research_crafting，不修改材料匹配或返回容器。沿用真实结果槽、Shift、配方书与配方填充门控，权限属于当前网格的玩家；完成/重置后原菜单及时刷新。基础材料/工具、未绑定配方和药草直接使用不设新门槛，得到药膏/绷带成品不代替研究。

完成节点后服务端读取实际加工配方显示研钵、固定损耗、100刻、副产物；精良绷带显示两份真实配方。普通合成没有虚构研钵或耗时，蜂蜜普通返瓶也从实际原料的crafting remainder读取。未完成节点不发送完整配方和档案；跨章节跳转只引用已下发可见节点。

### 13.5 每机登记、旧机器迁移及拆除

每机 `RegisteredRecipes` 是稳定配方ID集合，不存玩家引用/UUID，不追查登记者后来的研究。放置者知识只在实际玩家放置时自动合并；命令/结构无玩家时不猜测授权。打开菜单、放料、查看和取出不自动登记。

“登记已学配方”使用Minecraft原版菜单按钮请求，只带当前containerId与按钮0。菜单服务端检查player.containerMenu身份、实体仍为世界同一实例、方块存在及8格范围，拒绝其他按钮/远程/替换后的旧菜单。服务端读取个人研究和实际本机配方，`research`与节点recipes双向绑定一致才合并，重复新增0，不消费。新增时setChanged；集合不每刻广播。

机器选择先过滤基础或已登记的研究配方，再按ID排序；无权限的较早匹配不阻挡合法配方。只有受限匹配时状态为UNREGISTERED，不推进、不扣料、不扣耐久。登记后无需重放原料，继续自动连续加工；无菜单/授权人在线条件。每批完成保留全部输出整体预检，再检查机器权限；堵塞暂停、输入工具变化和多人共享沿用。

旧机器没有RegisteredRecipes时库存和进度保留，基础配方照常；受限配方停在等待登记状态，总时间从当前匹配配方读取，避免保存37刻显示成37/0。登记后当前配方/工具/指纹兼容才继续，否则重开。新研究字段纳入指纹，旧受限配方首次升级可能重新开始一批，材料没有提前扣除；旧基础配方的指纹保留兼容性。

不存在/错误类型/未绑定的ID不能执行，坏字符串安全忽略，非本机或旧未知ID不映射成其他方法。资源重载后重新选择当前有效配方，已删除配方清理旧加工进度；同一研究配方ID仍有效时登记保留。个人研究重置、死亡或离线不会撤销旧机器，只影响向新机器登记。

方块原掉落表只产普通新加工台，不copy_nbt；八槽库存仍由原onRemove→dropContents一次性清空后独立掉落。加工台物品覆盖本身的updateCustomBlockEntityTag并拒绝导入，不影响别的方块，不能用带旧BlockEntityTag/Data的物品恢复机器记录或复制库存。重新放置只查询新放置者研究；本轮未添加机器清除UI。

### 13.6 构建、测试和包检查

| 检查 | 实际结果 |
| --- | --- |
| 最终独立构建 | `gradlew.bat build --offline`：**BUILD SUCCESSFUL in 19s**，compileJava真实执行；11任务8执行、3缓存，日志 `build/routes-build-final.log` |
| 最终完整GameTest | **245项，244通过、1失败**，日志 `build/routes-tests-final.log`；GameTest/Gradle退出码1，不能称全套全绿 |
| 唯一失败 | 既有 `phantomfragmentsresourcesareshipped` 空翻译断言冲突，按第0.1节保持，不改名称、断言或排除项 |
| 本轮新增 | **10组全部通过**，共新增10个GameTest方法，不声称单独有30个新增测试 |
| 新测试覆盖 | 9节点/2章节与单层揭示、跨章可见线索、旧ID保存；跨格/缺料/重复提交、双前置编辑闭包；真实结果槽/Shift/蜂蜜批量返瓶、两玩家独立；真实BlockItem放置与伪造NBT；合并/幂等/创造不授权；未登记无消耗、连续加工及重置独立；保存加载/旧机器；错误距离/菜单/实体；合法候选优先与删除配方、研究字段网络往返；拆除干净物品与库存只掉一次；无效记录/仅打开不登记 |
| 既有回归 | 原18组手册/编辑及加工堵塞、最后工具耐久、保存、Shift和库存掉落回归通过。与本轮新门槛直接相关的旧测试补授权前置/更新节点数；不改药效和无关失败断言 |
| 正式JAR资源 | `route_checks.py`核对**248份JSON/PNG**与正式包逐字节一致；9节点6绑定、原普通材料/产量、研钵时间/副产物与前轮备份一致，原安神花路线除坐标外一致，四生成仍forge:none |
| 既有资源检查 | `package_checks.py`重新运行通过，保留手册16×16透明图标和正式UI图集，参考图和源文件未打包 |

正式JAR：`D:\cao的mod\white_lily\build\libs\white_lily-0.1.2.jar`，**887735字节**；SHA-256：`a6742a209e7397231ee7f481283ff45b03f4e96c16d39f369f9ef05020c43840`。协议3及加工配方网络字段有变化，客户端和服务端需一起更新。

### 13.7 客户端观察、用户验证与未验证项

实际运行开发客户端，进入已有独立 `Handbook UI QA 2026-10-23`，未进入其他用户世界。日志 `build/routes-client.log` 正常结束，**BUILD SUCCESSFUL in 8m 18s**。助手实际观察/操作：创造背包入口、新章节目录、三条草药根与旧花茶进度、包扎研究链、编辑显示全部节点、直接完成简易绷带时补齐粗布片并及时出现真实普通配方通知/档案、详情滚动和恢复视图。最终草药坐标从过大的间距收紧，稳定ID不变；最终坐标未在助手保存截图中复验。

随后工具提示用户也在操作测试窗口，助手提出暂停自动点击/由用户验证选择。用户按物理Esc停止Computer Use后，未再调用任何Computer Use。用户答复“**我在验证完成了**”，记录为**用户自行完成人工验证的确认**；不能把所有客户端检查写成助手逐项执行。本轮没有保存新的客户端截图，旧截图和离线图集预览不当作新路线截图。

助手未逐项实测登记按钮点击反馈、前置跳转点击、多种GUI缩放与联机共享；这些中的服务端行为有上述GameTest覆盖。没有两个真实客户端、真实服务器重启、启动器正式JAR加载或JEI/REI兼容测试；不得用GameTest mock两玩家代替真实联机。最后等待登记进度总量的小修已重新编译和完整245项测试，没有在停止操作后重新接管窗口验证。

### 13.8 使用与扩展

获取手册 `/give @s white_lily:investigation_handbook`。正常获得苦叶草/止血蓍草/粗布片后各揭示直接下一层，用详情列出的材料完成研究；研究加工方法后放置新台自动登记，旧台需点“登记已学配方”。管理员右键精良绷带直接完成可核对跨章节前置补齐；重置个人后旧台继续，新台不继承旧知识。

完整9节点、配方ID、材料和迁移规则见 `docs/基础研究路线与加工台登记说明.md`。以后新增本机研究加工，在现有配方JSON加research并在对应节点recipes绑定；普通合成用现有research_crafting序列化器。只改Screen画节点不会授予制作/登记权限。机器共享知识不回写个人研究。
