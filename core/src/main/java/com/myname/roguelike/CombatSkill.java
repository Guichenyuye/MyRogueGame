package com.myname.roguelike;

/**
 * 战斗技巧（量变类被动）。
 * <p>
 * 和 {@link Talent} 的区别只在「分量」和「来源」，数据结构故意做成一样的：
 * <ul>
 *   <li>天赋：只在精英节点产出，改的是玩法（质变），金色标题</li>
 *   <li>技巧：三选一产出，数量多，改的是数值（量变），浅蓝色标题</li>
 * </ul>
 * <p>
 * 0.2.3 起技巧分为「通用」与「角色专属」两类：{@link #owner} 为 null 表示通用，
 * 非 null 表示只有该角色能抽到，为以后加新角色（各有自己的专属技巧池）打底。
 * <p>
 * 0.2.3 中段：普攻改名「随心一箭」并改成远程类型，删除了两个普攻专属的数值技巧
 * （近战强化·普攻 x1.1 / 武器打磨·普攻 +1），换成「随性」（随心一箭暴击率 +10%）。
 * <p>
 * 触发时机写在 {@link BattleSystem} 的伤害管线里，技巧名用 {@link Id} 分发。
 */
public class CombatSkill {

    /** 技巧 ID：BattleSystem 用它判断「该不该生效」 */
    public enum Id {
        // ---- 通用（所有角色都能抽）----
        /** 铁壁防御：防御时额外获得 1 点护盾 */
        DEFEND_BONUS_SHIELD,
        /** 远程强化：远程技能伤害 x1.1 */
        RANGED_UP,
        /** 近战强化：近战技能伤害 x1.1 */
        MELEE_UP,
        /** 持久战意：战斗开始后每 3 回合额外恢复 1 点精力 */
        MP_REGEN,
        /** 范围强化：AOE 技能伤害 x1.05 */
        AOE_UP,
        /** 熟练施法：近战技能 CD >= 3 时，CD 减 1 */
        CD_REDUCE,
        // ---- 归尘专属 ----
        /** 双重打击：随心一箭额外攻击一次（固定 1 点伤害，不触发其他特效） */
        DOUBLE_HIT,
        /** 血之回响：远程技能恢复 1 滴血 */
        RANGED_LIFESTEAL,
        /** 随性：随心一箭暴击率 +10% */
        FREE_CRIT,
        // ---- 归尘专属（0.2.8 新增）----
        /** 锈蚀箭矢：所有远程伤害造成 2 层流血（多段远程每段独立触发） */
        RUSTED_ARROW
    }

    public final Id id;
    public final String name;
    /** 一句话说明。面板放不下，留给悬停浮窗和控制台自检 */
    public final String desc;
    /**
     * 归属角色（0.2.5 起）。{@code null} = 通用（所有角色都能抽），
     * 非 null = 该角色的专属技巧，只有这个角色能抽到。
     */
    public final CharacterType owner;

    private CombatSkill(Id id, String name, String desc, CharacterType owner) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.owner = owner;
    }

    // ================= 通用技巧（6 个）=================

    public static final CombatSkill DEFEND_BONUS_SHIELD = new CombatSkill(
            Id.DEFEND_BONUS_SHIELD, "铁壁防御", "防御时额外获得 1 点护盾", null);
    public static final CombatSkill RANGED_UP = new CombatSkill(
            Id.RANGED_UP, "远程强化", "远程技能伤害 x1.1", null);
    public static final CombatSkill MELEE_UP = new CombatSkill(
            Id.MELEE_UP, "近战强化", "近战技能伤害 x1.1", null);
    public static final CombatSkill MP_REGEN = new CombatSkill(
            Id.MP_REGEN, "持久战意", "每 3 回合额外恢复 1 点精力", null);
    public static final CombatSkill AOE_UP = new CombatSkill(
            Id.AOE_UP, "范围强化", "AOE 技能伤害 x1.05", null);
    public static final CombatSkill CD_REDUCE = new CombatSkill(
            Id.CD_REDUCE, "熟练施法", "近战技能 CD >= 3 时，CD 减 1", null);

    // ================= 归尘专属（3 个）=================

    public static final CombatSkill DOUBLE_HIT = new CombatSkill(
            Id.DOUBLE_HIT, "双重打击", "随心一箭额外攻击一次（固定 1 点伤害，不触发特效）", CharacterType.GUICHEN);
    public static final CombatSkill RANGED_LIFESTEAL = new CombatSkill(
            Id.RANGED_LIFESTEAL, "血之回响", "远程技能恢复 1 滴血", CharacterType.GUICHEN);
    public static final CombatSkill FREE_CRIT = new CombatSkill(
            Id.FREE_CRIT, "随性", "随心一箭暴击率 +10%", CharacterType.GUICHEN);

    /** 锈蚀箭矢：所有远程伤害造成 2 层流血（0.2.8 归尘专属） */
    public static final CombatSkill RUSTED_ARROW = new CombatSkill(
            Id.RUSTED_ARROW, "锈蚀箭矢", "所有远程伤害额外造成 2 层流血", CharacterType.GUICHEN);

    /** 全部技巧，三选一随机抽取时从这里遍历 */
    public static final CombatSkill[] ALL = {
            DEFEND_BONUS_SHIELD, RANGED_UP, MELEE_UP,
            MP_REGEN, AOE_UP, CD_REDUCE,
            DOUBLE_HIT, RANGED_LIFESTEAL, FREE_CRIT,
            RUSTED_ARROW
    };
}
