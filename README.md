# White Lily

[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](LICENSE.txt)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)

White Lily 是一个面向 Minecraft Forge 1.20.1 的属性与生存机制扩展模组。它以自定义属性为核心，把饥饿、意志、法术、枪械和弓箭等系统串成一套可配置的角色成长框架，并与 Iron's Spells 'n Spellbooks、TaCZ、Curios 等模组联动。

> White Lily is a custom attribute framework for Minecraft Forge 1.20.1, designed to work with Iron's Spells 'n Spellbooks and TaCZ.

## 功能概览

- **18 个自定义玩家属性**：伤害抵抗、伤害减免、载重、全局法强/法抗、意志坚定值、意志坚韧度、铳械精通/增幅、法术增幅/暴击、箭术精通/箭矢暴击与暴击伤害、恢复间隔/回复血量/耐力/饱和储量。全部通过 `DeferredRegister` 注册，并同步到客户端。
- **负重与生存压力**：载重值、耐力值、饱和储量与恢复间隔共同影响移动、饥饿和回血节奏。
- **意志系统**：意志坚定值过低时会追加不受护甲和附魔影响的“梦魇”伤害；意志坚韧度用于抵抗削减。
- **法术联动**：把 Iron's Spells 'n Spellbooks 的全部学派法强/法抗汇总为“全局法强/法抗”，并提供法术增幅与法术暴击。
- **枪械与弓箭联动**：为 TaCZ 的枪械伤害提供铳械精通/增幅；为原版箭矢提供箭术精通、暴击概率与暴击伤害。
- **状态效果**：`white_lily:impure_magic`（肮脏魔力）。
- **物品与饰品**：魔力苹果、血肉之环、改良之躯、损坏占位符、命运罗盘，以及 Curios 的“改良”栏位。
- **伤害类型**：`abnormal_mana`、`depletion`、`nightmare`、`shadow_damage`，均为数据包注册，带独立死亡提示。
- **游戏规则 `Happy_mode`**：建世界时可开关；进入世界后以存档内记录为准，`/gamerule` 无法在游戏内篡改。

## 环境要求

- Minecraft **1.20.1**
- Forge **47.4.10**（版本范围 `[47,)`）
- Java **17**
- 客户端与服务端**必须同时安装本模组及其全部前置**，否则会因注册表不一致而崩溃。

### 必需前置

| 模组 | 最低版本 |
| --- | --- |
| Placebo | `[8.6.0,)` |
| Apothic Attributes (`attributeslib`) | `[1.3.7,)` |
| Architectury | `[9.0.8,)` |
| [Let's Do] API (`doapi`) | `[1.2.15,)` |
| Terraform | `[7.0.1,)` |
| [Let's Do] Vinery | `[1.4.41,)` |
| TaCZ | `[1.1.8,)` |
| Player Animator | `[1.0.2-rc1+1.20,)` |
| GeckoLib | `[4.8.2,)` |
| Curios | `[5.14.1+1.20.1,)` |
| Iron's Lib | `[1.20.1-2,)` |
| Iron's Spells 'n Spellbooks | `[1.20.1-3,)` |

## 构建

```bash
./gradlew build
```

产物位于 `build/libs/`。开发环境运行：

```bash
./gradlew runClient
./gradlew runServer
```

本仓库不包含第三方模组 jar。若需要在开发环境中加载前置，请自行把它们放入项目根目录的 `libs/`（该目录已被 `.gitignore` 排除），并按 `build.gradle` 中的 `flatDir` / `fg.deobf` 配置使用。

## 测试

项目包含 6 个 GameTest，覆盖属性注册、伤害结算、饰品栏位与死亡提示等关键路径：

```bash
./gradlew runGameTestServer
```

通过时会输出 `All 6 required tests passed :)`。

## 项目结构

```text
src/main/java/com/damezame97/white_lily/
├── attribute/        自定义属性注册
├── capability/       玩家变量、饥饿状态、渐进时态
├── client/           客户端提示与诊断
├── command/          命令
├── gamerule/         游戏规则与 Happy_mode 相关逻辑
├── item/             物品与创造模式物品栏
├── mixin/            堆叠上限、食物、伤害源等补丁
├── procedures/       伤害、法术、枪械、弓箭结算
├── white_lilybuff/   状态效果
├── white_lilycurio/  Curios 饰品与栏位
├── white_lilydamage/ 自定义伤害类型入口
├── white_lilyfood/   食物物品
└── gametest/         GameTest
```

数据包内容位于 `src/main/resources/data/white_lily/`，包括伤害类型、Curios 栏位/实体映射与标签。

## 配置

首次启动后生成 `config/white_lily-common.toml`：

- `debugLogging`：启动时把已注册的自定义属性打印到日志，默认关闭。

## 许可证

本模组以 **GNU Lesser General Public License v3.0** 发布，完整文本见 [LICENSE.txt](LICENSE.txt)。LGPL-3.0 要求同时提供 GPL-3.0 文本，见 [COPYING](COPYING)。

仓库中的 Gradle Wrapper、Forge MDK 构建脚本等第三方文件仍遵循其各自许可证；Minecraft Forge 本身采用 LGPL-2.1。模组代码通过普通 Java 依赖方式引用 Forge，不因此被 Forge 的许可证传染。

## 致谢

- Minecraft Forge 与 Forge MDK
- Iron's Spells 'n Spellbooks、Iron's Lib
- TaCZ（永恒枪械工坊：零）
- Curios、GeckoLib、Player Animator
- Placebo、Apothic Attributes
- Architectury、[Let's Do] API、Terraform、[Let's Do] Vinery
