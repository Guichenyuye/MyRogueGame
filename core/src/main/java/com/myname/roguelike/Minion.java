package com.myname.roguelike;

/**
 * 召唤物（0.3.0 墓渊）。
 * <p>
 * 玩家侧单位：有血量、能按固定逻辑行动，站在玩家一方攻击敌人。
 * 和 {@link Enemy} 镜像：敌人有 {@code doTurn(Player, BattleSystem)}，
 * 召唤物有 {@code doTurn(List<Enemy>, BattleSystem)}，攻击的是敌人列表。
 * <p>
 * 纯 Java，不 import libGDX。行动逻辑由 {@link BattleSystem} 在敌方回合之前
 * （或之后）统一调度，见 {@code BattleSystem#minionTurn()}。
 */
public class Minion {

    /** 召唤物种类，决定行动逻辑 */
    public enum Kind {
        /** 普通僵尸：撕咬 5 伤 + 3 毒；每 3 次撕咬后 1 次重击 10 伤 */
        ZOMBIE,
        /** 幽魂：破点 7 伤打最低血敌人 / 魂穿 4 伤 AOE，轮换优先破点 */
        GHOST
    }

    public final Kind kind;
    public String name;
    public int maxHp;
    public int hp;

    /** 行动步数计数（用于「每 N 次换一次技能」的循环） */
    public int actionCount = 0;

    public Minion(Kind kind, String name, int maxHp) {
        this.kind = kind;
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    /**
     * 召唤物行动一次。由 {@link BattleSystem} 在玩家回合结束后、敌方回合前调度。
     *
     * @param enemies 敌人列表（打最前方 / 最低血 / 全体）
     * @param battle  战斗系统（写日志、结算伤害）
     */
    public void doTurn(java.util.List<Enemy> enemies, BattleSystem battle) {
        switch (kind) {
            case ZOMBIE -> zombieTurn(enemies, battle);
            case GHOST -> ghostTurn(enemies, battle);
        }
    }

    /** 僵尸：每 3 次撕咬后 1 次重击（撕咬 5 伤 + 3 毒，重击 10 伤） */
    private void zombieTurn(java.util.List<Enemy> enemies, BattleSystem battle) {
        Enemy target = battle.firstAliveEnemy();
        if (target == null) {
            return;
        }
        // actionCount % 4：0,1,2 = 撕咬，3 = 重击
        if (actionCount % 4 == 3) {
            // 重击：10 点伤害（走统一管线，吃召唤物伤害加成，不吃暴击/血之泪）
            battle.minionAttack(target, 10, false);
            battle.log("> " + name + " 重击 " + target.name + "（10 点伤害）");
        } else {
            // 撕咬：5 点伤害 + 3 层中毒（老毒物：中毒层数额外 +1，见 BattleSystem.applyPoisonToEnemy）
            battle.minionAttack(target, 5, false);
            battle.applyPoisonToEnemy(target, 3);
            battle.log("> " + name + " 撕咬 " + target.name + "（5 点伤害，附 3 层中毒）");
        }
        actionCount++;
    }

    /** 幽魂：破点（7 伤打最低血敌人）/ 魂穿（4 伤 AOE）轮换，优先破点 */
    private void ghostTurn(java.util.List<Enemy> enemies, BattleSystem battle) {
        // 优先破点（打最低血敌人），隔一次魂穿
        if (actionCount % 2 == 0) {
            Enemy lowest = battle.lowestHpEnemy();
            if (lowest != null) {
                battle.minionAttack(lowest, 7, false);
                battle.log("> " + name + " 破点 " + lowest.name + "（7 点伤害）");
            }
        } else {
            for (Enemy enemy : enemies) {
                if (enemy.isAlive()) {
                    battle.minionAttack(enemy, 4, false);
                    battle.log("> " + name + " 魂穿 " + enemy.name + "（4 点伤害）");
                }
            }
        }
        actionCount++;
    }
}
