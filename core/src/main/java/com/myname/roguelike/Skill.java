package com.myname.roguelike;

/**
 * 技能的数据定义（静态面板，所有角色共享同一份）。
 * <p>
 * 这里只放“不会变”的数据：名字、精力消耗、CD、释放方式。
 * “这个角色还有几回合冷却”这种状态放在 {@link Player} 里，两边不混在一起。
 * <p>
 * 将来接 JSON 配置表时，只需要把下面的 4 个常量换成从文件读出来的对象，
 * BattleSystem 里的 switch 不用动。
 */
public class Skill {

    /** 释放方式（文档 3.3） */
    public enum Type {
        MELEE,   // 近战：打最前面的敌人
        RANGED,  // 远程：可以任选目标
        AOE,     // 群体：打全体
        DEFEND   // 防御：不造成伤害
    }

    /** 技能 ID：BattleSystem 用它分发逻辑，将来对应 JSON 里的 id 字段 */
    public enum Id {
        NORMAL_ATTACK, DEFEND, PRECISE_SHOT, SWEEP,
        // 0.2.3 可替换技能
        CHARGE,        // 蓄力：耗 2 精力 CD2，获得蓄力 Buff，下次攻击 x2.5
        MULTI_SHOT,    // 多重射击：耗 4 精力 CD2 AOE，精准 x1.5 + 5
        BLOOD_SPIKE,   // 血刺：耗 3 精力 CD1 近战多段，3 次 力量 x0.2 + 1
        // 0.2.5 空梦技能
        MIRROR_PIERCE, // 镜像穿刺：耗 0 精力 CD0 近战，2 次 力量 x1.1 + 1
        DISGUISE,      // 伪装：耗 1 精力 CD0 防御，护盾 = 防御 x1.5
        PERFECT_COPY,  // 完美仿制：耗 3 精力 CD2，下次攻击变 3 段且每段 x0.7
        MIRROR_FLASH,  // 镜光一瞬：耗 4 精力 CD2 AOE，力量 x1.2 + 精准 x1.1
        // 0.2.8 降临者技能
        SWORD_STRIKE,  // 剑击：耗 0 精力 CD0 近战，力量 x1.2
        BLOCK,         // 格挡：耗 1 精力 CD0 防御，护盾 = 3 + 防御 x1.5
        EAT_MY_SWORD,  // 吃我一剑：耗 3 精力 CD2 远程，精准 x5，自身下回合眩晕
        SLOW_BURN,     // 厚积薄发：耗 1 精力 CD2 防御，下次攻击 +150%（x2.5）
        SLASH,         // 切割：耗 2 精力 CD2 防御，接下来 3 次攻击附加 3 层流血（降临者替换技能）
        // 0.3.0 墓渊技能
        FLASH_THRUST,  // 瞬身突刺：耗 0 精力 CD0 近战，力量x1.5 + 精准x1.2
        SOUL_WREATH,   // 魂绕：耗 1 精力 CD0 防御，护盾 = 召唤物数量x3 + 防御x1.5
        SUMMON_ZOMBIE, // 唤起：耗 3 精力 CD3，召唤一个普通僵尸
        DEATH_WHIRL,   // 死之旋风：耗 4 精力 CD3 AOE，力量x2
        SUMMON_GHOST,  // 后方支援：耗 5 精力 CD4，召唤一个幽魂（墓渊替换技能）
        DEATH_SURGE    // 死亡激荡：耗 4 精力 CD3 远程，对最低血敌人 5 + 精准x1.5（墓渊替换技能）
    }

    public final Id id;
    public final String name;
    public final Type type;
    /** 精力消耗 */
    public final int mpCost;
    /** 冷却回合数（文档 2.4：CD1 = 释放后隔 1 回合可用） */
    public final int cd;
    public final String desc;
    /**
     * 归属角色（0.2.8 起）。{@code null} = 通用（所有角色都能作为替换技能抽到），
     * 非 null = 该角色的专属技能，只有这个角色能从精英战奖励里抽到。
     * 初始四技能由 {@link CharacterData} 按角色注入，不参与这里的抽取过滤；
     * 这个字段只约束「可替换技能池」的抽取（见 {@code RunState#rollUnownedReplSkill()}）。
     */
    public final CharacterType owner;

    /** 技能等级上限（0~5，共 6 级） */
    public static final int MAX_LEVEL = 5;

    private Skill(Id id, String name, Type type, int mpCost, int cd, String desc) {
        this(id, name, type, mpCost, cd, desc, null);
    }

    private Skill(Id id, String name, Type type, int mpCost, int cd, String desc, CharacterType owner) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.mpCost = mpCost;
        this.cd = cd;
        this.desc = desc;
        this.owner = owner;
    }

    /**
     * 该技能升到 {@code level} 级时，比 0 级多打多少伤害。
     * <p>
     * 这里先做成「每级统一 +2」——框架先跑通，将来给每个技能设计差异化成长
     * （比如精准射击每级 +3、横扫每级 +1 但加目标数）时，只改这个方法里的
     * 分发表，BattleSystem 和界面都不用动。
     */
    public int levelBonusDamage(int level) {
        return level * 2;
    }

    // ================= 归尘初始四技能（文档 3.4）=================

    /** 槽 1：随心一箭（普攻），锁死，0 精力 CD0，远程类型 */
    public static final Skill NORMAL_ATTACK = new Skill(
            Id.NORMAL_ATTACK, "随心一箭", Type.RANGED, 0, 0,
            "伤害 = 精准x1.2 + 5，触发血之泪");

    /** 槽 2：防御，锁死，耗 1 精力 CD0，护盾 = 防御x2 */
    public static final Skill DEFEND = new Skill(
            Id.DEFEND, "防御", Type.DEFEND, 1, 0,
            "获得护盾 = 防御x2，持续到本回合敌方行动结束");

    /** 槽 3：精准射击，耗 3 精力 CD2，远程 */
    public static final Skill PRECISE_SHOT = new Skill(
            Id.PRECISE_SHOT, "精准射击", Type.RANGED, 3, 2,
            "伤害 = 精准x1.5 + 10，可自选目标");

    /** 槽 4：横扫，耗 2 精力 CD1，AOE */
    public static final Skill SWEEP = new Skill(
            Id.SWEEP, "横扫", Type.AOE, 2, 1,
            "对每个敌人造成 力量x1.0 + 6 伤害");

    // ================= 0.2.3 可替换技能 ==================

    /** 蓄力：耗 2 精力 CD2，获得蓄力 Buff，下次攻击伤害 x2.5（归尘专属替换技能） */
    public static final Skill CHARGE = new Skill(
            Id.CHARGE, "蓄力", Type.DEFEND, 2, 2,
            "获得蓄力 Buff，下次攻击伤害 x2.5", CharacterType.GUICHEN);

    /** 多重射击：耗 4 精力 CD2 AOE，对所有敌人造成 精准x1.5 + 5（归尘专属替换技能） */
    public static final Skill MULTI_SHOT = new Skill(
            Id.MULTI_SHOT, "多重射击", Type.AOE, 4, 2,
            "对所有敌人造成 精准x1.5 + 5 伤害", CharacterType.GUICHEN);

    /** 血刺：耗 3 精力 CD1 近战多段，对最前方敌人造成 3 次 力量x0.2 + 1（归尘专属替换技能） */
    public static final Skill BLOOD_SPIKE = new Skill(
            Id.BLOOD_SPIKE, "血刺", Type.MELEE, 3, 1,
            "对最前方敌人造成 3 次 力量x0.2 + 1 伤害", CharacterType.GUICHEN);

    // ================= 0.2.5 空梦技能 ==================

    /** 镜像穿刺：耗 0 精力 CD0 近战，对最前方敌人造成 2 次 力量x1.1 + 1 */
    public static final Skill MIRROR_PIERCE = new Skill(
            Id.MIRROR_PIERCE, "镜像穿刺", Type.MELEE, 0, 0,
            "对最前方敌人造成 2 次 力量x1.1 + 1 伤害");

    /** 伪装：耗 1 精力 CD0 防御，护盾 = 防御x1.5 */
    public static final Skill DISGUISE = new Skill(
            Id.DISGUISE, "伪装", Type.DEFEND, 1, 0,
            "获得护盾 = 防御x1.5，持续到本回合敌方行动结束");

    /** 完美仿制：耗 3 精力 CD2，下次攻击变 3 段且每段 x0.7 */
    public static final Skill PERFECT_COPY = new Skill(
            Id.PERFECT_COPY, "完美仿制", Type.DEFEND, 3, 2,
            "下次攻击伤害 x0.7，但重复结算 3 次");

    /** 镜光一瞬：耗 4 精力 CD2 AOE，对所有敌人造成 力量x1.2 + 精准x1.1 */
    public static final Skill MIRROR_FLASH = new Skill(
            Id.MIRROR_FLASH, "镜光一瞬", Type.AOE, 4, 2,
            "对所有敌人造成 力量x1.2 + 精准x1.1 伤害");

    // ================= 0.2.8 降临者技能 ==================

    /** 剑击：耗 0 精力 CD0 近战，对最前方敌人造成 力量x1.2 */
    public static final Skill SWORD_STRIKE = new Skill(
            Id.SWORD_STRIKE, "剑击", Type.MELEE, 0, 0,
            "对最前方敌人造成 力量x1.2 伤害");

    /** 格挡：耗 1 精力 CD0 防御，护盾 = 3 + 防御x1.5 */
    public static final Skill BLOCK = new Skill(
            Id.BLOCK, "格挡", Type.DEFEND, 1, 0,
            "获得护盾 = 3 + 防御x1.5，持续到本回合敌方行动结束");

    /** 吃我一剑：耗 3 精力 CD2 远程，精准x5，自身下回合眩晕 */
    public static final Skill EAT_MY_SWORD = new Skill(
            Id.EAT_MY_SWORD, "吃我一剑", Type.RANGED, 3, 2,
            "伤害 = 精准x5，但自身下回合眩晕（斩杀技）");

    /** 厚积薄发：耗 1 精力 CD2 防御，下次攻击伤害 +150%（即 x2.5） */
    public static final Skill SLOW_BURN = new Skill(
            Id.SLOW_BURN, "厚积薄发", Type.DEFEND, 1, 2,
            "下次攻击伤害 +150%（最终伤害 x2.5）");

    /** 切割：耗 2 精力 CD2 防御，接下来 3 次攻击附加 3 层流血（降临者专属替换技能） */
    public static final Skill SLASH = new Skill(
            Id.SLASH, "切割", Type.DEFEND, 2, 2,
            "接下来 3 次攻击对目标附加 3 层流血", CharacterType.DESCENDER);

    // ================= 0.3.0 墓渊技能 ==================

    /** 瞬身突刺：耗 0 精力 CD0 近战，对最前方敌人造成 力量x1.5 + 精准x1.2 伤害 */
    public static final Skill FLASH_THRUST = new Skill(
            Id.FLASH_THRUST, "瞬身突刺", Type.MELEE, 0, 0,
            "对最前方敌人造成 力量x1.5 + 精准x1.2 伤害");

    /** 魂绕：耗 1 精力 CD0 防御，护盾 = 召唤物数量x3 + 防御x1.5 */
    public static final Skill SOUL_WREATH = new Skill(
            Id.SOUL_WREATH, "魂绕", Type.DEFEND, 1, 0,
            "获得护盾 = 召唤物数量x3 + 防御x1.5");

    /** 唤起：耗 3 精力 CD3，召唤一个普通僵尸（血 = 精准x4） */
    public static final Skill SUMMON_ZOMBIE = new Skill(
            Id.SUMMON_ZOMBIE, "唤起", Type.DEFEND, 3, 3,
            "召唤一个普通僵尸（血量 = 精准x4）");

    /** 死之旋风：耗 4 精力 CD3 AOE，对所有敌人造成 力量x2 伤害 */
    public static final Skill DEATH_WHIRL = new Skill(
            Id.DEATH_WHIRL, "死之旋风", Type.AOE, 4, 3,
            "对所有敌人造成 力量x2 伤害");

    /** 后方支援：耗 5 精力 CD4，召唤一个幽魂（血 = 精准x5）（墓渊专属替换技能） */
    public static final Skill SUMMON_GHOST = new Skill(
            Id.SUMMON_GHOST, "后方支援", Type.DEFEND, 5, 4,
            "召唤一个幽魂（血量 = 精准x5）", CharacterType.MUYUAN);

    /** 死亡激荡：耗 4 精力 CD3 远程，对最低血量敌人造成 5 + 精准x1.5（墓渊专属替换技能） */
    public static final Skill DEATH_SURGE = new Skill(
            Id.DEATH_SURGE, "死亡激荡", Type.RANGED, 4, 3,
            "对血量最低的敌人造成 5 + 精准x1.5 伤害", CharacterType.MUYUAN);

    @Override
    public String toString() {
        return name + "(耗" + mpCost + " CD" + cd + ")";
    }
}
