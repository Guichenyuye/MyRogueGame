package com.myname.roguelike;

/**
 * 骸骨战士 —— 普通怪（0.2.5）。
 * <p>
 * 四步循环：轻击 -> 骨质增生 -> 轻击 -> 重击。
 * 「骨质增生」回血并永久提升后续伤害，是它和史莱姆最大的区别——
 * 打得越久它越硬，逼玩家要么速杀、要么算好它的增伤节奏。
 */
public class BoneWarrior extends Enemy {

    /** 循环步数：0 轻击 / 1 骨质增生 / 2 轻击 / 3 重击 */
    private int step = 0;

    /** 骨质增生累计的永久增伤（本场战斗内有效） */
    private int extraDamage = 0;

    public BoneWarrior() {
        super("骸骨战士", 35, 2);
    }

    /** 轻击的实际伤害（意图栏和结算共用，避免两处算法走偏） */
    private int lightHit() {
        return 5 + extraDamage;
    }

    /** 重击的实际伤害 */
    private int heavyHit() {
        return 11 + extraDamage;
    }

    @Override
    public String getIntent() {
        return switch (step % 4) {
            case 0 -> "轻击 (" + lightHit() + " 伤害)";
            case 1 -> "骨质增生 (回 10 血，后续伤害 +2)";
            case 2 -> "轻击 (" + lightHit() + " 伤害)";
            default -> "重击 (" + heavyHit() + " 伤害)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step % 4) {
            case 0, 2 -> battle.damagePlayer(lightHit(), name + "-轻击");
            case 1 -> {
                // 骨质增生：恢复自身 10 点生命，后续伤害永久 +2
                int before = hp;
                hp = Math.min(maxHp, hp + 10);
                extraDamage += 2;
                battle.log("< " + name + " 使用了【骨质增生】：恢复 " + (hp - before)
                        + " 点生命（" + before + " -> " + hp + "/" + maxHp
                        + "），后续伤害 +2（累计 +" + extraDamage + "）");
            }
            default -> battle.damagePlayer(heavyHit(), name + "-重击");
        }
        step++;
    }
}
