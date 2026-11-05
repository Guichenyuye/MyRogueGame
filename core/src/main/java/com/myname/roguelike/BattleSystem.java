package com.myname.roguelike;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 战斗系统：回合流程 + 所有伤害结算。
 * <p>
 * 这里只管「规则」，不管「显示」——所有过程写进 {@link #getLog()}，
 * 控制台测试和后面的 GameScreen 都从同一个日志里取文字画。
 * <p>
 * 回合结构（文档 2.1）：玩家回合 -> 敌方回合 -> 循环，玩家先手。
 */
public class BattleSystem {

    public enum State { PLAYER_TURN, VICTORY, DEFEAT }

    /** 减伤公式里的常数（文档 2.5：防御/(防御+25)） */
    public static final int DEFENSE_K = 25;

    /** 血之泪：敌人每受到 1 次伤害，归尘回 1 点血（文档 2.7 已定：按“伤害次数”算，AOE 每个目标各回 1 点） */
    public static final int BLOOD_TEAR_HEAL = 1;

    private final Player player;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private final Random rng;

    private int round = 1;
    private State state = State.PLAYER_TURN;

    /** 本回合是否因晕眩/旧疾被跳过（0.2.6 数值框架）。玩家行动时若为 true，直接结束行动进入敌方回合 */
    private boolean skipThisTurn = false;

    /** 本场战斗是否有敌人怯战逃跑（0.3.0 新手及时）。逃跑则胜利后金币 0、经验减半 */
    private boolean fled = false;

    /**
     * 这场战斗里每个天赋触发了几次。
     * <p>
     * 界面靠「比上次读到的数字大」来判断刚刚有没有触发，然后金光闪一下（杀戮尖塔遗物的做法）。
     * 用 EnumMap 而不是一个 int：以后加第二个天赋不用动界面代码。
     */
    private final Map<Talent.Id, Integer> talentTriggers = new EnumMap<>(Talent.Id.class);

    public int talentTriggerCount(Talent.Id id) {
        Integer value = talentTriggers.get(id);
        return value == null ? 0 : value;
    }

    public BattleSystem(Player player, Random rng) {
        this.player = player;
        this.rng = rng;
    }

    // ==================== 流程 ====================

    public void addEnemy(Enemy enemy) {
        enemies.add(enemy);
    }

    /** 开战：玩家先手，第 1 回合开始 */
    public void start() {
        // 镜中人每场战斗最多免疫 2 次，开战时清零（上一场战斗的计数不能带过来）
        player.mirrorAvatarTriggerCount = 0;
        // 亡者气质每场战斗第一次受超 15 伤减半，开战重置（0.3.0 墓渊）
        player.deadAuraUsed = false;
        // 召唤物不跨战斗保留：上一场战斗的僵尸/幽魂清空（0.3.0 墓渊）
        player.minions.clear();
        log.add("——— 战斗开始：" + player.name + " VS " + enemyNames() + " ———");
        player.startTurn();
        log.add("");
        log.add("===== 第 " + round + " 回合 ／ 你的回合 =====");
        printStatus();
    }

    /**
     * 玩家使用某个槽位的技能。成功执行后会自动跑完敌方回合并进入下一回合，
     * 所以 UI 那边一次点击 = 一次调用。
     *
     * @return 是否成功释放
     */
    public boolean useSkill(int slot) {
        if (state != State.PLAYER_TURN) {
            log.add("! 现在不能行动（战斗状态：" + state + "）");
            return false;
        }

        // 晕眩 / 旧疾：本回合被跳过（0.2.6 数值框架）。不耗精力、不放技能，
        // 直接进入敌方回合。判定发生在上一回合结束时（见 enemyTurn），这里只消费标记。
        if (skipThisTurn) {
            skipThisTurn = false;
            log.add("! 你被控制住了，本回合无法行动");
            enemyTurn();
            return true;
        }

        String reason = player.whyCannotUse(slot);
        if (reason != null) {
            log.add("! " + reason);
            return false;
        }

        Skill skill = player.slots[slot];
        int mpBefore = player.mp;
        player.mp -= skill.mpCost;
        player.onSkillUsed(slot);
        int level = player.skillLevels[slot];
        log.add("> 你使用【" + skill.name + (level > 0 ? " Lv" + level : "")
                + "】（精力 " + mpBefore + " -> " + player.mp + "）");
        int hpBeforeAction = player.hp;

        switch (skill.id) {
            case NORMAL_ATTACK -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawNormalAttack(player) + skill.levelBonusDamage(level);
                    // 随心一箭是远程类型，吃远程乘区（远程强化 / 箭术大师 / 血之回响）
                    attackEnemy(target, raw, Skill.Type.RANGED, true);
                    // 双重打击：随心一箭额外攻击一次（固定 1 点，不触发特效）
                    maybeDoubleHit(target);
                }
            }
            case DEFEND -> {
                int amount = player.defense * 2;
                // 铁壁防御：防御时额外获得 1 点护盾
                if (player.hasCombatSkill(CombatSkill.Id.DEFEND_BONUS_SHIELD)) {
                    amount += 1;
                }
                player.setShield(amount, 1);
                log.add("  护盾 +" + amount + "（防御 " + player.defense + "x2"
                        + (player.hasCombatSkill(CombatSkill.Id.DEFEND_BONUS_SHIELD) ? "+1" : "")
                        + "），撑过本回合敌方行动后消失");
                // 慵懒之徒：使用防御技能后，获得下次攻击 x1.2 的 Buff
                if (player.hasTalent(Talent.Id.LAZY)) {
                    player.lazyBuff = true;
                    log.add("[天赋] 慵懒之徒：下次攻击伤害 x1.2");
                }
            }
            case PRECISE_SHOT -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawPreciseShot(player) + skill.levelBonusDamage(level);
                    attackEnemy(target, raw, Skill.Type.RANGED, false);
                }
            }
            case SWEEP -> {
                int raw = rawSweep(player) + skill.levelBonusDamage(level);
                for (Enemy enemy : enemies) {
                    if (enemy.isAlive()) {
                        attackEnemy(enemy, raw, Skill.Type.AOE, false);
                    }
                }
            }
            case CHARGE -> {
                player.chargeBuff = true;
                log.add("  你摆出架势，蓄势待发（下次攻击伤害 x2.5）");
            }
            case MULTI_SHOT -> {
                int raw = rawMultiShot(player) + skill.levelBonusDamage(level);
                for (Enemy enemy : enemies) {
                    if (enemy.isAlive()) {
                        attackEnemy(enemy, raw, Skill.Type.AOE, false);
                    }
                }
            }
            case BLOOD_SPIKE -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawBloodSpike(player) + skill.levelBonusDamage(level);
                    // 血刺：近战多段，基础 3 次；温和之人让每次伤害结算翻倍（3 -> 6 次）
                    int hits = 3;
                    if (player.hasTalent(Talent.Id.GENTLE)) {
                        hits *= 2;
                    }
                    for (int i = 0; i < hits; i++) {
                        attackEnemy(target, raw, Skill.Type.MELEE, false);
                        if (!target.isAlive()) {
                            break;
                        }
                    }
                }
            }
            case MIRROR_PIERCE -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawMirrorPierce(player) + skill.levelBonusDamage(level);
                    // 镜像穿刺：近战 2 次；完美仿制把它变成 3 次
                    boolean copy = player.perfectCopyBuff;
                    player.perfectCopyBuff = false;
                    int hits = copy ? 3 : 2;
                    for (int i = 0; i < hits; i++) {
                        attackEnemy(target, raw, Skill.Type.MELEE, false, copy);
                        if (!target.isAlive()) {
                            break;
                        }
                    }
                }
            }
            case DISGUISE -> {
                int amount = (int) (player.defense * 1.5);
                // 铁壁防御：防御时额外获得 1 点护盾（通用技巧，空梦也能抽到）
                if (player.hasCombatSkill(CombatSkill.Id.DEFEND_BONUS_SHIELD)) {
                    amount += 1;
                }
                player.setShield(amount, 1);
                log.add("  护盾 +" + amount + "（防御 " + player.defense + "x1.5"
                        + (player.hasCombatSkill(CombatSkill.Id.DEFEND_BONUS_SHIELD) ? "+1" : "")
                        + "），撑过本回合敌方行动后消失");
            }
            case PERFECT_COPY -> {
                player.perfectCopyBuff = true;
                log.add("  你凝出三道残影（下次攻击变 3 段，每段 x0.7）");
            }
            case MIRROR_FLASH -> {
                int raw = rawMirrorFlash(player) + skill.levelBonusDamage(level);
                boolean copy = player.perfectCopyBuff;
                player.perfectCopyBuff = false;
                int hits = copy ? 3 : 1;
                for (Enemy enemy : enemies) {
                    if (!enemy.isAlive()) {
                        continue;
                    }
                    for (int i = 0; i < hits; i++) {
                        attackEnemy(enemy, raw, Skill.Type.AOE, false, copy);
                        if (!enemy.isAlive()) {
                            break;
                        }
                    }
                }
            }
            case SWORD_STRIKE -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawSwordStrike(player) + skill.levelBonusDamage(level);
                    attackEnemy(target, raw, Skill.Type.MELEE, false);
                }
            }
            case BLOCK -> {
                int amount = 3 + (int) (player.defense * 1.5);
                // 铁壁防御：防御时额外获得 1 点护盾（通用技巧）
                if (player.hasCombatSkill(CombatSkill.Id.DEFEND_BONUS_SHIELD)) {
                    amount += 1;
                }
                player.setShield(amount, 1);
                log.add("  护盾 +" + amount + "（3 + 防御 " + player.defense + "x1.5"
                        + (player.hasCombatSkill(CombatSkill.Id.DEFEND_BONUS_SHIELD) ? "+1" : "")
                        + "），撑过本回合敌方行动后消失");
            }
            case EAT_MY_SWORD -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawEatMySword(player) + skill.levelBonusDamage(level);
                    attackEnemy(target, raw, Skill.Type.RANGED, false);
                }
                // 自身眩晕 1 回合（下回合被跳过）
                StatusEffect.addStacks(player.statusEffects, StatusEffect.STUN, 1, 1);
                log.add("[状态] 吃我一剑的反噬：自身下回合被眩晕");
            }
            case SLOW_BURN -> {
                player.slowBurnBuff = true;
                log.add("  你屏息凝神，将全身气力压入下一剑（下次攻击伤害 x2.5）");
            }
            case SLASH -> {
                player.slashCharges = 3;
                log.add("  你摆出切割架势（接下来 3 次攻击附加 3 层流血）");
            }
            case FLASH_THRUST -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    int raw = rawFlashThrust(player) + skill.levelBonusDamage(level);
                    attackEnemy(target, raw, Skill.Type.MELEE, false);
                }
            }
            case SOUL_WREATH -> {
                int amount = (int) (player.minions.size() * 3 + player.defense * 1.5);
                player.setShield(amount, 1);
                log.add("  护盾 +" + amount + "（召唤物 " + player.minions.size() + "x3 + 防御 "
                        + player.defense + "x1.5），撑过本回合敌方行动后消失");
            }
            case SUMMON_ZOMBIE -> {
                summonZombie();
            }
            case DEATH_WHIRL -> {
                int raw = rawDeathWhirl(player) + skill.levelBonusDamage(level);
                for (Enemy enemy : enemies) {
                    if (enemy.isAlive()) {
                        attackEnemy(enemy, raw, Skill.Type.AOE, false);
                    }
                }
            }
            case SUMMON_GHOST -> {
                summonGhost();
            }
            case DEATH_SURGE -> {
                Enemy target = lowestHpEnemy();
                if (target != null) {
                    int raw = rawDeathSurge(player) + skill.levelBonusDamage(level);
                    attackEnemy(target, raw, Skill.Type.RANGED, false);
                }
            }
        }

        // 血之泪的「闪烁」按一次出手记一次：无论横扫打中几个目标，
        // 只要这次出手真的回血了，面板就闪一下。回血本身仍是每个目标各 1 点。
        if (player.hp > hpBeforeAction) {
            notifyTalentTriggered(Talent.Id.BLOOD_TEAR, "血之泪",
                    "恢复 " + (player.hp - hpBeforeAction) + " 点生命值（HP " + hpBeforeAction
                            + " -> " + player.hp + "/" + player.maxHp + "）");
        }

        // 召唤物行动（0.3.0 墓渊）：玩家出手后、判定胜负前，场上每个存活召唤物行动一次。
        minionTurn();

        if (allEnemiesDead()) {
            state = State.VICTORY;
            log.add("* 战斗胜利！HP " + player.hp + "/" + player.maxHp + "，剩余精力 " + player.mp);
            return true;
        }

        enemyTurn();
        return true;
    }

    private void enemyTurn() {
        log.add("--- 敌方回合 ---");

        // 敌方回合开始：先结算每个敌人身上的中毒（0.3.1 补全）。
        // 中毒在敌方回合开始扣血，层数 -1（毒一层层挥发）；扣死敌人也不影响它本回合行动前先被结算。
        tickEnemyStatusAtTurnStart();

        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            if (state == State.DEFEAT) {
                return;
            }
            enemy.doTurn(player, this);
        }

        if (state == State.DEFEAT) {
            return;
        }

        // 0.3.2 修复：敌人在回合开始被毒死（或召唤物/流血结算致死）后，若场上已无活敌，
        // 必须立即判胜利，否则 state 停在 PLAYER_TURN 却无敌人可打，玩家「无法继续行动」。
        if (allEnemiesDead()) {
            state = State.VICTORY;
            log.add("* 战斗胜利！HP " + player.hp + "/" + player.maxHp + "，剩余精力 " + player.mp);
            return;
        }

        if (player.onEnemyTurnEnd()) {
            log.add("  （护盾到期消散）");
        }

        round++;
        player.startTurn();
        log.add("");
        log.add("===== 第 " + round + " 回合 ／ 你的回合 =====");

        // 回合开始的状态结算（0.2.6 数值框架）
        tickStatusEffectsAtTurnStart();

        printStatus();
    }

    // ==================== 召唤物（0.3.0 墓渊）====================

    /** 召唤一个普通僵尸（血 = 精准×4，若带「死气」技巧则 +5） */
    private void summonZombie() {
        int hp = player.precision * 4;
        if (player.hasCombatSkill(CombatSkill.Id.DEATH_QI)) {
            hp += 5;
        }
        Minion zombie = new Minion(Minion.Kind.ZOMBIE, "普通僵尸", hp);
        player.minions.add(zombie);
        log.add("  你唤起了普通僵尸（HP " + hp + "）");
    }

    /** 召唤一个幽魂（血 = 精准×5，若带「死气」技巧则 +5） */
    private void summonGhost() {
        int hp = player.precision * 5;
        if (player.hasCombatSkill(CombatSkill.Id.DEATH_QI)) {
            hp += 5;
        }
        Minion ghost = new Minion(Minion.Kind.GHOST, "幽魂", hp);
        player.minions.add(ghost);
        log.add("  你唤起了幽魂（HP " + hp + "）");
    }

    /** 召唤物行动（0.3.0 墓渊）：玩家出手后、判定胜负前，场上每个存活召唤物行动一次。 */
    private void minionTurn() {
        if (player.minions.isEmpty()) {
            return;
        }
        for (Minion minion : player.minions) {
            if (minion.isAlive() && !allEnemiesDead()) {
                minion.doTurn(enemies, this);
            }
        }
        // 召唤物行动后可能击杀敌人，再次判定胜负留给调用方（useSkill 里的 allEnemiesDead）
    }

    /**
     * 使用一个道具（0.3.0）。
     * <p>
     * 不消耗精力、不消耗行动次数，一回合内可连续使用多瓶，用完消失。
     * 只能在玩家回合使用。返回是否成功（道具存在且已消耗）。
     *
     * @param itemIndex 道具在 {@code player.items} 里的下标
     */
    public boolean useItem(int itemIndex) {
        if (state != State.PLAYER_TURN) {
            log.add("! 现在不能使用道具");
            return false;
        }
        if (itemIndex < 0 || itemIndex >= player.items.size()) {
            return false;
        }
        Item item = player.items.remove(itemIndex);
        applyItem(item);
        log.add("> 你使用了【" + item.name + "】");

        // 0.3.2 修复：道具理论上不会直接杀敌，但若因某种结算（如百变药水抽到毒药 + 敌人已残）
        // 场上已无活敌，立即判胜，避免 state 停在 PLAYER_TURN 却无敌人可打、玩家「无法行动」。
        if (allEnemiesDead()) {
            state = State.VICTORY;
            log.add("* 战斗胜利！HP " + player.hp + "/" + player.maxHp + "，剩余精力 " + player.mp);
        }
        return true;
    }

    /** 按道具 ID 执行效果（百变药水会在这里递归抽取） */
    private void applyItem(Item item) {
        switch (item.id) {
            case REGEN_POTION -> {
                StatusEffect.addStacks(player.statusEffects, StatusEffect.REGEN, 1, 3);
                log.add("  每回合恢复 4 点生命，持续 3 回合");
            }
            case WEAKEN_POTION -> {
                for (Enemy enemy : enemies) {
                    if (enemy.isAlive()) {
                        StatusEffect.addStacks(enemy.statusEffects, StatusEffect.WEAKEN, 1, 1);
                    }
                }
                log.add("  所有敌人虚弱 1 回合");
            }
            case STRENGTH_POTION -> {
                StatusEffect.addStacks(player.statusEffects, StatusEffect.STRENGTH_UP, 1, 3);
                log.add("  每次攻击额外 +5 点伤害，持续 3 回合");
            }
            case POISON_POTION -> {
                Enemy target = firstAliveEnemy();
                if (target != null) {
                    applyPoisonToEnemy(target, 5);
                    log.add("  " + target.name + " 被挂上 5 层中毒");
                }
            }
            case HEAL_POTION -> {
                int before = player.hp;
                player.heal(10);
                log.add("  立即恢复 " + (player.hp - before) + " 点生命（HP " + player.hp
                        + "/" + player.maxHp + "）");
            }
            case MYSTERY_POTION -> {
                Item rolled = Item.MYSTERY_POOL[rng.nextInt(Item.MYSTERY_POOL.length)];
                log.add("  百变药水变成了【" + rolled.name + "】");
                applyItem(rolled);
            }
            case HOLY_WATER -> {
                // 清除所有负面效果（不含诅咒）。增益（isBuff）保留。
                player.statusEffects.removeIf(inst -> !inst.effect.isBuff);
                log.add("  清除了所有负面效果");
            }
        }
    }

    /**
     * 召唤物对单个敌人造成伤害（0.3.0 墓渊）。
     * <p>
     * 走一条简化的结算：固定伤害 → 久经/诸武精通召唤物加成 → 防御减伤 → 扣血。
     * 不判暴击、不触发血之泪（血之泪是归尘专属，召唤物不共享）、不吃虚弱（召唤物不受玩家负面效果影响）。
     *
     * @param target      目标敌人
     * @param raw         原始伤害
     * @param isPlayerHit 是否算作「玩家造成」的伤害（用于再临/诸武精通等，这里恒 false，召唤物独立）
     */
    public void minionAttack(Enemy target, int raw, boolean isPlayerHit) {
        double value = raw;
        // 久经（召唤物伤害 x1.1）
        if (player.hasCombatSkill(CombatSkill.Id.SEASONED)) {
            value *= 1.1;
        }
        // 诸武精通（所有伤害含召唤物 x1.2）
        if (player.hasTalent(Talent.Id.WEAPON_MASTERY)) {
            value *= 1.2;
        }
        int damage = (int) value;
        int finalDamage = applyDefense(damage, target.defense);
        // 敌人自身免伤（0.3.1 骑士长祈祷）
        if (target.damageTakenMult < 1.0) {
            int reduced = (int) (finalDamage * target.damageTakenMult);
            if (reduced < finalDamage) {
                finalDamage = reduced;
            }
        }
        // 先扣护盾，护盾不够的部分再扣血
        int hpDamage = target.absorbByShield(finalDamage);
        target.hp = Math.max(0, target.hp - hpDamage);

        // 再临：墓渊每次攻击恢复召唤物 1 点血（这里「墓渊攻击」泛指召唤物攻击结算后回血）
        if (player.hasCombatSkill(CombatSkill.Id.RECALL)) {
            for (Minion m : player.minions) {
                if (m.isAlive()) {
                    m.hp = Math.min(m.maxHp, m.hp + 1);
                }
            }
        }

        // 亡语：召唤物「杀死」敌人时？不，亡语是召唤物死亡时触发，见 removeDeadMinions。
    }

    /** 血量最低的存活敌人（幽魂破点 / 死亡激荡目标） */
    public Enemy lowestHpEnemy() {
        Enemy result = null;
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            if (result == null || enemy.hp < result.hp) {
                result = enemy;
            }
        }
        return result;
    }

    /**
     * 给敌人施加中毒（0.3.1 统一入口）。
     * <p>
     * 所有「自身 / 召唤物给敌人上毒」都走这个方法，而不是直接 {@code StatusEffect.addStacks}，
     * 这样「老毒物」（自身和召唤物造成的中毒额外 +1 层）才能统一生效，不会漏掉某条路径。
     * <p>
     * 中毒本身靠 {@link StatusEffect#addStacks} 的「层数相加」实现叠加：旧层数 + 新层数，
     * 剩余回合刷新（取较大值）。敌人的中毒扣血在敌方回合开始时结算（见
     * {@link #tickEnemyStatusAtTurnStart()}）。
     *
     * @param target 目标敌人
     * @param stacks 基础中毒层数
     */
    public void applyPoisonToEnemy(Enemy target, int stacks) {
        int total = stacks;
        if (player.hasCombatSkill(CombatSkill.Id.OLD_POISONER)) {
            total += 1;
            log.add("[技巧] 老毒物：中毒层数额外 +1");
        }
        StatusEffect.addStacks(target.statusEffects, StatusEffect.POISON, total, 3);
    }

    /** 最靠前的存活召唤物（替死鬼转移伤害目标） */
    private Minion firstAliveMinion() {
        for (Minion m : player.minions) {
            if (m.isAlive()) {
                return m;
            }
        }
        return null;
    }

    /**
     * 召唤物死亡结算（0.3.0 墓渊）：
     * <ul>
     *   <li>亡语：对全体敌人造成其最大生命 20% 伤害</li>
     *   <li>死气勃勃：墓渊回 2 血（不超上限）</li>
     * </ul>
     * 由替死鬼转移致死、召唤物被 AOE 击杀时调用。
     */
    private void onMinionDeath(Minion minion) {
        // 亡语：对全体敌人造成其最大生命 20% 伤害
        if (player.hasTalent(Talent.Id.DEATH_WHISPER)) {
            int dmg = Math.max(1, minion.maxHp / 5);
            for (Enemy enemy : enemies) {
                if (enemy.isAlive()) {
                    int finalDamage = applyDefense(dmg, enemy.defense);
                    enemy.hp = Math.max(0, enemy.hp - finalDamage);
                    log.add("[天赋] 亡语：" + enemy.name + " 受到 " + finalDamage + " 点伤害（"
                            + enemy.hp + "/" + enemy.maxHp + "）");
                }
            }
        }
        // 死气勃勃：场上任意单位死亡，墓渊回 2 血（不超上限）
        if (player.hasTalent(Talent.Id.THRIVING_DEATH)) {
            int before = player.hp;
            player.heal(2);
            int gained = player.hp - before;
            if (gained > 0) {
                log.add("[天赋] 死气勃勃：恢复 " + gained + " 点生命（HP " + player.hp
                        + "/" + player.maxHp + "）");
            }
        }
        player.minions.remove(minion);
    }

    /**
     * 玩家回合开始时的负面效果结算（0.2.6 数值框架）。
     * <p>
     * 顺序：中毒扣血 -> 判定晕眩（必跳）/ 旧疾（10% 概率跳）-> 回合数递减。
     * 中毒/晕眩/旧疾都只在这一处结算，敌人技能往玩家身上挂状态即可，
     * 不需要各写一份扣血 / 跳过的逻辑。
     */
    private void tickStatusEffectsAtTurnStart() {
        // 生命恢复（增益，0.3.0 生命恢复药水）：回合开始回 4 点血
        if (StatusEffect.has(player.statusEffects, StatusEffect.Id.REGEN) && player.isAlive()) {
            int before = player.hp;
            player.heal(StatusEffect.REGEN.magnitude);   // magnitude = 4
            int gained = player.hp - before;
            if (gained > 0) {
                log.add("[状态] 生命恢复：恢复 " + gained + " 点生命（HP "
                        + player.hp + "/" + player.maxHp + "）");
            }
        }

        // 中毒：回合开始按「当前层数」扣血，随后层数 -1（毒一层层挥发）
        int poisonStacks = StatusEffect.stacksOf(player.statusEffects, StatusEffect.Id.POISON);
        if (poisonStacks > 0 && player.isAlive()) {
            player.takeDamage(poisonStacks);
            log.add("[状态] 中毒：扣除 " + poisonStacks + " 点生命（HP "
                    + player.hp + "/" + player.maxHp + "）");
            StatusEffect.removeStacks(player.statusEffects, StatusEffect.Id.POISON, 1);
            if (!player.isAlive()) {
                state = State.DEFEAT;
                log.add("!! " + player.name + "倒下了……");
                return;
            }
        }

        // 晕眩：必跳过本回合
        if (StatusEffect.has(player.statusEffects, StatusEffect.Id.STUN)) {
            skipThisTurn = true;
            log.add("[状态] 晕眩：本回合被跳过");
            return;
        }

        // 旧疾（诅咒）：10% 概率跳过本回合
        if (Curse.has(player.curses, Curse.Id.OLD_ILLNESS)
                && rng.nextDouble() < 0.10) {
            skipThisTurn = true;
            log.add("[诅咒] 旧疾：本回合被跳过");
            return;
        }

        // 回合数递减：正数回合的效果到期移除
        // 注意：中毒 / 流血靠「层数」自然消耗（中毒回合开始 -1 层、流血每次受伤 -1 层），
        // 不吃这里的回合到期删除，否则层数还没耗完就被 duration 归零误删了。
        for (int i = player.statusEffects.size() - 1; i >= 0; i--) {
            StatusEffect.Instance inst = player.statusEffects.get(i);
            if (inst.effect.id == StatusEffect.Id.POISON || inst.effect.id == StatusEffect.Id.BLEED) {
                continue;
            }
            if (inst.duration > 0) {
                inst.duration--;
                if (inst.duration <= 0) {
                    player.statusEffects.remove(i);
                }
            }
        }
    }

    /**
     * 敌方回合开始时，结算每个敌人身上的中毒（0.3.1 补全）。
     * <p>
     * 和玩家同语义：按「当前层数」扣血，随后层数 -1（毒一层层挥发）。
     * 之前敌人被挂中毒后从不扣血（毒只是叠在状态栏里好看），召唤流打不出毒伤，
     * 这里把它补上，让僵尸撕咬 / 剧毒药水 / 老毒物真正生效。
     */
    private void tickEnemyStatusAtTurnStart() {
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            int poisonStacks = StatusEffect.stacksOf(enemy.statusEffects, StatusEffect.Id.POISON);
            if (poisonStacks > 0) {
                enemy.hp = Math.max(0, enemy.hp - poisonStacks);
                StatusEffect.removeStacks(enemy.statusEffects, StatusEffect.Id.POISON, 1);
                log.add("[状态] " + enemy.name + " 中毒：扣除 " + poisonStacks + " 点生命（"
                        + enemy.hp + "/" + enemy.maxHp + "）");
            }
        }
    }


    // ==================== 伤害结算 ====================

    /**
     * 文档 2.5：受击伤害 = 原始伤害 × (1 - 防御/(防御+25))，最低 1 点。
     * 玩家打敌人、敌人打玩家走同一套公式，只是取对方的防御值。
     */
    public static int applyDefense(int rawDamage, int defense) {
        float reduction = (float) defense / (defense + DEFENSE_K);
        return Math.max(1, (int) (rawDamage * (1 - reduction)));
    }

    /** 随心一箭伤害 = 精准×1.2 + 5 */
    public static int rawNormalAttack(Player p) {
        return (int) (p.precision * 1.2 + 5);
    }

    /** 精准射击伤害 = 精准×1.5 + 10 */
    public static int rawPreciseShot(Player p) {
        return (int) (p.precision * 1.5 + 10);
    }

    /** 横扫单体伤害 = 力量×1.0 + 6 */
    public static int rawSweep(Player p) {
        return (int) (p.strength * 1.0 + 6);
    }

    /** 多重射击单体伤害 = 精准×1.5 + 5 */
    public static int rawMultiShot(Player p) {
        return (int) (p.precision * 1.5 + 5);
    }

    /** 血刺单段伤害 = 力量×0.2 + 1 */
    public static int rawBloodSpike(Player p) {
        return (int) (p.strength * 0.2 + 1);
    }

    /** 镜像穿刺单段伤害 = 力量×1.1 + 1（0.2.5 空梦） */
    public static int rawMirrorPierce(Player p) {
        return (int) (p.strength * 1.1 + 1);
    }

    /** 镜光一瞬单体伤害 = 力量×1.2 + 精准×1.1（0.2.5 空梦） */
    public static int rawMirrorFlash(Player p) {
        return (int) (p.strength * 1.2 + p.precision * 1.1);
    }

    /** 剑击伤害 = 力量×1.2（0.2.8 降临者） */
    public static int rawSwordStrike(Player p) {
        return (int) (p.strength * 1.2);
    }

    /** 吃我一剑伤害 = 精准×5（0.2.8 降临者，斩杀技） */
    public static int rawEatMySword(Player p) {
        return p.precision * 5;
    }

    /** 瞬身突刺伤害 = 力量×1.5 + 精准×1.2（0.3.0 墓渊） */
    public static int rawFlashThrust(Player p) {
        return (int) (p.strength * 1.5 + p.precision * 1.2);
    }

    /** 死之旋风单体伤害 = 力量×2（0.3.0 墓渊） */
    public static int rawDeathWhirl(Player p) {
        return p.strength * 2;
    }

    /** 死亡激荡伤害 = 5 + 精准×1.5（0.3.0 墓渊） */
    public static int rawDeathSurge(Player p) {
        return (int) (5 + p.precision * 1.5);
    }

    /**
     * 敌人对玩家造成伤害：先按防御减伤，再扣护盾（文档 2.6 待定项，这里定为「先减伤后扣盾」）。
     * <p>
     * 0.2.3：「脱离掌控」在 HP < 20% 时受到伤害 x1.2。
     */
    public void damagePlayer(int rawDamage, String source) {
        // 镜中人（0.2.6 空梦专属）：受击时 10% 概率免疫本次伤害，每场最多 2 次。
        // 在减伤、护盾之前判定 —— 免疫就是完全免疫，不该先扣护盾再「免疫」。
        if (player.hasTalent(Talent.Id.MIRROR_AVATAR)
                && player.mirrorAvatarTriggerCount < 2
                && rng.nextDouble() < 0.10) {
            player.mirrorAvatarTriggerCount++;
            log.add("[天赋] 镜中人触发！免疫了本次伤害（剩余免疫次数 "
                    + (2 - player.mirrorAvatarTriggerCount) + "）");
            return;
        }

        int damage = applyDefense(rawDamage, player.defense);

        // 长期训练（0.3.0 通用技巧）：免伤 +1%（乘法减伤，减伤之后）
        if (player.hasCombatSkill(CombatSkill.Id.LONG_TRAINING)) {
            int reduced = (int) (damage * 0.99);
            if (reduced < damage) {
                damage = reduced;
            }
        }

        // 亡者气质（0.3.0 墓渊天赋）：每场战斗第一次受到超过 15 的伤害减半
        if (player.hasTalent(Talent.Id.DEAD_AURA) && !player.deadAuraUsed && damage > 15) {
            player.deadAuraUsed = true;
            int halved = damage / 2;
            log.add("[天赋] 亡者气质：第一次受到超过 15 的伤害减半（" + damage + " -> " + halved + "）");
            damage = halved;
        }

        // 陈年宝箱（宝箱怪）的毒雾 Buff：下次受击附带 3 层中毒（0.2.8）
        // 在减伤之后、护盾之前结算（无论护盾是否挡下，毒雾都沾上）
        if (player.poisonOnNextHit) {
            player.poisonOnNextHit = false;
            StatusEffect.addStacks(player.statusEffects, StatusEffect.POISON, 3, 3);
            log.add("[状态] 陈年毒雾：你吸入了毒气，获得 3 层中毒");
        }

        // 脱离掌控：HP < 20% 时受到伤害 x1.2（向下取整，减伤之后）
        if (player.hasTalent(Talent.Id.OUT_OF_CONTROL)
                && player.hp < player.maxHp * 0.20) {
            int boosted = (int) (damage * 1.2);
            if (boosted > damage) {
                log.add("[天赋] 脱离掌控：HP 危急，受到伤害 " + damage + " -> " + boosted);
                damage = boosted;
            }
        }

        // 易伤（负面效果）：受到的伤害 +50%（0.2.6 数值框架，减伤之后）
        if (StatusEffect.has(player.statusEffects, StatusEffect.Id.VULNERABLE)) {
            int boosted = (int) (damage * 1.5);
            if (boosted > damage) {
                log.add("[状态] 易伤：受到伤害 " + damage + " -> " + boosted);
                damage = boosted;
            }
        }

        // 替死鬼（0.3.0 墓渊天赋）：伤害转移给场上召唤物；无召唤物时自身受击 +10%
        if (player.hasTalent(Talent.Id.SCAPEGOAT)) {
            Minion firstAlive = firstAliveMinion();
            if (firstAlive != null) {
                // 伤害转移给最前面的存活召唤物
                int absorbed = Math.min(firstAlive.hp, damage);
                firstAlive.hp -= damage;
                log.add("[天赋] 替死鬼：" + firstAlive.name + " 替你承受了 " + damage + " 点伤害（"
                        + Math.max(0, firstAlive.hp) + "/" + firstAlive.maxHp + "）");
                if (firstAlive.hp <= 0) {
                    log.add("[天赋] 亡语触发：" + firstAlive.name + " 倒下了");
                    onMinionDeath(firstAlive);
                }
                return;   // 伤害完全被召唤物吸收
            } else {
                int boosted = (int) (damage * 1.1);
                if (boosted > damage) {
                    log.add("[天赋] 替死鬼：无召唤物可挡，受到伤害 " + damage + " -> " + boosted);
                    damage = boosted;
                }
            }
        }

        if (player.shield > 0) {
            if (player.shield >= damage) {
                player.shield -= damage;
                log.add("< " + source + " 原始 " + rawDamage + "，防御 " + player.defense + " -> " + damage
                        + " 点，全被护盾吸收（护盾剩 " + player.shield + "）");
                return;
            }
            int overflow = damage - player.shield;
            log.add("< " + source + "：护盾碎了（吸收 " + player.shield + "），" + overflow + " 点穿透");
            player.shield = 0;
            damage = overflow;
        }

        player.takeDamage(damage);
        log.add("< " + source + " 原始 " + rawDamage + "，防御 " + player.defense + " -> " + damage
                + " 点（HP " + player.hp + "/" + player.maxHp + "）");

        // 流血（负面效果）：每次受伤额外扣「层数」点血，随后层数 -1（0.2.6 数值框架）
        if (StatusEffect.has(player.statusEffects, StatusEffect.Id.BLEED)) {
            int bleedStacks = StatusEffect.stacksOf(player.statusEffects, StatusEffect.Id.BLEED);
            if (bleedStacks > 0 && player.isAlive()) {
                player.takeDamage(bleedStacks);
                StatusEffect.removeStacks(player.statusEffects, StatusEffect.Id.BLEED, 1);
                log.add("[状态] 流血：额外扣除 " + bleedStacks + " 点生命（剩余 " + bleedStacks
                        + " 层 -> " + (bleedStacks - 1) + " 层）");
            }
        }

        // 降临者（0.2.8）：第一次死亡时立即以 30% 最大生命原地复活，整局限 1 次。
        if (!player.isAlive() && player.hasTalent(Talent.Id.DESCENDER) && !player.descenderRevived) {
            player.descenderRevived = true;
            int reviveHp = (int) (player.maxHp * 0.30);
            player.hp = Math.max(1, reviveHp);
            log.add("[天赋] 降临者：你从死亡边缘爬了回来！恢复 " + player.hp + " 点生命（整局仅此一次）");
        }

        if (!player.isAlive()) {
            state = State.DEFEAT;
            log.add("!! " + player.name + "倒下了……");
        }
    }

    /**
     * 统一伤害管线（0.2.3 大一统，唯一一条伤害计算路径，严禁另起炉灶）。
     * <p>
     * 对单个目标结算一次攻击。优先级：
     * <ol>
     *   <li>基础值：调用方传入的 {@code raw}（已含技能公式 + 等级加成）</li>
     *   <li>固定值加法：欢愉之礼（+1~3，0.2.5 空梦专属，在乘区之前，可被一切乘法放大）</li>
     *   <li>乘区组合：近战 / 远程 / AOE 三类，**同类相加、不同类相乘**</li>
     *   <li>特殊触发：不惧疼痛（扣血 x1.1）/ 脱离掌控（x2.5）/ 慵懒之徒 Buff（x1.2）/ 蓄力 Buff（x2.5）/ 完美仿制（x0.7）</li>
     *   <li>暴击判定：x1.5（随心一箭若有「随性」技巧则暴击率 +10%）</li>
     *   <li>防御减伤：applyDefense</li>
     * </ol>
     * <p>
     * 多段伤害由调用方用循环包裹本方法（温和之人 2 次、血刺 3 次、镜像穿刺 2 次、完美仿制 3 次），
     * 每段独立判定暴击、防御减伤、欢愉之礼和血之泪。
     *
     * @param target        目标敌人
     * @param raw           原始伤害（技能公式 + 等级加成之后）
     * @param type          技能类型（MELEE / RANGED / AOE），决定套哪些乘区
     * @param isBasicAttack 是否随心一箭（普攻）。只影响「随性」的暴击率加成，不再单独参与伤害乘区
     * @param perfectCopy   是否处于「完美仿制」的 x0.7 生效状态（空梦）
     */
    private void calculateDamage(Enemy target, int raw, Skill.Type type, boolean isBasicAttack, boolean perfectCopy) {
        // ---- 1. 基础值 ----
        double value = raw;

        // ---- 2. 固定值加法（欢愉之礼：空梦专属，每次伤害结算 +1~3，必须在乘区之前，
        //      否则拿了一堆乘法加成后这点附加伤害会变得毫无意义）----
        if (player.hasTalent(Talent.Id.JOY_GIFT)) {
            int joyBonus = rng.nextInt(2) + 1;   // 1~2（0.2.6 从 1~3 下调）
            value += joyBonus;
            log.add("[天赋] 欢愉之礼：附加 " + joyBonus + " 点伤害");
        }

        // 力量药水（0.3.0 增益）：每次攻击额外 +5 点伤害。放在乘区之前（固定值加法），
        // 但不触发连击/血之泪（血之泪在末尾按伤害次数结算，与这个 +5 无关，它只影响 value）。
        // 会吃后续的防御减伤（applyDefense），符合「吃免伤」的语义。
        if (StatusEffect.has(player.statusEffects, StatusEffect.Id.STRENGTH_UP)) {
            value += StatusEffect.STRENGTH_UP.magnitude;   // +5
        }

        // ---- 3. 乘区组合（同类相加、不同类相乘）----
        double meleeMult = 1.0;     // 近战乘区
        double rangedMult = 1.0;    // 远程乘区
        double aoeMult = 1.0;       // AOE 乘区

        if (type == Skill.Type.MELEE) {
            // 近战技能强化：近战技能 x1.1（血刺、镜像穿刺等）
            if (player.hasCombatSkill(CombatSkill.Id.MELEE_UP)) {
                meleeMult += 0.1;
            }
        }

        if (type == Skill.Type.RANGED) {
            // 远程强化：远程 x1.1（随心一箭 / 精准射击都吃）
            if (player.hasCombatSkill(CombatSkill.Id.RANGED_UP)) {
                rangedMult += 0.1;
            }
            // 箭术大师（天赋）：远程 x1.5（与远程强化相加 = x1.6，同类相加）
            if (player.hasTalent(Talent.Id.ARCHERY_MASTER)) {
                rangedMult += 0.5;
            }
        }

        if (type == Skill.Type.AOE) {
            // 范围强化：AOE x1.05
            if (player.hasCombatSkill(CombatSkill.Id.AOE_UP)) {
                aoeMult += 0.05;
            }
        }

        // 温和之人（天赋）：近战攻击 x0.6，但调用方会跑 2 次
        if (type == Skill.Type.MELEE && player.hasTalent(Talent.Id.GENTLE)) {
            meleeMult *= 0.6;
        }

        // 乘区相乘：近战 x 远程 x AOE（一次攻击只属于其中一类，其余为 1.0）
        value = value * meleeMult * rangedMult * aoeMult;

        // 诸武精通（0.3.0 宝箱限定天赋）：所有伤害（含召唤物，召唤物在 minionAttack 里另算）x1.2
        if (player.hasTalent(Talent.Id.WEAPON_MASTERY)) {
            value *= 1.2;
        }

        // 替死鬼（0.3.0 墓渊天赋）：无召唤物时，墓渊造成的伤害 x1.1
        if (player.hasTalent(Talent.Id.SCAPEGOAT) && firstAliveMinion() == null) {
            value *= 1.1;
        }

        // 贪婪者（0.3.0 诅咒）：金币 < 50 伤害 -20%，金币 < 100 伤害 -10%
        if (Curse.has(player.curses, Curse.Id.GREEDY)) {
            if (player.gold < 50) {
                value *= 0.8;
                log.add("[诅咒] 贪婪者：金币不足 50，本次伤害 -20%");
            } else if (player.gold < 100) {
                value *= 0.9;
                log.add("[诅咒] 贪婪者：金币不足 100，本次伤害 -10%");
            }
        }

        // ---- 4. 特殊触发判定 ----
        // 完美仿制（空梦）：x0.7，由调用方在出手时统一传入，三段共享同一个开关
        if (perfectCopy) {
            value *= 0.7;
        }
        // 虚弱（负面效果）：造成的伤害 -30%（0.2.6 数值框架，与其它乘区相乘）
        if (StatusEffect.has(player.statusEffects, StatusEffect.Id.WEAKEN)) {
            value *= (1 - StatusEffect.of(StatusEffect.Id.WEAKEN).magnitude / 100.0);
            log.add("[状态] 虚弱：本次伤害 -30%");
        }
        // 怠惰（诅咒）：每次攻击造成的伤害 -10%
        if (Curse.has(player.curses, Curse.Id.SLOTH)) {
            value *= 0.9;
            log.add("[诅咒] 怠惰：本次伤害 -10%");
        }
        // 恶魔抓痕（诅咒）：每次攻击自身受到 2 点伤害
        if (Curse.has(player.curses, Curse.Id.DEMON_CLAW)) {
            player.takeDamage(2);
            log.add("[诅咒] 恶魔抓痕：每次攻击自身受到 2 点伤害");
        }
        // 不惧疼痛：扣自身 2 血，本次伤害 x1.1
        if (player.hasTalent(Talent.Id.FEARLESS)) {
            player.takeDamage(2);
            value *= 1.1;
            log.add("[天赋] 不惧疼痛：扣除 2 点生命，本次伤害 x1.1");
        }
        // 脱离掌控：HP < 20% 时伤害 x2.5
        if (player.hasTalent(Talent.Id.OUT_OF_CONTROL)
                && player.hp < player.maxHp * 0.20) {
            value *= 2.5;
            log.add("[天赋] 脱离掌控：HP 危急，伤害 x2.5");
        }
        // 慵懒之徒：有 Buff 时 x1.2，消耗 Buff
        if (player.lazyBuff) {
            value *= 1.2;
            player.lazyBuff = false;
            log.add("[天赋] 慵懒之徒：下次攻击 x1.2 已消耗");
        }
        // 蓄力：有 Buff 时 x2.5，消耗 Buff
        if (player.chargeBuff) {
            value *= 2.5;
            player.chargeBuff = false;
            log.add("[技能] 蓄力：本次攻击 x2.5");
        }
        // 厚积薄发（0.2.8 降临者）：有 Buff 时 x2.5，消耗 Buff
        if (player.slowBurnBuff) {
            value *= 2.5;
            player.slowBurnBuff = false;
            log.add("[技能] 厚积薄发：本次攻击 x2.5");
        }

        int damage = (int) value;

        // ---- 5. 暴击判定 ----
        double crit;
        if (player.critLocked) {
            // 出金了（0.3.0 宝箱奖励）：暴击率固定在 30%，后续无法提升
            crit = 0.30;
        } else {
            crit = player.critRate;
            // 随性：随心一箭暴击率 +10%
            if (isBasicAttack && player.hasCombatSkill(CombatSkill.Id.FREE_CRIT)) {
                crit += 0.10;
            }
            // 天佑（0.2.8 降临者天赋）：暴击率 +10%
            if (player.hasTalent(Talent.Id.HEAVENLY_BLESSING)) {
                crit += 0.10;
            }
            // 聚精会神（0.3.0 通用技巧）：暴击率 +2%
            if (player.hasCombatSkill(CombatSkill.Id.FOCUS)) {
                crit += 0.02;
            }
        }
        if (rng.nextDouble() < crit) {
            damage = (int) (value * player.critDamage);
            log.add("** 暴击！" + (int) value + " -> " + damage + "（x" + player.critDamage + "）");
        }

        // ---- 6. 防御减伤 + 敌人护盾（0.3.1 骑士系）----
        int finalDamage = applyDefense(damage, target.defense);
        // 敌人自身免伤（0.3.1 骑士长祈祷）：在防御减伤之后额外乘一次 damageTakenMult
        if (target.damageTakenMult < 1.0) {
            int reduced = (int) (finalDamage * target.damageTakenMult);
            if (reduced < finalDamage) {
                finalDamage = reduced;
            }
        }
        // 先扣护盾，护盾不够的部分再扣血
        int hpDamage = target.absorbByShield(finalDamage);
        int blocked = finalDamage - hpDamage;
        target.hp = Math.max(0, target.hp - hpDamage);
        if (blocked > 0) {
            log.add("  " + target.name + " 的护盾吸收了 " + blocked + " 点（护盾剩余 " + target.shield + "）");
        }
        log.add("  " + target.name + " 受到 " + hpDamage + " 点（原始 " + damage
                + "，防御 " + target.defense + "）-> " + target.hp + "/" + target.maxHp);

        // ---- 7. 敌人流血结算（0.2.8 补全）----
        // 敌人身上的流血与玩家同语义：每次受到伤害后额外扣「层数」点血，随后层数 -1。
        // 放在扣血之后、血之泪之前，避免「敌人流血致死」后血之泪还白回 1 点血。
        if (target.isAlive() && StatusEffect.has(target.statusEffects, StatusEffect.Id.BLEED)) {
            int enemyBleed = StatusEffect.stacksOf(target.statusEffects, StatusEffect.Id.BLEED);
            if (enemyBleed > 0) {
                target.hp = Math.max(0, target.hp - enemyBleed);
                StatusEffect.removeStacks(target.statusEffects, StatusEffect.Id.BLEED, 1);
                log.add("[状态] " + target.name + " 流血：额外扣除 " + enemyBleed + " 点生命（"
                        + target.hp + "/" + target.maxHp + "）");
            }
        }

        // 锈蚀箭矢（0.2.8 归尘专属技巧）：所有远程伤害给目标敌人挂 2 层流血
        // 多段远程每段独立触发（calculateDamage 本身被循环包裹，每段都会走到这里）。
        if (type == Skill.Type.RANGED && player.hasCombatSkill(CombatSkill.Id.RUSTED_ARROW)) {
            StatusEffect.addStacks(target.statusEffects, StatusEffect.BLEED, 2, 3);
            log.add("[技巧] 锈蚀箭矢：" + target.name + " 被挂上 2 层流血");
        }

        // 切割（0.2.8 降临者技能）：有充能时，每次攻击给目标挂 3 层流血，充能 -1
        if (player.slashCharges > 0) {
            StatusEffect.addStacks(target.statusEffects, StatusEffect.BLEED, 3, 3);
            player.slashCharges--;
            log.add("[技能] 切割：" + target.name + " 被挂上 3 层流血（剩余 " + player.slashCharges + " 次）");
        }

        // 粗鄙之人（0.3.0 通用技巧）：攻击有 2% 概率晕眩目标敌人（放在扣血之后、血之泪之前）
        if (player.hasCombatSkill(CombatSkill.Id.CRUDE) && target.isAlive()
                && rng.nextDouble() < 0.02) {
            StatusEffect.addStacks(target.statusEffects, StatusEffect.STUN, 1, 1);
            log.add("[技巧] 粗鄙之人：" + target.name + " 被晕眩了");
        }

        // 血之泪：每段伤害回 1 血（按伤害次数算）
        if (player.hasTalent(Talent.Id.BLOOD_TEAR)) {
            player.heal(BLOOD_TEAR_HEAL);
        }

        // 血之回响：远程攻击每次伤害结算回 1 滴血（随心一箭 / 精准射击都吃）
        if (type == Skill.Type.RANGED && player.hasCombatSkill(CombatSkill.Id.RANGED_LIFESTEAL)) {
            player.heal(1);
            log.add("[技巧] 血之回响：远程技能恢复 1 点生命");
        }

        // 死气勃勃（0.3.0 墓渊天赋）：敌人被击杀时，墓渊回 2 血（不超上限）
        if (!target.isAlive() && player.hasTalent(Talent.Id.THRIVING_DEATH)) {
            int before = player.hp;
            player.heal(2);
            int gained = player.hp - before;
            if (gained > 0) {
                log.add("[天赋] 死气勃勃：敌人倒下，恢复 " + gained + " 点生命（HP "
                        + player.hp + "/" + player.maxHp + "）");
            }
        }
    }

    /** 对单个目标结算一次攻击（不带完美仿制开关，供归尘技能调用） */
    private void attackEnemy(Enemy target, int rawDamage, Skill.Type type, boolean isBasicAttack) {
        calculateDamage(target, rawDamage, type, isBasicAttack, false);
    }

    /** 对单个目标结算一次攻击（带完美仿制开关，供空梦技能调用） */
    private void attackEnemy(Enemy target, int rawDamage, Skill.Type type, boolean isBasicAttack, boolean perfectCopy) {
        calculateDamage(target, rawDamage, type, isBasicAttack, perfectCopy);
    }

    /**
     * 双重打击（归尘专属技巧）：随心一箭命中后额外攻击一次，固定造成 1 点伤害。
     * <p>
     * 不触发任何特效（不判暴击、不触发血之泪、不吃乘区），直接对目标扣 1 点血。
     * 用独立方法而不是走 {@link #calculateDamage}，因为它明确要「跳过一切加成」。
     */
    private void maybeDoubleHit(Enemy target) {
        if (!player.hasCombatSkill(CombatSkill.Id.DOUBLE_HIT)) {
            return;
        }
        if (!target.isAlive()) {
            return;   // 目标已死，没得追加
        }
        target.hp = Math.max(0, target.hp - 1);
        log.add("[技巧] 双重打击：额外攻击 1 点 -> " + target.hp + "/" + target.maxHp);
    }

    /**
     * 记一次天赋触发（用于面板金光闪烁），并打一条带前缀的日志。
     * <p>
     * 和「回血」分开：回血按伤害次数，闪烁按出手次数。两者拆开才能各管各的，
     * 不会出现「横扫打 2 个目标，回 2 血是对的，但闪 2 下很吵」这种互相打架的耦合。
     */
    private void notifyTalentTriggered(Talent.Id id, String name, String detail) {
        log.add("[天赋] " + name + "：" + detail);
        talentTriggers.merge(id, 1, Integer::sum);
    }

    // ==================== 查询 ====================

    public Player getPlayer() {
        return player;
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    /** 战斗日志。UI 直接按行画就行 */
    public List<String> getLog() {
        return log;
    }

    public State getState() {
        return state;
    }

    public int getRound() {
        return round;
    }

    /** 随机源（0.3.0 起公开，供敌人技能做概率判定，如侦查骑士 20% 晕眩） */
    public Random random() {
        return rng;
    }

    /** 标记本场有敌人逃跑（0.3.0 新手及时怯战） */
    public void markFled() {
        fled = true;
    }

    /** 本场是否有敌人逃跑（RunState.onBattleWin 据此决定金币 0、经验减半） */
    public boolean hasFled() {
        return fled;
    }

    public boolean allEnemiesDead() {
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /** 站位最靠前的活着的敌人（近战/默认目标） */
    public Enemy firstAliveEnemy() {
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                return enemy;
            }
        }
        return null;
    }

    /** 允许敌人往日志里写东西 */
    public void log(String line) {
        log.add(line);
    }

    /**
     * 给玩家挂一个负面效果，并触发「倒映」（0.2.8 空梦专属天赋）：
     * 若玩家拥有倒映，且这个效果不是晕眩，则给最前方敌人附加同样层数的同样效果。
     * <p>
     * 敌人技能给玩家上负面效果时**统一走这个方法**，不要直接
     * {@code StatusEffect.addStacks(player.statusEffects, ...)}，否则倒映不会触发。
     */
    public void inflictStatus(StatusEffect effect, int stacks, int duration) {
        StatusEffect.addStacks(player.statusEffects, effect, stacks, duration);
        // 倒映：非晕眩负面效果反弹给最前方敌人
        if (effect.id != StatusEffect.Id.STUN
                && player.hasTalent(Talent.Id.REFLECTION)) {
            Enemy target = firstAliveEnemy();
            if (target != null) {
                StatusEffect.addStacks(target.statusEffects, effect, stacks, duration);
                log.add("[天赋] 倒映：" + effect.name + " 反弹到了 " + target.name + " 身上");
            }
        }
    }

    private String enemyNames() {
        StringBuilder sb = new StringBuilder();
        for (Enemy enemy : enemies) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(enemy.name);
        }
        return sb.toString();
    }

    /**
     * 回合开始的状态摘要。
     * <p>
     * 每行前面加 {@code "# "}：这是给控制台看的，界面里左右面板已经把这些信息
     * 全画出来了，GameScreen 会把这些行过滤掉，避免中间日志被重复信息挤满。
     */
    private void printStatus() {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            Skill skill = player.slots[i];
            if (skill == null) {
                continue;
            }
            bar.append("[").append(i).append("]").append(skill.name);
            if (player.cdDisplay[i] > 0) {
                bar.append("(CD").append(player.cdDisplay[i]).append(")");
            } else if (player.mp < skill.mpCost) {
                bar.append("(蓝不足)");
            }
            bar.append("  ");
        }

        log.add("# " + player.name + "  HP " + player.hp + "/" + player.maxHp + "  MP " + player.mp + "/" + player.maxMp
                + "  护盾 " + player.shield);
        log.add("# 技能  " + bar.toString().trim());

        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                log.add("# " + enemy.name + "  已倒下");
                continue;
            }
            log.add("# " + enemy.name + "  HP " + enemy.hp + "/" + enemy.maxHp + "  防御 " + enemy.defense
                    + "  意图：" + enemy.getIntent());
        }
    }
}
