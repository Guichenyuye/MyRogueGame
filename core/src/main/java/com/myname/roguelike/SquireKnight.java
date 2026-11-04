package com.myname.roguelike;

/**
 * 见习骑士（0.3.1 南边领地普通敌人）。
 * <p>
 * 50 血，防御 2。攻击顺序 挥剑 -> 盾击 -> 循环：
 * <ul>
 *   <li>挥剑：6 点伤害</li>
 *   <li>盾击：4 点伤害，自身获得 3 点护盾</li>
 * </ul>
 */
public class SquireKnight extends Enemy {

    /** 循环步数：0 挥剑 / 1 盾击 */
    private int step = 0;

    public SquireKnight() {
        super("见习骑士", 50, 2);
    }

    @Override
    public String getIntent() {
        return switch (step) {
            case 0 -> "挥剑 (6 伤害)";
            default -> "盾击 (4 伤害，获得 3 护盾)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step) {
            case 0 -> {
                battle.damagePlayer(6, name + "-挥剑");
                step = 1;
            }
            default -> {
                battle.damagePlayer(4, name + "-盾击");
                shield += 3;
                battle.log("< " + name + " 架起了盾牌（护盾 " + shield + "）");
                step = 0;
            }
        }
    }
}
