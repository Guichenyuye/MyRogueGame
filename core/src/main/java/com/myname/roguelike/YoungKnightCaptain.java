package com.myname.roguelike;

/**
 * 年轻的骑士长（0.3.1 南边领地首领）。
 * <p>
 * 120 血，防御 5。AI 逻辑自洽，拒绝无脑连招：
 * <ul>
 *   <li><b>骑士礼仪</b>（特殊）：开战第 1 回合必定使用，双方各扣 10 点真实伤害（无视免伤/护盾），不进入后续循环。</li>
 *   <li>常规循环（用 {@link #SEQUENCE} 数组管理，不写 if-else 大乱炖）：
 *     <ol>
 *       <li>骑士突刺：10 点伤害</li>
 *       <li>神圣一击：20 点伤害（重击）</li>
 *       <li>祈祷：恢复 10 血，获得 2 回合 10% 免伤 Buff</li>
 *       <li>瞬息连刺：8 点伤害 x3（多段，可触发流血/易伤）</li>
 *       <li>圣洁之心：清除自身所有负面效果</li>
 *       <li>骑士突刺：10 点伤害（穿插）</li>
 *       <li>圣水洗礼：为武器附魔，后续所有伤害永久 +20%（可叠加，最高 3 层；每用一次，下次循环多穿插一次骑士突刺）</li>
 *     </ol>
 *   </li>
 * </ul>
 * <p>
 * 骑士礼仪的「真实伤害」用独立方法 {@link #knightlyCourtesy(Player, BattleSystem)} 实现，
 * 直接扣血，不走 {@code damagePlayer}，避免误触护盾和易伤减免。
 */
public class YoungKnightCaptain extends Enemy {

    /** 常规循环序列（技能编号，见下方 switch） */
    private static final int[] SEQUENCE = {
            1,  // 骑士突刺
            2,  // 神圣一击
            3,  // 祈祷
            4,  // 瞬息连刺
            5,  // 圣洁之心
            1,  // 骑士突刺（穿插）
            6,  // 圣水洗礼
    };

    /** 常规循环的步数下标 */
    private int step = 0;

    /** 是否还没放过开局「骑士礼仪」（第 1 回合必用） */
    private boolean firstTurn = true;

    /** 圣水洗礼的附魔层数（0~3），每层后续所有伤害 +20% */
    private int enchantStacks = 0;

    /** 祈祷给的免伤 Buff 剩余回合数（0 = 无） */
    private int prayerTurns = 0;

    public YoungKnightCaptain() {
        super("年轻的骑士长", 120, 5);
    }

    /** 附魔后的伤害倍率：每层 +20%，最高 3 层 = x1.6 */
    private double damageMult() {
        return 1.0 + 0.20 * enchantStacks;
    }

    @Override
    public String getIntent() {
        if (firstTurn) {
            return "骑士礼仪 (双方各扣 10 点真实伤害)";
        }
        return switch (SEQUENCE[step % SEQUENCE.length]) {
            case 1 -> "骑士突刺 (" + (int) (10 * damageMult()) + " 伤害)";
            case 2 -> "神圣一击 (" + (int) (20 * damageMult()) + " 伤害，重击)";
            case 3 -> "祈祷 (回 10 血，2 回合 10% 免伤)";
            case 4 -> "瞬息连刺 (8 伤害 x3)";
            case 5 -> "圣洁之心 (清除自身负面效果)";
            default -> "圣水洗礼 (永久 +20% 伤害，可叠加)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        // 开局必定先放骑士礼仪（第 1 回合）
        if (firstTurn) {
            firstTurn = false;
            knightlyCourtesy(player, battle);
            return;
        }

        int action = SEQUENCE[step % SEQUENCE.length];
        switch (action) {
            case 1 -> {   // 骑士突刺：10 伤害
                battle.damagePlayer((int) (10 * damageMult()), name + "-骑士突刺");
            }
            case 2 -> {   // 神圣一击：20 伤害（重击）
                battle.damagePlayer((int) (20 * damageMult()), name + "-神圣一击");
            }
            case 3 -> {   // 祈祷：回 10 血，2 回合 10% 免伤
                hp = Math.min(maxHp, hp + 10);
                prayerTurns = 2;
                damageTakenMult = 0.9;   // 10% 免伤
                battle.log("< " + name + " 祈祷（回复 10 血 -> " + hp + "/" + maxHp + "，获得 2 回合免伤）");
            }
            case 4 -> {   // 瞬息连刺：8 伤害 x3（多段）
                for (int i = 0; i < 3; i++) {
                    if (!player.isAlive()) {
                        break;
                    }
                    battle.damagePlayer((int) (8 * damageMult()), name + "-瞬息连刺");
                }
            }
            case 5 -> {   // 圣洁之心：清除自身所有负面效果
                statusEffects.removeIf(inst -> !inst.effect.isBuff);
                battle.log("< " + name + " 圣洁之心：清除了自身所有负面效果");
            }
            case 6 -> {   // 圣水洗礼：永久 +20% 伤害，可叠加最高 3 层
                if (enchantStacks < 3) {
                    enchantStacks++;
                    battle.log("< " + name + " 圣水洗礼：后续所有伤害永久 +20%（当前 " + enchantStacks + " 层）");
                } else {
                    battle.log("< " + name + " 圣水洗礼：附魔已达上限（3 层），不再提升");
                }
            }
        }

        // 祈祷免伤持续 2 个「敌方回合」：本次行动后 -1，归零则恢复 1.0
        if (prayerTurns > 0) {
            prayerTurns--;
            if (prayerTurns <= 0) {
                damageTakenMult = 1.0;
            }
        }

        step++;
    }

    /**
     * 骑士礼仪（开局）：双方各扣 10 点真实伤害，无视免伤 / 护盾。
     * <p>
     * 单独方法：真实伤害直接扣血，不经过 {@code damagePlayer}（那里有护盾、易伤、
     * 长期训练免伤等一堆减免逻辑，会污染「真实伤害」的语义）。
     */
    private void knightlyCourtesy(Player player, BattleSystem battle) {
        player.takeDamage(10);
        hp = Math.max(0, hp - 10);
        battle.log("< " + name + " 行骑士礼仪：双方各扣 10 点真实伤害"
                + "（你 " + player.hp + "/" + player.maxHp + "，骑士长 " + hp + "/" + maxHp + "）");
    }

    /** 供测试读取当前附魔层数（断言「圣水洗礼后伤害 +20%」用） */
    public int enchantStacks() {
        return enchantStacks;
    }
}
