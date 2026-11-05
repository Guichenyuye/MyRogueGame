# 归尘 · MyRogueGame

回合制肉鸽，libGDX 1.14.2 + Java 17，文字为主、像素字体。

**当前版本：0.3.2** — 敌人状态展示 + 悬停详情 + 路线规划：战斗界面显示敌人当前效果、面板极简化（只显示名字）鼠标悬停浮窗看完整描述、道具栏重叠修复、墓渊精英奖励技能池补全、「杀戮尖塔式」路线分支（节点连线 + 只能沿连线走 + 无死胡同）。

## 文档

| 文件 | 内容 |
|---|---|
| [docs/0.3.2.md](docs/0.3.2.md) | 当前实现快照：敌人状态/悬停详情/路线规划/墓渊奖励修复 |
| [docs/0.3.1.md](docs/0.3.1.md) | 召唤物 UI/毒叠加/多章节/骑士长/商店扩充 |
| [docs/0.3.0.md](docs/0.3.0.md) | 经济/道具/召唤物/墓渊/新敌人/事件 |
| [docs/0.2.8.md](docs/0.2.8.md) | 状态/诅咒/降临者捏脸/宝箱怪/新敌人/事件 |
| [docs/0.2.7.md](docs/0.2.7.md) | 角色选择视觉化/头像接入/背景 Cover 裁切 |
| [docs/0.2.6.md](docs/0.2.6.md) | 初始属性下调/镜中人天赋/欢愉之礼下调/负面效果框架 |
| [docs/0.2.5.md](docs/0.2.5.md) | 敌人预制池/骸骨战士/骸骨猎手/新角色空梦/中途重开 |
| [docs/0.2.4.md](docs/0.2.4.md) | 属性点回调 +1、灌木丛绕道改回血、角色选择界面 |
| [docs/0.2.3.md](docs/0.2.3.md) | 流派构筑 + 伤害管线重构（技巧分池/天赋重做/技能替换） |
| [docs/0.2.2.md](docs/0.2.2.md) | 节点重构（精英天赋/技能自选/宝箱防脸黑/篝火抉择/事件） |
| [docs/0.2.1.md](docs/0.2.1.md) | 体验修正（红白闪烁/三选一必出/属性可反悔） |
| [docs/0.2.0.md](docs/0.2.0.md) | 成长系统闭环（结算/三选一/属性点/技巧） |
| [docs/0.1.2.md](docs/0.1.2.md) | 成长系统前置（天赋/技巧展示、CD 重置、金币经验字段） |
| [docs/0.1.1.md](docs/0.1.1.md) | 一章节点地图 + 战斗界面 |
| [docs/节点地图设计.md](docs/节点地图设计.md) | 地图生成规则、节点类型、敌人配置、界面布局 |
| [docs/screenshots/](docs/screenshots/) | 真机截图 |

版本号规则：`0.n.m`，`0` = 测试阶段，`n` = 重大版本（新玩法系统），`m` = 小更新（数值 / bug / 界面）。每个版本对应 `docs/<版本号>.md` 一份文档。

## 快速开始

```bash
./gradlew :lwjgl3:run      # 启动游戏
./gradlew :core:battleTest # 不启动窗口，跑 252 项数值与地图自检
./gradlew build --offline  # 完整构建
```

调试用环境变量：`ROGUE_SEED=2026` 固定地图种子，`ROGUE_FLOOR=8` 直接从首领层开局。

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
