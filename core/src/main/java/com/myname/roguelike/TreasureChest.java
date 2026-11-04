package com.myname.roguelike;

/**
 * 宝箱怪（0.2.8）—— 伪装成宝箱的精英敌人。
 * <p>
 * AI 循环（七步）：咬击(7) → 连续咬击(5×2) → 陈年宝箱 → 血盆大口 → 陈年宝箱 → 连续咬击 → 洗劫 → 循环。
 * <ul>
 *   <li>咬击：7 点伤害</li>
 *   <li>连续咬击：5 点伤害共 2 段（独立结算，独立触发流血/易伤）</li>
 *   <li>陈年宝箱：给玩家 Buff——下次受到的攻击附带 3 层中毒</li>
 *   <li>血盆大口：20 点伤害 + 必定给玩家 3 回合易伤</li>
 *   <li>洗劫：偷走玩家 10 枚金币</li>
 * </ul>
 */
public class TreasureChest extends Enemy {

    /** 循环步数：0咬击 / 1连续咬击 / 2陈年宝箱 / 3血盆大口 / 4陈年宝箱 / 5连续咬击 / 6洗劫 */
    private int step = 0;

    public TreasureChest() {
        super("宝箱怪", 45, 2);
    }

    @Override
    public String getIntent() {
        return switch (step % 7) {
            case 0 -> "咬击 (7 伤害)";
            case 1 -> "连续咬击 (5 伤害 x2)";
            case 2 -> "陈年宝箱 (下次受击附带 3 层中毒)";
            case 3 -> "血盆大口 (20 伤害，附 3 回合易伤)";
            case 4 -> "陈年宝箱 (下次受击附带 3 层中毒)";
            case 5 -> "连续咬击 (5 伤害 x2)";
            default -> "洗劫 (偷 10 金币)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step % 7) {
            case 0 -> {
                battle.damagePlayer(7, name + "-咬击");
            }
            case 1, 5 -> {
                // 连续咬击：5 点伤害 ×2 段，独立结算（独立触发流血/易伤）
                battle.damagePlayer(5, name + "-连续咬击(第1段)");
                if (player.isAlive()) {
                    battle.damagePlayer(5, name + "-连续咬击(第2段)");
                }
            }
            case 2, 4 -> {
                // 陈年宝箱：给玩家挂「下次受击附带 3 层中毒」的 Buff
                // 用一个标记（毒雾 Buff）实现：下次 damagePlayer 时给玩家挂 3 层中毒。
                // 这里用一个简单字段表示「下次受击附带中毒」。
                player.poisonOnNextHit = true;
                battle.log("< " + name + " 张开了锈蚀的箱盖，喷出一团陈年毒雾（下次受击附带 3 层中毒）");
            }
            case 3 -> {
                // 血盆大口：20 伤害 + 必定 3 回合易伤
                battle.damagePlayer(20, name + "-血盆大口");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.VULNERABLE, 1, 3);
                }
            }
            default -> {
                // 洗劫：偷 10 金币
                int stolen = Math.min(10, player.gold);
                player.gold -= stolen;
                battle.log("< " + name + " 洗劫了你的行囊，偷走 " + stolen + " 枚金币");
            }
        }
        step++;
    }
}
