package com.myname.roguelike;

/**
 * 新手及时（0.3.0 普通敌人）。
 * <p>
 * 50 血。攻击顺序 1→2→1→2→1→3 循环：
 * <ul>
 *   <li>挥剑：对敌人造成 6 点伤害</li>
 *   <li>手忙脚乱：对敌人造成 10 点伤害，自己扣 2 血</li>
 *   <li>怯战：逃跑（若成功逃走，战斗胜利后不给予金币、经验减半）</li>
 * </ul>
 */
public class Novice extends Enemy {

    /** 循环序列：0 挥剑 / 1 手忙脚乱 / 2 怯战 */
    private static final int[] SEQUENCE = {0, 1, 0, 1, 0, 2};
    private int step = 0;

    /** 是否已逃跑（逃跑成功则战斗按「金币 0、经验减半」结算） */
    private boolean fled = false;

    public Novice() {
        super("新手及时", 50, 1);
    }

    public boolean hasFled() {
        return fled;
    }

    @Override
    public String getIntent() {
        return switch (SEQUENCE[step % SEQUENCE.length]) {
            case 0 -> "挥剑 (6 伤害)";
            case 1 -> "手忙脚乱 (10 伤害，自扣 2 血)";
            default -> "怯战 (逃跑)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        int action = SEQUENCE[step % SEQUENCE.length];
        switch (action) {
            case 0 -> battle.damagePlayer(6, name + "-挥剑");
            case 1 -> {
                battle.damagePlayer(10, name + "-手忙脚乱");
                hp = Math.max(0, hp - 2);
                battle.log("< " + name + " 手忙脚乱，自己扣了 2 点血（" + hp + "/" + maxHp + "）");
            }
            default -> {
                // 怯战：逃跑（标记 fled，本场战斗结束后不给予金币、经验减半）
                fled = true;
                battle.log("< " + name + " 怯战逃跑了！");
                battle.markFled();
            }
        }
        step++;
    }
}
