package com.myname.roguelike;

/**
 * 侦查骑士（0.3.0 普通敌人）。
 * <p>
 * 60 血。攻击顺序 1→2→3→1 循环：
 * <ul>
 *   <li>隐秘一击：对敌人造成 10 点伤害</li>
 *   <li>隐匿行踪：获得 10 点护盾，且下一回合攻击伤害 +150%</li>
 *   <li>骑士踢：对敌人造成 8 点伤害，20% 概率晕眩敌人</li>
 * </ul>
 */
public class ScoutKnight extends Enemy {

    /** 循环步数：0 隐秘一击 / 1 隐匿行踪 / 2 骑士踢 */
    private int step = 0;

    /** 隐匿行踪的 Buff：下次攻击伤害 +150%（x2.5） */
    private boolean hidden = false;

    /** 隐匿行踪给的护盾 */
    private int stealthShield = 0;

    public ScoutKnight() {
        super("侦查骑士", 60, 2);
    }

    @Override
    public String getIntent() {
        return switch (step) {
            case 0 -> "隐秘一击 (" + (hidden ? "37" : "10") + " 伤害)";
            case 1 -> "隐匿行踪 (获得 10 护盾，下击 +150%)";
            default -> "骑士踢 (8 伤害，20% 晕眩)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step) {
            case 0 -> {
                int dmg = hidden ? (int) (10 * 2.5) : 10;
                battle.damagePlayer(dmg, name + "-隐秘一击");
                hidden = false;
                step = 1;
            }
            case 1 -> {
                stealthShield = 10;
                hidden = true;
                battle.log("< " + name + " 隐匿了行踪（获得护盾，下击伤害 +150%）");
                step = 2;
            }
            default -> {
                battle.damagePlayer(8, name + "-骑士踢");
                // 20% 概率晕眩玩家
                if (player.isAlive() && battle.random().nextDouble() < 0.20) {
                    battle.inflictStatus(StatusEffect.STUN, 1, 1);
                }
                step = 0;
            }
        }
    }
}
