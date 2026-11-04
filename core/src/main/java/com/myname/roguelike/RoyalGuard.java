package com.myname.roguelike;

/**
 * 皇家守卫（0.3.1 南边领地精英敌人）。
 * <p>
 * 90 血，防御 4。攻击顺序 横扫 -> 坚守阵地 -> 破甲击 -> 循环：
 * <ul>
 *   <li>横扫：7 点伤害</li>
 *   <li>坚守阵地：获得 15 点护盾，并回复 5 点生命</li>
 *   <li>破甲击：10 点伤害，给玩家 2 层易伤</li>
 * </ul>
 */
public class RoyalGuard extends Enemy {

    /** 循环步数：0 横扫 / 1 坚守阵地 / 2 破甲击 */
    private int step = 0;

    public RoyalGuard() {
        super("皇家守卫", 90, 4);
    }

    @Override
    public String getIntent() {
        return switch (step) {
            case 0 -> "横扫 (7 伤害)";
            case 1 -> "坚守阵地 (获得 15 护盾，回 5 血)";
            default -> "破甲击 (10 伤害，2 层易伤)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step) {
            case 0 -> {
                battle.damagePlayer(7, name + "-横扫");
                step = 1;
            }
            case 1 -> {
                shield += 15;
                hp = Math.min(maxHp, hp + 5);
                battle.log("< " + name + " 坚守阵地（护盾 " + shield + "，回复 5 血 -> " + hp + "/" + maxHp + "）");
                step = 2;
            }
            default -> {
                battle.damagePlayer(10, name + "-破甲击");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.VULNERABLE, 2, 2);
                }
                step = 0;
            }
        }
    }
}
