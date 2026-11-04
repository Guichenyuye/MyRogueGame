package com.myname.roguelike;

/**
 * 史莱姆王 —— 精英与首领共用（文档《节点地图设计》第 4 节）。
 * <p>
 * 四步循环：撞击 -> 腐液（扣精力）-> 蓄力 -> 重压。
 * <p>
 * 「蓄力」这一拍是设计里唯一的**强制决策点**：意图栏会提前一整回合告诉玩家
 * "下回合要挨一记重压"，玩家必须在那回合决定是防御、抢伤害，还是赌一把。
 * 没有这一拍，史莱姆王就只是一只血更厚的史莱姆。
 */
public class SlimeKing extends Enemy {

    /** 是否是首领档（影响名字和全部数值） */
    private final boolean boss;

    /** 循环步数：0 撞击 / 1 腐液 / 2 蓄力 / 3 重压 */
    private int step = 0;

    public SlimeKing(boolean boss) {
        super(boss ? "腐化史莱姆王" : "史莱姆王",
                boss ? 95 : 62,
                boss ? 4 : 3);
        this.boss = boss;
    }

    // 首领档的数值卡得比较紧：玩家 80 血、每回合打 9 点左右，
    // 首领 95 血 = 11 回合左右；期间承伤约 7.5/回合，正好压在「必须用防御」的边上。
    // 再高一点玩家必死，再低一点就只是血厚一点的精英。
    public int hit() {
        return boss ? 8 : 7;
    }

    /** 腐蚀粘液：10 点伤害 + 3 层中毒（0.2.8 替换原「腐液」） */
    public int acid() {
        return 10;
    }

    public int heavy() {
        return boss ? 18 : 16;
    }

    public boolean isBoss() {
        return boss;
    }

    @Override
    public String getIntent() {
        return switch (step) {
            case 0 -> "撞击 (" + hit() + " 伤害)";
            case 1 -> "腐蚀粘液 (" + acid() + " 伤害，附 3 层中毒)";
            case 2 -> "蓄力 (下回合重压 " + heavy() + " 伤害)";
            default -> "重压 (" + heavy() + " 伤害)";
        };
    }

    @Override
    public void doTurn(Player player, BattleSystem battle) {
        switch (step) {
            case 0 -> {
                battle.damagePlayer(hit(), name + "-撞击");
                step = 1;
            }
            case 1 -> {
                battle.damagePlayer(acid(), name + "-腐蚀粘液");
                if (player.isAlive()) {
                    battle.inflictStatus(StatusEffect.POISON, 3, 3);
                }
                step = 2;
            }
            case 2 -> {
                battle.log("< " + name + " 开始蓄力，地面在轻轻震动……");
                step = 3;
            }
            default -> {
                battle.damagePlayer(heavy(), name + "-重压");
                step = 0;
            }
        }
    }
}
