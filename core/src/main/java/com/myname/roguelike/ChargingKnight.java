package com.myname.roguelike;

/**
 * 冲锋骑士（0.3.1 南边领地普通敌人）。
 * <p>
 * 65 血，防御 1。攻击顺序 长矛突刺 -> 蓄势冲锋 -> 循环：
 * <ul>
 *   <li>长矛突刺：8 点伤害</li>
 *   <li>蓄势冲锋：下回合造成 15 点伤害（意图栏提前显示，玩家有一回合反应时间）</li>
 * </ul>
 */
public class ChargingKnight extends Enemy {

    /** 循环步数：0 长矛突刺 / 1 蓄势冲锋 */
    private int step = 0;

    /** 是否处于「蓄势冲锋」状态（下回合将造成 15 伤害） */
    private boolean charging = false;

    public ChargingKnight() {
        super("冲锋骑士", 65, 1);
    }

    @Override
    public String getIntent() {
        if (charging) {
            return "冲锋 (15 伤害)";
        }
        return switch (step) {
            case 0 -> "长矛突刺 (8 伤害)";
            default -> "蓄势冲锋 (下回合 15 伤害)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        if (charging) {
            // 蓄势完成，本回合冲锋
            battle.damagePlayer(15, name + "-冲锋");
            charging = false;
            step = 0;
            return;
        }
        switch (step) {
            case 0 -> {
                battle.damagePlayer(8, name + "-长矛突刺");
                step = 1;
            }
            default -> {
                charging = true;
                battle.log("< " + name + " 开始蓄势冲锋……");
                step = 0;
            }
        }
    }
}
