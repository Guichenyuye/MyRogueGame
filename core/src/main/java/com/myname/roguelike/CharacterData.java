package com.myname.roguelike;

/**
 * 角色静态数据（0.2.4 引入，0.2.5 扩展）。
 * <p>
 * 每个角色一条数据：名字、一句话介绍、初始面板、初始天赋、初始四技能。
 * 这里只放「不会变」的配置，UI（{@code CharacterSelectScreen}）只负责循环渲染 {@link #ALL}，
 * **不把角色名/介绍硬编码在 UI 里**。以后加新角色：在 {@link CharacterType} 加枚举 +
 * 在这里加一条数据即可，UI 代码不用动。
 * <p>
 * 纯 Java，不 import libGDX。
 */
public class CharacterData {

    public final CharacterType type;
    public final String name;
    /** 一句话介绍，显示在角色选择界面 */
    public final String desc;
    /** 初始面板 */
    public final int maxHp;
    public final int maxMp;
    public final int strength;
    public final int precision;
    public final int defense;
    /** 初始天赋（角色创建时就自带的天赋） */
    public final Talent[] initialTalents;
    /** 初始四技能（槽 1~4） */
    public final Skill[] initialSkills;
    /** 角色头像资源路径（相对 assets 根，8x8 像素，Nearest 放大） */
    public final String avatarPath;
    /** 角色背景资源路径（相对 assets 根，1920x1080，Cover 模式铺满） */
    public final String backgroundPath;

    private CharacterData(CharacterType type, String name, String desc,
                          int maxHp, int maxMp, int strength, int precision, int defense,
                          Talent[] initialTalents, Skill[] initialSkills,
                          String avatarPath, String backgroundPath) {
        this.type = type;
        this.name = name;
        this.desc = desc;
        this.maxHp = maxHp;
        this.maxMp = maxMp;
        this.strength = strength;
        this.precision = precision;
        this.defense = defense;
        this.initialTalents = initialTalents;
        this.initialSkills = initialSkills;
        this.avatarPath = avatarPath;
        this.backgroundPath = backgroundPath;
    }

    /** 归尘：以血为引、以伤换伤的狂战士，初始自带「血之泪」（0.2.6 三维下调 5→3） */
    public static final CharacterData GUICHEN = new CharacterData(
            CharacterType.GUICHEN,
            "归尘",
            "以血为引，以伤换伤的狂战士",
            80, 10, 3, 3, 3,
            new Talent[]{ Talent.BLOOD_TEAR },
            new Skill[]{ Skill.NORMAL_ATTACK, Skill.DEFEND, Skill.PRECISE_SHOT, Skill.SWEEP },
            "resources/image/role/guichen_role.png",
            "resources/image/role_ground/guichen_ground.png");

    /** 空梦：镜像与倒影的舞者（0.2.5 测试角色），初始自带「欢愉之礼」（0.2.6 三维下调 7/4/4→4/2/3） */
    public static final CharacterData KONGMENG = new CharacterData(
            CharacterType.KONGMENG,
            "空梦",
            "镜像与倒影的舞者",
            70, 12, 4, 2, 3,
            new Talent[]{ Talent.JOY_GIFT },
            new Skill[]{ Skill.MIRROR_PIERCE, Skill.DISGUISE, Skill.PERFECT_COPY, Skill.MIRROR_FLASH },
            "resources/image/role/kongmeng_role.png",
            "resources/image/role_ground/kongmeng_ground.png");

    /**
     * 降临者：自定义捏脸角色（0.2.8）。
     * <p>
     * 这里的面板是「模板默认值」，实际属性由捏脸界面（CustomCharacterScreen）分配后覆盖：
     * 10 点自由属性分配到力量/精准/防御（初始均为 0），生命固定 50、精力固定 10。
     * 头像用代码生成的问号图（见 {@code Main}），背景复用归尘的（或可后续替换）。
     */
    public static final CharacterData DESCENDER = new CharacterData(
            CharacterType.DESCENDER,
            "降临者",
            "自混沌中降临的剑客",
            50, 10, 0, 0, 0,
            new Talent[]{ Talent.DESCENDER },
            new Skill[]{ Skill.SWORD_STRIKE, Skill.BLOCK, Skill.EAT_MY_SWORD, Skill.SLOW_BURN },
            "resources/image/role/guichen_role.png",   // 占位，实际由 Main 生成问号头像
            "resources/image/role_ground/guichen_ground.png");

    /**
     * 墓渊：驱使亡灵的召唤师（0.3.0）。
     * <p>
     * 面板 60 血 / 力 2 / 准 5 / 防 2；初始天赋「亡灵项链」；初始四技能
     * 瞬身突刺 / 魂绕 / 唤起（召唤僵尸）/ 死之旋风。贴图用 muyuan 前缀的头像和背景。
     */
    public static final CharacterData MUYUAN = new CharacterData(
            CharacterType.MUYUAN,
            "墓渊",
            "驱使亡灵的召唤师",
            60, 10, 2, 5, 2,
            new Talent[]{ Talent.NECKLACE_OF_UNDEAD },
            new Skill[]{ Skill.FLASH_THRUST, Skill.SOUL_WREATH, Skill.SUMMON_ZOMBIE, Skill.DEATH_WHIRL },
            "resources/image/role/muyuan_role.png",
            "resources/image/role_ground/muyuan_ground.png");

    /** 全部可选角色，角色选择界面从这里循环渲染。降临者（DESCENDER）走捏脸流程，不直接进地图 */
    public static final CharacterData[] ALL = { GUICHEN, KONGMENG, MUYUAN };

    /** 按类型查数据（找不到返回 null） */
    public static CharacterData of(CharacterType type) {
        for (CharacterData data : ALL) {
            if (data.type == type) {
                return data;
            }
        }
        // 降临者不在 ALL 里（走捏脸流程），单独兜底返回
        if (type == CharacterType.DESCENDER) {
            return DESCENDER;
        }
        return null;
    }
}
