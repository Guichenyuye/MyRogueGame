package com.myname.roguelike;

import java.util.List;

/**
 * 诅咒（0.2.8）。
 * <p>
 * 和 {@link StatusEffect} 里的「负面效果」区别：诅咒是**本局持续、不可消除**的长期 debuff，
 * 由事件（如「遇见邪教徒」的献祭）施加，挂在 {@link RunState} 的 {@code curses} 上，
 * 跨战斗保留。负面效果则是战斗内临时、有回合数的。
 * <p>
 * 纯 Java，不 import libGDX。
 */
public class Curse {

    public enum Id {
        /** 恶魔抓痕：每次攻击自身受到 2 点伤害 */
        DEMON_CLAW,
        /** 怠惰：每次攻击造成的伤害 -10% */
        SLOTH,
        /** 旧疾：有 10% 概率跳过本回合 */
        OLD_ILLNESS
    }

    public final Id id;
    public final String name;
    public final String desc;

    private Curse(Id id, String name, String desc) {
        this.id = id;
        this.name = name;
        this.desc = desc;
    }

    public static final Curse DEMON_CLAW = new Curse(
            Id.DEMON_CLAW, "恶魔抓痕", "每次攻击自身受到 2 点伤害");

    public static final Curse SLOTH = new Curse(
            Id.SLOTH, "怠惰", "每次攻击造成的伤害 -10%");

    public static final Curse OLD_ILLNESS = new Curse(
            Id.OLD_ILLNESS, "旧疾", "有 10% 概率跳过本回合");

    /** 全部诅咒，献祭随机抽取时从这里遍历 */
    public static final Curse[] ALL = { DEMON_CLAW, SLOTH, OLD_ILLNESS };

    /** 按 ID 查定义（找不到返回 null） */
    public static Curse of(Id id) {
        for (Curse c : ALL) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    /** 玩家是否带着某个诅咒 */
    public static boolean has(List<Curse> list, Id id) {
        for (Curse c : list) {
            if (c.id == id) {
                return true;
            }
        }
        return false;
    }
}
