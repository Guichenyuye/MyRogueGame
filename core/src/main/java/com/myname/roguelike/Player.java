package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家角色「归尘」。
 * <p>
 * 纯 Java，不 import 任何 libGDX 的类 —— 这样数值逻辑可以在控制台里单独跑，
 * 后面接 GameScreen 时直接拿对象读字段画字就行。
 * <p>
 * 字段含义对照设计文档：2.2 属性 / 2.3 精力 / 2.4 冷却 / 2.6 护盾 / 3.1 技能槽。
 */
public class Player {

    public static final int SLOT_COUNT = 4;

    public String name = "归尘";

    /** 角色类型（0.2.7）：界面层用它去 Main 查头像/背景纹理 */
    public CharacterType character = CharacterType.GUICHEN;

    // ===== 基础面板（文档 3.5 初始数值，0.2.5 起由 CharacterData 按角色注入）=====
    public int maxHp = 80;
    public int hp = 80;
    public int maxMp = 10;
    public int mp = 10;

    public int strength = 5;   // 力量：影响横扫 / 镜像穿刺 / 镜光一瞬
    public int precision = 5;  // 精准：影响随心一箭 / 精准射击 / 多重射击
    public int defense = 5;    // 防御：影响减伤 + 护盾量

    // ===== 暴击：所有角色固定 5% / 150%，不可提升（文档 2.2）=====
    public double critRate = 0.05;
    public double critDamage = 1.50;

    // ===== 护盾（文档 2.6）=====
    /** 当前护盾值。刷新机制，不叠加 */
    public int shield = 0;
    /** 护盾还能顶几个“敌方回合”。默认 1，撑过敌方回合后清零 */
    public int shieldDuration = 0;

    // ===== 技能槽（文档 3.1）：0 槽1(锁) 1 槽2(锁) 2 槽A 3 槽B =====
    /**
     * 用数组而不是 4 个独立字段：槽 3/槽 4 在精英节点会被整体替换，
     * 数组才能用循环处理冷却，也不会出现“换了技能忘了清 CD”。
     * <p>
     * 0.2.5 起初始四技能由 {@link CharacterData} 按角色注入，不再写死。
     */
    public final Skill[] slots = new Skill[SLOT_COUNT];

    /** 真实剩余冷却：释放技能后 = 该技能的 CD 值 */
    private final int[] cds = new int[SLOT_COUNT];

    /**
     * 判定/显示用的冷却（文档 2.4 的做法：回合开始时先把当前 CD 抄进这里，再把真实 CD 减 1）。
     * 这样 CD1 的效果正好是「本回合用完，下回合不可用，再下回合可用」。
     */
    public final int[] cdDisplay = new int[SLOT_COUNT];

    // ===== 成长：天赋（质变）与战斗技巧（量变）=====
    // 用对象而不是 List<String>：第二步要做「悬停看详情」，只有名字的话
    // 加 desc 时整条链路（面板、浮窗、控制台自检）都要重写一遍。
    /** 天赋：初始带「血之泪」，之后只在精英节点获得 */
    public final List<Talent> talents = new ArrayList<>();

    /** 战斗技巧：初始为空，靠三选一 / 商店积累 */
    public final List<CombatSkill> combatSkills = new ArrayList<>();

    /**
     * 玩家身上挂着的负面效果 / 诅咒实例（0.2.6 数值框架）。
     * 效果定义见 {@link StatusEffect}，这里存实例（层数 + 剩余回合）。
     * 敌人或事件通过 {@link StatusEffect#addStacks} 往这里加状态。
     * <p>
     * 0.2.8 起诅咒独立成 {@link Curse}，不再混在这个列表里，见 {@link #curses}。
     */
    public final List<StatusEffect.Instance> statusEffects = new ArrayList<>();

    /**
     * 本局持续、不可消除的诅咒（0.2.8）。
     * 由事件（如「遇见邪教徒」）施加，跨战斗保留。定义见 {@link Curse}。
     */
    public final List<Curse> curses = new ArrayList<>();

    /** 四个技能各自的等级（0~5）。技能本身是共享常量，等级是每个玩家的私有状态 */
    public final int[] skillLevels = new int[SLOT_COUNT];

    /**
     * 天赋触发时的闪光颜色（RGB，各分量 0~1）。默认红色（受击/增益的直观反馈）。
     * <p>
     * 为什么用三个 float 而不是 libGDX 的 {@code Color}：本类是纯 Java，
     * 不 import 图形库，控制台自检才能直接跑。界面层（GameScreen）拿到这三个值
     * 再组装成 Color。以后不同角色可以设成不同的闪光色，强化角色辨识度。
     */
    public float talentFlashR = 1f;
    public float talentFlashG = 0.20f;
    public float talentFlashB = 0.20f;

    /**
     * 「慵懒之徒」的 Buff：使用防御技能后置为 true，下次攻击结算时消耗并 x1.2。
     * 只保留一个布尔，不叠层 —— 防御两次也还是下一次攻击 x1.2。
     */
    public boolean lazyBuff = false;

    /**
     * 「蓄力」技能的 Buff：使用蓄力后置为 true，下次攻击结算时消耗并 x2.5。
     */
    public boolean chargeBuff = false;

    /**
     * 「完美仿制」技能的 Buff（0.2.5 空梦）：使用后置为 true，下一次攻击
     * 变 3 段、每段 x0.7，在出手时消耗。
     */
    public boolean perfectCopyBuff = false;

    /**
     * 「厚积薄发」技能的 Buff（0.2.8 降临者）：使用后置为 true，下一次攻击
     * 伤害 +150%（最终伤害 x2.5），在出手时消耗。
     */
    public boolean slowBurnBuff = false;

    /**
     * 「切割」技能的 Buff（0.2.8 降临者）：使用后置为 3，接下来每次攻击结算时
     * 给目标敌人附加 3 层流血，并 -1。归 0 后失效。
     */
    public int slashCharges = 0;

    /**
     * 「镜中人」天赋（0.2.6 空梦）本场已触发免疫的次数。
     * 每场战斗最多免疫 2 次，超过 2 次不再生效。战斗开始时重置为 0。
     */
    public int mirrorAvatarTriggerCount = 0;

    /**
     * 「降临者」天赋（0.2.8 降临者）本局是否已经复活过。
     * 整局只能复活 1 次：第一次死亡时以 30% 最大生命原地复活，之后不再触发。
     */
    public boolean descenderRevived = false;

    /**
     * 「陈年宝箱」（宝箱怪技能）给玩家挂的 Buff：下次受击附带 3 层中毒。
     * 在 {@link BattleSystem#damagePlayer} 里消耗。
     */
    public boolean poisonOnNextHit = false;

    /**
     * 玩家持有的金币（0.2.8 起从 RunState 迁移到 Player，金币是玩家拥有的资源）。
     * 宝箱怪的「洗劫」直接扣这里；胜利发金币也累加到这里。
     */
    public int gold = 0;

    /**
     * 已过的玩家回合数（startTurn 递增一次）。「持久战意」技巧用它在每 3 回合额外回蓝。
     */
    public int turnCount = 0;

    /** 默认归尘（保持向后兼容，BattleTest 里裸 new Player() 仍是归尘面板） */
    public Player() {
        this(CharacterType.GUICHEN);
    }

    /**
     * 按角色初始化面板、技能槽和初始天赋（0.2.5）。
     * <p>
     * 面板、初始技能、初始天赋全部来自 {@link CharacterData}，Player 不再硬编码任何角色数值，
     * 以后加新角色只要在 CharacterData 里补一条数据。
     */
    public Player(CharacterType character) {
        CharacterData data = CharacterData.of(character);
        if (data == null) {
            data = CharacterData.GUICHEN;   // 未知角色兜底成归尘
        }
        this.character = data.type;
        this.name = data.name;
        this.maxHp = data.maxHp;
        this.hp = data.maxHp;
        this.maxMp = data.maxMp;
        this.mp = data.maxMp;
        this.strength = data.strength;
        this.precision = data.precision;
        this.defense = data.defense;
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = data.initialSkills[i];
        }
        for (Talent t : data.initialTalents) {
            talents.add(t);
        }
    }

    /**
     * 降临者专属构造器（0.2.8 捏脸）：力量/精准/防御由玩家分配，生命固定 50、精力固定 10。
     * <p>
     * 技能和天赋仍来自 {@link CharacterData#DESCENDER} 模板，只有三维被捏脸结果覆盖。
     *
     * @param strength  分配后的力量
     * @param precision 分配后的精准
     * @param defense   分配后的防御
     */
    public static Player createDescender(int strength, int precision, int defense) {
        Player p = new Player(CharacterType.DESCENDER);
        p.strength = strength;
        p.precision = precision;
        p.defense = defense;
        // 生命/精力已由 CharacterData.DESCENDER 设定为 50/10，这里无需再改
        return p;
    }

    /** 有没有某个天赋。天赋变成数据后，血之泪不能写死，得先问它还在不在 */
    public boolean hasTalent(Talent.Id id) {
        for (Talent t : talents) {
            if (t.id == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获得一个天赋。统一走这个方法，不要直接 {@code talents.add(...)}：
     * <p>
     * 如果已有同一天赋，则不重复给（返回 false）。
     * 0.2.3 起天赋池里的天赋都是战斗中被动，没有「获得时立即生效」的类型了，
     * 但保留这个方法作为统一入口，将来加获得类天赋不用改调用方。
     */
    public boolean grantTalent(Talent talent) {
        if (talent == null || hasTalent(talent.id)) {
            return false;
        }
        talents.add(talent);
        return true;
    }

    /** 有没有某个战斗技巧。技巧效果在 BattleSystem 里判断，这里只提供查询 */
    public boolean hasCombatSkill(CombatSkill.Id id) {
        for (CombatSkill skill : combatSkills) {
            if (skill.id == id) {
                return true;
            }
        }
        return false;
    }

    /** 给第 slot 个技能升一级（不会超过 MAX_LEVEL） */
    public void upgradeSkill(int slot) {
        if (slot >= 0 && slot < SLOT_COUNT && skillLevels[slot] < Skill.MAX_LEVEL) {
            skillLevels[slot]++;
        }
    }

    /**
     * 只有槽 2（精准射击）和槽 3（横扫）能被替换；槽 0（随心一箭）、槽 1（防御）锁死。
     */
    public boolean isReplaceableSlot(int slot) {
        return slot == 2 || slot == 3;
    }

    /**
     * 用新技能替换第 slot 个槽位的技能，返回被替换下来的旧技能（放回技能池）。
     * <p>
     * 替换同时清掉该槽的冷却和等级（新技能从 0 级、0 冷却开始），
     * 避免「换了技能还带着旧技能的 CD / 等级」这种只改一半的 bug。
     */
    public Skill replaceSkill(int slot, Skill newSkill) {
        if (slot < 0 || slot >= SLOT_COUNT || newSkill == null) {
            return null;
        }
        Skill old = slots[slot];
        slots[slot] = newSkill;
        cds[slot] = 0;
        cdDisplay[slot] = 0;
        skillLevels[slot] = 0;
        return old;
    }

    // ==================== 回合流程 ====================

    /**
     * 玩家回合开始：精力回复 1 点，然后结算冷却（文档 2.3 / 2.4）。
     * 注意：护盾不在这里过期，而是在敌方回合结束时结算，见 {@link #onEnemyTurnEnd()}。
     */
    public void startTurn() {
        turnCount++;
        // 精打细算类技巧已废除；基础回蓝 1 点
        int regen = 1;
        // 持久战意：每 3 回合额外恢复 1 点精力
        if (hasCombatSkill(CombatSkill.Id.MP_REGEN) && turnCount % 3 == 0) {
            regen += 1;
        }
        mp = Math.min(maxMp, mp + regen);

        for (int i = 0; i < SLOT_COUNT; i++) {
            cdDisplay[i] = cds[i];
            if (cds[i] > 0) {
                cds[i]--;
            }
        }
    }

    /** 释放完技能后调用，让该槽进入冷却。熟练施法（CD >= 3 的近战技能）在这里减 1 */
    public void onSkillUsed(int slot) {
        int cd = slots[slot].cd;
        // 熟练施法：近战技能 CD >= 3 时，CD 减 1
        if (hasCombatSkill(CombatSkill.Id.CD_REDUCE)
                && slots[slot].type == Skill.Type.MELEE
                && cd >= 3) {
            cd -= 1;
        }
        cds[slot] = cd;
    }

    /**
     * 战斗胜利时把所有冷却清零（0.2 需求第 1 条：CD 跨节点重置）。
     * <p>
     * 做成方法而不是把 {@code cds} 改成 public 的原因：真实的冷却有两个数组，
     * 只清 {@code cds} 会漏掉 {@code cdDisplay}，界面上就会显示一个已经不存在的 CD。
     * 这种「改了一半」的 bug 以前在换技能槽时踩过（换了技能忘了清 CD），
     * 所以把「重置」这件事封成一个动作，调用方没法只做一半。
     */
    public void resetCooldowns() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            cds[i] = 0;
            cdDisplay[i] = 0;
        }
    }

    // ==================== 状态查询 ====================

    public boolean isAlive() {
        return hp > 0;
    }

    /**
     * 该槽位现在能不能放。
     *
     * @return null 表示可以放；否则返回给玩家看的原因
     */
    public String whyCannotUse(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT || slots[slot] == null) {
            return "该槽位没有技能";
        }
        if (!isAlive()) {
            return "你已经倒下了";
        }
        Skill skill = slots[slot];
        if (cdDisplay[slot] > 0) {
            return "【" + skill.name + "】冷却中，还需 " + cdDisplay[slot] + " 回合";
        }
        if (mp < skill.mpCost) {
            return "精力不足：【" + skill.name + "】需要 " + skill.mpCost + " 点，当前 " + mp + " 点";
        }
        return null;
    }

    public boolean canUse(int slot) {
        return whyCannotUse(slot) == null;
    }

    // ==================== 数值变更 ====================

    public void takeDamage(int damage) {
        hp = Math.max(0, hp - damage);
    }

    public void heal(int value) {
        hp = Math.min(maxHp, hp + value);
    }

    /** 被扣精力（史莱姆「包裹」），不会扣成负数 */
    public void drainMp(int value) {
        mp = Math.max(0, mp - value);
    }

    /**
     * 加 1 点属性到指定项（0.2 属性点分配）。
     * 四种：0 力量 / 1 精准 / 2 防御 / 3 生命。
     * <p>
     * 生命 +1 = 最大生命 +5（加生命上限的收益不能和加力量一样薄，
     * 否则没人会选它；同时当前生命也 +5，避免「加了上限血量反而空了一截」的违和感）。
     */
    public void addAttributePoint(int attr) {
        switch (attr) {
            case 0 -> strength++;
            case 1 -> precision++;
            case 2 -> defense++;
            case 3 -> {
                maxHp += 5;
                hp += 5;
            }
            default -> {
                // 未知属性，忽略
            }
        }
    }

    /** 上护盾：刷新而不是叠加（文档 2.6） */
    public void setShield(int value, int duration) {
        shield = value;
        shieldDuration = duration;
    }

    /**
     * 一个「敌方回合」结束时结算护盾寿命。
     * 文档 2.6：默认持续 1 回合，本回合敌人行动结束就消失，没被打完也清空。
     *
     * @return 护盾是否在这一刻消散（用于打日志）
     */
    public boolean onEnemyTurnEnd() {
        if (shieldDuration <= 0) {
            return false;
        }
        shieldDuration--;
        if (shieldDuration <= 0 && shield > 0) {
            shield = 0;
            return true;
        }
        return false;
    }
}
