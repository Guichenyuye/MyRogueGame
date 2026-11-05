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
- 负面效果 `StatusEffect`（虚弱/易伤/晕眩/中毒/流血 + 0.3.0 增益 REGEN/STRENGTH_UP，战斗临时有层数）+ 诅咒 `Curse`（恶魔抓痕/怠惰/旧疾/贪婪者，本局持续）。给玩家挂负面统一走 `BattleSystem.inflictStatus()`（否则「倒映」不触发）。金币在 `Player.gold`。
- **0.3.0 效果分增益/减益**：`StatusEffect.isBuff` + `priority` + `sortedByPriority()`（增益在前、同阵营按 priority 升序）。战斗界面「效果」栏增益绿、减益红橙。增益：REGEN(复，回合开始回4血)、STRENGTH_UP(力，每次攻击+5，乘区前固定加法，不触发连击/血之泪，吃免伤)。
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
- 归尘（狂战士，血之泪）、空梦（镜像，欢愉之礼/镜中人/倒映）、降临者（DESCENDER，捏脸 10 点自由属性，初始天赋原地复活一次）、**墓渊（MUYUAN，召唤师，60血/力2/准5/防2，初始天赋亡灵项链，贴图 muyuan 前缀）**。
- **降临者不在 `CharacterData.ALL`**，`of()` 单独兜底；`CharacterSelectScreen` 构造时拼成 `ALL + DESCENDER` 统一渲染/点选。
- 选人界面：头像 64×64（8×8 整数倍）、从左往右一排、每行最多 6 个（`PER_ROW=6`）超行换行；选中反馈=放大（不画框）；右侧详情面板；底部实心确认按钮；降临者确认进捏脸、其余进地图（`select(CharacterData)` 按类型分流）。
- **降临者捏脸（CustomCharacterScreen）**：10 点自由属性（力/准/防），**纯鼠标点 [-] [+] 分配**（无键盘加减）；顶部名字栏（Swing `JOptionPane.showInputDialog`，支持中文）+ 头像栏（`JFileChooser` 选本地图，Nearest 加载）；确认时 `player.name` 覆盖 + `game.setAvatar(DESCENDER,纹理)`。
- **注意**：`Gdx.input.getTextInput` 在 lwjgl3 桌面端不可用，改名字/输文字一律用 Swing `JOptionPane`。界面层名字一律读 `run.player.name`，**禁止硬编码「归尘」**（MapScreen 顶栏曾踩坑）。点击矩形要在 render 末尾（矩形 set 之后）处理输入。
- **属性加点全游戏只允许鼠标**（ResultScreen ATTRIBUTE + 降临者捏脸），去掉 NUM/QWER/A/D/W/S 键盘加减。
- **事件 Effect 返回 boolean**（成功 true / 条件不满足 false）：邪教徒「粗略看看」花 10 金币离开（金币不足 false，MapScreen 显示提示保持弹窗）；祭坛「摸索周边」+1 属性点。
- **事件整局只遇一次**：`RunState.seenEvents` 按标题记，`rollEvent()` 从未遇过池抽、抽中即标记、穷尽返回 null（MapScreen 降级成宝箱奖励）。
- **废弃营地事件（0.2.8 改造）**：仔细搜刮 +20 金币 30% 触发帐篷主人战斗（`RunState.eventCombat` 标记，`createEnemies()` 返回 `TentOwner`，`onBattleWin()` 清除）/ 粗略搜刮 +5 金币安全离开。帐篷主人 50血/10防，四步循环 锤击(10+2易伤)→狂乱攻击(7x2)→锤击→愤怒不已(下次x2.5+3易伤)，胜利按普通战斗结算。
- **技能也按角色归属**：`Skill.owner`（null=通用），归尘可替换技能（蓄力/多重射击/血刺）标 GUICHEN、降临者「切割」标 DESCENDER、墓渊「后方支援/死亡激荡」标 MUYUAN；`rollUnownedReplSkill()` 按角色过滤。

## 0.3.0 经济 / 道具 / 召唤物
- **道具** `Item`（7 种药水，`Item.MYSTERY_POOL` 不含百变本身），挂 `Player.items`；`BattleSystem.useItem(index)` 不耗精力不耗行动次数、可连用、用完消失。药水效果全在 `applyItem` switch 分发。
- **商店** `ShopScreen`（纯鼠标，金币不足置灰），**0.3.1 扩充 6 槽**：属性点（15/30/50 递增）、清诅咒（50 起 +15）、随机药水（10~15）、随机战斗技巧（40，`rollUnownedCombatSkill()`）、生命上限+5（35）、随机药水（第二槽）。`Player.attributePurchaseCount`/`curseCleanseCount` 计购买次数。SHOP 节点权重 12，不出现在 1/8 层。
- **召唤物底层** `Minion`（Kind.ZOMBIE/GHOST，血量+固定行动），挂 `Player.minions`；`BattleSystem.minionTurn()` 玩家出手后调度、`minionAttack()` 召唤物伤害（久经 x1.1/诸武精通 x1.2），开战清空不跨战斗。僵尸撕咬x3+重击、幽魂破点/魂穿轮换。`lowestHpEnemy()` 找最低血敌人。
- **墓渊专属天赋**：亡灵项链（战斗结束每召唤物回2血满血提升上限，`onBattleWin` 结算）、亡语（召唤物死对全敌其最大生命20%）、死气勃勃（场上单位死亡回2血）、亡者气质（每场首次受超15伤减半，`deadAuraUsed`）、替死鬼（伤害转移召唤物/无召唤物受击+10%造伤+10%）。
- **宝箱限定奖励**（`resolveTreasure` 最前按概率）：出金了 3%（`critLocked` 暴击锁 30%）、诸武精通 1%（`WEAPON_MASTERY` 全伤害含召唤物 x1.2，owner=null 不进常规池）。
- **新敌人**：侦查骑士 ScoutKnight(60血，20%晕眩用 `BattleSystem.random()`)、新手及时 Novice(50血，怯战逃跑→`battleFled` 金币0经验减半)、老骑士 OldKnight(精英80血)。EnemyPool 4~6层五选一、精英5~7层三选一。
- **新通用技巧**：长期训练(免伤+1%)/聚精会神(暴击+2%)/粗鄙之人(攻击2%晕眩)。**新诅咒贪婪者**（金币<50伤-20%、<100伤-10%）。

## 0.3.1 毒叠加 / 多章节 / 骑士长
- **敌人中毒回合结算**：`BattleSystem.tickEnemyStatusAtTurnStart()` 在敌方回合开始按当前层数扣血→层数-1。所有上毒统一走 `applyPoisonToEnemy(target, stacks)`（老毒物 +1 层在这里生效）。`StatusEffect.addStacks` 本就是层数相加，之前 bug 是敌人毒从不扣血。
- **多章节**：`Chapter` 枚举（SWAMP 沼之森 / SOUTHERN_LAND 南边领地）+ `RunState.currentChapter`。`EnemyPool.create(type, floor, rng, chapter)` 四参（旧三参默认 SWAMP 保留）。**南边领地只出骑士系**（不出史莱姆/骸骨）。章节选择入口 `ChapterSelectScreen`（选完角色后弹，纯鼠标/[1][2]/[ESC]，降临者带 `prebuiltPlayer` 也走这里）。MapScreen 顶栏、GameScreen 结算文案读 `run.currentChapter`。
- **骑士系敌人**（南边领地）：见习骑士 SquireKnight(50血防2，挥剑6→盾击4+3护盾)、冲锋骑士 ChargingKnight(65血防1，长矛突刺8→蓄势冲锋下回合15)、皇家守卫 RoyalGuard(精英90血防4，横扫7→坚守阵地15护盾回5血→破甲击10+2易伤)。
- **敌人护盾**：`Enemy.shield` + `absorbByShield(damage)`（先扣护盾不够再扣血），`calculateDamage`/`minionAttack` 都接入。`Enemy.damageTakenMult` 敌人免伤（骑士长祈祷 0.9）。
- **第二章 Boss 年轻的骑士长 YoungKnightCaptain**（120血防5）：`int[] SEQUENCE` 数组管循环，骑士礼仪开局独立方法 `knightlyCourtesy()`（双方各扣10真实伤害，走 `takeDamage` 无视护盾/免伤）。循环：突刺10→神圣一击20→祈祷回10血+2回合免伤→瞬息连刺8x3→圣洁之心清负面→突刺→圣水洗礼(永久+20%可叠3层，`enchantStacks`/`damageMult()`)。`getIntent()` 按附魔层数实时显示伤害。
- **存档骨架**：`Main.SaveManager` 静态类（`SAVE_FILE`/`enabled()`/`save()`/`load()`），第一版不做本地 IO。
- **老毒物** 通用技巧：自身/召唤物中毒额外+1层。

## 0.3.2 敌人状态 / 悬停详情 / 路线规划
- **敌人状态栏**：`GameScreen.drawEnemyBlock` 意图下方加 `drawEnemyStatus()`（增益绿/减益红橙/带层数，合成一行，逐段着色）。单敌人 roomy + 多敌人紧凑布局都显示。
- **悬停详情（Hover Tooltip）**：`Ui.drawTooltip(text, mouseX, mouseY, maxWidth)` 半透明黑底白字浮窗、自动换行、越界翻转、画最上层。`GameScreen.tryHover(desc, x, y, w)` 用 Gdx.input 坐标对每行文字做 Rectangle 命中。面板极简化：天赋/技巧/诅咒/效果/召唤物/技能槽/道具只显示名字（道具显示首字），desc 走 tooltip。
- **道具栏重叠修复**：「道具」标题单独占最左空间，道具框紧随其后，不再叠字。
- **墓渊精英奖励修复**：`rollUnownedReplSkill()` 替换池补全 `SUMMON_GHOST`/`DEATH_SURGE`（原来只硬编码归尘+降临者 4 技能，墓渊抽不到替换技能）。兜底：天赋+技能池都空转 1 属性点（`maybeEliteRewardOrAttribute`）。
- **路线规划（节点连线）**：`MapNode.connections`（List<MapNode> 前向连接）。`GameMap.connectLayers()` 逐层建上一→下一单向连接（比例映射 j=i*b/a 连 j 和 j+1），保证第1层通到Boss、无死胡同、无孤立。`RunState.currentNode` 记录当前停留节点，`options()` 返回 `currentNode.connections`（不再返回整层），`markVisited()` 里 currentNode=pending。`MapScreen.drawMap` 画连线（当前节点出发亮色加粗、其余暗色），`drawNode` 只高亮 selectable 节点、其余置灰，`*` 标记当前所在。
- 测试 325→**337 项全绿**。

## 调试入口
```bash
ROGUE_SEED=2026 java -jar lwjgl3/build/libs/MyRogueGame-1.0.0.jar   # 固定地图
ROGUE_FLOOR=8   java -jar ...                                        # 首领层开局
```

## 版本管理（必须遵守）
- 版本号 `0.n.m`：0=测试阶段；n=重大版本（新玩法系统）；m=小更新（数值/bug/界面）。n+1 时 m 归 0。
- 每版本一份 `docs/<版本号>.md` 可运行快照（只写"现在是什么"，计划放最后）；文件名纯版本号不改，**文档内部标题写「版本号+内容描述」**。README 有索引表，发版同步。改动很小就直接在前一份文档里调。
- 当前 **0.3.2**（敌人状态栏 + 悬停详情 + 道具栏修复 + 墓渊奖励修复 + 路线规划节点连线 + 5 项遗留修复）。下一版 = 悬停浮窗视觉调优 + 路线规划进阶（网格/单行道/分支互斥）+ 存档 IO + 骑士长真机实打。

## 0.3.2 遗留修复（实机反馈，务必记住的坑）
- **敌人中毒/流血致死不判胜**：`enemyTurn()` 里毒发扣死敌人后必须补 `allEnemiesDead()` 判 VICTORY，否则 state 停在 PLAYER_TURN 却无活敌，玩家「卡死」。`useItem()` 同理兜底判胜。
- **路线连线孤立节点**：`connectLayers()` 第 1 层只有一个节点（a=1）时必须扇出到下一层**所有**节点，否则下一层靠后节点无前驱、变灰到不了。a>1 用 `j=i*(b-1)/(a-1)` 均匀分布 + 扇出 j/j+1。
- **开局 currentNode 必须为 null**：若开局就设成第 1 层节点，`options()` 返回其 connections（第 2 层），玩家会跳过第 1 层强制战斗。`options()` 在 currentNode==null 时退回返回第 1 层节点。
- **一次性标记要消费掉**：`treasureMimic`（宝箱怪）在 `onBattleWin()` 末尾必须 `=false`，否则下一场普通战斗继续出宝箱怪；`eventCombat`、`battleFled` 同理。
- **MapScreen 鼠标点选节点**：记录可选节点 Rectangle（`selectableRects`），点击进入；键盘数字键保留。
- **空战斗拦截（防御性）**：`createEnemies()` 返回前必须检查列表为空则 `System.err` 告警 + 兜底 `new Slime()`，绝不能「空怪胜利」——那会让玩家放技能即 VICTORY 而节点未正确推进，地图原地死循环。
- **options() 空列表兜底（防御性）**：`currentNode.connections.isEmpty()` 时强制返回 `nodesOn(currentNode.floor + 1)`（clamp 到 floorCount）+ 告警，绝不返回空让玩家无路可走。正常 connectLayers 保证非空，此兜底纯防御。
- **连线职责别错位**：连线只在 `connectLayers()` 生成阶段建，**不要在 `onBattleWin()` 结算阶段动态补连线**；空列表兜底统一只在 `createEnemies()` 一处，**不要**在 EnemyPool 里 throw + 外层 catch（拆两处+异常控制流是画蛇添足）。connectLayers 现有 `j=i*(b-1)/(a-1)` 扇出算法已 2000 地图验证零死胡同，勿换成区间映射算法。

## 待办
- 悬停浮窗真机视觉调优（字体/边距/与鼠标相对位置）。
- 路线规划进阶：网格式随机路径、故意设计的单行道、分支互斥（选 A 则 B 消失）。
- 多章节「章节顺序推进」（当前是开局手动选章节测试第二章）。
- 存档系统实际 IO 落地（`SaveManager` 目前是空骨架）。
- 墓渊召唤物真机手感调优（召唤物强弱）。
- 骑士长真机实打测试（3 段连刺 + 圣水洗礼压迫感）。
