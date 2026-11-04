package com.myname.roguelike;

/**
 * 道具（0.3.0 起）。
 * <p>
 * 主动道具：在战斗中使用，**不消耗精力、不消耗行动次数**，一回合内可连续使用多瓶，
 * 用完消失。购买来自商店节点，也可由事件 / 宝箱发放。
 * <p>
 * 纯 Java，不 import libGDX。道具的「使用效果」由 {@link BattleSystem#useItem(int)}
 * 分发执行；这里只放「不会变」的定义（名字、一句话说明、分类）。
 */
public class Item {

    /** 道具 ID：BattleSystem 用它分发使用效果 */
    public enum Id {
        /** 生命恢复药水：使用后自身每回合恢复 4 血，持续 3 回合 */
        REGEN_POTION,
        /** 虚弱药水：使所有敌人虚弱 1 回合 */
        WEAKEN_POTION,
        /** 力量药水：每次攻击伤害 +5（增益效果，不触发连击/血之泪，吃免伤） */
        STRENGTH_POTION,
        /** 剧毒药水：给最前方敌人挂 5 层中毒 */
        POISON_POTION,
        /** 瞬间治疗药水：立即回 10 血 */
        HEAL_POTION,
        /** 百变药水：从药水池中随机抽一瓶并立即使用 */
        MYSTERY_POTION,
        /** 圣水：清除自身所有负面效果（不含诅咒） */
        HOLY_WATER
    }

    public final Id id;
    public final String name;
    /** 一句话说明，显示在道具槽悬停 / 商店 */
    public final String desc;

    private Item(Id id, String name, String desc) {
        this.id = id;
        this.name = name;
        this.desc = desc;
    }

    // ================= 药水池 =================

    public static final Item REGEN_POTION = new Item(
            Id.REGEN_POTION, "生命恢复药水", "使用后每回合恢复 4 点生命，持续 3 回合");

    public static final Item WEAKEN_POTION = new Item(
            Id.WEAKEN_POTION, "虚弱药水", "使所有敌人虚弱 1 回合（伤害 -30%）");

    public static final Item STRENGTH_POTION = new Item(
            Id.STRENGTH_POTION, "力量药水", "每次攻击额外造成 5 点伤害，持续若干回合");

    public static final Item POISON_POTION = new Item(
            Id.POISON_POTION, "剧毒药水", "给最前方敌人挂上 5 层中毒");

    public static final Item HEAL_POTION = new Item(
            Id.HEAL_POTION, "瞬间治疗药水", "立即恢复 10 点生命");

    public static final Item MYSTERY_POTION = new Item(
            Id.MYSTERY_POTION, "百变药水", "从所有药水中随机抽一瓶并立即使用");

    public static final Item HOLY_WATER = new Item(
            Id.HOLY_WATER, "圣水", "清除自身所有负面效果（不含诅咒）");

    /** 全部道具，商店 / 随机发放时从这里遍历 */
    public static final Item[] ALL = {
            REGEN_POTION, WEAKEN_POTION, STRENGTH_POTION, POISON_POTION,
            HEAL_POTION, MYSTERY_POTION, HOLY_WATER
    };

    /**
     * 百变药水可以随机抽到的药水池（不含百变药水本身，避免「百变抽百变」死循环）。
     */
    public static final Item[] MYSTERY_POOL = {
            REGEN_POTION, WEAKEN_POTION, STRENGTH_POTION, POISON_POTION, HEAL_POTION, HOLY_WATER
    };

    /** 按 ID 查定义（找不到返回 null） */
    public static Item of(Id id) {
        for (Item item : ALL) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }
}
