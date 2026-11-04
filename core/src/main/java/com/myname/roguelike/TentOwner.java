package com.myname.roguelike;

/**
 * 帐篷主人（0.2.8）—— 废弃营地「仔细搜刮」触发的隐藏敌人。
 * <p>
 * AI 四步循环：锤击 → 狂乱攻击 → 锤击 → 愤怒不已 → 循环。
 * <ul>
 *   <li>锤击：10 点伤害 + 施加 2 回合易伤</li>
 *   <li>狂乱攻击：7 点伤害，连续打两次</li>
 *   <li>愤怒不已：下次伤害 +150%（最终 x2.5），并给玩家施加 3 回合易伤</li>
 * </ul>
 * 战斗胜利按普通战斗结算（经验 / 金币照常）。
 */
public class TentOwner extends Enemy {

    /** 循环步数：0 锤击 / 1 狂乱攻击 / 2 锤击 / 3 愤怒不已 */
    private int step = 0;

    /** 愤怒不已的 Buff：下次攻击伤害 x2.5 */
    public boolean enraged = false;

    public TentOwner() {
        super("帐篷主人", 50, 10);
    }

    @Override
    public String getIntent() {
        return switch (step % 4) {
            case 0 -> "锤击 (10 伤害，附 2 回合易伤)";
            case 1 -> "狂乱攻击 (7 伤害 x2)";
            case 2 -> "锤击 (10 伤害，附 2 回合易伤)";
            default -> "愤怒不已 (下次伤害 x2.5，附 3 回合易伤)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step % 4) {
            case 0 -> {
                int dmg = enraged ? (int) (10 * 2.5) : 10;
                battle.damagePlayer(dmg, name + "-锤击");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.VULNERABLE, 1, 2);
                }
                enraged = false;
            }
            case 1 -> {
                // 狂乱攻击：7 点伤害连打两次（各自独立吃护盾/减伤）
                int dmg = enraged ? (int) (7 * 2.5) : 7;
                battle.damagePlayer(dmg, name + "-狂乱攻击");
                if (player.isAlive()) {
                    battle.damagePlayer(dmg, name + "-狂乱攻击");
                }
                enraged = false;
            }
            case 2 -> {
                int dmg = enraged ? (int) (10 * 2.5) : 10;
                battle.damagePlayer(dmg, name + "-锤击");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.VULNERABLE, 1, 2);
                }
                enraged = false;
            }
            default -> {
                // 愤怒不已：给玩家 3 回合易伤 + 自己下次伤害 x2.5
                battle.log("< " + name + " 怒火中烧，双眼通红……");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.VULNERABLE, 1, 3);
                }
                enraged = true;
            }
        }
        step++;
    }
}
