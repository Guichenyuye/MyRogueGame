package com.myname.roguelike;

/**
 * 一个事件（0.2.2 事件系统）。
 * <p>
 * 纯 Java 数据 + 效果，不 import libGDX —— BattleTest 能直接调 {@link #apply(RunState, int)}
 * 验证效果，界面（EventScreen）只负责展示文字和转发按键。
 * <p>
 * 每个事件有两个选项，玩家按 1/2 选。选项效果用 {@link Effect} 函数式接口表达，
 * 这样「扣血换属性」「扣技能等级换天赋」这种带副作用的选项也能用一句 lambda 写清楚。
 */
public class Event {

    /** 一个选项的效果：对当前这局 RunState 施加影响。
     *  <p>
     *  返回 {@code true} 表示成功执行（事件可以关闭）；{@code false} 表示条件不满足
     *  （如金币不足），事件保持打开、等待玩家选别的选项。 */
    @FunctionalInterface
    public interface Effect {
        boolean apply(RunState run);
    }

    public final String title;
    /** 事件描述正文 */
    public final String text;
    /** 选项 1 的按钮文字 */
    public final String option1Text;
    /** 选项 2 的按钮文字 */
    public final String option2Text;

    private final Effect option1;
    private final Effect option2;

    public Event(String title, String text,
                 String option1Text, Effect option1,
                 String option2Text, Effect option2) {
        this.title = title;
        this.text = text;
        this.option1Text = option1Text;
        this.option1 = option1;
        this.option2Text = option2Text;
        this.option2 = option2;
    }

    /** 玩家选了第 index 个选项（0 或 1），应用对应效果。返回是否成功执行（false = 条件不满足，事件保持） */
    public boolean apply(RunState run, int index) {
        if (index == 0) {
            return option1.apply(run);
        } else if (index == 1) {
            return option2.apply(run);
        }
        return false;
    }

    // ================= 内置事件池（0.2.2 至少 3 个）=================

    /**
     * 灌木丛：绕道（恢复 5 点生命） / 进入（-10 生命 +1 属性）。
     * <p>
     * 0.2.4：绕道从「+1 属性」改为「恢复 5 点生命」——绕道避开了危险但没拿到奖励，
     * 给玩家一个低风险的续航选项；进入的 +2 属性随全局属性点回调降为 +1。
     */
    public static final Event BUSH = new Event(
            "灌木丛",
            "一丛荆棘拦在路上，绕过去要花不少时间。",
            "绕道（恢复 5 点生命）",
            run -> {
                run.player.heal(5);
                return true;
            },
            "进入（-10 生命，+1 属性点）",
            run -> {
                run.player.takeDamage(10);
                run.pendingAttributePoints += 1;
                return true;
            });

    /**
     * 废弃营地（0.2.8 改造）：仔细搜刮——+20 金币，30% 概率惊动帐篷主人进入战斗
     * / 粗略搜刮——+5 金币安全离开。
     * <p>
     * 「进入战斗」通过 {@code run.eventCombat} 标记，由 MapScreen 检测后切入战斗，
     * {@link RunState#createEnemies()} 返回帐篷主人，胜利按普通战斗结算。
     */
    public static final Event CAMP = new Event(
            "废弃营地",
            "一顶破旧的帐篷，主人似乎很久没回来了。",
            "仔细搜刮（+20 金币，30% 惊动主人）",
            run -> {
                run.player.gold += 20;
                // 30% 概率惊动帐篷主人，进入战斗
                if (run.rng.nextDouble() < 0.30) {
                    run.eventCombat = true;
                }
                return true;
            },
            "粗略搜刮（+5 金币，安全离开）",
            run -> {
                run.player.gold += 5;
                return true;
            });

    /**
     * 神秘祭坛：祈祷（随机技能 -1 级，换 1 个随机天赋） / 离开。
     * <p>
     * 如果玩家没有可降级的技能（全是 0 级），祈祷就不扣技能、白拿天赋 —— 这点交给
     * 调用方（EventScreen）在生成事件时判断，这里只管把「扣 1 个随机技能 1 级」做出来。
     */
    public static final Event ALTAR = new Event(
            "神秘祭坛",
            "石台上刻着看不懂的符文，隐隐透出一丝危险的气息。",
            "祈祷（随机技能 -1 级，+1 随机天赋）",
            run -> {
                // 随机找一个有等级的技能，扣 1 级
                java.util.List<Integer> leveled = new java.util.ArrayList<>();
                for (int i = 0; i < Player.SLOT_COUNT; i++) {
                    if (run.player.skillLevels[i] > 0) {
                        leveled.add(i);
                    }
                }
                if (!leveled.isEmpty()) {
                    int slot = leveled.get(run.rng.nextInt(leveled.size()));
                    run.player.skillLevels[slot]--;
                }
                // 随机给一个未拥有的天赋
                Talent t = run.rollUnownedTalent();
                if (t != null) {
                    run.player.grantTalent(t);
                }
                return true;
            },
            "摸索周边（+1 自由属性点）",
            run -> {
                run.pendingAttributePoints += 1;
                return true;
            });

    /**
     * 遇见邪教徒（0.2.8）：粗略看看——花 10 金币才能离开（金币不足则无法离开，只能选献祭）
     * / 献祭——随机获得 1 个诅咒 + 随机 1 个天赋。
     * <p>
     * 诅咒从 {@link Curse#ALL} 随机抽（可重复叠加？不，诅咒列表允许重复但意义不大，
     * 这里简单抽一个加入）；天赋从当前角色未拥有池抽，抽不到就只拿诅咒。
     */
    public static final Event CULTIST = new Event(
            "遇见邪教徒",
            "几个披着黑袍的人围着一座血迹斑斑的祭坛，低声念着你听不懂的祷词。",
            "粗略看看（花 10 金币离开）",
            run -> {
                if (run.player.gold < 10) {
                    return false;   // 金币不足，离不开
                }
                run.player.gold -= 10;
                return true;
            },
            "献祭（随机诅咒 + 随机天赋）",
            run -> {
                Curse curse = Curse.ALL[run.rng.nextInt(Curse.ALL.length)];
                run.player.curses.add(curse);
                Talent talent = run.rollUnownedTalent();
                if (talent != null) {
                    run.player.grantTalent(talent);
                }
                return true;
            });

    /**
     * 大师的教导（0.3.0）：苦练——扣 5 点生命上限，随机血/力/准/防 +1
     * / 劳逸结合——花 30 金币，得 1 点自由属性。
     */
    public static final Event MASTER = new Event(
            "大师的教导",
            "一位白发老者正在林间打拳，见你经过，微微点头。",
            "苦练（-5 生命上限，随机属性 +1）",
            run -> {
                run.player.maxHp -= 5;
                run.player.hp = Math.min(run.player.hp, run.player.maxHp);
                // 随机血/力/准/防 +1（0 力 1 准 2 防 3 生命）
                int attr = run.rng.nextInt(4);
                if (attr == 3) {
                    run.player.maxHp += 5;
                    run.player.hp += 5;
                } else {
                    run.player.addAttributePoint(attr);
                }
                return true;
            },
            "劳逸结合（花 30 金币，+1 自由属性）",
            run -> {
                if (run.player.gold < 30) {
                    return false;
                }
                run.player.gold -= 30;
                run.pendingAttributePoints += 1;
                return true;
            });

    /**
     * 露天温泉（0.3.0）：简单泡泡——恢复 30% 生命 / 美美一泡——花 20 金币回满。
     */
    public static final Event HOT_SPRING = new Event(
            "露天温泉",
            "氤氲的热气从石缝间升起，一汪温泉静静地冒着泡。",
            "简单泡泡（恢复 30% 生命）",
            run -> {
                int amount = Math.max(1, (int) (run.player.maxHp * 0.30));
                run.player.heal(amount);
                return true;
            },
            "美美一泡（花 20 金币，回满生命）",
            run -> {
                if (run.player.gold < 20) {
                    return false;
                }
                run.player.gold -= 20;
                run.player.heal(9999);
                return true;
            });

    /** 全部事件，事件节点随机抽取时从这里遍历 */
    public static final Event[] ALL = { BUSH, CAMP, ALTAR, CULTIST, MASTER, HOT_SPRING };
}
