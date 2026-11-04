package com.myname.roguelike;

/**
 * 天赋（被动效果）的静态定义。
 * <p>
 * 和 {@link Skill} 同一个思路：这里只放"不会变"的数据，触发状态由
 * {@link BattleSystem} 记，展示归界面。
 * <p>
 * 0.2.3 起天赋池整体重做：废除旧的铁壁 / 狂暴 / 贪婪 / 顽强，
 * 统一为 6 个「改玩法（质变）」的被动。天赋只在精英节点产出（随机抽 1 个，展示给玩家看）。
 */
public class Talent {

    /** 天赋 ID：BattleSystem 用它记触发次数，将来对应 JSON 里的 id 字段 */
    public enum Id {
        /** 血之泪：每次造成伤害恢复 1 点生命（初始自带） */
        BLOOD_TEAR,
        /** 脱离掌控：HP < 20% 时，造成伤害 x2.5、受到伤害 x1.2 */
        OUT_OF_CONTROL,
        /** 慵懒之徒：使用防御技能后，获得「下次攻击伤害 x1.2」的 Buff */
        LAZY,
        /** 箭术大师：远程技能伤害 x1.5 */
        ARCHERY_MASTER,
        /** 温和之人：近战技能伤害 x0.6，但进行 2 次伤害结算 */
        GENTLE,
        /** 不惧疼痛：每次造成伤害前扣除自身 2 点生命，该次伤害 x1.1 */
        FEARLESS,
        /** 欢愉之礼：每次造成伤害时额外附加 1~2 点固定伤害（0.2.5 空梦专属，0.2.6 下调为 1~2） */
        JOY_GIFT,
        /** 镜中人：受击时 10% 概率免疫本次伤害，每场战斗最多触发 2 次（0.2.6 空梦专属） */
        MIRROR_AVATAR,
        /** 降临者：第一次死亡时立即以 30% 最大生命复活，整局限 1 次（0.2.8 降临者专属） */
        DESCENDER,
        /** 倒映：自身获得非晕眩负面效果时，给最前方敌人附加同样负面效果（0.2.8 空梦专属） */
        REFLECTION,
        /** 天佑：暴击率 +10%（0.2.8 降临者专属） */
        HEAVENLY_BLESSING
    }

    public final Id id;
    public final String name;
    /** 一句话说明，显示在战斗界面左面板 */
    public final String desc;
    /**
     * 归属角色（0.2.5 起天赋按角色完全专属，没有通用天赋）。
     * 精英节点 / 宝箱 / 祭坛抽取时，只会从「当前角色 owner 匹配」的天赋里抽。
     */
    public final CharacterType owner;

    private Talent(Id id, String name, String desc, CharacterType owner) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.owner = owner;
    }

    /** 血之泪：造成伤害时回 1 血（文档 2.7，按伤害次数结算） */
    public static final Talent BLOOD_TEAR = new Talent(
            Id.BLOOD_TEAR, "血之泪", "每次造成伤害恢复 1 点生命", CharacterType.GUICHEN);

    /** 脱离掌控：HP < 20% 时伤害 x2.5、受伤 x1.2 */
    public static final Talent OUT_OF_CONTROL = new Talent(
            Id.OUT_OF_CONTROL, "脱离掌控", "HP 低于 20% 时，伤害 x2.5、受伤 x1.2", CharacterType.GUICHEN);

    /** 慵懒之徒：防御后下次攻击伤害 x1.2 */
    public static final Talent LAZY = new Talent(
            Id.LAZY, "慵懒之徒", "使用防御后，下次攻击伤害 x1.2", CharacterType.GUICHEN);

    /** 箭术大师：远程技能伤害 x1.5 */
    public static final Talent ARCHERY_MASTER = new Talent(
            Id.ARCHERY_MASTER, "箭术大师", "远程技能伤害 x1.5", CharacterType.GUICHEN);

    /** 温和之人：近战伤害 x0.6，但结算 2 次 */
    public static final Talent GENTLE = new Talent(
            Id.GENTLE, "温和之人", "近战伤害 x0.6，但进行 2 次伤害结算", CharacterType.GUICHEN);

    /** 不惧疼痛：造成伤害前扣自身 2 血，该次伤害 x1.1 */
    public static final Talent FEARLESS = new Talent(
            Id.FEARLESS, "不惧疼痛", "每次造成伤害前扣自身 2 血，该次伤害 x1.1", CharacterType.GUICHEN);

    /** 欢愉之礼：每次造成伤害额外附加 1~2 点固定伤害（0.2.5 空梦专属，0.2.6 下调为 1~2） */
    public static final Talent JOY_GIFT = new Talent(
            Id.JOY_GIFT, "欢愉之礼", "每次造成伤害额外附加 1~2 点伤害", CharacterType.KONGMENG);

    /** 镜中人：受击时 10% 概率免疫本次伤害，每场战斗最多触发 2 次（0.2.6 空梦专属） */
    public static final Talent MIRROR_AVATAR = new Talent(
            Id.MIRROR_AVATAR, "镜中人", "受击时 10% 概率免疫本次伤害，每场战斗最多触发 2 次", CharacterType.KONGMENG);

    /** 降临者：第一次死亡时立即以 30% 最大生命复活，整局限 1 次（0.2.8 降临者专属） */
    public static final Talent DESCENDER = new Talent(
            Id.DESCENDER, "降临者", "第一次死亡时立即以 30% 最大生命复活，整局限 1 次", CharacterType.DESCENDER);

    /** 倒映：自身获得非晕眩负面效果时，给最前方敌人附加同样负面效果（0.2.8 空梦专属） */
    public static final Talent REFLECTION = new Talent(
            Id.REFLECTION, "倒映", "自身获得非晕眩负面效果时，给最前方敌人附加同样负面效果", CharacterType.KONGMENG);

    /** 天佑：暴击率 +10%（0.2.8 降临者专属） */
    public static final Talent HEAVENLY_BLESSING = new Talent(
            Id.HEAVENLY_BLESSING, "天佑", "暴击率 +10%", CharacterType.DESCENDER);

    /**
     * 全部天赋，精英节点随机抽取时从这里遍历。
     * <p>
     * 0.2.5 起天赋按角色完全专属，抽取时要先按 {@code owner} 过滤出当前角色的池子，
     * 见 {@link RunState#rollUnownedTalent()}。
     */
    public static final Talent[] ALL = {
            BLOOD_TEAR, OUT_OF_CONTROL, LAZY, ARCHERY_MASTER, GENTLE, FEARLESS,
            JOY_GIFT, MIRROR_AVATAR, DESCENDER, REFLECTION, HEAVENLY_BLESSING
    };
}
