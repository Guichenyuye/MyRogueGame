package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * 敌人基类（文档 5.1）。
 * <p>
 * 敌人没有力量/精准，伤害直接写在技能里；也没有蓝条和 CD。
 * 抽这一层是为了让横扫（AOE）能对一组敌人循环，后面加远程怪、精英怪时直接继承。
 * <p>
 * 0.2.8 起敌人也挂负面效果（{@link #statusEffects}），用于「锈蚀箭矢」给敌人挂流血、
 * 「倒映」反弹负面效果、中毒等。结算钩子在 {@link BattleSystem}。
 */
public abstract class Enemy {

    public String name;
    public int maxHp;
    public int hp;
    public int defense;

    /**
     * 受击伤害倍率（0.3.1 骑士长祈祷等「免伤」用）。
     * 默认 1.0。小于 1 表示减伤（如祈祷的 10% 免伤 = 0.9），在防御减伤之后额外乘一次。
     */
    public double damageTakenMult = 1.0;

    /**
     * 敌人护盾（0.3.1 南边领地骑士系）。
     * 见习骑士「盾击」、皇家守卫「坚守阵地」会给护盾；受到玩家/召唤物伤害时先扣护盾再扣血。
     */
    public int shield = 0;

    /** 敌人身上挂着的负面效果实例（0.2.8）。层数 + 剩余回合，见 {@link StatusEffect} */
    public final List<StatusEffect.Instance> statusEffects = new ArrayList<>();

    protected Enemy(String name, int maxHp, int defense) {
        this.name = name;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.defense = defense;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    /**
     * 用护盾吸收伤害，返回「护盾没能挡住、仍需扣血」的部分。
     * <p>
     * 先扣护盾，护盾不够再扣血。调用方拿到返回值后再 {@code hp -= 返回值}。
     * 例：护盾 15，伤害 7 -> 护盾剩 8，返回 0；伤害 20 -> 护盾清空，返回 5。
     */
    public int absorbByShield(int damage) {
        if (shield <= 0) {
            return damage;
        }
        int absorbed = Math.min(shield, damage);
        shield -= absorbed;
        return damage - absorbed;
    }

    /** 头顶显示的下回合意图（文档 5.3） */
    public abstract String getIntent();

    /** 执行一次行动 */
    public abstract void doTurn(Player player, BattleSystem battle);
}
