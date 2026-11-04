package com.myname.roguelike;

/**
 * 史莱姆 —— 普通怪（文档 5.4）。
 * <p>
 * 固定顺序：撞击 -> 强力撞击 -> 包裹 -> 循环。
 * 构造参数里带 {@code bonus}，同一个类可以出「第 1 层的弱史莱姆」和「第 7 层的强化史莱姆」，
 * 不用为每层再复制一个子类。
 */
public class Slime extends Enemy {

    /** 固定顺序计数：0=撞击, 1=强力撞击, 2=包裹 */
    public int turnCounter = 0;

    /** 包裹累计的永久增伤（本场战斗内有效） */
    public int extraDamage = 0;

    /** 楼层带来的基础伤害加成 */
    private final int bonus;

    public Slime() {
        this("史莱姆", 30, 1, 0);
    }

    public Slime(String name, int maxHp, int defense, int bonus) {
        super(name, maxHp, defense);
        this.bonus = bonus;
    }

    /** 撞击的实际伤害（意图栏和真正结算都用这个，避免两处算法走偏） */
    public int hit() {
        return 5 + extraDamage + bonus;
    }

    /** 强力撞击的实际伤害 */
    public int strongHit() {
        return 8 + extraDamage + bonus;
    }

    @Override
    public String getIntent() {
        return switch (turnCounter % 3) {
            case 0 -> "撞击 (" + hit() + " 伤害)";
            case 1 -> "强力撞击 (" + strongHit() + " 伤害)";
            default -> "包裹 (扣 1 精力，自身伤害 +2)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        int action = turnCounter % 3;

        if (action == 0) {
            battle.damagePlayer(hit(), "史莱姆-撞击");
        } else if (action == 1) {
            battle.damagePlayer(strongHit(), "史莱姆-强力撞击");
        } else {
            // 包裹：无伤害，扣 1 当前精力（0 则不变），自身伤害永久 +2
            int before = player.mp;
            player.drainMp(1);
            extraDamage += 2;
            battle.log("< " + name + " 使用了【包裹】：你的精力 " + before + " -> " + player.mp
                    + "，史莱姆后续伤害 +2（累计 +" + extraDamage + "）");
        }

        turnCounter++;
    }
}
