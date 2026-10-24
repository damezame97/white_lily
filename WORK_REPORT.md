# White Lily —— 构建验证工作报告（交接给下一位代码助手）

> **最新记录：2026-10-24 浏览器“同类聚集＋名称排序”见第24节；第23节生存解锁及其余既有浏览器/权限改动保留。历史构建、哈希和画面不代表最终状态。**

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

---

## 14. 2026-10-24 加工台共享与熔炉取货调整

### 14.1 最终规则

- **简易加工台**：机器登记共享。基础加工不需要登记、不需要个人研究；研究加工只需本机登记且研究绑定有效，任何玩家都能使用。取消加工台 `operator` 绑定、操作者个人研究前置、操作者离线/死亡/换维度暂停、`outputOwner/outputRecipe` 归属和“他人产物禁止改输入/换人清进度”。
- **配方浏览器**：增加当前 `processing_menu` 会话的机器上下文；浏览范围 = 个人已学 + 本机基础 + 本机已登记有效加工；机器权限精确到配方 ID，关闭/换机器/失效清除。详情区分“已掌握·本机可加工”“本机已登记·个人未研究”“本机未登记”“基础加工”。
- **熔炉/高炉/烟熏炉**：保留 owner 烧制权限和 `canBurn` 门控；结果槽拿取不再检查取货者 owner/配方/研究，普通点击和 Shift 均可取。未改酿造台、Vinery、铁砧、附魔台、ISS、TaCZ 等设备。
- 旧 `Operator`/`OutputOwner`/`OutputRecipe` 只忽略不读取，保存不再写入；库存、进度、`RegisteredRecipes` 保留。拆除仍掉落全新加工台，不携带知识或库存。

### 14.2 实际改动文件

```text
src/main/java/.../processing/processing_block_entity.java
src/main/java/.../processing/processing_menu.java
src/main/java/.../processing/processing_network.java（新增）
src/main/java/.../client/processing_client_context.java（新增）
src/main/java/.../client/recipe_browser.java
src/main/java/.../client/recipe_browser_index.java
src/main/java/.../client/processing_screen.java
src/main/java/.../recipebook/recipe_unlocks.java（Transfer.handle）
src/main/java/.../recipebook/recipe_menu_gate.java（熔炉结果槽）
src/main/java/.../mixin/recipebook/MixinFurnaceRecipeGate.java（移除 last recipe 写入）
src/main/java/.../white_lily.java（注册 processing_network）
src/main/resources/assets/white_lily/lang/{zh_cn,en_us}.json
src/main/java/.../gametest/ProcessingUnlockGameTests.java（重写）
src/main/java/.../gametest/FurnaceTakePermissionGameTests.java（新增）
src/main/java/.../gametest/{ProcessingBugRegression,ProcessingGame,ProcessingMenu,ResearchRoutesMachine}GameTests.java（更新旧 operator 断言）
docs/加工台权限-对话交接.md
docs/基础研究路线与加工台登记说明.md
docs/配方图鉴与个人解锁.md
WORK_REPORT.md
```

### 14.3 构建与测试

| 项目 | 实际结果 |
|---|---|
| `gradlew.bat compileJava --offline` | BUILD SUCCESSFUL |
| `gradlew.bat runGameTestServer --offline` | **272 项全部通过**，`All 272 required tests passed` |
| `gradlew.bat build --offline` | BUILD SUCCESSFUL |
| 产物 | `build/libs/white_lily-0.1.35.jar`，1,038,485 字节 |
| SHA-256 | `D31FDDD04811C3335699FB329FAC9FA6A8AA53D2656B1401EB33737ED8ECCB4B` |

新增/更新测试覆盖：登记机器无操作者/菜单自动加工；未研究玩家取共享成品且不能反向登记；登记者撤销研究后机器继续；旧归属字段不限制生产/取货且不再写回；基础配方无登记/无个人研究；登记不清兼容进度；机器上下文只含精确基础/登记配方；熔炉普通/Shift 取货且 owner 无权限/离线不影响；堵塞、最后耐久、原子消耗、拆除掉落回归。

### 14.4 未验证项

- 未启动真实客户端逐项点击浏览器状态、机器上下文刷新、关闭/换机器清理和多人同时查看；这些已通过编译、服务端菜单/上下文测试覆盖，属于静态+GameTest 结论。
- 未做两个真实客户端联机、真实服务器重启、正式启动器 JAR 加载、全部 GUI 缩放组合和 JEI/REI 兼容测试。
- 不把 `build` 成功、GameTest 272 项或离线资源检查冒充实机客户端验证。

---

## 15. 2026-10-24 可配置按键绑定

### 15.1 已实现

新增客户端独立模块：

```text
src/main/java/.../client/white_lily_keybinds.java
src/main/java/.../client/white_lily_key_handler.java
src/main/java/.../client/recipe_browser_key_bridge.java
src/main/java/.../client/white_lily_key_display.java
```

- 使用 Forge `RegisterKeyMappingsEvent` + `KeyMapping` 注册，不另做私有配置。
- `KeyConflictContext.GUI` 用于浏览器键，`KeyConflictContext.IN_GAME` 用于手册；`KeyModifier.CONTROL` 保留 `Ctrl+O` / `Ctrl+F` 组合。
- 分类：`key.categories.white_lily.recipe_browser`、`key.categories.white_lily.research_handbook`。
- 9 个实际映射：显示/隐藏、搜索、配方、用途、收藏、返回、上一配方、下一配方、打开调查手册。
- 手册按键默认 `GLFW_KEY_UNKNOWN`（未绑定），恢复默认仍为未绑定；只在该玩家没有其他 Screen、有世界/连接/存活时调用既有 `research_client.openFromItem()`，复用服务端快照与节流，不绕过研究权限。
- 输入使用 KeyMapping `isDown()` 边沿轮询，长按不重复；键盘和鼠标映射都走同一路径。
- 绑定显示使用 `KeyMapping.getTranslatedKeyMessage()`，支持修饰键、鼠标键和未绑定名称；语言键已加入中英文。
- 新增 `research_client.isOpening()` 防止手册请求重复。

### 15.2 浏览器接入状态（未完成，不能算已接入）

`recipe_browser.java` 及其 UI/交互仍由另一位助手修改，本轮**没有直接改写该文件**。

- 8 个浏览器 KeyMapping 已注册，但 `recipe_browser_key_bridge` 的 handler 尚未由 `recipe_browser` 安装。
- 因此当前状态：按键设置能看到浏览器键；在补丁合入前，浏览器键不会动作；旧硬编码 `Ctrl+O/Ctrl+F/R/U/A/←/→/PageUp/PageDown` 仍然有效。
- 具体接入位置、`Panel/BrowserScreen/RecipeScreen` 动作代码、旧判断删除清单、5 个动态提示替换，见：
  `docs/配方浏览器按键绑定接入.md`
- 合入后必须删除旧 GLFW 判断并安装 bridge，否则会出现同一功能两套路径；本轮**不声称浏览器按键已接通**。

### 15.3 构建

| 项目 | 实际结果 |
|---|---|
| `gradlew.bat compileJava --offline` | BUILD SUCCESSFUL |
| `gradlew.bat build --offline` | BUILD SUCCESSFUL |
| 产物 | `build/libs/white_lily-0.1.35.jar`，1,093,890 字节 |
| SHA-256 | `8A46910A79AB7B79D4DD4C4BD40DA522E3A78A11419C03F78C80CE6806F6954F` |

构建开始前检测到另一助手正在运行 `gradlew build runGameTestServer`，已等待其结束；没有并发运行构建、客户端或资源生成。

### 15.4 未验证项

- 未启动真实客户端，因此未实测“按键设置分类/冲突提示/改键/取消绑定/恢复默认/重启保留”。
- 未实测文字输入不误触、悬停配方/用途/收藏一次响应、浏览器桥接后的动态提示。
- 专用服务器未单独启动验证；按键类均带 `Dist.CLIENT` 或位于 client 包，公共入口没有引用它们，属于静态结论。
- 浏览器功能仍未接通，不能把注册表或补丁文档当作已完成接入。


---

## 16. 2026-10-24 配方浏览器第一轮优化与恢复任务最终交付

### 16.1 分工、边界和开始时状态

- 本轮恢复于暂停工作区，HEAD `55f9570`，未回退/恢复文件、未提交/推送、未改ACL、未升级依赖。未找到适用AGENTS.md；已读README、本报告和现有说明。
- 开始时已有大量未提交修改，包含其他助手的机器共享登记、`processing_client_context`、熔炉取货与可配置按键模块。保留这些内容，未恢复operator/outputOwner授权。
- 暂停前的`browser_history`、`browser_order`、结构化填充反馈已实际接入，并非本轮重新创建的空类；暂停前详情仍为深色单条大面板。本轮基于其调用链完成浅灰紧凑多条页面和独立缩放。
- 用户承担游戏内验证。本轮 **没有运行runClient、没有打开Minecraft、没有使用Computer Use、没有操作用户测试世界**。客户端画面/交互/改键/多人结果统一 **待用户验证**。仅执行项目既有GameTestServer测试环境（`run/world`测试服务器，不进入`run/saves`普通客户端存档）。
- 暂停前旧深色截图 `docs/previews/recipe-browser-20261024/01-crafting.png` 保留为历史，旁附README明确不是最终验收截图。两张用户概念参考已实际查看，未打进游戏资源。

### 16.2 实际完成与关键文件

| 文件（src/main/java/com/damezame97/white_lily/ 下） | 用途 |
|---|---|
| `client/recipe_browser.java` | 浅灰像素面板/凹槽/箭头，多真实配方纵向分页；逐条填充按钮；列表透明、缩放、输入与窗口适配；查询历史、改键桥接、反馈与提示 |
| `client/recipe_browser_index.java` | 可见配方索引、按语言缓存排序、来源提示、当前机器精确配方范围、指纹缓存和失效 |
| `client/recipe_browser_preferences.java` | 客户端排序与两套独立比例保存，复用原收藏文件 |
| `client/recipe_browser_tooltips.java` | 原生提示避边，超长提示保持正常字号，Alt滚轮阅读 |
| `mixin/client/MixinRecipeBrowserContainer.java` | 在容器原生tooltip前绘制右栏；沿用打开子界面不关闭真实菜单逻辑 |
| `mixin/client/MixinRecipeBrowserTooltip.java` | 原生tooltip成功绘制时的范围/位置补充；不替换Forge自定义组件 |
| `recipebook/browser_display.java`、`browser_recipe_display.java` | 共用设备展示数据及真实配方构造，可在GameTest服务器验证；保持替代材料、数量、返还/副产物 |
| `recipebook/browser_layout.java` | 独立可测的格距、适配比例、完整单元分页、目标定位和缩放命中 |
| `recipebook/browser_order.java`、`browser_history.java` | 三种稳定排序和32条数据历史；不是另一套研究/解锁系统 |
| `recipebook/browser_transfer.java`、`browser_transfer_feedback.java`、`transfer_reason.java` | 服务端权威填充验证、原子规划包装、结构化回复与客户端关联 |
| `recipebook/recipe_unlocks.java` | 复用现有频道，加入指纹/请求序号、限流、重复请求缓存与原因回复；旧三参调用也使用独立序号 |
| `processing/processing_menu.java` | 原有分配/原子填充器增加只读预判和具体拒绝码，权限/共享加工规则不变 |
| `gametest/RecipeBrowserOptimizationGameTests.java` | 8组新增浏览器风险测试，纳入全量280项 |

其他本轮文件：

- `src/main/resources/assets/white_lily/lang/{zh_cn,en_us}.json`：排序、比例、适配、原因、来源、动态结果、返还等文本；无文字烘焙GUI图。
- `src/main/resources/white_lily.mixins.json`：仅在client列表加入tooltip补充Mixin。
- `docs/配方图鉴与个人解锁.md`：最终操作、缓存、权限、协议及人工验收。
- `docs/配方浏览器按键绑定接入.md`：顶部明确桥接已完成，下文保留历史交接。
- 正式GUI使用原生按钮及代码像素绘制浅灰框/槽/箭头/火焰，不需要新增背景PNG或引入UI依赖；复用正式物品贴图。没有把概念图整张铺入游戏。

### 16.3 最终交互与分页

1. **设备布局**：合成九宫格保留空位/相对位置，无序有标记；烧制上原料、中火焰、下空燃料位、右结果，备注真实耗时经验；加工台工具/原料/容器/主产物/返还/副产物分别显示并有标签；切石与锻造独立布局。未知类型保留通用规则预览，不添加新服务器制作类型。
2. **紧凑宽度**：依据实际槽位与逐条＋的范围定宽，控制条有最低可读宽度；不为说明预留右半空白。说明常显最多三行，悬停可读全部。每条＋直接捕获对应Display/配方ID，另有Button→Display映射，不靠当前页第一条推断。
3. **多条页面**：同查询/分类按真实配方ID稳定排列，根据每条图形高度+备注高度打包完整单元；Ingredient替代材料仅轮播，不重复配方。普通页不会在底部拼半条卡片；超高特殊卡单独分页且可滚动。
4. **独立比例**：`itemScale`与`recipeScale`各默认100%、范围50%～150%、步进10%，分别复原。配置在客户端`config/white_lily-recipe-browser.json`，收藏继续`config/white_lily-recipe-favorites.json`。原版GUI缩放与研究树不变。列表格距真正改变列/行/容量；配方图形和＋整体缩放，控制与tooltip字号不变。
5. **适配**：窗口/缩放时按列表首项NBT或当前配方ID定位新页，合法最小页码1/1；列表空间不足临时收起。配方空间不足显示“首选→适配”比例，保存值不改；极小空间给明确扩大窗口提示。窗口恢复会恢复首选比例。
6. **排序**：DEFAULT保留原来的显示名不区分大小写排序并以ID/NBT平局；NAME用当前语言Collator，中文实际验证拼音顺序；MOD按物品namespace分组，Minecraft优先，其他按实际模组名（回退namespace），组内名称。语言、资源、标签、个人权限、机器上下文变化时清缓存；不每帧重排。
7. **历史**：最多32条，保存目标NBT、模式、分类、配方ID、滚动和列表搜索/页码/筛选；不保存Screen链或权限快照。返回重新查有效索引，机器失效/离开后不能靠历史保留临时权限。
8. **按键与输入**：已安装现有`recipe_browser_key_bridge`，GUI通过KeyMapping匹配，删除旧硬编码浏览器动作；搜索聚焦优先编辑文本。物品/按钮/输入接收并捕获对应释放/拖拽，空白列表不拦库存，详情模态不穿透。
9. **tooltip**：容器右栏在原生提示之前绘制；前景详情不画后方容器提示；保留物品图像和Forge组件。边缘夹取、换行及长说明滚动不缩字号、不重复绘制。静态核对本版本真实GuiGraphics注入点和refmap，但最终视觉仍待用户验证。

### 16.4 填充反馈、权限与协议

- 原因码：`OK`、`WRONG_DEVICE`、`UNSUPPORTED`、`NOT_AUTHORIZED`、`UNREGISTERED`、`MISSING_MATERIALS`、`MISSING_TOOL`、`MISSING_CONTAINER`、`INVENTORY_FULL`、`ALLOCATION`、`MENU_INVALID`、`DATA_CHANGED`、`COOLDOWN`。全部中英文齐备。
- 灰按钮按坐标判断悬停，避免原版`isMouseOver`因`active=false`屏蔽原因；客户端预判不授权，服务端再次校验菜单、距离、机器、研究、配方和库存。
- 指纹来自配方真实网络序列化；回复关联请求序号/菜单/配方，重复请求缓存32项，不再执行一次；等待80tick超时恢复。失败不额外扣料或复制。
- 继续复用加工台现有规划器，普通合成仍走原版ServerPlaceRecipe。当前不支持自动填充的烧制/切石/锻造/特殊类型明确说明不支持，本轮没有补造制作逻辑。
- 权限区分个人已掌握、基础加工、本机登记/个人未研究、个人掌握/本机未登记、个人掌握/本机可加工。上下文精确到当前机器配方ID，未学者不获得个人研究，不放开同产物其他制作路线。没有恢复operator/outputOwner，也未取消熔炉烧制门控。
- `white_lily:recipe_unlocks`协议 **1→2**；客户端与服务器须一起更新该JAR。

### 16.5 实际构建、测试与静态检查

环境：Java17 / Forge47.4.10 / Minecraft1.20.1，依赖未升级。构建前确认现有Gradle进程空闲；各轮顺序运行，没有并发争用。

| 检查 | 真实结果 |
|---|---|
| `compileJava --offline` | 真实执行，成功；49条现有弃用警告，没有编译错误 |
| `build runGameTestServer --offline --console=plain` 最终轮 | **BUILD SUCCESSFUL，280/280通过，0失败**，1分22秒 |
| 最终日志 | `build/browser-final-acceptance.log` |
| 额外静态检查 | `build/browser-static-checks.txt`：ZIP完整性、浏览器类、client Mixin/refmap、中英文新增键、61份配方/worldgen/biome modifier资源与当前源码字节一致 |
| 最终JAR | `build/libs/white_lily-0.1.35.jar`，**1,109,259字节** |
| SHA-256 | `4074FAF55E5FB5BB0AD2CC34730804CE73ADD8147A1DC6585459C00FC8FAF8BD` |
| 客户端/启动器/多人实机 | **待用户验证**；本次恢复任务没有启动客户端 |

测试过程如实保留：第一次恢复回归280项通过；扩展真实返还显示测试后曾1项失败（`build/browser-resume-final.log`），原因是本轮新测试随机选到产物为煤炭的真实熔炼配方，却断言所有槽不含煤。已把本轮测试固定到真实原版`minecraft:charcoal`，并非修改药效/旧断言；后续两轮均280项通过。未改已有无关测试断言。

8组新增风险测试覆盖：真实中文/英文排序、同名/NBT与模组连续组；有界历史/NBT复制；实际加工规划的工具/材料/容器/背包回收/槽位分配原因和失败不变；请求重放/冷却/指纹过期；未登记/错设备/个人权限；真实有序空位、烧制燃料占位、加工数量/副产物/工具、蜂蜜/处方返瓶、替代材料不复制配方；完整卡分页、空页/超高卡、目标定位；比例格距和命中/适配。既有共享机器和熔炉取货回归仍通过。

`git diff --check`全工作区仍提示他人既有`ResearchRoutesMachineGameTests.java:230`文件末尾多空行，未为本轮清理他人改动；本轮相关文件检查无新增空白错误。`src/main/resources/data`无git差异，因此配方、研究和自然生成未改。

### 16.6 未完成范围与用户验收

本轮授权内实现和自动检查已完成；没有把“编译通过”写成“游戏内通过”。以下统一 **待用户验证**：浅灰紧凑观感、多配方整页、两比例容量与各条＋、tooltip叠层与Shift长文、透明空白输入、改键提示、窗口/全屏/GUI缩放、真实多人权限、正式启动器加载。

简短验收流程：

1. 更新客户端和服务器同一JAR，备份旧JAR后替换，不同时保留两份模组；游戏内打开背包。
2. 找具有多条同设备配方的已授权物品，检查浅灰九宫格→结果→＋、下方紧凑来源，没有右侧说明大留白；逐页无半条普通配方。
3. 列表与配方分别50/100/150%，容量真实变化；逐条＋填充正确配方；各自复原、重开、重启保留。
4. 背包右边与浏览器最右/最下项、Shift长说明置顶不截断；Alt滚轮读取超长说明；前景详情不冒出后方提示。
5. 默认/拼音名称/模组组排序，搜索+收藏仍合法；材料左/右键跳转并逐级返回。
6. 当前加工台分别测试本机已登记、个人已学未登记、缺工具/原料/容器、回收背包满；离开/换机器不保留额外配方。
7. 改键后检查文字输入、配方/用途/收藏、翻页、提示；调小窗口/GUI缩放，右栏不压真实槽位，恢复后比例不被永久改写。

完整操作说明在`docs/配方图鉴与个人解锁.md`末节。没有要求用户先验收才打包，没有请求游戏控制权。

---

## 17. 2026-10-24 配方浏览器作弊测试模式与管理给予

### 17.1 实际实现

- 浏览器右下角新增紧凑“作弊设置”按钮；只在不与页码、物品格、已有控制条重叠的预留位置绘制。点击打开小面板，含独立的“物品拿取”“显示所有物品”两个开关，默认关闭；关闭面板不清空开关。
- 权限：**创造模式 + 命令权限等级 2**，由服务端 `browser_cheat_network.canUse` 判定；客户端只显示状态。登录、重生、换维度、游戏模式改变、每20刻权限变化都会同步；退出/失去权限时客户端立即清除开关与作弊浏览范围。
- 物品拿取：右侧列表左键给予 1 个，Shift+左键给予一组；只对列表物品格生效，不碰材料格、设备图标、“＋”按钮或真实背包槽位。
- 给予：客户端只发“物品栈 + 1个/一组 + 请求号 + 当前containerId”；服务端重新验证权限、菜单/距离、物品非空非空气、请求号/节流，并通过请求玩家自己的命令来源执行 `give @s <registry id><SNBT> <count>`。不接收任意命令、任意目标或任意数量。
- 数量：服务端读取当前 `ItemStack.getMaxStackSize()`；不可堆叠为1；石头测试为64，药水测试为1。
- NBT：合法药水（`Potion`）与附魔书（`StoredEnchantments`）经真实 `/give` 保留；空栈、空气、损坏/过期 containerId 拒绝；同一玩家5刻内重复请求返回 `COOLDOWN`。
- 未解锁物品：显示所有物品模式使用注册表全物品 + 有界的药水/喷溅/滞留药水/附魔书变体，不使用任意 NBT 组合；没有配方的物品也显示。给予不检查个人配方/研究；也不直接授予配方或研究，正常获得事件保留。
- 显示所有物品：`recipe_browser_index` 在作弊模式使用全部真实配方和全物品目录；关闭后重新按个人/机器范围构建，不把作弊索引当永久缓存。搜索、排序、收藏、分页、比例仍沿用现有实现。
- 收藏冲突：拿取开启时，列表左键优先给予，Shift+左键优先给予一组；同一输入不再触发Shift收藏或打开配方。收藏键若绑定到鼠标左键（含Shift+左键）在本列表中被屏蔽；改绑到不冲突键仍正常，中键等入口保留。提示读取真实 KeyMapping。
- 动态分类：沿用并核验 `RecipeScreen.refresh` 从当前 `matches` 的真实 `Display.category` 生成分类；无匹配为空，单分类按钮禁用，多分类用紧凑循环选择；切换 showAll/权限/机器上下文后重算并纠正页码，没有新增固定“合成/熔炉/加工”标签。
- 按键双触发修复：`white_lily_key_handler` 只处理独立的手册键；浏览器键统一由 `recipe_browser` 的 KeyMapping ScreenEvent 分发，避免 InputEvent + ScreenEvent 各执行一次。

### 17.2 改动文件

```text
src/main/java/.../client/browser_cheat_client.java（新增）
src/main/java/.../recipebook/browser_cheat_network.java（新增）
src/main/java/.../recipebook/browser_item_catalog.java（新增）
src/main/java/.../client/recipe_browser.java
src/main/java/.../client/recipe_browser_index.java
src/main/java/.../client/white_lily_key_handler.java
src/main/java/.../white_lily.java（注册浏览器作弊频道）
src/main/resources/assets/white_lily/lang/{zh_cn,en_us}.json
src/main/java/.../gametest/BrowserCheatGameTests.java（新增）
src/main/java/.../gametest/ProcessingTestPlayers.java（测试权限等级）
docs/配方图鉴与个人解锁.md
WORK_REPORT.md
```

没有修改配方数据、研究条件、加工台共享/机器授权、熔炉烧制/取货、药效、产量或自然生成。

### 17.3 构建与测试

| 项目 | 实际结果 |
|---|---|
| `compileJava --offline` | BUILD SUCCESSFUL |
| `runGameTestServer --offline` | **287/287 通过，0 失败** |
| `build --offline` | BUILD SUCCESSFUL |
| 产物 | `build/libs/white_lily-0.1.35.jar`，1,137,799 字节 |
| SHA-256 | `ABCAC1FA7C06AD3CCA3F45E50682A66EDE85DF6C5011DC641C1FDF9E6EA869A6` |

新增自动测试覆盖：创造+权限2可用、生存+权限/创造无权限拒绝；单发1个、真实64整组、不可堆叠1、药水/附魔书NBT、空物品/过期containerId、5刻重复节流；给予不直接授予配方；全物品目录包含无配方物品、药水/附魔书变体、无空气/空栈/重复；既有 280 项浏览器/加工台/熔炉回归继续通过。

### 17.4 未验证项（待用户游戏内验收）

- 未运行 `runClient`、未接管窗口、未打开 Minecraft、未使用 Computer Use。
- 未实测作弊按钮的实际像素位置/遮挡、两个开关点击、提示文案、不同GUI缩放/小窗口、真实 `/give` 背包满掉落、多人服务器权限撤销的客户端刷新。
- 未做两个真实客户端、真实服务器重启、正式启动器加载；GameTest 的权限/命令来源与目录是服务端自动验证，不等于客户端实测。
- 动态分类的自动验证仅覆盖“实际配方/索引”路径，未逐项点击所有设备标签。

### 17.5 用户验收清单

1. 生存或有OP但非创造：作弊按钮禁用并显示权限原因；创造+权限2可打开，两个开关默认关闭。
2. 开“物品拿取”：左键给1、Shift+左键给一组；石头64、药水/附魔书NBT、不可堆叠1；Shift+左键不再收藏；关闭后左键恢复查询。
3. 开“显示所有物品”：可见未解锁/无配方物品与药水/附魔书变体；搜索、排序、收藏、分页、比例仍正常；关闭后恢复普通范围。
4. 退出世界/服务器、切生存或撤销权限：开关清除、作弊范围消失，重进默认关闭。
5. 检查作弊按钮不遮挡页码/物品/控制条；设置面板可关闭且不关闭开关。
6. 当前查询分类只显示实际设备；单分类无多余切换；无匹配显示提示而非假标签。
7. 改键/搜索输入/收藏冲突：拿取开启时左键与Shift+左键由拿取优先，其他收藏键正常；搜索输入不误触。
8. 确认未修改配方、研究、机器授权和熔炉规则。

---

## 18. 2026-10-24 第二轮：前景弹层、紧凑配方页、填充返回与会话历史

### 18.1 实际完成

- **作弊/设置弹层层级**：底层物品列表先在容器原生tooltip之前绘制；作弊与设置菜单在`ScreenEvent.Render.Post`作为当前活动层重绘，使用不透明深色背景并最后绘制自身提示。弹层覆盖范围内的原生槽位tooltip由容器Mixin取消，底层物品不再显示悬停高亮或物品提示。弹层打开时点击任何位置都由面板吞掉：菜单外只关闭菜单，不穿透到给予、收藏、配方/用途查询或真实背包槽位。菜单按锚点避边，底部优先向上展开；窗口尺寸变化由新Screen初始化或布局尺寸检测关闭弹层；作弊菜单与设置菜单互斥。
- **右栏顶部压缩**：第一行只保留标题、设置按钮、收起按钮，第二行搜索框；物品格从`y+41`开始。排序、物品比例加减、恢复100%、仅收藏移入设置菜单；设置按钮悬停显示当前排序、物品比例和收藏状态。配方页面比例仍在详情独立控制，配置键与保存逻辑未改。232px高的右栏物品行数从6增至8（测试断言）。
- **每页多条紧凑工作台配方**：新增`browser_card_layout`，单元高度=真实设备图高度×实际比例+10px单行元数据。100%下普通工作台卡高82px；440px详情体（339px可用）可放4条，360px窗口（251px可用）可放3条，总配方不足3条时按真实数量显示。工作台仍为九宫格→产物→独立`＋`；`＋`位于实际产物槽右侧（工作台x=136,y=30），提交自己的Display/配方ID；替代材料不拆成额外卡。窗口/比例变化按当前配方ID重新定位；完整卡打包，极小窗口单卡滚动，不重叠半条。
- **加工台/烧制/切石/锻造字段保留**：加工台继续显示工具、容器、材料、成品、返还物、副产物；烧制的燃料位/时间/经验不再被紧凑高度裁掉（燃料槽底76，单元最小78）；切石/锻造专用布局未改。备注只常显一行，悬停该行显示完整备注和配方ID。
- **填充成功返回**：新增`browser_transfer_state`与关联回应队列。点击`＋`后进入等待并禁用重复提交；只有匹配当前`containerId + request + recipe`的`OK`回应才关闭详情并返回原容器。失败留在详情并显示真实`transfer_reason`；`MENU_INVALID`直接安全退出，不返回失效菜单；80tick无回应恢复按钮。旧请求、旧菜单、旧配方回应不会关闭新详情页。成功返回复用原Screen/containerMenu，不重新打开工作方块，不自动制作或取成品。
- **会话级查询历史**：历史从`RecipeScreen`实例移到`recipe_browser`静态会话层，关闭详情回容器、重新查询另一物品都不清空；退出世界/断线清空。打开新详情时把上一次有效详情查询入栈；`navigate`只在真实目标/模式变化时入栈；`back`只弹出、不把刚离开页面再次压入。保存目标NBT、配方/用途、设备分类、配方ID、滚动位置；最多32条。返回不恢复过时搜索/排序/缩放偏好，按当前比例重新定位；目标不可见时提示。返回时使用当前索引重新筛选，不保存权限或机器快照。
- **动态分类保留**：仍从当前`matches`的`Display.category`生成；无匹配不显示分类，单分类按钮禁用，多分类紧凑循环；查询/权限/机器/作弊/比例变化后重算并纠正页码。未新增固定设备标签。
- **未改**：配方内容、药效、研究条件、加工台登记共享、熔炉烧制门控/取货、其他模组设备权限、服务端`give`权限与原子转移逻辑均未改。

### 18.2 改动文件

```text
新增：
src/main/java/.../recipebook/browser_card_layout.java
src/main/java/.../recipebook/browser_transfer_state.java
src/main/java/.../gametest/RecipeBrowserInteractionGameTests.java

修改：
src/main/java/.../client/recipe_browser.java
src/main/java/.../mixin/client/MixinRecipeBrowserContainer.java
src/main/java/.../recipebook/browser_layout.java
src/main/java/.../recipebook/browser_history.java
src/main/java/.../recipebook/browser_transfer_feedback.java
src/main/resources/assets/white_lily/lang/{zh_cn,en_us}.json
gradle.properties（版本 0.1.36）
docs/配方图鉴与个人解锁.md
WORK_REPORT.md
```

### 18.3 构建与自动测试（实际输出）

| 项目 | 结果 |
|---|---|
| `gradlew runGameTestServer --offline` | **293/293 通过，0 失败**；日志 `build/gametest-run.log` |
| `gradlew build --offline --rerun-tasks` | **BUILD SUCCESSFUL in 27s**，11/11 task 实际执行；日志 `build/build-run.log` |
| 正式产物 | `build/libs/white_lily-0.1.36.jar`，**1,153,553 字节** |
| SHA-256 | `A14431CAAAD68FBBC23E9E58FE40F85521833880E32D5CE8BFA4581CD6EBD6FB` |

新增6项GameTest：紧凑卡高与每页容量（含360px窗口3条）、50%比例容量与同配方定位、顶部压缩行数、弹层避边、服务端回应必须匹配菜单/请求/配方、80tick超时与反馈队列一对一消费、跨Screen历史与返回不重复入栈。既有的287项测试继续通过；未修改断言来制造通过。

### 18.4 未验证项（客户端统一“待用户验证”）

- 未运行`runClient`，未启动/接管Minecraft，未使用Computer Use，未打开用户存档。
- 未实测弹层实际像素遮挡、菜单自身tooltip层级、弹层外点击是否真的不穿透、窗口缩放后的弹层关闭、设置按钮实际图标/文案。
- 未实测100%下常规窗口是否每条工作台配方都完整显示、3条时底部是否留白可接受、加工台/烧制字段的实际视觉间距。
- 未实测真实填充成功回容器、失败停留、超时、旧回应、失效菜单；GameTest只验证状态机与关联，不是客户端Screen实测。
- 未实测跨Screen历史（丛林木板→关闭→书→返回）、动态分类逐项点击、多人权限/机器上下文撤销。
- 未做正式启动器加载、两客户端联机、服务器重启；没有提交或推送。

### 18.5 用户验收清单

1. 打开作弊设置：面板应完全遮住下方物品，悬停只在面板自身提示；点面板外只关闭，不给予/不收藏/不查询/不取放背包。
2. 设置按钮：第一行只有标题/设置/收起，搜索框下立即出现物品；设置里可切三种排序、调物品比例、恢复100%、切换仅收藏；悬停显示当前状态。
3. 工作台详情100%：常规窗口应看到至少3条实际配方，每条九宫格→产物→自己的`＋`，下方一行有序/无序、权限/来源；无右侧大空白；翻页不截断半条。
4. 调配方比例50%/150%、改窗口/GUI缩放：每页容量变化、当前配方尽量保持、页码不越界。
5. 点击`＋`：等待后成功应回到原背包/工作台/加工台；失败应留在详情并显示原因；缺材料、缺工具、菜单失效、超时分别验证；旧回应不应关闭新页面。
6. 丛林木板详情→关闭→书详情→“返回”：应回到丛林木板；再返回不循环；同目标刷新不重复压栈；退出世界后历史清空。
7. 设备分类：铁镐制作只显示实际工作台来源；无匹配不显示假分类；单分类无箭头；离开机器后历史目标不恢复临时配方。
8. 确认未改变配方、研究、机器授权、熔炉规则和作弊`/give`权限。

---

## 19. 2026-10-24 清空三类自动配方解锁配置

### 19.1 实际修改

- 唯一匹配的配置文件：`src/main/resources/data/white_lily/recipe_unlocks/defaults.json`。
- 已将其四项全部改为空数组：`initial`、`initial_outputs`、`on_pickup`、`on_advancement`；保留文件、加载器和数据包路径，不删除框架。
- 检查了`src/`下所有`recipe_unlocks`资源，只有该文件；未发现其他本仓库同类自动授予配置。`recipe_unlock_rules.java`仍按原字段读取空数组，不改成全局恒false。

### 19.2 保留项

未修改：金剑配方书及其他配方书、调查手册/研究节点/研究授予、个人配方权限与解锁框架、事件监听/网络/存档、加工台登记与权限、熔炉及设备机制、配方浏览器/作弊、制作配方/物品/药效。旧玩家已学配方、机器记录和存档不清理、不迁移。

### 19.3 验证

- `defaults.json`可被`ConvertFrom-Json`解析；四项数组计数均为0。
- 解包最终JAR内`data/white_lily/recipe_unlocks/defaults.json`核对，同样四项为空。
- `git diff`本任务仅该配置文件和本段报告；未改Java、配方书、手册、研究或玩家数据。
- 构建：`gradlew build --offline` → **BUILD SUCCESSFUL in 17s**。
- 产物：`build/libs/white_lily-0.1.36.jar`，**1,152,719 字节**，SHA-256 `65F15F564198E3610E1EAA63DF10A6881D267C022DD5C069601069F0409362CD`。
- 未启动游戏、未执行`/reload`、未修改测试世界、未新增测试；未运行`clean`。

---

## 20. 2026-10-24 作弊弹窗前置于底部物品缩放入口

### 20.1 问题原因

- 物品模型由`GuiGraphics.renderItem`绘制在z=150；普通tooltip在z=400。作弊/设置弹窗此前虽在`Render.Post`最后绘制，但背景、文字和控件仍在z=0，容器渲染结束后深度测试已恢复，因此物品图标会压过弹窗；只把背景改成不透明或单独抬高背景不能解决。
- 修复：把弹窗作为完整活动层处理。`drawCheatPanel`/`drawSettingsPanel`统一`pushPose → translate(0,0,450) → 背景+文字+悬停控件+关闭按钮 → popPose`；弹窗自身tooltip再以父级z=100绘制（内部z=400，总计500），最后绘制。矩阵用`try/finally`恢复，未改动深度函数、混合或裁剪状态。
- 弹窗打开期间，容器原生槽位tooltip统一取消（不再只在弹窗矩形内取消），避免底层tooltip以z=400盖住弹窗；面板自身物品hover/tooltip也停止。点击由`Panel.click`先行吞掉，滚轮返回true；关闭按钮、外侧关闭均不会穿透到物品/背包/配方/收藏。
- 背景列表不再重复绘制；弹窗打开时物品网格仍只画一次，弹窗画在其上。

### 20.2 底部物品缩放

- 主物品列表恢复`〔−〕〔100%〕〔＋〕`快捷入口，位置为右侧浏览器面板的**左下角**控制行；右侧同一行仍是作弊设置，下面是翻页行。
- 复用`recipe_browser_preferences.itemScale()`与`Panel.scaleItems()`，设置菜单内同一比例控件也调用同一方法；没有第二份偏好或第二套缩放状态。
- 范围仍为50%～150%，每次10个百分点；点击百分比恢复100%。缩放后重新计算`cellSize`、列数、行数、每页容量和命中/悬停/tooltip坐标，页码由现有`rebuildGrid/locateItem`修正。
- 底部固定预留36px（控制行16px+翻页行18px+间隙），物品网格行数按`itemRows(h,41,36,cell)`计算，不会排到按钮下。面板可用宽度<132px或高度<140px时保持原有紧凑入口，避免底部按钮挤出。
- 顶部仍只有标题/设置/收起与搜索框；排序、物品比例仍可从设置菜单调整。配方详情比例代码未改。

### 20.3 改动与验证

```text
src/main/java/.../client/recipe_browser.java
src/main/java/.../gametest/RecipeBrowserInteractionGameTests.java（新增底部预留/容量断言）
WORK_REPORT.md
```

- `runGameTestServer --offline`：**294/294 通过，0 失败**（新增`bottomControlStripReservesSpaceAndItemScaleChangesCapacity`：网格底边不进入36px控制区；50%格距增加行数；50/100/150格距；详情卡高不受物品比例影响）。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 27s**。
- 产物：`build/libs/white_lily-0.1.36.jar`，**1,153,749 字节**，SHA-256 `884E3549231D98DC215486E8D32484E642D7F1FA4196F4C4F201EBB4F6CF114D`。本节产物取代第19节记录的旧JAR哈希。
- 未启动客户端、未运行`runClient`、未接管窗口；所有画面/点击实际效果 **待用户验证**。未提交、未推送、未改目录权限。

---

## 21. 2026-10-24 常驻侧栏收起状态与入口恢复

### 21.1 根因

- `compact`同时承担“玩家主动收起”和“空间不足临时收起”，且`collapsed`只在点击收起按钮/快捷键时置true。进入紧凑入口后，代码只`show(new BrowserScreen(parent))`，没有先清除手动收起状态并重新布局，因此窗口放大/GUI缩放变小后仍停留在紧凑入口，侧栏无法恢复。
- 独立浏览页面`BrowserScreen`也只有紧凑入口一个路径，恢复侧栏后没有独立的常驻入口。

### 21.2 状态修复

- 拆分状态：`collapsed`仅表示玩家主动隐藏；`spaceShort`每次`layout()`按当前逻辑宽度/高度计算（`available < 130`或`height < 140`）；`compact = collapsed || spaceShort`。
- 背包旁紧凑入口点击时：
  1. 先清除`collapsed`并重新`layout()`；
  2. 若空间足够，直接恢复常驻侧栏；
  3. 若空间仍不足，才打开独立浏览页面。
- `Ctrl+O`使用同一状态转换：常驻侧栏可见时收起；手动收起但空间足够时恢复；空间不足时打开独立页面。
- 窗口尺寸、原版GUI缩放变化会重新初始化/布局，重新计算`spaceShort`和`compact`；临时收起不会写入`collapsed`，空间恢复后侧栏自动回来。
- 独立页面保留为另外入口：普通侧栏标题左侧的`≡`按钮打开`BrowserScreen`，不替代侧栏；紧凑入口只在空间不足时作为回退。
- 重新计算面板宽度阈值：60px缩放组+58px作弊按钮+边距，最小可用宽度改为130px，而不是用旧的64/132阈值误判。

### 21.3 验证与产物

- 本任务实际改动：`client/recipe_browser.java`、`gametest/RecipeBrowserInteractionGameTests.java`（入口状态/底部容量断言）、中英文语言键、`WORK_REPORT.md`；未改排序、配方、权限、历史或详情布局。
- `runGameTestServer --offline`：**294/294 通过，0 失败**。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 26s**。
- 产物：`build/libs/white_lily-0.1.36.jar`，**1,155,310 字节**，SHA-256 `C8B5AD1DD23358C94CFFD355007AF6258D8DAFFD551A237AA757281288362535`。本节产物取代第20节记录的旧JAR哈希。
- 未启动客户端、未运行`runClient`、未接管窗口；侧栏恢复、窗口/GUI缩放和独立页面均为**待用户验证**。未提交、未推送、未改目录权限。

---

## 22. 2026-10-24 常驻侧栏自适应布局与临时覆盖

### 22.1 根因

- 常驻侧栏宽度被固定上限 `146` 截断。GUI缩放3/4下右侧逻辑空间明明有余量，却始终只有约7列，看起来像固定排满一行就换行；窗口变大也不会增加列。
- `spaceShort`使用固定`available < 130 || height < 140`；GUI缩放6时逻辑宽度不足，侧栏直接收起，而紧凑入口只打开独立`BrowserScreen`，没有临时覆盖侧栏。
- `collapsed`仍混合表达玩家主动隐藏与空间不足；旧状态可能让入口一直只能打开独立页面。

### 22.2 实际修复

- 常驻侧栏宽度改为使用当前`parent.width`（Minecraft GUI逻辑宽度）与容器右边界计算出的真实`available`，不再固定146；`columns`用`browser_layout.columns(w,cell)`、`rows`用`browser_layout.itemRows(...)`，每次布局按实际宽高重算。
- 物品格步长仍由已保存的`itemScale`决定；搜索、排序、收藏、比例、锚点页码保留。网格、点击、悬停、滚动、给予/收藏统一使用同一`columns/rows/cellSize`。
- 降级顺序：
  1. 宽度足够时用完整控件；宽度68～129px时使用紧凑底部控件（缩小缩放组、作弊按钮改为紧凑图标）。
  2. 宽度至少68px时仍显示常驻窄侧栏（3列左右），不再因130阈值直接消失。
  3. 宽度小于68px或高度小于“41px顶栏 + 当前格高 + 36px底栏”时，才显示紧凑入口。
- 紧凑入口点击：先清除玩家主动`collapsed`并重新布局；空间足够恢复常驻侧栏，空间仍不足打开**临时覆盖侧栏**`BrowserScreen(overlay=true)`，不再只打开独立页面。
- 覆盖侧栏为独立模态Screen但使用紧凑面板，不修改容器布局；屏幕内绘制、顶部`×`关闭、ESC关闭；由Screen隔离背包点击/tooltip，不产生一次操作同时作用于两者。
- 独立大页面仍由普通侧栏左侧`≡`打开`BrowserScreen(overlay=false)`，不替代常驻侧栏。
- 窗口大小、GUI缩放、全屏切换触发Screen重新初始化/布局；临时收起不写`collapsed`，空间恢复自动恢复常驻侧栏；玩家主动隐藏需入口或`Ctrl+O`恢复。`Ctrl+O`/改绑显示键与按钮共用状态转换。
- 保留弹窗不透明前景层与点击拦截；弹窗按新布局避边，尺寸变化关闭/重定位。

### 22.3 验证与产物

- 改动：`client/recipe_browser.java`、`recipebook/browser_layout.java`、`gametest/RecipeBrowserInteractionGameTests.java`、中英文语言键、`WORK_REPORT.md`。
- `runGameTestServer --offline`：**294/294 通过，0 失败**；新增/扩展断言覆盖：不同逻辑宽高与格步长的列/行不越界、宽高增加容量增加、不出现固定7列、50/100/150比例容量、`sidebarEntry`在空间足够时恢复侧栏/不足时才用覆盖页、底部36px预留、详情比例独立。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 26s**。
- 产物：`build/libs/white_lily-0.1.36.jar`，**1,157,050 字节**，SHA-256 `5D850DF1063B0DDEEC6395232E947B412763CD77560E4DA6BA870EA37E87082E`。本节产物取代第21节记录的旧JAR哈希。
- 覆盖侧栏在窗口/高度恢复到可容纳常驻侧栏时自动关闭并回到常驻模式；独立大页面不自动关闭。
- 未启动客户端、未运行`runClient`、未接管窗口；GUI缩放3/6、窗口放大、侧栏恢复、覆盖侧栏和点击隔离均为**待用户验证**。未提交、未推送、未改目录权限。

---

## 23. 2026-10-24 生存配方解锁第一批

### 23.1 配置与通用机制

- 规则文件：`src/main/resources/data/white_lily/recipe_unlocks/defaults.json`。
- 保持 `initial`、`initial_outputs`、`on_advancement` 为空，不恢复旧的整套默认路线。
- `recipe_unlock_rules`在原有`recipes`/`outputs`之外，增加通用字段：
  - `items`：一个获得规则可列多个物品/标签选择器，共用同一Grant。
  - `inputs`：按真实`Ingredient`候选输入筛选，避免只按产物误授予。
  - `recipe_types`：按实际配方类型路径筛选（crafting/stonecutting/smelting/smoking/blasting）。
  - `conditional`：`seen`记录与`unlocked`配方条件全部满足才授予；条件先后顺序无关。
- `recipe_unlocks`在初始化/登录、新获得物品、显式解锁和配置重载时调用`evaluateConditional`；最多16轮收敛，利用现有`seen`与个人`recipes`持久化记录。没有每tick遍历全部配方，不清理旧记录，不撤销旧配方。
- 触发不消耗物品；同一获得只处理一次；重复获得不重复授予或刷新提示。

### 23.2 实际路线与数量（本次实际加载配方计数）

| 触发 | 授予 |
|---|---|
| 任意 `#minecraft:logs` / `#minecraft:planks` / `#minecraft:bamboo_blocks` / `#forge:logs` / `#forge:stripped_logs` | 木材整组 **136** 条（木板、木棍、五种木工具、工作台、箱子、碗、蘑菇煲、梯子、木门/活板门/台阶/楼梯/按钮/压力板/栅栏/栅栏门/告示牌）；不含床、船、木炭、火把 |
| `#minecraft:stone_tool_materials`（圆石/深板岩圆石/黑石） | 石镐/斧/锹/锄/剑、熔炉、拉杆 **7** 条 |
| 圆石 | 圆石建筑 **6** 条；→石头烧制 **1** 条 |
| 深板岩圆石 | 深板岩圆石建筑+磨制深板岩 **17** 条；→深板岩烧制 **1** 条 |
| 黑石 | 黑石建筑+磨制黑石 **14** 条 |
| 石头 | 石头建筑、石砖、石按钮/压力板 **11** 条；→平滑石头 **1** 条 |
| 平滑石头 | 实际存在的平滑石台阶/楼梯 **2** 条；无平滑石墙 |
| 石砖 | 石砖台阶/楼梯/墙 **6** 条 |
| 磨制深板岩 / 深板岩砖 / 深板岩瓦 | **14 / 11 / 6** 条 |
| 磨制黑石 / 磨制黑石砖 | **13 / 6** 条 |
| 沙子 / 红沙 | 各 **1** 条玻璃烧制 |
| 黏土球 / 黏土块 | 各 **1** 条红砖 / 陶瓦烧制 |
| 粗铁/铁矿石/深层铁矿石、粗金/金矿石/深层金矿石、粗铜/铜矿石/深层铜矿石 | 每种原料各 **1** 条对应普通熔炉冶炼 |
| 8种生肉/鱼/土豆 | 每种 **2** 条（普通熔炉+烟熏炉） |
| 熔炉 | 木炭烧制 **1** 条 + 烟熏炉制作 **1** 条 |
| `#minecraft:coals`（煤炭或木炭） | 普通火把 **1** 条 |
| 铁锭 + 已解锁熔炉制作 | 高炉制作 **1** 条 |
| 对应矿物 + 已解锁高炉制作 | 9种矿物各 **1** 条高炉冶炼；`conditional`共10条规则 |

计数为当前开发环境真实`RecipeManager`解析结果；数据包/模组增加变体时数量会变化，不写死为永久常量。木材使用实际标签，Stripped Oak Log/Bamboo Block/Bamboo Planks/`signs`/`coals`等标签已在GameTest中实际断言。

### 23.3 高炉与设备权限

- 高炉制作：`seen iron_ingot`且`unlocked minecraft:furnace`；两个条件顺序无关，初始化/新获得/解锁时补发。没有把“获得熔炉物品”当作“解锁熔炉制作”。
- 高炉冶炼：对应`raw/ore/deepslate ore`的`seen`且`unlocked minecraft:blast_furnace`；后满足条件时补发。只授予输入精确匹配的blasting配方，不开放其他矿物或装备回收。
- 熔炉、烟熏炉、高炉共用`AbstractFurnaceBlockEntity`与`recipe_machine_owner`；owner玩家仍按`recipe_unlocks.isUnlocked(owner,recipe)`检查。烟熏炉没有额外独立operation权限；切石机结果由`recipe_menu_gate`按配方`isUnlocked`检查。未新增设备operation权限。
- 高炉制作材料仍按原配方（铁锭、熔炉、平滑石头），未改数量；熔炉成品自由取货规则未改。

### 23.4 未覆盖与已知缺口

- 未新增金属装备、铁块/铁粒转换、铜建筑、玻璃制品、灯笼/灵魂火把/红石火把、营火、床/船、石英/末地石/海晶石路线。
- 深层“深板岩”没有实际建筑配方，故未加建筑规则；平滑石墙等不存在的配方不会授予。
- 模组木材/石材/食物变体依赖实际标签/配方类型，未逐模组实机验证。
- **首次进世界发放调查手册当前未实现**：只找到手册物品/界面/测试，没有登录发放逻辑；按边界未改，列为后续项。
- 旧玩家已学配方、获得历史、机器登记未清理；新规则在配置代次变化/登录时会对已有`seen`补评估，可能补发符合历史获得的路线（不会撤销）。

### 23.5 构建与自动测试

- 新增`RecipeUnlockRoutesGameTests`，覆盖：标签实际覆盖（stripped/bamboo/signs/coals/stone tool materials）、无初始开放、原木/去皮/木板/竹材触发整组木作且不开放床/船/木炭/火把、基础石材共享与建筑按材料分离、深板岩链、平滑石实际配方、各矿物/食物烧制、熔炉木炭/烟熏炉、煤炭/木炭火把、高炉双条件两种顺序与后补发、重复获得不重复、实际路线计数、旧`seen`历史无配方补评估。
- `runGameTestServer --offline`：**305/305 通过，0 失败**。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 25s**。
- 产物：`build/libs/white_lily-0.1.36.jar`，**1,170,626 字节**，SHA-256 `A91022797DA58D797CFC183B39CEA3D679DB1D1A5F14707038040171F0AEC0E9`。
- 未启动客户端、未运行`runClient`、未接管窗口；所有实际获取提示、配方书刷新、旧玩家补发和多人owner行为均为**待用户验证**。未提交、未推送、未改目录权限。

### 23.6 旧世界“有获得历史但没有配方”的修复

- 根因：`recipe_unlock_rules.generation`此前是**每进程重载计数器**，不是配置内容签名。旧世界在空规则构建中把玩家`generation`存成1；重启加载新规则后计数器也常从1开始，`initialize`误判“规则没变”，不再对已有`seen_items`补发；`acquired`对已见过物品又直接return，导致角色获得物品也不解锁。
- 修复：`generation`改为按排序后的规则文件ID/JSON计算的**稳定内容签名**。配置内容变化后，旧`generation`必然不匹配，登录/初始化时重新对`seen`应用获得规则；新增测试构造“旧generation=1、已见橡木/圆石、无配方”的玩家NBT，验证`initialize`补发木作与石工具。
- 已在线且本进程已初始化过的旧角色，需要重新登录（或触发一次数据包初始化）让`initialize`执行补评估；之后新获得的物品继续即时授予。旧配方、获得历史和机器登记不清理。


## 24. 2026-10-24 浏览器同类聚集＋名称排序

### 范围与文件

本轮仅排序、客户端分类资源和必要分页调用；未启动客户端/runClient，无Computer Use，无提交/推送/ACL变更。未找到适用AGENTS.md。检查并保留既有UI、作弊显示所有、按键、解锁与其他助手未提交改动，未改配方/权限/版本号（沿用0.1.36）。

- `recipebook/browser_order.java`：复用三模式比较器，增加主类别键；原分页算法提为同模块`grid`供UI与测试共用，非重复排序系统。
- 新增`recipebook/browser_categories.java`：按有序规则首次命中、标签/具体类型/显式ID分类；Item分类缓存，不改变NBT物品集合。
- 新增`client/browser_category_reload.java`：客户端首次资源加载与资源包重载读取配置，定位错误日志、安全回退并使索引失效。
- `client/recipe_browser_index.java`：仅对现有可见物品生成分类/名称键，继续缓存各模式；标签更新清分类缓存，语言/资源/物品/个人/机器/作弊上下文改变重建索引。模组显示名空值回退namespace。
- `client/recipe_browser.java`：原grid委托共用算法，切换排序不先归零页码，使既有首项定位生效；不重做UI，不显示类别名或类别占位。
- 新增`assets/white_lily/recipe_browser/categories.json`（源码在`src/main/resources/`）：23类集中配置。
- `assets/white_lily/lang/{zh_cn,en_us}.json`：内部类别名称键，均不渲染到网格。
- 新增`gametest/BrowserCategorySortGameTests.java`：4组风险测试。
- `docs/配方浏览器分类排序.md`：配置、优先级、缓存、扩展与验收；`docs/配方图鉴与个人解锁.md`追加最新排序说明。

### 最终规则

默认：跨模组按当前语言类别名、物品名排序，类别名相同时以稳定类别ID保持聚集；同名以注册ID/NBT变体键破平。名称：忽略类别、模组，只按显示名。模组：Minecraft优先，其余按模组显示名/namespace；各模组内部按类别、物品名排序。中文沿用已验证的简体中文Collator拼音排序，英文基本大小写无关，显示格式不参与；多音字按JDK表。

未知类别以自身显示名作内部类别键，不集中到“其他”。默认/名称连续填格，没有类别标题、换行或空位；只有模组标题沿旧布局计入容量。搜索/收藏后无空模组标题、无新增/重复物品；同ID不同NBT不合并。偏好继续存现有`config/white_lily-recipe-browser.json`的`sort`字段，窗口、缩放和窄侧栏保持。

匹配优先级（数组从上到下首个命中，不是类别显示顺序）：

药草 → 医疗用品 → 加工工具 → 原木 → 木板 → 台阶 → 楼梯 → 门 → 活板门 → 按钮 → 压力板 → 栅栏 → 栅栏门 → 矿石 → 金属粒 → 金属锭 → 宝石 → 粉末 → 护甲 → 武器 → 工具 → 基础材料 → 食物。

原木/木板用Minecraft语义标签；形状类用标签+实际方块类型；金属/矿石/宝石/粉末用Forge语义标签；武器先于宽泛工具匹配；药草/医疗/项目材料/研钵等用明确ID补充。TaCZ真实枪械载体`modern_kinetic_gun`明确归武器，不按整个namespace推断。原木标签覆盖正常/去皮原木、菌柄与正确打标签的第三方木材。不采用燃料、所有方块或中文名字子串。

扩展：在客户端资源包同路径覆盖`categories.json`，给已有规则补items/tags/kinds，或新增类别及中英文`screen.white_lily.recipe_browser.sort_category.<category>`。标签为空不匹配；无效规则独立跳过、记录文件/规则序号，未注册物品ID有警告；文件整体损坏回退名称。配置不授予任何服务端权限，不新增网络协议。

### 验证与产物

- `gradlew.bat build runGameTestServer --offline --console=plain`：**BUILD SUCCESSFUL，309/309 GameTest通过，0失败，1分6秒**。日志：`build/browser-category-verified.log`。`compileJava`真实执行。
- 新增4组：真实Minecraft/Forge标签、去皮原木/菌柄、类型与项目显式ID；固定多重匹配优先级与配置替换；跨namespace聚类/纯名称/未知回退/中文类别拼音；过滤后窄列与9/18/27格距下模组标题容量、无类别空位；坏规则定位与安全回退。原有英文、同名/NBT、作弊/个人/机器权限测试仍通过。
- 过程记录：初轮309项通过；补充未注册ID校验后，新增测试准确发现配置误写`white_lily:low_quality_potion`，1项失败（`build/browser-category-final.log`）；已按实际源码改成`white_lily:low_qualitypotion`，未改物品注册或删断言，最终309项通过。
- 正式JAR：`build/libs/white_lily-0.1.36.jar`，**1,192,230字节**；SHA-256 `FAA528176296702842D1322EA925D2A60593D02D82A9025C7661A60BAE525C46`。
- 已检查ZIP完整性、分类/排序/客户端重载类、配置与两种语言的23个类别键均打包正确；本轮相关文件`git diff --check`通过。现有弃用/Gradle警告保留，未修改无关断言或玩法。
- 客户端资源重载回调、画面和交互未实机验证，统一 **待用户验证**；GameTest验证共用分类/排序/分页逻辑，不能代替实际UI验证。

### 用户验收（待用户验证）

1. 默认：不同模组原木和木板各自聚集，连续填格，无类别文字/空行。
2. 名称：纯中文拼音/英文字母顺序；模组：Minecraft优先，各模组内部同类聚集，仅有模组标题。
3. 搜索、收藏、多页、窄侧栏及50/100/150%物品比例不丢/不重/不留空标题；切换模式尽量保留原查看项，重启记住模式。
4. 切换语言、F3+T刷新排序；退出/切换加工台不扩大权限；无作弊权限不能借分类看到全部物品。

## 25. 合成配方冲突选择器（2026-10-24）

### 实现与文件

- 新增`recipebook/crafting_choice.java`、`crafting_choice_network.java`、`crafting_choice_layout.java`及`client/crafting_choice_client.java`。复用现有研究/配方权限；按原版2×2/3×3真实网格匹配全部授权配方，以ID区分，不合并同产物。预览保留数量/NBT；选择不消耗。
- 新增`MixinCraftingChoiceAction`、`MixinCraftingChoiceRemainder`、`MixinCraftingChoicePlacement`；更新预览门控、研究取出门控、Shift门控。原版结果槽继续负责消耗、事件和统计，余料明确使用选中配方；整个Shift循环锁定ID，背包装不下一批时停止。第三方自定义菜单子类保留原结算。
- 更新`browser_transfer.java`使内置浏览器填充同时选择目标ID；`recipe_browser.java`增加模态入口/按钮输入优先处理/tooltip支持；主类注册独立方向性通道，mixins配置及中英文语言补齐。客户端和服务端须同时更新。
- 选择请求校验菜单、距离、会话、输入版本、序号、实时材料及权限。原版网络点击入口负责菜单生命周期检查，结果槽仍重新验证材料/权限/产物；不将网络距离校验重复施加到原版菜单直接API。
- 弹窗独立Screen，原版浅灰不透明像素面板、完整候选行、滚动、实际材料与产物说明、选中勾；外部点击消费、Esc返回。客户端画面与鼠标交互均待用户验证。

### 验证

- 最终命令：`gradlew.bat build runGameTestServer --offline --console=plain`，日志`build/crafting-choice-release.log`。**compileJava真实执行，BUILD SUCCESSFUL，315/315 GameTest通过，0失败，1分4秒**。
- 新增`CraftingChoiceGameTests`6组，使用临时专用冲突配方，finally恢复RecipeManager，不改正式生存配方。覆盖A/B实际产物与NBT、不同余料及事件次数、第一条锁定、独立玩家、2×2/3×3、数字键、Shift同物品不切配方、背包满、输入/会话/关闭过期、资源变化、浏览器目标选择及布局边界。
- 过程失败如实保留：初轮9项旧菜单测试失败，限制为原版精确菜单类后315项通过；后续冗余生命周期校验使`researchUnlockRefreshesImmediatelyAndKeepsPlayersIndependent`、`researchRecipeBookDeniesPrematureAwardsAndSanitizesLegacyProgress`、`recipeBookUnlocksImmediatelyOnceAndOnlyForItsUser`3项直接菜单取出测试失败。已去除结果槽重复的网络生命周期条件，网络端仍保留校验，未更改断言；最终上述3项及全套通过。
- JAR：`build/libs/white_lily-0.1.36.jar`，1,243,473字节，SHA256 `49F7FE021BD58D02D7D70725F93C45CAA0ED7DA2FE5336CE5D81E9147CC6D8DF`。ZIP完整、选择器类包含、可选测试namespace不在正式包中。保留既有编译弃用/Gradle警告。
- 未启动Minecraft/runClient，未操作用户存档。当前依赖中未发现外部JEI/REI；已接入内置浏览器及原版ServerPlaceRecipe路径，外部JEI专属传输未验证。

### 可选手动测试包

`docs/test-datapacks/white_lily_conflict_test.zip`，format15，4份独立namespace配方；已静态校验ZIP及JSON，不自动安装、不打入JAR。1木棍→1圆石/2泥土；1纸+普通水瓶→1绿宝石不返瓶/2钻石返瓶。第二条返瓶复用现有序列化器。安装与显式权限命令见`docs/test-datapacks/README-冲突选择器手动测试.md`；功能说明见`docs/合成配方冲突选择器.md`。

用户验收：两种配方切换、普通/数字键/Shift、余料、内置浏览器＋、弹窗遮挡及tooltip、外部点击不穿透、Esc、GUI缩放/小窗口。外部JEI如另行安装需单独验证；均标记待用户验证，不以自动测试替代。

## 26. 铁器阶段：简易铁工具、7本知识书、抄录与日用品路线（2026-10-24）

### 范围与文件

- 本轮只新增已确认内容；未恢复历史清空规则，未改调查手册研究/UI、机器共享登记、浏览器交互/按键/排序、木作/石作/烧制/高炉既有路线，未清旧玩家记录，未提交/推送/改权限，未启动客户端。
- 新增5个独立工具类及共享材料包装：`item/makeshift_iron_tool.java`、`makeshift_iron_pickaxe/axe/shovel/hoe/sword.java`。不预扣普通铁工具耐久，不复用普通工具物品。
- `items.java`/`creative_tabs.java`：注册12件物品；7本书进现有配方书栏（金剑书仍第一），5件工具进现有生计栏工具段，不新建创造分类。
- `recipe_unlock_book.java`：固定书和随机书共用服务端阅读、消耗、消息、10刻冷却；随机书只从有效未掌握池等概率选1，客户端不决定结果，不把结果写NBT。
- `crafting/template_copy_recipe.java` + `prescription_recipes.TEMPLATE_COPY`：抄录专用无序序列化器，仅这些配方返还模板；书物品本身没有全局crafting remainder。
- `recipe_unlock_rules.java`/`recipe_unlocks.java`：`conditional`新增`unlocked_outputs`（任意一条已解锁配方的真实产物匹配标签/物品），保留`seen`、精确`unlocked`；事件驱动16轮收敛，非每tick全量扫描。抄录权限通过7条`on_pickup`按模板精确授予，配方本身仍要求原书，不全局放开。
- `recipe_menu_gate.java`：Stonecutter结果槽同时检查`white_lily:operation/stonecutter`与具体切石配方；Anvil继续既有`white_lily:operation/anvil`。装备改造书只授予`minecraft:smithing_table`，不授予升级配方。
- `browser_recipe_display.java`：抄录页展示模板返还和说明；未改浏览器交互/排序。
- 资源：12张正式贴图按内容复制并保持字节一致，外部中文名源图保留；12个模型、5条工具配方、7条抄录配方、中英文名称/提示。
- `defaults.json`：当前**48条on_pickup、25条conditional、initial/initial_outputs/on_advancement为空**。新增7条书本抄录拾取、铁锭四向转换、桶→炼药锅、5条简易工具组合条件、10条日用品组合条件。

### 关键行为

- 简易工具：正规铁工具实际最大耐久/3向下取整、至少1（本环境5件均为83，但逐件读取对应正规工具而非写死）；采掘等级/速度/攻击/攻速/附魔/修复材料对应铁；铁砧修复仍为简易物品，不升级不破上限。
- 解锁：木工具+石工具+铁锭历史，顺序无关；仅铁锭不开放简易或正规铁工具；三条件末项满足时补发。
- 书写：固定书准确授予；工具随机池5、防具随机池4；全掌握不消耗；目标缺失/无效不消耗；创造不消耗；随机书副本仍按读者进度抽取。
- 抄录：原书+书+墨囊+羽毛→同种书；原书保留名称/NBT只返一份，材料正常消耗；普通/Shift/批量/满背包安全；不触发阅读；金剑书不加抄录。
- 日用品：铁锭转换仅4条压缩/拆分配方；剪刀/打火石/桶/炼药锅/铁栏杆/锁链/铁门/铁活板门/灯笼/灵魂灯笼/箱子矿车/指南针均按确认条件；木门与活板门用`unlocked_outputs`判断任意一种，不要求全树种；未加漏斗、活塞、弩、其他矿车或未确认红石。
- 旧玩家：`generation`仍是内容签名；配置变化后登录/初始化会按已有`seen`补评新书抄录和铁器路线，不撤销旧配方。已在线角色需重登以触发本轮补评估。

### 验证与调整

- 新增`IronStageGameTests`10组，覆盖物品/模型/贴图/创造栏、五工具耐久与属性、铁砧修复、6种三条件顺序、同材料版本切换与浏览器拒绝、固定书/随机书/冷却、7条抄录及NBT/Shift/满背包/网络序列化、日用品双条件、切石机operation与具体配方分离、铁砧operation、旧历史补评估。
- 旧测试前提因本轮明确改变，已同步调整且保留实际行为断言：
  - `LivelihoodGameTests`生计栏由29项改为34项，新增5件简易工具，原相对顺序不变；
  - `RecipeUnlockGameTests`配方书栏由1项改为8项，金剑书仍居首，新增7本知识书。
- 最终`runGameTestServer --offline`：**326/326通过，0失败**（日志`build/iron-stage-gametest-final.log`）。
- 最终`build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 27s**（日志`build/iron-stage-build-final.log`）。
- 正式JAR：`build/libs/white_lily-0.1.37.jar`，**1,296,180字节**；SHA-256 `7FA1F1D40FC7A3007D42D247D11C43A8CC8D76CB4904909D9DBE0852CE9CCECD`。已检查12张贴图/模型/配方/语言均在包内，字节与外部源图一致，未打包绘制源、备份或预览。
- 现有弃用API/Gradle警告保留；`ResearchRoutesMachineGameTests`原有的EOF空行提示属其他助手未提交改动，本轮未动。未启动Minecraft/runClient，未操作用户存档。

### 缺口与待用户验证

- 第一本正式来源本轮只有创造栏/命令；未加战利品、村民交易、世界生成或普通制作来源。
- 模组木材/石材变体的门、活板门、切石配方按实际标签/配方动态判断，未逐模组实机验证；数据包覆盖可能改变数量。
- 外部JEI/REI、客户端配方书填充、配方选择弹窗、切石机/铁砧实际点击与贴图观感均**待用户验证**；GameTest只证明服务端逻辑与资源打包。
- 用户验收：创造栏位置；五件工具耐久83与修复；三条件不同顺序；仅简易/学会正规后两版本切换；7本书固定/随机/全掌握不消耗；抄录NBT模板与批量；日用品双条件；切石机先有具体配方再读书；旧角色重登补发。

## 27. 本轮新物品接入“按住Shift查看详细介绍”（2026-10-24）

### 修改文件

- `item/detail_tooltip.java`：新增通用`appendDetails(tooltip, expanded, details)`；折叠时只追加与石制研钵完全相同的`detail_tooltip.hint()`，展开时才追加详情。Shift判断仍为原有`Screen.hasShiftDown()`，未复制第二套逻辑。
- `item/basic_material_item.java`：原有三行简介/usage展开改走同一通用入口，输出内容、灰色样式和顺序不变。
- `item/makeshift_iron_tool.java`及5个简易工具类：默认只显示统一Shift提示；展开时按原顺序显示简介（灰+斜体）和实际/正规耐久说明（默认颜色）。调用`super.appendHoverText`，未拦截或重复原版攻击/攻速/附魔/耐久提示。
- `item/recipe_unlock_book.java`：新增`copyable`标记与`appendDetails`。7本新书折叠时只显示统一提示，展开后依次显示学习方式、目标内容（随机池或固定配方/操作）和抄录规则；金剑书走原常显路径，完全保留旧tooltip。
- `item/items.java`：7本新书使用`recipe_unlock_book.copyable(...)`；金剑书构造不变。未改数值、耐久、配方、解锁、学习或抄录行为。
- `lang/{zh_cn,en_us}.json`：新增知识书抄录规则提示，中英文齐全；其他提示均复用已有语言键。
- `gametest/IronStageGameTests.java`：新增1组针对性测试，覆盖12件物品折叠时仅一条统一提示、5件工具展开顺序、7本书展开含抄录规则、金剑书保持常显原样。

### 覆盖物品

- 简易铁镐、铁斧、铁锹、铁锄、铁剑。
- 铁匠手记·工具、铁匠手记·防具、配方书-盾牌、工匠手记·切石机、铁匠手记·锻造、工匠手记·装备改造、工匠手记·运输。
- 现有配方书-金剑不在本轮覆盖内，仍保持原始终显说明。

### 验证与产物

- `runGameTestServer --offline`：**327/327通过，0失败**（日志`build/shift-tooltip-gametest2.log`）。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 33s**（日志`build/shift-tooltip-build.log`）。
- 正式JAR：`build/libs/white_lily-0.1.38.jar`，**1,299,842字节**；SHA-256 `F7E7A0067A5D628955C0FAC1A0CFC3E16D0716F2220F03325DD1ED8D3B5DCC06`。
- 未启动客户端/`runClient`；背包、创造栏、配方浏览器悬停时的实际折叠/展开、文字换行和左右Shift表现**待用户验证**。GameTest验证组件输出与本地化键结构，不冒充实机UI验证。

## 28. 删除指定氛围简介并补齐苦痛之戒功能说明（2026-10-24）

### 删除范围

- 基础材料6种：`coarse_cloth`、`thin_cloth_strip`、`cork_stopper`、`beeswax_block`、`charcoal_powder`、`blank_label`。
- 加工材料/用品9种：`plant_fiber`、`wooden_tray`、`stone_mortar`、`clay_bowl`、`glass_tube`、`filter_paper`、`desiccant_packet`、`herbal_residue`、`herbal_pouch`。
- 设备1种：`simple_processing_table`（删除指定的旧木桌外观叙述）。
- 共逐项删除中英文各53个键：16件物品各3行简介（48）＋苦痛之戒旧风味1行、旧文学简介4行（5）。未批量删除所有`description`键。
- `basic_material_item`新增“无详情”模式：13件无实际功能说明的物品不再显示无意义Shift提示，也不生成空白行；研钵、布包、加工台改为只展开`usage`功能行。
- 保留：研钵工具槽/1点耐久/最大128；布包“普通材料，暂不提供储物功能”；加工台自动加工/输出空间不足暂停/更换工具或取走必要材料清空进度。其余药草、绷带、药剂、手册、处方、麻秆、简易铁工具、苹果、配方书、检测仪等未改。

### 苦痛之戒

- 实际物品ID：`white_lily:stored_suffering`，中文名“苦痛之戒”。
- 仅移除旧风味/空叙述，机制未改；Shift展开现为4行功能说明：
  1. 装备在非装饰戒指栏时，受到至少3点（经护甲、吸收等减伤后的）实际伤害，会将其中的30%延后，只立即承受70%。
  2. 每笔延后伤害在5秒（100刻）后直接扣除；每次触发固定消耗1点耐久，不受耐久附魔或Unbreakable影响。
  3. 最大耐久512。摘下、击碎或检测到戒指缺失时，未结算欠款会立即全部扣除，可能致死。
  4. 延后伤害绕过护甲、吸收和抗性；同时装备多枚戒指时，每次受击只由第一枚触发。

### 验证

- 调整既有测试前提：`BasicMaterialsGameTests`改断言6种无详情材料不再显示Shift/空白；`LivelihoodGameTests`改断言7种无详情加工材料、研钵2条/布包1条/加工台3条功能行及对应语言键；`StoredSufferingGameTests`改断言4行灰色功能说明。未新增大量同构测试。
- `runGameTestServer --offline`：**327/327通过，0失败**（日志`build/tooltip-trim-gametest2.log`）。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 30s**（日志`build/tooltip-trim-build.log`）。
- 正式JAR：`build/libs/white_lily-0.1.40.jar`，**1,298,319字节**；SHA-256 `1C390FE77441DAB17F4E5B41478AC5E2AF145DF429B0EB67B04D7296FC9EFE29`。
- 未启动客户端；实际tooltip换行、Shift展开与中文观感**待用户验证**。

## 2026-10-24 百分比血量 HUD 第一版

- 新增独立客户端覆盖层 `percentage_health_overlay`，仅替换生命图标：固定10颗红心，按当前生命/最大生命裁切；保留原版中毒、凋零、冰冻及极限模式贴图。黄色吸收生命另起行显示。饥饿图标本轮未改。
- 新增 `health_damage_trail`：真实血量即时变化，浅红残影停留6刻后在12刻内追到真实血量；连续受伤重新计时；回血、上限变化、死亡及玩家对象切换做状态协调。未修改伤害或恢复逻辑。
- 精度受原版9像素心形限制，采用整数像素裁切；第一版暂未复刻原版回血跳动和受击闪烁。
- `compileJava --offline` 通过；独立 Java 动画检查覆盖首次显示、延迟、插值、62%比例、连续受伤、上限变化、死亡，全部通过。
- 打包日志：`build/percentage-health-build.log`。未启动游戏；画面效果由用户实机验证。

## 2026-10-24 苦痛之戒欠血层

- 百分比生命 HUD 在当前生命末端增加暗紫红覆盖：范围为 max(0, 当前生命-待结算伤害) 至当前生命，按最大生命归一化；黄色吸收生命不参与。欠款超过现有生命时覆盖全部剩余红心。
- 新增只向本人发送的 suffering_hud 服务端同步通道；金额变化、登录、复活、切换维度时同步，客户端仅保存临时显示值，不改存档账本或结算机制。保留原有 action bar 提示。
- 欠款结算时紫红层随服务端金额减少，实际生命降低触发现有浅红扣血残影；连续触发会扩展待扣区域。
- `build --offline` 通过；产物 `build/libs/white_lily-0.1.40.jar`，日志 `build/suffering-hud-build.log`。未启动客户端，实际颜色与动画待用户确认。
- `runGameTestServer --offline`：327/327通过；日志 `build/suffering-hud-gametest.log`。客户端视觉未验证。

## 2026-10-24 饥饿百分比与饱和度金边

- 新增 percentage_food_overlay：固定10个原版鸡腿，饥饿值/20即时像素裁切，从右向左填充；保留饥饿效果图标、原版隐藏HUD及坐骑血量替换规则。无残影或插值。
- 饱和度以10个细金色像素轮廓覆盖鸡腿，独立即时更新；分母采用稳定满饥饿容量20，Happy_mode时加正向BUILDING_ENDURANCE，避免饥饿下降导致金边比例上升。原有进食时按当前饥饿值限制饱和度的机制未修改。
- 新增只发给本人的 food_hud 服务端快照，补足原版饱和度变化未完全同步的问题；内容变化即发，并每20刻重发，玩家对象隔离显示状态。
- 客户端视觉尚未验证，未启动游戏；打包与自动回归日志见 build/food-hud-verification.log。
- 最终 build runGameTestServer --offline 成功，327/327自动测试通过；JAR为 build/libs/white_lily-0.1.40.jar。

## 2026-10-24 饱和度金边闭环修正

- 从本地原版 icons.png 的完整鸡腿区域(52,27)核对实际不透明像素，将手绘交叉轮廓换成沿肉块及骨柄外侧的闭合像素轮廓，去掉底部网格交叉。百分比裁切和同步规则不变。
- 构建日志 build/food-halo-contour-build.log；未启动客户端，观感由用户确认。

## 2026-10-24 安神花茶堆叠上限改为16

- 核对实现：`white_lily:calming_flower_tea` 注册为 `items.CALMING_FLOWER_TEA`，物品类 `medical_item extends Item`，在构造器中原写死 `Kind.TEA ? 1 : 16`。现统一改为 `new Properties().stacksTo(16)`；父类为普通 `Item`，饮用时间仍32刻、`UseAnim.DRINK`、效果与配方未改。
- 未发现其他针对花茶的“不可堆叠”写死逻辑。玩家背包Mixin把容器槽上限抬到128，但槽位仍取 `min(容器上限, 物品上限)`，实际仍为16；配方浏览器作弊`fullStack`读取`stack.getMaxStackSize()`，自动变为16。
- `medical_actions.finish` 保持只 `shrink(1)`：生存整组16饮用后剩15并返还1个玻璃瓶；最后一瓶返回手持玻璃瓶；背包满时整组仍只减1、剩余茶不被覆盖、空瓶安全掉落；创造模式整组不减且不产瓶。
- 更新`MedicalGameTests`覆盖最大堆叠、整组-1+返瓶、满背包掉落、最后一瓶、创造模式；`BrowserCheatGameTests`覆盖作弊拿取整组16。未改配方、研究、效果或饮用时间。
- `runGameTestServer --offline`：**327/327通过，0失败**（日志`build/tea-stack-gametest.log`）。
- `build --offline --rerun-tasks`：**BUILD SUCCESSFUL in 30s**（日志`build/tea-stack-build.log`）。
- 正式JAR：`build/libs/white_lily-0.1.41.jar`，**1,317,983字节**；SHA-256 `5ABD572603D372221803BD4371D3BE76F2894E72FF9ACA0D7E59D40C6BD34902`。
- 未启动客户端；实际整组饮用、返瓶动画与作弊栏显示**待用户验证**。

## 2026-10-24 创造模式生命/饥饿上限测试物品

- 在现有创造测试栏追加9件物品：生命扩容器Ⅰ/Ⅱ(+1/+10)、生命缩减器Ⅰ/Ⅱ(-1/-10)、饥饿扩容器Ⅰ/Ⅱ(+1/+10)、饥饿缩减器Ⅰ/Ⅱ(-1/-10)、生命与饥饿检测仪。无生存配方；修改器服务端验证创造模式，不消耗，5刻冷却，使用后提示结果。检测仪读取当前值与上限。
- 增加上限不补充当前值；降低后截断超出值；下限为1。生命使用独立永久属性修饰符，考虑已有乘算修饰符以按有效上限增减。重生继承本测试修饰符。
- 新增FoodData饥饿上限，默认为20，随玩家食物NBT保存并在重生时继承；进食、是否吃饱、原版自然恢复阈值及Happy_mode满饥饿判定适配上限。饥饿上限技术边界1000000。
- 饥饿HUD同步实际上限，鸡腿仍固定10个；饱和度金边基准改为饥饿上限加已有储存饱和度属性。增加上限时饥饿/饱和度均不自动补满。
- 9张新PNG为原生16×16透明像素画：红心/鸡腿主题、加减号、Ⅰ/Ⅱ角标和仪表；新增对应模型、中英名称及功能提示。
- 新增3条功能测试，覆盖权限、不消耗、不补满、上下限、进食、NBT、乘算属性及重生继承；更新既有测试栏7→16件断言。
- 未启动客户端，使用与HUD观感由用户确认；最终构建/自动回归日志build/capacity-test-verification-final.log。
- 最终服务端自动回归330/330通过；随后补齐客户端FoodData当前值同步并重新build，打包日志build/capacity-test-package.log。
- 本次实际最新产物：build/libs/white_lily-0.1.41.jar（已核对包含上限物品类、Mixin及检测仪贴图）；0.1.40为旧包。

## 2026-10-24 管理员测试命名及生存使用

- 9件生命/饥饿上限物品与检测仪统一添加“管理员测试·”名称前缀；原“创造测试工具”栏目改名“管理员测试工具”，同步英文。
- 移除上限工具的创造模式使用限制，生存模式持有即可使用，不额外增加OP权限限制；保持不消耗、冷却、上下限及保存规则。原污染/净化工具机制本次未改。
- 更新功能提示并删除过期的创造模式限制提示，修改自动测试验证生存模式下生命与饥饿扩容均生效、不补满、不消耗。
- 构建与自动回归日志build/admin-capacity-verification.log；未启动客户端。
- build与330/330自动测试全部通过；产物build/libs/white_lily-0.1.41.jar。

## 2026-10-24 测试工具持有即用与三件状态测试物品

- 用户要求：所列生命/饥饿上限工具、检测仪、污染/净化片持有即可使用；新增生命恢复、饥饿与饱和恢复、固定扣血三个工具及原生16×16贴图。
- `pollution_test_item.java` 移除等级2管理员权限检查和生存消耗，保留污染数值、无随机症状/治疗副作用、10刻冷却；原上限工具和检测仪已无权限限制，保持现状。仅调整所列测试工具，不改隐藏改良饰品、配方浏览器作弊权限或其他管理员操作。
- 新增 `item/state_test_item.java`，在 `items.java` 注册 `health_restore_test`、`food_restore_test`、`instant_damage_test`，追加至 `creative_tabs.java` 管理员测试栏（共19项）。三件均自身使用、服务端结算、不消耗、堆叠1、5刻冷却，无生存配方与研究路线。
- 生命恢复器直接设为当前最大生命值，不经过治疗倍率；饥饿与饱和恢复器读取 `food_capacity` 上限，Happy_mode 开启时饱和上限额外计入非负 `BUILDING_ENDURANCE`，清零 FoodData exhaustion；不写死20。扣血器直接减少当前生命10点，不消耗吸收生命，不受护甲、抗性、创造无敌和受伤无敌时间影响；不足时调用死亡处理。
- 新增三个 `models/item/*_test.json` 与对应 `textures/item/*_test.png`；新增中英名称及说明，并同步六种污染/净化片描述。PNG逐像素在16×16画布绘制，分别9/8/7色、透明背景且alpha仅0/255。绘图源 `art-source/state_test_items/draw.ps1`；最近邻预览独立放在 `docs/previews/state-test-items-20261024/`，不作为正式资源。
- 更新 `MedicalLifecycleGameTests` 生存无权限使用与不消耗断言，更新 `LivelihoodGameTests` 栏目数量，新增 `StateTestItemGameTests` 三条：实际生命上限、饥饿/饱和动态上限及开关、固定扣血与致死。
- 初次 `build --offline` 退出0，23秒，9项实际执行/2项UP-TO-DATE；初次自动回归332/333，失败为测试使用真实服务器参数设置Happy_mode，被既有存档规则锁还原，已修正为仅测试设置/恢复规则传null，不改用户存档配置与锁定机制。
- 未启动Minecraft客户端；最终回归及打包结果追加如下。
- 最终验证：runGameTestServer --offline 退出0，333/333通过，0失败（build/state-test-gametest.log）；build --offline 退出0，13秒，7项实际执行/4项UP-TO-DATE（build/state-test-build-final.log）。
- 最终产物 build/libs/white_lily-0.1.41.jar，1348045 字节，SHA-256 D40D8FECDB02D9D9318CA5D73185D678AD10E983DBC4868016D890344D358A11。已核对JAR包含三件状态工具类、模型和正式贴图。未启动客户端，游戏内图标/交互由用户验证；未提交或推送。

## 2026-10-24 羊毛、皮革、甘蔗及染料路线 / 白色睡袋

- 新增白色睡袋：任意颜色羊毛横排3个，可混色，产出1；真实两格放置，沿用原版睡眠条件及多人跳夜；通过Forge重生点事件阻止睡袋设置/覆盖重生点。不可睡维度直接提示，不爆炸。使用常规模型渲染，无额外床实体渲染器；不添加简介或染色配方。
- 自绘原生16×16透明物品图标，以及16×16布面/枕头方块贴图；生计栏新增睡袋。
- 新增独立数据规则textiles_and_dyes.json：皮革→四件皮甲；任意羊毛→全色地毯/床、睡袋、线制白羊毛；线→线制白羊毛；皮革+纸获得历史AND→书；甘蔗→纸及甘蔗制糖，不解锁蜂蜜制糖。
- 任一实际染料花卉入口、墨囊/可可豆/青金石/骨粉/仙人掌/海泡菜或任意染料→全部现有染料产物配方（含烧制/混色）；先掌握羊毛制作、再结合任意染料获得历史开放羊毛染色，顺序无关，不绕过旗帜/潜影盒等物品权限。既有配方授权框架保持原机制。
- 新增5条服务端测试覆盖混色横排、拒绝竖排、皮革纸两种先后顺序、染料来源与越级边界、夜间真实入睡、保留原重生点/阻止首次设置，以及拆除任一端仅掉1件。更新生计栏新增物品后的顺序断言。
- 初次检查修正了测试中甘蔗制糖ID；夜间测试需同步刷新天空亮度缓存，避免isDay仍按前一时刻判断。最终日志build/textiles-verification-final.log。未启动游戏客户端，画面及交互由用户确认。
- 最终build成功，338/338自动测试通过；实际夜间入睡、重生点保护与两端拆除掉落测试均通过。产物build/libs/white_lily-0.1.41.jar。
