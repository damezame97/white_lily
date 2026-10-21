# IDEA 提交前检查修复（2026-10-21）

针对新增物品、处方与戒指相关提交检查中的 14 个错误和 144 个警告进行核对。修复范围为 13 个 Java 文件，未修改 Mixin、已有伤害公式或物品注册 ID。

- 当前 Forge 47.4.10 已回移植 `ResourceLocation.fromNamespaceAndPath`，替换新增代码中标记为待移除的构造器。提示里的 `since=1.20.6` 是依赖声明的弃用版本，不代表项目运行版本被更改。
- 戒指测试改用 Curios 临时槽位修饰符，替代待移除的 SlotHelper 和已弃用的 grow 接口。
- 补齐物品、配方与 Capability 覆写方法的空值契约；tooltip 的 Level 仍允许 null，Capability 的 Direction 仍允许 null。
- 保存 NBT 标签时使用局部变量判空；测试对必须存在的属性、状态、方块实体和反序列化配方显式检查非空。颜色断言采用空值安全比较。
- 删除与基类完全相同的不可维修覆写、冗余参数与连接空值条件。戒指仍不可维修。
- Forge 注解发现的测试局部标注反射使用；游戏引擎持有的 Level 不应由调用者关闭，因此只在相关类局部说明资源归属。Curios 运行时 API 桩造成的常量判断误报仅在对应测试局部标注，没有关闭项目检查。
- 保留既有 `low_qualitypotion` ID；该词是兼容存档的标识符，局部排除拼写检查，并在测试复用名称常量。
- 期间发现 Apothic Attributes 的随机暴击会干扰固定伤害断言。核对其 `AttributeEvents.apothCriticalStrike` 后，仅将新增戒指测试样本的 `attributeslib:crit_chance` 设为零；真实游戏规则不变。既有弓箭/枪械测试仍有随机暴击干扰的潜在间歇性，未为此改动其原有代码。

## 验证

- IDEA 对本次未提交的 13 个 Java 文件重新运行 Project Default 检查，提示“未发现任何可报告的内容”。此结论限于本次修复文件，不代表整个项目及运行目录内第三方数据包都无检查提示。
- 最终 `gradlew.bat build runGameTestServer --console=plain`：BUILD SUCCESSFUL，全部 127 项必需 GameTest 通过。
- 最终日志：`build/idea-inspection-final-verification.log`；`git diff --check` 通过。
- 未启动客户端人工复测，未提交、推送或合并 Git 分支。
