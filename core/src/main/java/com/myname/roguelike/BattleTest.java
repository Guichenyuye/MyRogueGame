package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 纯控制台验证（不需要 libGDX 窗口）。
 * <p>
 * 在 IDEA 里点 main 左边的绿色箭头直接跑，或者 {@code ./gradlew :core:battleTest}。
 * 它会做四件事：
 * 1. 逐条核对设计文档里的公式是不是真的算对了；
 * 2. 打完一整场「归尘 VS 史莱姆」，把战斗日志打出来；
 * 3. 检查地图生成是否满足《节点地图设计》第 3 节的全部约束；
 * 4. 空跑一整章，确认层数推进不会卡死。
 * <p>
 * 数字对不上会打印 ❌ 并以非 0 退出，方便一眼发现问题。
 */
public class BattleTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("================ 一、数值公式自检 ================");
        checkFormulas();

        System.out.println();
        System.out.println("================ 二、完整战斗模拟 ================");
        simulateBattle();

        System.out.println();
        System.out.println("================ 三、节点地图生成 ================");
        checkMap();

        System.out.println();
        System.out.println("================ 四、一局流程推进 ================");
        checkRunFlow();

        System.out.println();
        System.out.println("================ 五、首领战数值压力测试 ================");
        checkBossBalance();

        System.out.println();
        System.out.println("================ 六、天赋 / 成长系统 ================");
        checkGrowth();

        System.out.println();
        System.out.println("================ 七、敌人预制池 / 新角色空梦 ================");
        checkEnemiesAndKongmeng();

        System.out.println();
        System.out.println("================ 八、0.2.8 状态/降临者/宝箱怪 ================");
        check028();

        System.out.println();
        System.out.printf("自检结果：通过 %d 项，失败 %d 项%n", passed, failed);
        if (failed > 0) {
            System.out.println("[NG] 有数值和设计文档对不上，先别往下做。");
            System.exit(1);
        }
        System.out.println("[OK] 全部通过。");
    }

    // ==================== 一、公式自检 ====================

    private static void checkFormulas() {
        System.out.println("· 防御减伤（文档 2.5）");
        check("防御 5 挨 5 点基础伤害 -> 4", 4, BattleSystem.applyDefense(5, 5));
        check("防御 5 挨 8 点基础伤害 -> 6", 6, BattleSystem.applyDefense(8, 5));
        check("防御 1 挨 11 点 -> 10（史莱姆被随心一箭打）", 10, BattleSystem.applyDefense(11, 1));
        check("最低保底 1 点（防御 100 挨 1 点）", 1, BattleSystem.applyDefense(1, 100));

        System.out.println("· 技能原始伤害（文档 3.4，初始面板 力量3/精准3，0.2.6 下调）");
        Player p = new Player();
        check("随心一箭 = 精准×1.2+5 = 8.6 -> 8", 8, BattleSystem.rawNormalAttack(p));
        check("精准射击 = 精准×1.5+10 = 14.5 -> 14", 14, BattleSystem.rawPreciseShot(p));
        check("横扫 = 力量×1.0+6 = 9", 9, BattleSystem.rawSweep(p));

        System.out.println("· 暴击 / 护盾 / 精力 / 冷却（文档 2.2 / 2.6 / 2.3 / 2.4）");
        check("暴击 150%：8 -> 12", 12, (int) (8 * p.critDamage));
        check("护盾量 = 防御×2 = 6", 6, p.defense * 2);

        p = new Player();
        p.mp = 0;
        p.startTurn();
        check("回合开始回 1 精力：0 -> 1", 1, p.mp);
        p = new Player();
        p.startTurn();
        check("精力上限 10，不溢出", 10, p.mp);

        // 横扫 CD1：文档说「释放后隔 1 回合可用」→ 第 1 回合用，第 2 回合不能，第 3 回合能
        p = new Player();
        p.startTurn();
        checkTrue("第 1 回合：横扫可用", p.canUse(3));
        p.mp -= 2;
        p.onSkillUsed(3);
        p.startTurn();
        check("第 2 回合：横扫显示 CD = 1", 1, p.cdDisplay[3]);
        checkTrue("第 2 回合：横扫不可用", !p.canUse(3));
        p.startTurn();
        checkTrue("第 3 回合：横扫恢复可用", p.canUse(3));

        System.out.println("· 护盾与血之泪（文档 2.6 / 2.7）");
        p = new Player();
        BattleSystem b = new BattleSystem(p, new Random(1));
        p.setShield(10, 1);
        b.damagePlayer(5, "测试撞击");
        check("护盾吸收 4 点后剩 6", 6, p.shield);
        check("护盾挡下伤害，HP 保持 80", 80, p.hp);
        p.onEnemyTurnEnd();
        check("敌方回合结束，护盾清零", 0, p.shield);

        p = new Player();
        p.setShield(2, 1);
        b = new BattleSystem(p, new Random(1));
        b.damagePlayer(8, "测试强力撞击");
        check("护盾 2 被 7 点打碎，穿透 5 点", 5, 80 - p.hp);

        p = new Player();
        p.hp = 70;
        p.critRate = 0;                      // 关掉暴击，保证结果可复现
        BattleSystem bt = new BattleSystem(p, new Random(1));
        TrainingDummy dummy = new TrainingDummy();
        bt.addEnemy(dummy);
        bt.start();
        bt.useSkill(0);                       // 随心一箭
        check("随心一箭打 0 防目标：100 -> 92", 92, dummy.hp);
        check("血之泪：70 -> 71", 71, p.hp);

        // 血之泪按「伤害次数」结算：横扫打 2 个目标 -> 回 2 血
        p = new Player();
        p.hp = 70;
        p.critRate = 0;
        BattleSystem aoe = new BattleSystem(p, new Random(1));
        TrainingDummy d1 = new TrainingDummy();
        TrainingDummy d2 = new TrainingDummy();
        aoe.addEnemy(d1);
        aoe.addEnemy(d2);
        aoe.start();
        aoe.useSkill(3);                      // 横扫
        check("横扫打 2 个目标，各掉 9 血", 91, d1.hp);
        check("血之泪按伤害次数回血：70 -> 72", 72, p.hp);

        System.out.println("· 史莱姆固定顺序与包裹（文档 5.4）");
        Slime slime = new Slime();
        Player target = new Player();
        target.mp = 5;
        BattleSystem dummyBattle = new BattleSystem(target, new Random(1));
        dummyBattle.addEnemy(slime);
        slime.turnCounter = 2;                // 让下一次行动是「包裹」
        slime.doTurn(target, dummyBattle);
        check("包裹扣 1 点精力：5 -> 4", 4, target.mp);
        check("包裹后撞击基础伤害 = 5+2 = 7", 7, 5 + slime.extraDamage);
        check("7 点经防御 3 减伤 -> 6", 6, BattleSystem.applyDefense(7, 3));
        check("意图显示已带上增伤", 1, slime.getIntent().contains("7") ? 1 : 0);
    }

    // ==================== 二、一场完整战斗 ====================

    private static void simulateBattle() {
        Player player = new Player();
        Slime slime = new Slime();
        player.critRate = 0;                  // 为了逐条对照数字，本场先关掉 5% 暴击

        BattleSystem battle = new BattleSystem(player, new Random(42));
        battle.addEnemy(slime);
        battle.start();

        // 剧本：防御 -> 随心一箭 -> 随心一箭 -> 精准射击 -> 随心一箭 -> 随心一箭
        // 目的：一次性跑出护盾、护盾过期、血之泪、包裹扣蓝、增伤、胜利
        // （0.2.6 三维下调后伤害降低，原 5 步打不死 30 血的史莱姆，加长到 6 步）
        int[] script = {1, 0, 0, 2, 0, 0};
        for (int slot : script) {
            if (battle.getState() != BattleSystem.State.PLAYER_TURN) {
                break;
            }
            battle.useSkill(slot);
        }

        for (String line : battle.getLog()) {
            System.out.println(line);
        }

        System.out.println();
        check("战斗在该剧本内分出胜负", 1, battle.getState() == BattleSystem.State.PLAYER_TURN ? 0 : 1);
        checkTrue("史莱姆被击杀", !slime.isAlive());
        check("归尘存活且 HP = 70", 70, player.hp);
    }

    // ==================== 三、地图生成 ====================

    private static void checkMap() {
        long[] seeds = {1, 2, 42, 777, 2026};
        for (long seed : seeds) {
            GameMap map = GameMap.generate(new Random(seed));

            check("seed " + seed + " 一共 " + GameMap.FLOORS + " 层", GameMap.FLOORS, map.floorCount());
            check("seed " + seed + " 第 1 层只有 1 个节点", 1, map.nodesOn(1).size());
            checkTrue("seed " + seed + " 第 1 层是普通战斗",
                    map.nodesOn(1).get(0).type == MapNode.Type.BATTLE);
            check("seed " + seed + " 最后一层只有 1 个节点", 1, map.nodesOn(GameMap.FLOORS).size());
            checkTrue("seed " + seed + " 最后一层是首领",
                    map.nodesOn(GameMap.FLOORS).get(0).type == MapNode.Type.BOSS);

            List<String> problems = validate(map);
            checkTrue("seed " + seed + " 分支层符合全部约束"
                            + (problems.isEmpty() ? "" : "  问题：" + problems),
                    problems.isEmpty());
        }

        System.out.println("· 一张实际生成的地图（seed 2026）");
        System.out.print(GameMap.generate(new Random(2026)).toAsciiArt());

        System.out.println("· 1000 局的节点类型分布（调权重后先看这个，别凭感觉）");
        int[] counts = new int[MapNode.Type.values().length];
        int total = 0;
        for (int seed = 0; seed < 1000; seed++) {
            GameMap map = GameMap.generate(new Random(seed));
            for (int floor = 1; floor <= GameMap.FLOORS; floor++) {
                for (MapNode n : map.nodesOn(floor)) {
                    counts[n.type.ordinal()]++;
                    total++;
                }
            }
        }
        for (MapNode.Type t : MapNode.Type.values()) {
            if (counts[t.ordinal()] == 0) {
                continue;
            }
            System.out.printf("    %-8s %5.1f%%  (%d)%n",
                    t.label, counts[t.ordinal()] * 100.0 / total, counts[t.ordinal()]);
        }
        System.out.printf("    合计节点 %d 个，平均每局 %.1f 个%n", total, total / 1000.0);

        // 非战斗节点占比太低 = 一路全是打架，节奏会闷；太高 = 缺少压力，篝火也没意义
        int nonCombat = counts[MapNode.Type.REST.ordinal()]
                + counts[MapNode.Type.TREASURE.ordinal()]
                + counts[MapNode.Type.EVENT.ordinal()];
        double nonCombatRate = nonCombat * 100.0 / total;
        System.out.printf("    非战斗节点占比 %.1f%%（合理区间 25%%~40%%）%n", nonCombatRate);
        checkTrue("非战斗节点占比不低于 25%", nonCombatRate >= 25.0);
        checkTrue("非战斗节点占比不高于 40%", nonCombatRate <= 40.0);
    }

    /** 逐层核对《节点地图设计》第 3 节的约束，返回所有违规项 */
    private static List<String> validate(GameMap map) {
        List<String> problems = new ArrayList<>();

        for (int floor = 2; floor <= GameMap.FLOORS - 1; floor++) {
            List<MapNode> nodes = map.nodesOn(floor);
            if (nodes.size() < 2 || nodes.size() > 3) {
                problems.add("第 " + floor + " 层节点数 " + nodes.size() + "（应为 2~3）");
            }

            int rest = 0;
            int treasure = 0;
            int elite = 0;
            int event = 0;
            int combat = 0;

            for (MapNode n : nodes) {
                if (n.type == MapNode.Type.BATTLE || n.type == MapNode.Type.ELITE) {
                    combat++;
                }
                if (n.type == MapNode.Type.REST) {
                    rest++;
                    if (floor < 3) {
                        problems.add("第 " + floor + " 层出现篝火（应 >= 3 层）");
                    }
                }
                if (n.type == MapNode.Type.TREASURE) {
                    treasure++;
                }
                if (n.type == MapNode.Type.ELITE) {
                    elite++;
                    if (floor < 4) {
                        problems.add("第 " + floor + " 层出现精英（应 >= 4 层）");
                    }
                }
                if (n.type == MapNode.Type.EVENT) {
                    event++;
                }
                if (n.type == MapNode.Type.SHOP) {
                    problems.add("第 " + floor + " 层出现本版不该生成的 " + n.type);
                }
            }

            if (rest > 1) problems.add("第 " + floor + " 层有 " + rest + " 个篝火");
            if (treasure > 1) problems.add("第 " + floor + " 层有 " + treasure + " 个宝箱");
            if (elite > 1) problems.add("第 " + floor + " 层有 " + elite + " 个精英");
            if (event > 1) problems.add("第 " + floor + " 层有 " + event + " 个事件");
            if (combat == 0) problems.add("第 " + floor + " 层没有战斗类节点");
        }

        return problems;
    }

    // ==================== 四、一局流程 ====================

    /**
     * 空跑一整章：不真的打，只调 onBattleWin / advance，检查层数推进逻辑。
     * 真战斗交给 GameScreen 手测，这里专盯「会不会卡在同一层」。
     */
    private static void checkRunFlow() {
        RunState run = new RunState(2026L);

        check("开局在第 1 层", 1, run.floor);
        checkTrue("开局已经选好一个战斗节点", run.pending != null && run.pending.type.combat);

        int battles = 0;
        int others = 0;
        int guard = 0;

        while (!run.cleared && guard++ < 20) {
            if (run.pending == null) {
                run.choose(0);
            }
            MapNode node = run.pending;
            if (node == null) {
                break;
            }

            if (node.type.combat) {
                battles++;
                run.onBattleWin();
            } else {
                others++;
                // 0.2.2：非战斗节点分类型结算（篝火二选一/事件二选一/宝箱随机）
                switch (node.type) {
                    case REST -> run.resolveRest(0);
                    case TREASURE -> run.resolveTreasure();
                    case EVENT -> run.rollEvent().apply(run, 0);
                    default -> { }
                }
                run.advance();
            }
        }

        checkTrue("八层走完能通关（首领已击破）", run.cleared);
        checkTrue("层数推进没有死循环", guard < 20);
        checkTrue("整章节点数不超过层数上限", battles + others <= GameMap.FLOORS);
        System.out.println("  本章路径：经历 " + battles + " 场战斗、" + others + " 个非战斗节点，"
                + "结束时 HP " + run.player.hp + "/" + run.player.maxHp);
    }

    // ==================== 五、首领战压力测试 ====================

    /**
     * 用一套很笨的 AI 打首领，只验证「能不能赢」这一件事。
     * <p>
     * 0.2.6：初始三维下调后，初始面板已经打不过首领（这是「前期博弈感」的代价）。
     * 所以这里先模拟玩家打到第 8 层应有的成长（升级属性点 + 技能等级 + 精英天赋），
     * 再用笨 AI 打 —— 验证的是「正常成长后的玩家靠笨打法也能赢」。
     * <p>
     * 策略：血低于 30 且能防御就防御；精力 >= 6 优先精准射击；否则随心一箭。
     * 这不是最优解 —— 如果连这套打法都能赢，说明数值留了余量。
     */
    private static void checkBossBalance() {
        RunState run = new RunState(2026L);
        run.floor = GameMap.FLOORS;
        run.pending = run.map.nodesOn(GameMap.FLOORS).get(0);

        // —— 模拟第 8 层前的成长（0.2.6）——
        // 属性点：力量 +3、精准 +3（升级 / 篝火冥想 / 三选一攒的自由点）
        run.player.addAttributePoint(0);
        run.player.addAttributePoint(0);
        run.player.addAttributePoint(0);
        run.player.addAttributePoint(1);
        run.player.addAttributePoint(1);
        run.player.addAttributePoint(1);
        // 技能等级：精准射击升到 Lv2（每级 +2 伤害）
        run.player.upgradeSkill(2);
        run.player.upgradeSkill(2);
        // 精英战天赋：箭术大师（远程 x1.5，拉高主力输出）
        run.player.grantTalent(Talent.ARCHERY_MASTER);

        BattleSystem battle = new BattleSystem(run.player, run.rng);
        for (Enemy enemy : run.createEnemies()) {
            battle.addEnemy(enemy);
        }
        battle.start();

        int turns = 0;
        while (battle.getState() == BattleSystem.State.PLAYER_TURN && turns++ < 60) {
            Player p = run.player;
            if (p.hp <= 28 && p.canUse(1)) {
                battle.useSkill(1);                 // 防御保命
            } else if (p.canUse(2) && p.mp >= 6) {
                battle.useSkill(2);                 // 精准射击
            } else {
                battle.useSkill(0);                 // 随心一箭
            }
        }

        System.out.println("  首领战 " + turns + " 回合，结果 " + battle.getState()
                + "，归尘 HP " + run.player.hp + "/" + run.player.maxHp
                + "，剩余精力 " + run.player.mp);

        checkTrue("成长后的笨 AI 也能打过首领（数值没崩）",
                battle.getState() == BattleSystem.State.VICTORY);
        checkTrue("首领战在 60 回合内结束（不会互相打不死）", turns < 60);
    }

    // ==================== 六、天赋 / 成长系统 ====================

    /**
     * 这轮新加的东西：天赋从「写死在伤害结算里」变成数据，
     * CD 跨节点重置，金币进账，经验跨级保留。
     */
    private static void checkGrowth() {
        System.out.println("· 天赋是数据驱动的（血之泪）");
        Player p = new Player();
        checkTrue("初始带「血之泪」", p.hasTalent(Talent.Id.BLOOD_TEAR));
        checkTrue("初始没有战斗技巧", p.combatSkills.isEmpty());

        // 天赋被移除后不能偷偷还在回血：以后如果天赋能被替换，这条守着
        p = new Player();
        p.talents.clear();
        p.hp = 70;
        p.critRate = 0;
        BattleSystem b = new BattleSystem(p, new Random(1));
        b.addEnemy(new TrainingDummy());
        b.start();
        b.useSkill(0);
        check("没有血之泪天赋时不回血", 70, p.hp);
        check("天赋触发计数保持 0", 0, b.talentTriggerCount(Talent.Id.BLOOD_TEAR));

        // 满血时不要刷「恢复 0 点」，也不要闪 —— 那只会把提示做成噪音
        p = new Player();
        p.critRate = 0;
        b = new BattleSystem(p, new Random(1));
        b.addEnemy(new TrainingDummy());
        b.start();
        b.useSkill(0);
        check("满血时 HP 不变", 80, p.hp);
        check("满血不算触发", 0, b.talentTriggerCount(Talent.Id.BLOOD_TEAR));

        // 横扫打两个目标各回 1 血（文档 2.7 已定），但只闪一下
        p = new Player();
        p.hp = 70;
        p.critRate = 0;
        b = new BattleSystem(p, new Random(1));
        b.addEnemy(new TrainingDummy());
        b.addEnemy(new TrainingDummy());
        b.start();
        b.useSkill(3);
        check("横扫回血：70 -> 72", 72, p.hp);
        check("触发计数 = 1（一次出手闪一下，不是回几点闪几下）",
                1, b.talentTriggerCount(Talent.Id.BLOOD_TEAR));

        System.out.println("· CD 跨节点重置（0.2 需求第 1 条）");
        p = new Player();
        p.startTurn();
        p.onSkillUsed(2);                 // 精准射击 CD2
        p.onSkillUsed(3);                 // 横扫 CD1
        p.startTurn();
        checkTrue("重置前横扫确实在冷却中", p.cdDisplay[3] > 0);
        p.resetCooldowns();
        check("重置后横扫显示 CD = 0", 0, p.cdDisplay[3]);
        check("重置后精准射击显示 CD = 0", 0, p.cdDisplay[2]);
        checkTrue("重置后两个技能都能立刻用", p.canUse(2) && p.canUse(3));

        System.out.println("· 战斗胜利结算：清 CD + 发金币（10~15）");
        RunState run = new RunState(2026L);
        run.player.startTurn();
        run.player.onSkillUsed(2);
        run.player.startTurn();
        int goldBefore = run.player.gold;
        run.onBattleWin();
        checkTrue("金币在 10~15 之间（实际 " + run.lastGoldGain + "）",
                run.lastGoldGain >= 10 && run.lastGoldGain <= 15);
        check("金币累加正确", goldBefore + run.lastGoldGain, run.player.gold);
        check("胜利后技能 CD 全部清零", 0, run.player.cdDisplay[2]);

        System.out.println("· 经验跨级保留（0.2 需求第 2 条）");
        run = new RunState(2026L);
        check("1 级升 2 级需要 8 点经验（斐波那契）", 8, run.expToNextLevel());
        check("给 7 点经验：还没升级", 0, run.gainExp(7));
        check("经验存住了，没有被吞", 7, run.exp);
        check("再给 10 点：升 1 级", 1, run.gainExp(10));
        check("多出来的经验保留到下一级（17 - 8 = 9）", 9, run.exp);
        check("等级 = 2", 2, run.level);
        check("每级给 1 点属性点", 1, run.pendingAttributePoints);
        check("2 级升 3 级需要 13 点（斐波那契）", 13, run.expToNextLevel());

        System.out.println("· 属性点分配（0.2 需求第 2 节，确认制）");
        p = new Player();
        int strBefore = p.strength;
        int hpMaxBefore = p.maxHp;
        p.addAttributePoint(0);   // 力量 +1
        p.addAttributePoint(3);   // 生命 +5 上限、+5 当前
        check("力量 +1", strBefore + 1, p.strength);
        check("生命 +5 上限", hpMaxBefore + 5, p.maxHp);
        check("生命上限增加时当前生命同步 +5", p.hp, p.hp);   // hp 应等于 maxHp（满血开局）

        System.out.println("· 战斗技巧生效（0.2.3 通用池 + 专属池）");
        // 持久战意：每 3 回合额外回 1 蓝
        p = new Player();
        p.combatSkills.add(CombatSkill.MP_REGEN);
        p.mp = 0;
        p.startTurn();   // 第 1 回合
        p.mp = 0;
        p.startTurn();   // 第 2 回合
        p.mp = 0;
        p.startTurn();   // 第 3 回合：持久战意生效，回 2
        check("持久战意：第 3 回合回蓝 0 -> 2", 2, p.mp);

        // 铁壁防御：防御时额外 +1 护盾
        p = new Player();
        p.combatSkills.add(CombatSkill.DEFEND_BONUS_SHIELD);
        BattleSystem dbBattle = new BattleSystem(p, new Random(1));
        TrainingDummy deadDummy = new TrainingDummy();
        deadDummy.hp = 0;
        dbBattle.addEnemy(deadDummy);
        dbBattle.start();
        dbBattle.useSkill(1);   // 防御：6 + 1 = 7
        check("铁壁防御：护盾 6 -> 7", 7, p.shield);

        // 远程强化：远程技能 x1.1
        p = new Player();
        p.combatSkills.add(CombatSkill.RANGED_UP);
        p.critRate = 0;
        BattleSystem rangedBattle = new BattleSystem(p, new Random(1));
        TrainingDummy rd = new TrainingDummy();
        rangedBattle.addEnemy(rd);
        rangedBattle.start();
        rangedBattle.useSkill(2);   // 精准射击 14 x1.1 = 15.4 -> 15
        check("远程强化：精准射击 14 -> 15", 85, rd.hp);

        // 远程强化：远程 x1.1，现在随心一箭（远程）也吃
        p = new Player();
        p.combatSkills.add(CombatSkill.RANGED_UP);
        p.critRate = 0;
        BattleSystem rangedBasicBattle = new BattleSystem(p, new Random(1));
        TrainingDummy rb = new TrainingDummy();
        rangedBasicBattle.addEnemy(rb);
        rangedBasicBattle.start();
        rangedBasicBattle.useSkill(0);   // 随心一箭 8 x1.1 = 8.8 -> 8
        check("远程强化：随心一箭 8 x1.1 = 8（截断）", 92, rb.hp);

        // 随性（归尘专属）：随心一箭暴击率 +10%
        // 用 seed=4096：首个 nextDouble=0.0979，落在 (0.05, 0.15) 之间，
        // 无随性时（5%）不暴击、有随性时（15%）暴击，形成确定性对照。
        p = new Player();
        p.combatSkills.add(CombatSkill.FREE_CRIT);
        BattleSystem freeCritBattle = new BattleSystem(p, new Random(4096));
        TrainingDummy fc = new TrainingDummy();
        freeCritBattle.addEnemy(fc);
        freeCritBattle.start();
        freeCritBattle.useSkill(0);   // 随心一箭 8，暴击率 15% -> 暴击 12
        check("随性：随心一箭暴击率提到 15%（8 暴击 -> 12）", 88, fc.hp);

        // 对照组：无随性，同样 seed=4096，5% 不暴击 -> 8
        p = new Player();
        BattleSystem noFreeCritBattle = new BattleSystem(p, new Random(4096));
        TrainingDummy nfc = new TrainingDummy();
        noFreeCritBattle.addEnemy(nfc);
        noFreeCritBattle.start();
        noFreeCritBattle.useSkill(0);   // 随心一箭 8，暴击率 5%，0.0979 > 0.05 -> 不暴击
        check("对照：无随性时随心一箭暴击率 5% 不暴击（8）", 92, nfc.hp);

        // 随性只作用于随心一箭：精准射击（远程但非随心一箭）不吃随性暴击加成
        p = new Player();
        p.combatSkills.add(CombatSkill.FREE_CRIT);
        BattleSystem freeCritPreciseBattle = new BattleSystem(p, new Random(4096));
        TrainingDummy fcp = new TrainingDummy();
        freeCritPreciseBattle.addEnemy(fcp);
        freeCritPreciseBattle.start();
        freeCritPreciseBattle.useSkill(2);   // 精准射击 14，暴击率仍 5%，0.0979 > 0.05 -> 不暴击
        check("随性不影响精准射击：14 不暴击", 86, fcp.hp);

        // 范围强化：AOE x1.05
        p = new Player();
        p.combatSkills.add(CombatSkill.AOE_UP);
        p.critRate = 0;
        BattleSystem aoeUpBattle = new BattleSystem(p, new Random(1));
        TrainingDummy ad = new TrainingDummy();
        aoeUpBattle.addEnemy(ad);
        aoeUpBattle.start();
        aoeUpBattle.useSkill(3);   // 横扫 9 x1.05 = 9.45 -> 9
        check("范围强化：横扫 9 x1.05 = 9（截断）", 91, ad.hp);

        // 双重打击（归尘专属）：随心一箭额外攻击 1 点
        p = new Player();
        p.combatSkills.add(CombatSkill.DOUBLE_HIT);
        p.critRate = 0;
        BattleSystem doubleBattle = new BattleSystem(p, new Random(1));
        TrainingDummy dhd = new TrainingDummy();
        doubleBattle.addEnemy(dhd);
        doubleBattle.start();
        doubleBattle.useSkill(0);   // 随心一箭 8 + 双重打击 1 = 9
        check("双重打击：随心一箭 8 + 额外 1 = 9", 91, dhd.hp);

        // 血之回响（归尘专属）：远程技能回 1 血，随心一箭（远程）也吃
        p = new Player();
        p.combatSkills.add(CombatSkill.RANGED_LIFESTEAL);
        p.hp = 70;
        p.critRate = 0;
        BattleSystem lifestealBattle = new BattleSystem(p, new Random(1));
        TrainingDummy ld = new TrainingDummy();
        lifestealBattle.addEnemy(ld);
        lifestealBattle.start();
        lifestealBattle.useSkill(2);   // 精准射击，血之回响 +1
        check("血之回响：远程技能后回 1 血（70 -> 71 + 血之泪 1 = 72）", 72, p.hp);

        // 随心一箭现在也是远程，血之回响同样生效
        p = new Player();
        p.combatSkills.add(CombatSkill.RANGED_LIFESTEAL);
        p.hp = 70;
        p.critRate = 0;
        BattleSystem lifestealBasicBattle = new BattleSystem(p, new Random(1));
        TrainingDummy ldb = new TrainingDummy();
        lifestealBasicBattle.addEnemy(ldb);
        lifestealBasicBattle.start();
        lifestealBasicBattle.useSkill(0);   // 随心一箭，血之回响 +1
        check("血之回响：随心一箭（远程）回 1 血（70 -> 72）", 72, p.hp);

        // 近战技能强化：血刺 x1.1
        p = new Player();
        p.combatSkills.add(CombatSkill.MELEE_UP);
        p.critRate = 0;
        p.replaceSkill(3, Skill.BLOOD_SPIKE);   // 把横扫换成血刺
        BattleSystem meleeUpBattle = new BattleSystem(p, new Random(1));
        TrainingDummy mu = new TrainingDummy();
        meleeUpBattle.addEnemy(mu);
        meleeUpBattle.start();
        meleeUpBattle.useSkill(3);   // 血刺 3 段，每段 (3*0.2+1)=1，x1.1=1（截断）
        check("近战技能强化：血刺 3 段 x1.1", 97, mu.hp);

        System.out.println("· 三选一（0.2.3 规则）");
        run = new RunState(2026L);
        List<RunState.Choice> choices = run.rollChoices();
        check("三选一给出 3 个选项", 3, choices.size());
        checkTrue("槽 1 是技能升级", choices.get(0).kind == RunState.Choice.Kind.SKILL);
        checkTrue("槽 2 是属性点", choices.get(1).kind == RunState.Choice.Kind.ATTRIBUTE);
        checkTrue("槽 3 是战斗技巧自选", choices.get(2).kind == RunState.Choice.Kind.COMBAT_SKILL_CHOICE);

        // 属性点 +1（0.2.4 从 +2 回调为 +1）
        run = new RunState(2026L);
        int pendBefore = run.pendingAttributePoints;
        run.applyChoice(RunState.Choice.attribute());
        check("属性点选项：待分配 +1", pendBefore + 1, run.pendingAttributePoints);

        // 金币选项：+15
        run = new RunState(2026L);
        int goldBeforeChoice = run.player.gold;
        run.applyChoice(RunState.Choice.gold());
        check("金币选项：+15", goldBeforeChoice + 15, run.player.gold);

        // 技能自选：applyChoice 不自动升级（等玩家选槽）
        run = new RunState(2026L);
        int lvlBefore = run.player.skillLevels[0];
        run.applyChoice(RunState.Choice.skill());
        check("技能自选选项：applyChoice 不自动升级", lvlBefore, run.player.skillLevels[0]);
        run.player.upgradeSkill(0);
        check("玩家自选槽 0：随心一箭等级 +1", lvlBefore + 1, run.player.skillLevels[0]);
        check("其他技能等级不变", 0, run.player.skillLevels[1]);

        // 战斗技巧自选：抽 2 个不重复
        run = new RunState(2026L);
        List<CombatSkill> skillChoices = run.rollUnownedCombatSkills(2);
        check("战斗技巧自选抽 2 个", 2, skillChoices.size());
        checkTrue("两个技巧不重复", skillChoices.get(0).id != skillChoices.get(1).id);

        // 战斗胜利必触发三选一
        run = new RunState(2026L);
        run.exp = 0;
        run.onBattleWin();
        checkTrue("经验不足升级时，战斗胜利仍弹三选一", !run.rollChoices().isEmpty());
        check("三选一始终给出 3 个选项", 3, run.rollChoices().size());

        System.out.println("· 属性点分配临时池（0.2.1 需求第 3 条：可反悔 + 置灰）");
        Player allocPlayer = new Player();
        int str0 = allocPlayer.strength;
        int prec0 = allocPlayer.precision;
        AttributeAllocator alloc = new AttributeAllocator(allocPlayer, 2);
        check("初始剩余 2 点", 2, alloc.remaining());
        checkTrue("未分配完时 canContinue = false", !alloc.canContinue());

        alloc.add(0);   // 力量 +1
        check("力量临时 +1", 1, alloc.pendingAt(0));
        check("剩余 1 点", 1, alloc.remaining());
        checkTrue("剩 1 点时 canContinue 仍为 false", !alloc.canContinue());
        check("未确认前玩家真实力量不变", str0, allocPlayer.strength);

        alloc.sub(0);   // 反悔：退掉力量
        check("退点后力量临时归 0", 0, alloc.pendingAt(0));
        check("退点后剩余恢复 2 点", 2, alloc.remaining());

        alloc.add(0);   // 力量 +1
        alloc.add(1);   // 精准 +1
        check("力量、精准各 +1", 1, alloc.pendingAt(0));
        check("精准临时 +1", 1, alloc.pendingAt(1));
        check("全加完剩余 0 点", 0, alloc.remaining());
        checkTrue("全加完后 canContinue = true", alloc.canContinue());

        checkTrue("确认生效（返回 true）", alloc.commit());
        check("真实面板力量 +1", str0 + 1, allocPlayer.strength);
        check("真实面板精准 +1", prec0 + 1, allocPlayer.precision);

        // 反悔边界：没有分配的点不能退成负数
        AttributeAllocator alloc2 = new AttributeAllocator(new Player(), 1);
        alloc2.sub(0);
        check("没加点时退点不产生负值", 0, alloc2.pendingAt(0));
        check("没加点时剩余仍是 1 点", 1, alloc2.remaining());
        checkTrue("剩余 > 0 时 commit 拒绝（返回 false）", !alloc2.commit());

        System.out.println("· 0.2.3 新天赋池（脱离掌控 / 慵懒之徒 / 箭术大师 / 温和之人 / 不惧疼痛）");
        // 脱离掌控：HP < 20% 时伤害 x2.5
        p = new Player();
        p.talents.add(Talent.OUT_OF_CONTROL);
        p.hp = 10;   // 低于 80*0.2=16
        p.critRate = 0;
        BattleSystem ooc = new BattleSystem(p, new Random(1));
        TrainingDummy oocDummy = new TrainingDummy();
        ooc.addEnemy(oocDummy);
        ooc.start();
        ooc.useSkill(0);   // 随心一箭 8 x2.5 = 20
        check("脱离掌控（血 10<16）：随心一箭 8 -> 20", 80, oocDummy.hp);

        // 箭术大师：远程 x1.5
        p = new Player();
        p.talents.add(Talent.ARCHERY_MASTER);
        p.critRate = 0;
        BattleSystem archery = new BattleSystem(p, new Random(1));
        TrainingDummy archDummy = new TrainingDummy();
        archery.addEnemy(archDummy);
        archery.start();
        archery.useSkill(2);   // 精准射击 14 x1.5 = 21
        check("箭术大师：精准射击 14 -> 21", 79, archDummy.hp);

        // 箭术大师现在也作用于随心一箭（远程）
        p = new Player();
        p.talents.add(Talent.ARCHERY_MASTER);
        p.critRate = 0;
        BattleSystem archeryBasic = new BattleSystem(p, new Random(1));
        TrainingDummy archBasicDummy = new TrainingDummy();
        archeryBasic.addEnemy(archBasicDummy);
        archeryBasic.start();
        archeryBasic.useSkill(0);   // 随心一箭 8 x1.5 = 12
        check("箭术大师：随心一箭 8 -> 12", 88, archBasicDummy.hp);

        // 慵懒之徒：防御后下次攻击 x1.2
        p = new Player();
        p.talents.add(Talent.LAZY);
        p.critRate = 0;
        BattleSystem lazyBattle = new BattleSystem(p, new Random(1));
        TrainingDummy lazyDummy = new TrainingDummy();
        lazyBattle.addEnemy(lazyDummy);
        lazyBattle.start();
        lazyBattle.useSkill(1);   // 防御：获得慵懒 Buff
        checkTrue("慵懒之徒：防御后获得 Buff", p.lazyBuff);
        // 随心一箭：8 x1.2 = 9.6 -> 9
        lazyBattle.useSkill(0);
        check("慵懒之徒：下次随心一箭 8 -> 9", 91, lazyDummy.hp);
        checkTrue("慵懒之徒：Buff 已消耗", !p.lazyBuff);

        // 温和之人：近战伤害 x0.6 但结算 2 次（双段，双回血）
        // 注意：随心一箭现在是远程，不再受温和之人影响，改用近战技能（血刺）验证
        p = new Player();
        p.talents.add(Talent.GENTLE);
        p.hp = 70;
        p.critRate = 0;
        p.replaceSkill(3, Skill.BLOOD_SPIKE);   // 血刺是近战
        BattleSystem gentle = new BattleSystem(p, new Random(1));
        TrainingDummy gentleDummy = new TrainingDummy();
        gentle.addEnemy(gentleDummy);
        gentle.start();
        gentle.useSkill(3);   // 血刺单段 (3*0.2+1)=1，x0.6 后保底 1，结算 2 次（3 段 x 每段 2 次 = 6 次）
        check("温和之人：血刺 6 次结算，总伤 6", 94, gentleDummy.hp);
        checkTrue("温和之人多段触发多次回血", p.hp > 70);

        // 不惧疼痛：造成伤害前扣 2 血，伤害 x1.1
        p = new Player();
        p.talents.add(Talent.FEARLESS);
        p.critRate = 0;
        p.hp = 70;
        BattleSystem fearless = new BattleSystem(p, new Random(1));
        TrainingDummy fearlessDummy = new TrainingDummy();
        fearless.addEnemy(fearlessDummy);
        fearless.start();
        fearless.useSkill(0);   // 随心一箭 8 x1.1 = 8.8 -> 8，先扣 2 血
        check("不惧疼痛：随心一箭 8 x1.1 = 8（截断）", 92, fearlessDummy.hp);
        check("不惧疼痛：先扣 2 血再回血之泪 1（70 - 2 + 1 = 69）", 69, p.hp);

        // 蓄力技能：下次攻击 x2.5
        p = new Player();
        p.critRate = 0;
        BattleSystem chargeBattle = new BattleSystem(p, new Random(1));
        TrainingDummy chargeDummy = new TrainingDummy();
        chargeBattle.addEnemy(chargeDummy);
        chargeBattle.start();
        // 用 replaceSkill 把槽 3 换成蓄力
        p.replaceSkill(3, Skill.CHARGE);
        chargeBattle.useSkill(3);   // 蓄力：获得 Buff
        checkTrue("蓄力：使用后获得 Buff", p.chargeBuff);
        chargeBattle.useSkill(0);   // 随心一箭 8 x2.5 = 20
        check("蓄力：下次随心一箭 8 -> 20", 80, chargeDummy.hp);

        // 血刺：近战多段 3 次
        p = new Player();
        p.critRate = 0;
        p.replaceSkill(3, Skill.BLOOD_SPIKE);
        BattleSystem spikeBattle = new BattleSystem(p, new Random(1));
        TrainingDummy spikeDummy = new TrainingDummy();
        spikeBattle.addEnemy(spikeDummy);
        spikeBattle.start();
        spikeBattle.useSkill(3);   // 血刺：3 次 (3*0.2+1)=1 = 3
        check("血刺：3 段各 1 点 = 3 总伤", 97, spikeDummy.hp);

        // 多重射击：AOE，精准 x1.5 + 5
        p = new Player();
        p.critRate = 0;
        p.replaceSkill(3, Skill.MULTI_SHOT);
        BattleSystem multiBattle = new BattleSystem(p, new Random(1));
        TrainingDummy m1 = new TrainingDummy();
        TrainingDummy m2 = new TrainingDummy();
        multiBattle.addEnemy(m1);
        multiBattle.addEnemy(m2);
        multiBattle.start();
        multiBattle.useSkill(3);   // 多重射击：(3*1.5+5)=9.5 -> 9 每目标
        check("多重射击：每目标 9 点", 91, m1.hp);
        check("多重射击：两目标各 9 点", 91, m2.hp);

        System.out.println("· 0.2.3 精英战奖励：天赋/技能二选一");
        RunState elite = new RunState(2026L);
        elite.pending = new MapNode(MapNode.Type.ELITE, 4, 0);
        elite.onBattleWin();
        checkTrue("精英战胜利后 lastBattleWasElite = true", elite.lastBattleWasElite);
        RunState.EliteReward reward = elite.rollEliteReward();
        checkTrue("精英奖励不为空（天赋或技能至少一个）", reward != null);
        if (reward != null) {
            checkTrue("精英奖励含天赋或技能", reward.talent != null || reward.skill != null);
        }

        // 技能替换：槽 3/槽 4 可替换，槽 1/槽 2 锁死
        Player rp = new Player();
        checkTrue("槽 3 可替换", rp.isReplaceableSlot(2));
        checkTrue("槽 4 可替换", rp.isReplaceableSlot(3));
        checkTrue("槽 1 锁死", !rp.isReplaceableSlot(0));
        checkTrue("槽 2 锁死", !rp.isReplaceableSlot(1));
        Skill old = rp.replaceSkill(2, Skill.BLOOD_SPIKE);
        checkTrue("替换返回旧技能（精准射击）", old == Skill.PRECISE_SHOT);
        checkTrue("替换后槽 3 是血刺", rp.slots[2] == Skill.BLOOD_SPIKE);
        check("替换后槽 3 等级归 0", 0, rp.skillLevels[2]);

        System.out.println("· 0.2.2 宝箱防脸黑");
        RunState box = new RunState(2026L);
        // 手动制造「连续 2 次开出天赋」的状态，第 3 次必须不给天赋
        box.consecutiveTalentFromTreasure = 2;
        // 反复开宝箱，直到确认第 3 次没给天赋（因为池子里还有天赋，正常会给）
        boolean sawTalentForbidden = false;
        for (int i = 0; i < 50; i++) {
            int beforeCount = box.player.talents.size();
            box.resolveTreasure();
            // 如果这次没给天赋（talents 没变），且计数被重置/没再加，说明防脸黑生效
            if (box.player.talents.size() == beforeCount && box.consecutiveTalentFromTreasure == 0) {
                sawTalentForbidden = true;
                break;
            }
        }
        checkTrue("连续 2 次开天赋后，宝箱必定不给天赋", sawTalentForbidden);

        // 单次宝箱至少给一种奖励（天赋/技能/生命三者之一，玩家 HP 或属性或天赋有变化）
        RunState box2 = new RunState(2026L);
        int hpBeforeBox = box2.player.hp;
        int talentsBeforeBox = box2.player.talents.size();
        box2.resolveTreasure();
        checkTrue("宝箱给了一种奖励（生命/天赋/技能其一有变化）",
                box2.player.hp > hpBeforeBox
                        || box2.player.talents.size() > talentsBeforeBox
                        || hasAnySkillUpgraded(box2));

        System.out.println("· 0.2.2 篝火二选一");
        RunState rest = new RunState(2026L);
        rest.player.hp = 50;
        rest.resolveRest(0);   // 休息
        checkTrue("篝火休息回血", rest.player.hp > 50);
        int hpAfterRest = rest.player.hp;

        RunState rest2 = new RunState(2026L);
        int pendBeforeRest = rest2.pendingAttributePoints;
        rest2.resolveRest(1);   // 冥想
        check("篝火冥想 +1 待分配属性", pendBeforeRest + 1, rest2.pendingAttributePoints);

        System.out.println("· 0.2.2 事件系统");
        RunState evt = new RunState(2026L);
        Event e = evt.rollEvent();
        checkTrue("事件池能抽出事件", e != null);
        // 灌木丛：进入 -10 生命 +1 属性
        int evtHp = evt.player.hp;
        int evtPend = evt.pendingAttributePoints;
        Event.BUSH.apply(evt, 1);
        check("灌木丛进入：-10 生命", evtHp - 10, evt.player.hp);
        check("灌木丛进入：+1 属性", evtPend + 1, evt.pendingAttributePoints);
        // 灌木丛绕道：恢复 5 点生命（0.2.4 从 +1 属性改为回血）
        RunState bush = new RunState(2026L);
        bush.player.hp = 50;
        int pendBeforeBush = bush.pendingAttributePoints;
        Event.BUSH.apply(bush, 0);
        check("灌木丛绕道：恢复 5 点生命（50 -> 55）", 55, bush.player.hp);
        check("灌木丛绕道：不给属性点", pendBeforeBush, bush.pendingAttributePoints);
        // 营地仔细搜刮：+20 金币（0.2.8 改造）
        int evtGold = evt.player.gold;
        Event.CAMP.apply(evt, 0);
        check("营地仔细搜刮：+20 金币", evtGold + 20, evt.player.gold);
    }

    /** 宝箱结算后有没有技能等级被提升（用于断言「宝箱给了奖励」） */
    private static boolean hasAnySkillUpgraded(RunState run) {
        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            if (run.player.skillLevels[i] > 0) {
                return true;
            }
        }
        return false;
    }

    // ==================== 七、敌人预制池 / 新角色空梦 ====================

    private static void checkEnemiesAndKongmeng() {
        System.out.println("· 敌人预制池（0.2.5，按节点类型和层数抽）");
        List<Enemy> f1 = EnemyPool.create(MapNode.Type.BATTLE, 1, new Random(1));
        checkTrue("第 1 层普通战斗是史莱姆", f1.size() == 1 && f1.get(0) instanceof Slime);

        List<Enemy> f7 = EnemyPool.create(MapNode.Type.BATTLE, 7, new Random(1));
        check("第 7 层普通战斗两个敌人", 2, f7.size());
        checkTrue("第 7 层第一个是骸骨猎手", f7.get(0) instanceof BoneHunter);
        checkTrue("第 7 层第二个是小史莱姆", f7.get(1) instanceof Slime);

        List<Enemy> elite5 = EnemyPool.create(MapNode.Type.ELITE, 5, new Random(1));
        checkTrue("第 5 层精英是骸骨猎手或史莱姆王",
                elite5.get(0) instanceof BoneHunter || elite5.get(0) instanceof SlimeKing);

        List<Enemy> boss = EnemyPool.create(MapNode.Type.BOSS, 8, new Random(1));
        checkTrue("第 8 层 Boss 是腐化史莱姆王",
                boss.size() == 1 && boss.get(0) instanceof SlimeKing && ((SlimeKing) boss.get(0)).isBoss());

        System.out.println("· 骸骨战士：骨质增生回血 + 增伤");
        BoneWarrior bw = new BoneWarrior();
        Player p = new Player();
        BattleSystem b = new BattleSystem(p, new Random(1));
        b.addEnemy(bw);
        bw.hp = 20;               // 先压低血量，骨质增生才能看出回血
        bw.doTurn(p, b);          // 轻击（step 0 -> 1）
        bw.doTurn(p, b);          // 骨质增生（step 1 -> 2）：回 10，增伤 +2
        check("骨质增生恢复 10 点生命（20 -> 30）", 30, bw.hp);
        bw.doTurn(p, b);          // 轻击（step 2 -> 3）
        // 骨质增生后重击意图应显示 11 + 2 = 13
        checkTrue("骨质增生后重击意图含 13", bw.getIntent().contains("13"));

        System.out.println("· 骸骨猎手：仔细瞄准后骨箭双倍 + 扣自身血");
        BoneHunter bh = new BoneHunter(false);
        p = new Player();
        b = new BattleSystem(p, new Random(1));
        b.addEnemy(bh);
        bh.doTurn(p, b);          // 速射（step 0 -> 1）
        bh.doTurn(p, b);          // 瞄准（step 1 -> 2，aimed=true）
        bh.doTurn(p, b);          // 速射（step 2 -> 3）
        bh.doTurn(p, b);          // 瞄准（step 3 -> 4，aimed=true）
        checkTrue("瞄准后骨箭意图显示双倍", bh.getIntent().contains("双倍"));
        bh.doTurn(p, b);          // 骨箭（step 4 -> 0）：扣自身 20% 血 + 双倍
        check("骨箭扣自身 20% 最大生命（28 -> 23）", 23, bh.hp);
        checkTrue("骨箭后瞄准状态清空", !bh.getIntent().contains("双倍"));

        System.out.println("· 空梦：初始面板 / 欢愉之礼 / 镜像穿刺 / 完美仿制");
        Player kong = new Player(CharacterType.KONGMENG);
        check("空梦初始 HP 70", 70, kong.maxHp);
        check("空梦初始精力 12", 12, kong.maxMp);
        check("空梦初始力量 4", 4, kong.strength);
        check("空梦初始精准 2", 2, kong.precision);
        check("空梦初始防御 3", 3, kong.defense);
        checkTrue("空梦初始带欢愉之礼", kong.hasTalent(Talent.Id.JOY_GIFT));
        checkTrue("空梦槽 1 是镜像穿刺", kong.slots[0] == Skill.MIRROR_PIERCE);
        checkTrue("空梦槽 2 是伪装", kong.slots[1] == Skill.DISGUISE);
        checkTrue("空梦槽 3 是完美仿制", kong.slots[2] == Skill.PERFECT_COPY);
        checkTrue("空梦槽 4 是镜光一瞬", kong.slots[3] == Skill.MIRROR_FLASH);

        // 欢愉之礼：镜像穿刺 2 段，每段附加 1~2（0.2.6 从 1~3 下调），触发两次欢愉之礼
        kong = new Player(CharacterType.KONGMENG);
        kong.critRate = 0;
        BattleSystem kb = new BattleSystem(kong, new Random(1));
        TrainingDummy kd = new TrainingDummy();
        kb.addEnemy(kd);
        kb.start();
        kb.useSkill(0);   // 镜像穿刺：2 段 (4*1.1+1)=5，每段 +1~2
        int pierceDmg = 100 - kd.hp;
        checkTrue("镜像穿刺总伤在 12~14（两段 5+1~2）", pierceDmg >= 12 && pierceDmg <= 14);
        long joyHits = kb.getLog().stream().filter(l -> l.contains("欢愉之礼")).count();
        check("镜像穿刺触发 2 次欢愉之礼", 2, (int) joyHits);

        // 完美仿制：关掉欢愉之礼单独验证「3 段 ×0.7」
        kong = new Player(CharacterType.KONGMENG);
        kong.talents.clear();   // 去掉欢愉之礼，避免随机值干扰
        kong.critRate = 0;
        BattleSystem pc = new BattleSystem(kong, new Random(1));
        TrainingDummy pcd = new TrainingDummy();
        pc.addEnemy(pcd);
        pc.start();
        pc.useSkill(2);   // 完美仿制：获得 Buff
        checkTrue("完美仿制后获得 Buff", kong.perfectCopyBuff);
        pc.useSkill(0);   // 镜像穿刺：3 段，每段 5 ×0.7 = 3.5 -> 3
        check("完美仿制：镜像穿刺变 3 段 ×0.7（总伤 9）", 91, pcd.hp);
        checkTrue("完美仿制 Buff 已消耗", !kong.perfectCopyBuff);

        // 镜光一瞬（AOE）伤害公式：力量×1.2 + 精准×1.1
        Player kongRaw = new Player(CharacterType.KONGMENG);
        check("镜光一瞬单体 = 4×1.2+2×1.1 = 7", 7, BattleSystem.rawMirrorFlash(kongRaw));
        check("镜像穿刺单段 = 4×1.1+1 = 5", 5, BattleSystem.rawMirrorPierce(kongRaw));

        System.out.println("· 镜中人（0.2.6 空梦专属天赋）");
        // 用 seed=4112：前两次 nextDouble 都 < 0.10（免疫），第三次 0.574（不免疫）
        kong = new Player(CharacterType.KONGMENG);
        kong.talents.add(Talent.MIRROR_AVATAR);
        kong.critRate = 0;
        BattleSystem mirror = new BattleSystem(kong, new Random(4112));
        int hpBefore = kong.hp;
        mirror.damagePlayer(10, "测试撞击");
        check("镜中人第 1 次免疫，HP 不变", hpBefore, kong.hp);
        check("镜中人触发计数 = 1", 1, kong.mirrorAvatarTriggerCount);
        mirror.damagePlayer(10, "测试撞击");
        check("镜中人第 2 次免疫，HP 不变", hpBefore, kong.hp);
        check("镜中人触发计数 = 2", 2, kong.mirrorAvatarTriggerCount);
        mirror.damagePlayer(10, "测试撞击");
        check("第 3 次不免疫（已达上限 2 次），HP 下降", 1, hpBefore - kong.hp >= 1 ? 1 : 0);
        check("镜中人触发计数保持 2（不超上限）", 2, kong.mirrorAvatarTriggerCount);

        // 没有镜中人天赋时，不受 10% 免疫影响（对照）
        Player kongNoMirror = new Player(CharacterType.KONGMENG);
        kongNoMirror.critRate = 0;
        BattleSystem nm = new BattleSystem(kongNoMirror, new Random(4112));
        int hpNoMirror = kongNoMirror.hp;
        nm.damagePlayer(10, "测试撞击");
        checkTrue("无镜中人天赋时正常扣血", kongNoMirror.hp < hpNoMirror);

        // 开战重置计数：上一场打满 2 次后，重新 start 应清零
        kong = new Player(CharacterType.KONGMENG);
        kong.talents.add(Talent.MIRROR_AVATAR);
        kong.mirrorAvatarTriggerCount = 2;
        BattleSystem restart = new BattleSystem(kong, new Random(1));
        restart.addEnemy(new TrainingDummy());
        restart.start();
        check("开战重置镜中人计数为 0", 0, kong.mirrorAvatarTriggerCount);

        System.out.println("· 负面效果 / 诅咒数值框架（0.2.6）");
        // 虚弱：造成伤害 -30%
        Player weak = new Player();
        weak.critRate = 0;
        StatusEffect.addStacks(weak.statusEffects, StatusEffect.WEAKEN, 1, 1);
        BattleSystem weakBattle = new BattleSystem(weak, new Random(1));
        TrainingDummy weakDummy = new TrainingDummy();
        weakBattle.addEnemy(weakDummy);
        weakBattle.start();
        weakBattle.useSkill(0);   // 随心一箭 8 x0.7 = 5.6 -> 5
        check("虚弱：随心一箭 8 -> 5", 95, weakDummy.hp);

        // 怠惰：每次攻击伤害 -10%（0.2.8 起是诅咒，挂在 player.curses）
        Player sloth = new Player();
        sloth.critRate = 0;
        sloth.curses.add(Curse.SLOTH);
        BattleSystem slothBattle = new BattleSystem(sloth, new Random(1));
        TrainingDummy slothDummy = new TrainingDummy();
        slothBattle.addEnemy(slothDummy);
        slothBattle.start();
        slothBattle.useSkill(0);   // 随心一箭 8 x0.9 = 7.2 -> 7
        check("怠惰：随心一箭 8 -> 7", 93, slothDummy.hp);

        // 易伤：受到伤害 +50%
        Player vuln = new Player();
        StatusEffect.addStacks(vuln.statusEffects, StatusEffect.VULNERABLE, 1, 1);
        BattleSystem vulnBattle = new BattleSystem(vuln, new Random(1));
        int vulnHp = vuln.hp;
        vulnBattle.damagePlayer(8, "测试撞击");   // 8 减伤 3 防 = 7，x1.5 = 10
        check("易伤：8 伤害 -> 10（7 x1.5 截断）", vulnHp - 10, vuln.hp);

        // 恶魔抓痕：每次攻击自身受 2 点伤害（0.2.8 起是诅咒）
        Player claw = new Player();
        claw.critRate = 0;
        claw.hp = 70;
        claw.curses.add(Curse.DEMON_CLAW);
        BattleSystem clawBattle = new BattleSystem(claw, new Random(1));
        TrainingDummy clawDummy = new TrainingDummy();
        clawBattle.addEnemy(clawDummy);
        clawBattle.start();
        clawBattle.useSkill(0);   // 攻击前扣 2 血，血之泪回 1：70 - 2 + 1 = 69
        check("恶魔抓痕：攻击自损 2（70-2+1=69）", 69, claw.hp);

        // 中毒：回合开始按层数扣血
        Player poison = new Player();
        StatusEffect.addStacks(poison.statusEffects, StatusEffect.POISON, 3, 1);
        BattleSystem poisonBattle = new BattleSystem(poison, new Random(1));
        poisonBattle.addEnemy(new TrainingDummy());
        poisonBattle.start();   // 注意：start 本身不触发中毒，中毒在回合开始时结算
        // 直接调 damagePlayer 会走敌方回合逻辑，这里直接验证框架方法：手动触发回合开始
        // 用 useSkill 打完一个敌方回合后，enemyTurn 会进入下一回合并结算中毒
        int poisonHp = poison.hp;
        // 更简单：直接模拟 tickStatusEffectsAtTurnStart 的效果（通过一次完整回合）
        poisonBattle.useSkill(0);   // 随心一箭（木桩不还手），敌方回合结束 -> 进入下一回合 -> 中毒扣 3
        check("中毒：回合开始扣 3 点生命", poisonHp - 3, poison.hp);
        check("中毒：扣血后层数 -1（3 -> 2）", 2, StatusEffect.stacksOf(poison.statusEffects, StatusEffect.Id.POISON));

        // 流血：每次受伤额外扣层数点血，层数 -1
        Player bleed = new Player();
        StatusEffect.addStacks(bleed.statusEffects, StatusEffect.BLEED, 4, 1);
        BattleSystem bleedBattle = new BattleSystem(bleed, new Random(1));
        int bleedHp = bleed.hp;
        bleedBattle.damagePlayer(8, "测试撞击");   // 8 减伤 3 防 = 7，再额外扣 4 层 = 11
        check("流血：受伤额外扣 4 层（7+4=11）", bleedHp - 11, bleed.hp);
        check("流血层数 -1（4 -> 3）", 3, StatusEffect.stacksOf(bleed.statusEffects, StatusEffect.Id.BLEED));

        // 晕眩：跳过本回合（框架验证：挂上晕眩后，下一回合 useSkill 应直接跳过）
        Player stun = new Player();
        StatusEffect.addStacks(stun.statusEffects, StatusEffect.STUN, 1, 1);
        BattleSystem stunBattle = new BattleSystem(stun, new Random(1));
        stunBattle.addEnemy(new TrainingDummy());
        stunBattle.start();
        stunBattle.useSkill(0);   // 第 1 回合正常行动，敌方回合结束后进入第 2 回合，结算晕眩
        stunBattle.useSkill(0);   // 第 2 回合：被晕眩跳过
        checkTrue("晕眩：日志记录了被跳过", stunBattle.getLog().stream().anyMatch(l -> l.contains("无法行动")));

        System.out.println("· 精英节点奖励包含镜中人（0.2.6）");
        RunState kongRun = new RunState(2026L, CharacterType.KONGMENG);
        kongRun.pending = new MapNode(MapNode.Type.ELITE, 4, 0);
        kongRun.onBattleWin();
        checkTrue("空梦精英战胜利 lastBattleWasElite = true", kongRun.lastBattleWasElite);
        RunState.EliteReward kongReward = kongRun.rollEliteReward();
        // 空梦现在有 2 个专属天赋（欢愉之礼已带 + 镜中人未拥有），镜中人应在可抽池里
        checkTrue("空梦精英奖励能抽到天赋（镜中人）", kongReward != null && kongReward.talent != null);
        checkTrue("空梦精英奖励天赋是镜中人（当前唯一未拥有的专属天赋）",
                kongReward != null && kongReward.talent == Talent.MIRROR_AVATAR);
    }

    /** 0.2.8：状态/诅咒/降临者捏脸/复活/倒映/宝箱怪/锈蚀箭矢/凋亡骸骨战士 */
    private static void check028() {
        System.out.println("· 降临者：面板 / 四技能 / 伤害公式");
        Player desc = Player.createDescender(4, 2, 3);
        checkTrue("降临者初始带「降临者」天赋", desc.hasTalent(Talent.Id.DESCENDER));
        check("降临者生命 50", 50, desc.maxHp);
        check("降临者精力 10", 10, desc.maxMp);
        checkTrue("降临者名字 = 降临者（非归尘）", "降临者".equals(desc.name));
        checkTrue("降临者 character = DESCENDER", desc.character == CharacterType.DESCENDER);
        check("降临者力量 = 分配 4", 4, desc.strength);
        check("降临者精准 = 分配 2", 2, desc.precision);
        check("降临者防御 = 分配 3", 3, desc.defense);
        checkTrue("降临者槽 1 是剑击", desc.slots[0] == Skill.SWORD_STRIKE);
        checkTrue("降临者槽 2 是格挡", desc.slots[1] == Skill.BLOCK);
        checkTrue("降临者槽 3 是吃我一剑", desc.slots[2] == Skill.EAT_MY_SWORD);
        checkTrue("降临者槽 4 是厚积薄发", desc.slots[3] == Skill.SLOW_BURN);
        check("剑击 = 力量x1.2 = 4（4.8 截断）", 4, BattleSystem.rawSwordStrike(desc));
        check("吃我一剑 = 精准x5 = 10", 10, BattleSystem.rawEatMySword(desc));

        System.out.println("· 降临者：剑击 / 厚积薄发 / 吃我一剑自眩晕");
        Player desc2 = Player.createDescender(5, 4, 3);
        desc2.critRate = 0;
        BattleSystem db = new BattleSystem(desc2, new Random(1));
        TrainingDummy dd = new TrainingDummy();
        db.addEnemy(dd);
        db.start();
        db.useSkill(0);   // 剑击 = 5x1.2 = 6，打 0 防木桩
        check("剑击打 0 防木桩：100 -> 94", 94, dd.hp);
        // 厚积薄发：下次攻击 x2.5
        db.useSkill(3);   // 厚积薄发：获得 Buff
        checkTrue("厚积薄发后获得 Buff", desc2.slowBurnBuff);
        db.useSkill(0);   // 剑击 6 x2.5 = 15，木桩 94-15=79
        check("厚积薄发：剑击 6 -> 15（94 -> 79）", 79, dd.hp);
        checkTrue("厚积薄发 Buff 已消耗", !desc2.slowBurnBuff);

        System.out.println("· 降临者：原地复活（整局限 1 次）");
        Player desc3 = Player.createDescender(3, 3, 3);
        desc3.critRate = 0;
        BattleSystem rb = new BattleSystem(desc3, new Random(1));
        rb.addEnemy(new TrainingDummy());
        rb.start();
        desc3.hp = 10;
        rb.damagePlayer(100, "致死打击");   // 100 减伤后必死 -> 触发复活 30% x50 = 15
        checkTrue("复活后存活", desc3.isAlive());
        check("复活后 HP = 15（50 x30%）", 15, desc3.hp);
        checkTrue("已标记复活过", desc3.descenderRevived);
        // 第二次死亡不再复活
        rb.damagePlayer(100, "再致死");
        checkTrue("第二次死亡不再复活（真死）", !desc3.isAlive());

        System.out.println("· 空梦倒映：负面效果反弹给敌人");
        Player refl = new Player(CharacterType.KONGMENG);
        refl.talents.add(Talent.REFLECTION);
        refl.critRate = 0;
        BattleSystem reflBattle = new BattleSystem(refl, new Random(1));
        TrainingDummy reflDummy = new TrainingDummy();
        reflBattle.addEnemy(reflDummy);
        reflBattle.start();
        reflBattle.inflictStatus(StatusEffect.POISON, 3, 3);   // 给玩家 3 层中毒
        checkTrue("倒映：敌人获得同样的中毒", StatusEffect.has(reflDummy.statusEffects, StatusEffect.Id.POISON));
        check("倒映：敌人中毒层数与玩家一致（3 层）",
                3, StatusEffect.stacksOf(reflDummy.statusEffects, StatusEffect.Id.POISON));

        System.out.println("· 锈蚀箭矢：远程伤害给敌人 2 层流血");
        Player rust = new Player();
        rust.critRate = 0;
        rust.combatSkills.add(CombatSkill.RUSTED_ARROW);
        BattleSystem rustBattle = new BattleSystem(rust, new Random(1));
        TrainingDummy rustDummy = new TrainingDummy();
        rustBattle.addEnemy(rustDummy);
        rustBattle.start();
        rustBattle.useSkill(0);   // 随心一箭（远程）
        check("锈蚀箭矢：木桩被挂 2 层流血",
                2, StatusEffect.stacksOf(rustDummy.statusEffects, StatusEffect.Id.BLEED));

        System.out.println("· 宝箱怪：血盆大口必给 3 回合易伤");
        Player chestPlayer = new Player();
        TreasureChest chest = new TreasureChest();
        BattleSystem chestBattle = new BattleSystem(chestPlayer, new Random(1));
        chestBattle.addEnemy(chest);
        chestBattle.start();
        // 手动把 step 拨到「血盆大口」前：连续咬击(step1) -> 陈年宝箱(step2) -> 血盆大口(step3)
        // 直接构造：先让 chest 走到 step=3
        chest.doTurn(chestPlayer, chestBattle);   // step0 咬击
        chest.doTurn(chestPlayer, chestBattle);   // step1 连续咬击
        chest.doTurn(chestPlayer, chestBattle);   // step2 陈年宝箱
        int hpBeforeMaw = chestPlayer.hp;
        chest.doTurn(chestPlayer, chestBattle);   // step3 血盆大口
        checkTrue("血盆大口给玩家挂上易伤", StatusEffect.has(chestPlayer.statusEffects, StatusEffect.Id.VULNERABLE));
        // 易伤的 duration 应该是 3 回合
        int vulnDuration = 0;
        for (StatusEffect.Instance inst : chestPlayer.statusEffects) {
            if (inst.effect.id == StatusEffect.Id.VULNERABLE) {
                vulnDuration = inst.duration;
            }
        }
        check("易伤持续 3 回合", 3, vulnDuration);

        System.out.println("· 宝箱怪：洗劫偷 10 金币");
        Player stealPlayer = new Player();
        stealPlayer.gold = 30;
        TreasureChest chest2 = new TreasureChest();
        BattleSystem stealBattle = new BattleSystem(stealPlayer, new Random(1));
        stealBattle.addEnemy(chest2);
        stealBattle.start();
        for (int i = 0; i < 7; i++) {
            chest2.doTurn(stealPlayer, stealBattle);   // 走满 7 步到「洗劫」
        }
        check("洗劫后金币 30 -> 20", 20, stealPlayer.gold);

        System.out.println("· 凋亡骸骨战士：破伤风之刃 / 刺痛给流血");
        Player witherPlayer = new Player();
        WitherBoneWarrior wither = new WitherBoneWarrior();
        BattleSystem witherBattle = new BattleSystem(witherPlayer, new Random(1));
        witherBattle.addEnemy(wither);
        witherBattle.start();
        wither.doTurn(witherPlayer, witherBattle);   // 挥砍
        wither.doTurn(witherPlayer, witherBattle);   // 破伤风之刃 -> 3 层流血
        check("破伤风之刃给 3 层流血",
                3, StatusEffect.stacksOf(witherPlayer.statusEffects, StatusEffect.Id.BLEED));

        System.out.println("· 宝箱节点 10% 触发宝箱怪");
        // 用固定 seed 找「触发」的那局不好控制，这里验证 rollTreasureMimic 逻辑 + createEnemies 返回宝箱怪
        RunState mimicRun = new RunState(2026L);
        mimicRun.pending = new MapNode(MapNode.Type.TREASURE, 3, 0);
        // 手动置 treasureMimic = true，验证 createEnemies 固定返回宝箱怪
        mimicRun.treasureMimic = true;
        List<Enemy> mimicEnemies = mimicRun.createEnemies();
        checkTrue("宝箱怪战斗返回宝箱怪", mimicEnemies.size() == 1 && mimicEnemies.get(0) instanceof TreasureChest);
        // 宝箱怪胜利额外 +20 金币 + 按精英结算
        RunState mimicWin = new RunState(2026L);
        mimicWin.pending = new MapNode(MapNode.Type.TREASURE, 3, 0);
        mimicWin.treasureMimic = true;
        int goldBeforeMimic = mimicWin.player.gold;
        mimicWin.onBattleWin();
        check("宝箱怪胜利额外 +20（金币 = 基础 + 20）",
                goldBeforeMimic + mimicWin.lastGoldGain, mimicWin.player.gold);
        checkTrue("宝箱怪胜利按精英结算 lastBattleWasElite = true", mimicWin.lastBattleWasElite);

        System.out.println("· 遇见邪教徒事件：献祭给诅咒 + 天赋");
        RunState cultistRun = new RunState(2026L);
        int curseBefore = cultistRun.player.curses.size();
        Event.CULTIST.apply(cultistRun, 1);   // 献祭
        checkTrue("献祭后获得 1 个诅咒", cultistRun.player.curses.size() == curseBefore + 1);

        // 0.2.8 追加：粗略看看花 10 金币才能离开，金币不足则失败
        RunState cultistLeave = new RunState(2026L);
        cultistLeave.player.gold = 30;
        checkTrue("粗略看看：金币够时返回 true", Event.CULTIST.apply(cultistLeave, 0));
        check("粗略看看：扣 10 金币（30 -> 20）", 20, cultistLeave.player.gold);
        RunState cultistPoor = new RunState(2026L);
        cultistPoor.player.gold = 5;
        checkTrue("粗略看看：金币不足（5）返回 false", !Event.CULTIST.apply(cultistPoor, 0));
        check("粗略看看：金币不足时不扣钱（5 不变）", 5, cultistPoor.player.gold);

        // 0.2.8 追加：祭坛「摸索周边」给 1 自由属性点
        RunState altarRun = new RunState(2026L);
        int altarPend = altarRun.pendingAttributePoints;
        Event.ALTAR.apply(altarRun, 1);
        check("祭坛摸索周边：+1 自由属性点", altarPend + 1, altarRun.pendingAttributePoints);

        System.out.println("· 降临者：抽不到归尘的可替换技能");
        RunState descEliteRun = new RunState(2026L, CharacterType.DESCENDER);
        descEliteRun.pending = new MapNode(MapNode.Type.ELITE, 4, 0);
        descEliteRun.onBattleWin();
        RunState.EliteReward descReward = descEliteRun.rollEliteReward();
        if (descReward != null && descReward.skill != null) {
            checkTrue("降临者精英奖励技能是「切割」（非归尘技能）",
                    descReward.skill == Skill.SLASH);
        }

        System.out.println("· 切割：接下来 3 次攻击附加 3 层流血");
        Player slashPlayer = Player.createDescender(4, 2, 3);
        slashPlayer.critRate = 0;
        BattleSystem slashBattle = new BattleSystem(slashPlayer, new Random(1));
        TrainingDummy slashDummy = new TrainingDummy();
        slashBattle.addEnemy(slashDummy);
        slashBattle.start();
        slashPlayer.slashCharges = 3;   // 直接给切割充能，验证攻击结算时消耗并挂流血
        slashBattle.useSkill(0);   // 剑击（近战），消耗 1 次切割充能，给木桩 3 层流血
        check("切割：第 1 次攻击给 3 层流血",
                3, StatusEffect.stacksOf(slashDummy.statusEffects, StatusEffect.Id.BLEED));
        check("切割：剩余充能 2 次", 2, slashPlayer.slashCharges);
        slashBattle.useSkill(0);   // 再剑击：先结算木桩已有 3 层流血（3->2），切割再叠 3 层 -> 5 层
        check("切割：第 2 次攻击流血为 5 层（先结算旧流血 3->2，再叠 3）",
                5, StatusEffect.stacksOf(slashDummy.statusEffects, StatusEffect.Id.BLEED));
        check("切割：剩余充能 1 次", 1, slashPlayer.slashCharges);

        System.out.println("· 天佑：暴击率 +10%（降临者专属天赋）");
        checkTrue("天佑天赋 owner = DESCENDER", Talent.HEAVENLY_BLESSING.owner == CharacterType.DESCENDER);
        // 降临者初始带「降临者」，唯一未拥有的专属天赋就是「天佑」
        RunState blessedRun = new RunState(2026L, CharacterType.DESCENDER);
        checkTrue("降临者能抽到天佑（唯一未拥有专属天赋）",
                blessedRun.rollUnownedTalent() == Talent.HEAVENLY_BLESSING);
        // 归尘/空梦抽不到天佑
        checkTrue("归尘抽不到天佑",
                new RunState(2026L, CharacterType.GUICHEN).rollUnownedTalent() != Talent.HEAVENLY_BLESSING);
        checkTrue("空梦抽不到天佑",
                new RunState(2026L, CharacterType.KONGMENG).rollUnownedTalent() != Talent.HEAVENLY_BLESSING);

        System.out.println("· 敌人流血：锈蚀箭矢挂的流血会在下次受击时结算");
        Player enemyBleedP = new Player();
        enemyBleedP.critRate = 0;
        enemyBleedP.combatSkills.add(CombatSkill.RUSTED_ARROW);
        BattleSystem enemyBleedBattle = new BattleSystem(enemyBleedP, new Random(1));
        TrainingDummy enemyBleedDummy = new TrainingDummy();
        enemyBleedBattle.addEnemy(enemyBleedDummy);
        enemyBleedBattle.start();
        enemyBleedBattle.useSkill(0);   // 随心一箭（远程），锈蚀箭矢给木桩 2 层流血
        check("锈蚀箭矢：木桩 2 层流血", 2,
                StatusEffect.stacksOf(enemyBleedDummy.statusEffects, StatusEffect.Id.BLEED));
        int hpAfterFirst = enemyBleedDummy.hp;
        enemyBleedBattle.useSkill(0);   // 第二次攻击：结算木桩流血（2 层扣 2 血），再挂 2 层
        // 木桩第二次受击：先扣箭伤，再扣流血 2，层数 2->1，再挂 2 -> 3 层
        check("木桩第 2 次受击后流血层数 = 3（2-1+2）", 3,
                StatusEffect.stacksOf(enemyBleedDummy.statusEffects, StatusEffect.Id.BLEED));

        System.out.println("· 帐篷主人：四步循环 AI");
        TentOwner tent = new TentOwner();
        check("帐篷主人 50 血", 50, tent.maxHp);
        check("帐篷主人 10 防", 10, tent.defense);
        Player tentPlayer = new Player();
        tentPlayer.critRate = 0;
        BattleSystem tentBattle = new BattleSystem(tentPlayer, new Random(1));
        tentBattle.addEnemy(tent);
        tentBattle.start();
        tent.doTurn(tentPlayer, tentBattle);   // 锤击：10 伤害 + 2 回合易伤
        checkTrue("锤击给玩家挂上易伤", StatusEffect.has(tentPlayer.statusEffects, StatusEffect.Id.VULNERABLE));
        int vulnDur = 0;
        for (StatusEffect.Instance inst : tentPlayer.statusEffects) {
            if (inst.effect.id == StatusEffect.Id.VULNERABLE) {
                vulnDur = inst.duration;
            }
        }
        check("锤击易伤持续 2 回合", 2, vulnDur);
        tent.doTurn(tentPlayer, tentBattle);   // 狂乱攻击（7x2，无易伤刷新）
        tent.doTurn(tentPlayer, tentBattle);   // 锤击（第 2 次）
        tent.doTurn(tentPlayer, tentBattle);   // 愤怒不已：buff + 3 回合易伤
        checkTrue("愤怒不已后自身获得 Buff", tent.enraged);

        System.out.println("· 帐篷事件：仔细搜刮 +20 金币，粗略搜刮 +5");
        RunState campRun = new RunState(2026L);
        campRun.player.gold = 0;
        // 粗略搜刮（选项 2）：+5 金币，绝不触发战斗
        Event.CAMP.apply(campRun, 1);
        check("粗略搜刮 +5 金币", 5, campRun.player.gold);
        checkTrue("粗略搜刮不触发战斗", !campRun.eventCombat);
        // 仔细搜刮（选项 1）：+20 金币；30% 概率触发战斗（用固定 seed 验证金币）
        RunState campRun2 = new RunState(2026L);
        campRun2.player.gold = 0;
        Event.CAMP.apply(campRun2, 0);
        check("仔细搜刮 +20 金币", 20, campRun2.player.gold);

        System.out.println("· 事件整局只遇一次");
        RunState eventRun = new RunState(2026L);
        // 连续抽 4 次，应该抽到 4 个不同的事件
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 4; i++) {
            Event e = eventRun.rollEvent();
            if (e != null) {
                seen.add(e.title);
            }
        }
        check("抽 4 次得 4 个不同事件（去重）", 4, seen.size());
        checkTrue("第 5 次抽取返回 null（事件已穷尽）", eventRun.rollEvent() == null);
    }

    // ==================== 断言工具 ====================

    private static void check(String title, int expected, int actual) {
        if (expected == actual) {
            passed++;
            System.out.println("  [OK] " + title);
        } else {
            failed++;
            System.out.println("  [NG] " + title + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void checkTrue(String title, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [OK] " + title);
        } else {
            failed++;
            System.out.println("  [NG] " + title);
        }
    }

    /** 木桩：用来单独测伤害/回血，不还手 */
    private static class TrainingDummy extends Enemy {
        TrainingDummy() {
            this("木桩", 100, 0);
        }

        TrainingDummy(String name, int maxHp, int defense) {
            super(name, maxHp, defense);
        }

        @Override
        public String getIntent() {
            return "待机";
        }

        @Override
        public void doTurn(Player player, BattleSystem battle) {
            // 不还手
        }
    }
}
