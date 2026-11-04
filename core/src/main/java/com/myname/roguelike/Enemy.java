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

    /** 头顶显示的下回合意图（文档 5.3） */
    public abstract String getIntent();

    /** 执行一次行动 */
    public abstract void doTurn(Player player, BattleSystem battle);
}
