# MyRogueGame 项目长期笔记

## 技术栈与约定
- libGDX 1.14.2 + Java 17（`gradle.properties` 写死 `D:/java17`/`D:/JAVA21`，禁 toolchain 自动下载）。包名 `com.myname.roguelike`，源码 UTF-8。
- **战斗层绝不 import libGDX**：`Player`/`Enemy`/`Slime`/`Skill`/`BattleSystem`/`RunState` 全是纯 Java，可控制台验证；UI 只读字段 + `BattleSystem.getLog()`。
- 数值自检：`./gradlew :core:battleTest`（IDEA 直接跑 `BattleTest.main`）。Windows 终端 `chcp 65001` 防中文乱码。
- 界面层 `Main extends Game`（共享 SpriteBatch/BitmapFont/FreeTypeFontGenerator/头像背景 Map/白图）。Screen 的 `dispose()` 只释放自己创建的资源。切换 `setScreen(new XxxScreen(game, ...))`。
- **一局状态在 `RunState`**（唯一 Player、GameMap、floor、pending、cleared/failed、curses、character）。GameScreen 不自己 new Player。
- `Ui` 画图工具（rect/frame/bar/text/textCentered/textWrapped/line），1x1 白图拉伸，避免 ShapeRenderer begin/end 冲突。
- **字体** `assets/Minecraft.ttf`（全中文字形 63446），必须 `parameter.incremental=true`；基准 20px，界面按 1.0 缩放（像素字体缩小会糊），标题 2.0+。**非整数缩放会字形渗色出横线**——放不下就改布局别缩字号。
- **禁用装饰字符**：`·`/`×`/`→`/`★`/`▶`/`✦`/`☠` 字形畸形。日志标记 ASCII：`>` 玩家(青)、`<` 敌人(红)、`[天赋]`(金)、`[技巧]`(浅蓝)、`*` 结算(绿)、`**` 暴击(金)、`!` 非法(橙)、`!!` 死亡(红)、`#` 状态摘要、`->` 箭头、`x2` 乘法。`GameScreen.logColor` 按前缀上色。
- `BitmapFont.draw(x,y)` 的 y 是文字顶部（文字占 `[y-行高,y]`）。截图验证 Windows 125% 缩放必须先 `SetProcessDpiAwareness(2)`。

## 已裁定规则（设计文档待定项，用户拍板）
1. 防御双向：玩家打怪也套 `伤害×(1-防御/(防御+25))`，最低 1 点。
2. 伤害取整统一 `(int)` 截断向下，公式全用 double 运算再转 int（不用 float）。
3. 护盾结算：先按防御减伤，再扣护盾；护盾寿命在「敌方回合全部结束」结算（`onEnemyTurnEnd`），不是玩家回合开始。
4. 血之泪按「伤害次数」回血（横扫打 N 目标回 N 点、多段每段 1 点），实现在 `BattleSystem.attackEnemy()`；但**闪烁按出手次数**（横扫只闪一下、只一条日志），`useSkill` 出手末尾按 hp 是否增加统一记一次。满血不回血不闪。
5. CD：`Player.cds[4]`（真实）+ `cdDisplay[4]`（判定/显示），回合开始先抄再减 1 → CD1=用后隔 1 回合可用。跨节点 `resetCooldowns()` 同时清两者。

## 代码结构要点
- **伤害管线唯一路径** `BattleSystem.calculateDamage(target,raw,type,isBasicAttack)`：基础值→固定加法（欢愉之礼）→乘区（近战/远程/AOE/虚弱/易伤，同类相加异类相乘）→特殊触发（完美仿制×0.7/厚积薄发×2.5）→暴击→防御减伤。**禁止另写第二套伤害公式**。双重打击是特例（固定 1 点走 `maybeDoubleHit`，跳过一切加成/暴击/回血）。
- 伤害只按近战/远程/AOE 三类，不再分「普攻」维度；`isBasicAttack` 只用于「随性」暴击率加成。普攻「随心一箭」是远程，吃全部远程加成（远程强化/箭术大师/血之回响）。
- `Skill` 静态数据（`Id` 枚举供 BattleSystem switch），技能原始伤害是 static 方法（`rawNormalAttack/rawPreciseShot/rawSweep`），UI 直接拿来加点预览。技能槽可替换（`isReplaceableSlot` 只许槽 2/3，替换清 CD 清等级）。
- `Enemy` 抽象基类：`getIntent()` 意图、`doTurn(Player,BattleSystem)`；新怪继承即可。敌人也有 `statusEffects` 列表（锈蚀箭矢/倒映给敌挂状态）。
- 天赋 `Player.talents`（质变，按角色完全专属 `Talent.owner`）+ 技巧 `combatSkills`（量变，通用/专属 `CombatSkill.owner`）。用对象不用 List<String>（悬停要 desc）。
- 天赋触发**绝不缩放**，反馈只靠颜色（`talentFlashR/G/B` 三 float 存色纯 Java）。
- 负面效果 `StatusEffect`（虚弱/易伤/晕眩/中毒/流血，战斗临时有层数）+ 诅咒 `Curse`（恶魔抓痕/怠惰/旧疾，本局持续）。给玩家挂负面统一走 `BattleSystem.inflictStatus()`（否则「倒映」不触发）。金币在 `Player.gold`。
- **中毒/流血靠层数自然消耗，不吃回合递减删除**：中毒回合开始扣「当前层数」血→层数-1；流血每次受伤扣层数→层数-1。`tickStatusEffectsAtTurnStart` 的回合递减循环跳过 POISON/BLEED。**敌人流血在 `calculateDamage` 里结算**（敌人每次受击后额外扣层数血→层数-1，放扣血后、血之泪前）。
- **战斗界面诅咒/负面效果分开显示**：`StatusEffect.shortName` 单字（虚/脆/晕/毒/血），诅咒用完整名字。GameScreen 左面板在天赋/技巧后加「—— 诅咒 ——」（紫红，本局持续）+「—— 状态 ——」（橙，单字+层数，战斗临时），空则不显示。

## 地图
- 一章 8 层（1 层固定战斗，2~7 层 2~3 分支，8 层首领），全连通，未做路线互斥。
- 权重 38/22/20/20（战斗/篝火/宝箱/精英）。同层不许重复→重抽→BATTLE 变相加权，压到这组才得 51% 战斗。
- `battleTest` 有 1000 局分布统计 + 非战斗占比断言（25%~40%）。
- 精英/首领都是 `SlimeKing`（四步循环，蓄力是唯一决策点，意图提前一回合可见）。首领 95 血/4 防/8·10·18，实测 9 回合剩 30/80 通关。
- 非战斗节点（篝火 30% 回血/宝箱最大生命+5）不切屏，MapScreen 弹结算框。节点框只有一行类型名。

## 角色
- `CharacterData` 数据驱动（name/desc/面板/初始天赋/初始技能/avatarPath/backgroundPath），UI 不硬编码，加角色只补数据。
- 归尘（狂战士，血之泪）、空梦（镜像，欢愉之礼/镜中人/倒映）、降临者（DESCENDER，捏脸 10 点自由属性，初始天赋原地复活一次）。
- **降临者不在 `CharacterData.ALL`**，`of()` 单独兜底；`CharacterSelectScreen` 构造时拼成 `ALL + DESCENDER` 统一渲染/点选。
- 选人界面：头像 64×64（8×8 整数倍）、从左往右一排、每行最多 6 个（`PER_ROW=6`）超行换行；选中反馈=放大（不画框）；右侧详情面板；底部实心确认按钮；降临者确认进捏脸、其余进地图（`select(CharacterData)` 按类型分流）。
- **降临者捏脸（CustomCharacterScreen）**：10 点自由属性（力/准/防），**纯鼠标点 [-] [+] 分配**（无键盘加减）；顶部名字栏（Swing `JOptionPane.showInputDialog`，支持中文）+ 头像栏（`JFileChooser` 选本地图，Nearest 加载）；确认时 `player.name` 覆盖 + `game.setAvatar(DESCENDER,纹理)`。
- **注意**：`Gdx.input.getTextInput` 在 lwjgl3 桌面端不可用，改名字/输文字一律用 Swing `JOptionPane`。界面层名字一律读 `run.player.name`，**禁止硬编码「归尘」**（MapScreen 顶栏曾踩坑）。点击矩形要在 render 末尾（矩形 set 之后）处理输入。
- **属性加点全游戏只允许鼠标**（ResultScreen ATTRIBUTE + 降临者捏脸），去掉 NUM/QWER/A/D/W/S 键盘加减。
- **事件 Effect 返回 boolean**（成功 true / 条件不满足 false）：邪教徒「粗略看看」花 10 金币离开（金币不足 false，MapScreen 显示提示保持弹窗）；祭坛「摸索周边」+1 属性点。
- **事件整局只遇一次**：`RunState.seenEvents` 按标题记，`rollEvent()` 从未遇过池抽、抽中即标记、穷尽返回 null（MapScreen 降级成宝箱奖励）。
- **废弃营地事件（0.2.8 改造）**：仔细搜刮 +20 金币 30% 触发帐篷主人战斗（`RunState.eventCombat` 标记，`createEnemies()` 返回 `TentOwner`，`onBattleWin()` 清除）/ 粗略搜刮 +5 金币安全离开。帐篷主人 50血/10防，四步循环 锤击(10+2易伤)→狂乱攻击(7x2)→锤击→愤怒不已(下次x2.5+3易伤)，胜利按普通战斗结算。
- **技能也按角色归属**：`Skill.owner`（null=通用），归尘可替换技能（蓄力/多重射击/血刺）标 GUICHEN、降临者「切割」标 DESCENDER；`rollUnownedReplSkill()` 按角色过滤。

## 调试入口
```bash
ROGUE_SEED=2026 java -jar lwjgl3/build/libs/MyRogueGame-1.0.0.jar   # 固定地图
ROGUE_FLOOR=8   java -jar ...                                        # 首领层开局
```

## 版本管理（必须遵守）
- 版本号 `0.n.m`：0=测试阶段；n=重大版本（新玩法系统）；m=小更新（数值/bug/界面）。n+1 时 m 归 0。
- 每版本一份 `docs/<版本号>.md` 可运行快照（只写"现在是什么"，计划放最后）；文件名纯版本号不改，**文档内部标题写「版本号+内容描述」**。README 有索引表，发版同步。改动很小就直接在前一份文档里调。
- 当前 **0.2.8**（切片一战斗+切片二地图+成长闭环+事件/流派/伤害管线重构+角色选择+敌人预制池+状态诅咒系统/降临者/宝箱怪）。0.3.0 = 经济与事件（商店/金币消费闭环）。

## 待办
- 商店（金币消费闭环）→ 0.3.0。
- 多章节、存档。
- 精英给天赋/普通给技巧的「来源区分」已大部分接上；连击随机 20% 待真机截图。
