package com.myname.roguelike;

/**
 * 老骑士（0.3.0 精英敌人）。
 * <p>
 * 80 血。攻击顺序 3→5→4→3→2→1 循环：
 * <ul>
 *   <li>指导一剑：对单个敌人造成 5 点伤害，自身后续所有伤害 +1（可叠加）</li>
 *   <li>迅捷一击：对敌人造成 8 点伤害</li>
 *   <li>嗜酒：自身获得 2 层虚弱，但下次攻击伤害 x2</li>
 *   <li>指导一剑（同上）</li>
 *   <li>沉稳：清除负面效果，下一次攻击伤害 x1.5</li>
 *   <li>沉重打击：对所有敌人造成 15 点伤害，自身旧疾复发获得 2 层易伤</li>
 * </ul>
 */
public class OldKnight extends Enemy {

    /** 循环序列（技能编号） */
    private static final int[] SEQUENCE = {3, 5, 4, 3, 2, 1};
    private int step = 0;

    /** 指导一剑累计的永久增伤 */
    private int bonusDamage = 0;

    /** 嗜酒/沉稳的下击增伤倍率（下次攻击后消耗） */
    private double nextHitMult = 1.0;

    public OldKnight() {
        super("老骑士", 80, 3);
    }

    @Override
    public String getIntent() {
        return switch (SEQUENCE[step % SEQUENCE.length]) {
            case 1 -> "沉重打击 (AOE 15 伤害，自身易伤)";
            case 2 -> "沉稳 (清负面，下击 x1.5)";
            case 3 -> "指导一剑 (5 伤害，后续伤害 +1)";
            case 4 -> "嗜酒 (自身虚弱，下击 x2)";
            default -> "迅捷一击 (8 伤害)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        int action = SEQUENCE[step % SEQUENCE.length];
        switch (action) {
            case 1 -> {   // 沉重打击：AOE 15 伤害，自身 2 层易伤
                battle.damagePlayer(15, name + "-沉重打击");
                StatusEffect.addStacks(statusEffects, StatusEffect.VULNERABLE, 2, 2);
                battle.log("< " + name + " 旧疾复发，自身获得 2 层易伤");
            }
            case 2 -> {   // 沉稳：清负面，下击 x1.5
                statusEffects.removeIf(inst -> !inst.effect.isBuff);
                nextHitMult = 1.5;
                battle.log("< " + name + " 稳住身形，清除了负面效果（下次攻击 x1.5）");
            }
            case 3 -> {   // 指导一剑：5 伤害，后续伤害 +1 永久
                int dmg = (int) (5 * nextHitMult);
                nextHitMult = 1.0;
                battle.damagePlayer(dmg, name + "-指导一剑");
                bonusDamage += 1;
                battle.log("< " + name + " 剑术愈发老练（后续伤害 +1，累计 +" + bonusDamage + "）");
            }
            case 4 -> {   // 嗜酒：自身 2 层虚弱，下击 x2
                StatusEffect.addStacks(statusEffects, StatusEffect.WEAKEN, 2, 2);
                nextHitMult = 2.0;
                battle.log("< " + name + " 灌了口酒（自身虚弱，下次攻击 x2）");
            }
            default -> {  // 迅捷一击：8 伤害
                int dmg = (int) (8 * nextHitMult);
                nextHitMult = 1.0;
                battle.damagePlayer(dmg, name + "-迅捷一击");
            }
        }
        step++;
    }
}
