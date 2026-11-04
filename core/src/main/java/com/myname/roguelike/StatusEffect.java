package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * 负面效果 / 诅咒的静态定义（0.2.6 数值框架）。
 * <p>
 * 这一版**只搭数值框架，不接具体敌人技能**：定义清楚每种效果的
 * 类型、参数、结算语义，把「玩家挂状态」和「BattleSystem 按状态改结算」的
 * 钩子打通。以后敌人或事件往玩家身上挂这些状态时，直接 {@link #addStacks}。
 * <p>
 * 纯 Java，不 import libGDX。状态实例（几层、剩几回合）放在 {@link Player}
 * 的 {@code statusEffects} 里，这里只放「不会变」的效果定义。
 */
public class StatusEffect {

    /** 效果 ID：BattleSystem 的结算钩子按它分发。诅咒（0.2.8）已独立成 {@link Curse} */
    public enum Id {
        // ---- 负面效果（战斗临时，有回合数）----
        /** 虚弱：造成的伤害 -30% */
        WEAKEN,
        /** 易伤：受到的伤害 +50% */
        VULNERABLE,
        /** 晕眩：跳过本回合行动 */
        STUN,
        /** 中毒：每回合开始按当前层数扣血，随后层数 -1 */
        POISON,
        /** 流血：每次受伤额外扣「层数」点血，随后层数 -1 */
        BLEED,
        // ---- 增益效果（0.3.0 起，正面 buff）----
        /** 生命恢复：每回合开始恢复 4 点生命，持续若干回合（生命恢复药水） */
        REGEN,
        /** 力量：每次攻击额外造成 5 点伤害（力量药水，不触发连击/血之泪，吃免伤） */
        STRENGTH_UP
    }

    public final Id id;
    public final String name;
    /** 一句话说明，显示在状态栏 / 悬停浮窗 */
    public final String desc;
    /** 单字简称（0.2.8）：战斗界面效果栏用「晕 / 毒 / 血 / 虚 / 脆 / 复 / 力」这种单字紧凑显示 */
    public final String shortName;

    /**
     * 基础强度参数（百分比按百分数记，如 30 = 30%、50 = 50%、10 = 10%）。
     * 虚弱 / 易伤 / 怠惰用它；晕眩 / 中毒 / 流血 / 恶魔抓痕 / 旧疾 / 增益类用不到。
     */
    public final int magnitude;

    /**
     * 是否为增益（正面效果）。0.3.0 起区分：增益（绿色/金色）显示在前，
     * 减益（红色/紫色）显示在后，见 {@code GameScreen} 的效果栏排序。
     */
    public final boolean isBuff;

    /**
     * 展示优先级（0.3.0）：数字越小越靠前。增益整体排在减益之前，
     * 同阵营内部再按这个值排（玩家自定义的「复4 流3 毒5」顺序）。
     */
    public final int priority;

    private StatusEffect(Id id, String name, String desc, String shortName, int magnitude) {
        this(id, name, desc, shortName, magnitude, false, 0);
    }

    private StatusEffect(Id id, String name, String desc, String shortName, int magnitude,
                         boolean isBuff, int priority) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.shortName = shortName;
        this.magnitude = magnitude;
        this.isBuff = isBuff;
        this.priority = priority;
    }

    // ================= 负面效果 =================

    public static final StatusEffect WEAKEN = new StatusEffect(
            Id.WEAKEN, "虚弱", "造成的伤害 -30%", "虚", 30);

    public static final StatusEffect VULNERABLE = new StatusEffect(
            Id.VULNERABLE, "易伤", "受到的伤害 +50%", "脆", 50);

    public static final StatusEffect STUN = new StatusEffect(
            Id.STUN, "晕眩", "跳过本回合行动", "晕", 0);

    public static final StatusEffect POISON = new StatusEffect(
            Id.POISON, "中毒", "每回合开始按当前层数扣除生命，随后层数 -1", "毒", 0);

    public static final StatusEffect BLEED = new StatusEffect(
            Id.BLEED, "流血", "每次受伤额外扣除层数点生命，随后层数 -1", "血", 0);

    // ================= 增益效果（0.3.0 起）=================

    /** 生命恢复：每回合开始恢复 4 点生命（生命恢复药水） */
    public static final StatusEffect REGEN = new StatusEffect(
            Id.REGEN, "生命恢复", "每回合开始恢复 4 点生命", "复", 4, true, 10);

    /** 力量：每次攻击额外造成 5 点伤害（力量药水） */
    public static final StatusEffect STRENGTH_UP = new StatusEffect(
            Id.STRENGTH_UP, "力量", "每次攻击额外造成 5 点伤害", "力", 5, true, 20);

    /** 全部效果定义，遍历 / 展示用（0.2.8 起不含诅咒，诅咒见 {@link Curse#ALL}） */
    public static final StatusEffect[] ALL = {
            WEAKEN, VULNERABLE, STUN, POISON, BLEED, REGEN, STRENGTH_UP
    };

    /** 按 ID 查定义（找不到返回 null） */
    public static StatusEffect of(Id id) {
        for (StatusEffect e : ALL) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    // ================= 实例（挂在 Player 上）=================

    /**
     * 玩家身上的一种状态的实例：几层、还剩几回合。
     * <p>
     * {@code duration} = -1 表示「本局持续、不因回合数到期」（诅咒默认如此），
     * 0 表示到期。负面效果通常有正数回合数，回合开始时 {@code duration--}。
     */
    public static class Instance {
        public final StatusEffect effect;
        /** 层数（中毒 / 流血依赖层数；其余效果层数通常恒 1） */
        public int stacks;
        /** 剩余回合数。-1 = 不因回合数到期（诅咒） */
        public int duration;

        public Instance(StatusEffect effect, int stacks, int duration) {
            this.effect = effect;
            this.stacks = stacks;
            this.duration = duration;
        }
    }

    /**
     * 给玩家的状态列表按效果叠加层数 / 刷新回合。
     * <p>
     * 叠加规则（数值框架暂定，后续接敌人技能时可按效果再细化）：
     * 同名效果不新增实例，而是取「层数相加、回合数取较大值」。
     * 诅咒（duration = -1）保持 -1。
     */
    public static void addStacks(List<Instance> list, StatusEffect effect, int stacks, int duration) {
        for (Instance inst : list) {
            if (inst.effect.id == effect.id) {
                inst.stacks += stacks;
                if (inst.duration != -1 && duration != -1) {
                    inst.duration = Math.max(inst.duration, duration);
                } else if (duration == -1) {
                    inst.duration = -1;
                }
                return;
            }
        }
        list.add(new Instance(effect, stacks, duration));
    }

    /** 玩家是否带有某个效果 */
    public static boolean has(List<Instance> list, Id id) {
        for (Instance inst : list) {
            if (inst.effect.id == id) {
                return true;
            }
        }
        return false;
    }

    /** 玩家某效果的层数（没有则 0） */
    public static int stacksOf(List<Instance> list, Id id) {
        for (Instance inst : list) {
            if (inst.effect.id == id) {
                return inst.stacks;
            }
        }
        return 0;
    }

    /** 移除某效果的指定层数，层数归零则删实例。返回实际移除的层数 */
    public static int removeStacks(List<Instance> list, Id id, int amount) {
        for (int i = 0; i < list.size(); i++) {
            Instance inst = list.get(i);
            if (inst.effect.id == id) {
                int removed = Math.min(inst.stacks, amount);
                inst.stacks -= removed;
                if (inst.stacks <= 0) {
                    list.remove(i);
                }
                return removed;
            }
        }
        return 0;
    }

    /** 复制一份当前状态列表（给界面显示/调试用，避免外部误改内部状态） */
    public static List<Instance> copyOf(List<Instance> list) {
        return new ArrayList<>(list);
    }

    /**
     * 返回一份按「增益在前、减益在后，同阵营内按 priority 升序」排序后的副本（0.3.0）。
     * <p>
     * 不修改原列表，界面用它来决定效果栏的显示顺序（正面效果显示在前，如「复4 力5 毒5」）。
     */
    public static List<Instance> sortedByPriority(List<Instance> list) {
        List<Instance> copy = new ArrayList<>(list);
        copy.sort((a, b) -> {
            if (a.effect.isBuff != b.effect.isBuff) {
                return a.effect.isBuff ? -1 : 1;   // 增益在前
            }
            return Integer.compare(a.effect.priority, b.effect.priority);
        });
        return copy;
    }
}
