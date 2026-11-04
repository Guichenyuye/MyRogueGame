package com.myname.roguelike;

/**
 * 骸骨猎手 —— 普通 / 精英（0.2.5）。
 * <p>
 * 五步循环：速射 -> 仔细瞄准 -> 速射 -> 仔细瞄准 -> 骨箭。
 * 「仔细瞄准」给下一次骨箭叠一个翻倍 Buff，意图栏会提前把「双倍骨箭」标出来，
 * 让玩家在挨那一箭之前有整整一回合决定要不要防御。
 */
public class BoneHunter extends Enemy {

    /** 循环步数：0 速射 / 1 瞄准 / 2 速射 / 3 瞄准 / 4 骨箭 */
    private int step = 0;

    /** 是否处于「仔细瞄准」状态（下一次骨箭翻倍） */
    private boolean aimed = false;

    public BoneHunter(boolean elite) {
        super(elite ? "骸骨猎手" : "骸骨猎手", elite ? 45 : 28, 1);
    }

    /** 骨箭的基础伤害 */
    private int baseBoneArrow() {
        return 12;
    }

    /** 骨箭的实际伤害（含翻倍 Buff） */
    private int boneArrowDamage() {
        return aimed ? baseBoneArrow() * 2 : baseBoneArrow();
    }

    @Override
    public String getIntent() {
        return switch (step % 5) {
            case 0, 2 -> "速射 (4 伤害)";
            case 1, 3 -> "仔细瞄准 (下次骨箭伤害翻倍)";
            default -> aimed
                    ? "骨箭 (" + boneArrowDamage() + " 伤害，双倍)"
                    : "骨箭 (" + baseBoneArrow() + " 伤害，扣自身 20% 生命)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step % 5) {
            case 0, 2 -> battle.damagePlayer(4, name + "-速射");
            case 1, 3 -> {
                aimed = true;
                battle.log("< " + name + " 仔细瞄准，下一支骨箭将造成双倍伤害……");
            }
            default -> {
                // 骨箭：先扣自身 20% 最大生命，再造成 12（瞄准则 24）伤害
                int selfCost = Math.max(1, (int) (maxHp * 0.20));
                int before = hp;
                hp = Math.max(1, hp - selfCost);
                int dmg = boneArrowDamage();
                battle.log("< " + name + " 射出骨箭，以自身血肉为代价（生命 "
                        + before + " -> " + hp + "/" + maxHp + "）");
                aimed = false;
                battle.damagePlayer(dmg, name + "-骨箭");
            }
        }
        step++;
    }
}
