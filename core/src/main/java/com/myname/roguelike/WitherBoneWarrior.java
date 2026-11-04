package com.myname.roguelike;

/**
 * 凋亡骸骨战士（0.2.8）—— 加入第 4~6 层普通池。
 * <p>
 * AI 循环（三步）：挥砍(6) → 破伤风之刃(4，给 3 层流血) → 刺痛(给 5 层流血) → 循环。
 * 它的特色是「伤口会持续流血」：破伤风之刃和刺痛给玩家叠流血层数，
 * 玩家每次受击都要额外吃流血伤害，逼玩家速战速决或算好层数。
 */
public class WitherBoneWarrior extends Enemy {

    /** 循环步数：0 挥砍 / 1 破伤风之刃 / 2 刺痛 */
    private int step = 0;

    public WitherBoneWarrior() {
        super("凋亡骸骨战士", 40, 3);
    }

    @Override
    public String getIntent() {
        return switch (step % 3) {
            case 0 -> "挥砍 (6 伤害)";
            case 1 -> "破伤风之刃 (4 伤害，附 3 层流血)";
            default -> "刺痛 (附 5 层流血)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step % 3) {
            case 0 -> battle.damagePlayer(6, name + "-挥砍");
            case 1 -> {
                battle.damagePlayer(4, name + "-破伤风之刃");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.BLEED, 3, 3);
                }
            }
            default -> {
                battle.inflictStatus(StatusEffect.BLEED, 5, 3);
            }
        }
        step++;
    }
}
