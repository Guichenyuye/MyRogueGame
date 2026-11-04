package com.myname.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 一局游戏的状态（设计文档《节点地图设计》第 6 节）。
 * <p>
 * 为什么要有这个类：{@link Player} 在一局里必须**只有一个实例**，
 * 血量、精力、最大生命要在节点之间连续。之前 GameScreen 自己 {@code new Player()}，
 * 打完一场出去血量就丢了，篝火和宝箱也就没意义。
 * <p>
 * 纯数据 + 流程，不 import libGDX。
 */
public class RunState {

    public final Player player;
    public final GameMap map;
    public final Random rng;
    public final long seed;

    /** 本局玩家选择的角色（0.2.4 起在角色选择界面锁定）。默认归尘 */
    public CharacterType character = CharacterType.GUICHEN;

    /**
     * 本局持续、不可消除的诅咒（0.2.8）。
     * 与 {@link Player#curses} 共享同一个列表引用，事件通过 {@code run.curses.add(...)}
     * 施加，战斗结算时 {@link BattleSystem} 直接读 {@code player.curses}，两边不会不同步。
     */
    public final List<Curse> curses;

    /** 玩家当前所在的层（1 起），也就是「即将挑战」的那一层 */
    public int floor = 1;

    /** 玩家在当前层选中的节点，null 表示还没选 */
    public MapNode pending;

    /** 首领已击破 */
    public boolean cleared = false;

    /** 归尘倒下，本局结束 */
    public boolean failed = false;

    // ===== 0.2 成长系统 =====
    // 一局的状态全部挂在这里（包括 player），将来做存档只序列化这一个对象就够。
    // 金币（0.2.8 起）迁移到了 Player.gold，这里通过 player 访问，见各处引用。
    public int exp = 0;
    public int level = 1;
    /** 攒着没花的属性点。0.2 的三选一界面会强制花掉 */
    public int pendingAttributePoints = 0;

    /** 宝箱防脸黑：连续几次开出天赋。连续 2 次后，第 3 次宝箱强制不给天赋（0.2.2 需求第 3 条） */
    public int consecutiveTalentFromTreasure = 0;

    /** 刚刚那场胜利拿到的资源（结算页要显示 "+12" 这种数字，得单独记一份） */
    public int lastGoldGain = 0;
    public int lastExpGain = 0;

    /** 刚打完的那场是不是精英战（0.2.2：精英战胜利后额外弹天赋二选一） */
    public boolean lastBattleWasElite = false;

    /**
     * 当前宝箱节点是否触发了宝箱怪（0.2.8）。
     * 宝箱节点有 10% 概率变成「宝箱怪」精英战斗；触发后此标记为 true，
     * {@link #createEnemies()} 固定返回宝箱怪，胜利后按精英奖励 + 20 金币结算。
     */
    public boolean treasureMimic = false;

    /** 本局已经触发过的事件标题（0.2.8：事件整局只能遇一次，见 {@link #rollEvent()}） */
    private final List<String> seenEvents = new ArrayList<>();

    /**
     * 当前事件是否触发了隐藏战斗（0.2.8 帐篷主人）。
     * 「仔细搜刮」30% 概率置 true，{@link #createEnemies()} 固定返回帐篷主人，
     * 胜利按普通战斗结算。
     */
    public boolean eventCombat = false;

    public RunState() {
        this(resolveSeed(), CharacterType.GUICHEN);
    }

    /** 指定角色（0.2.4 角色选择界面用） */
    public RunState(CharacterType character) {
        this(resolveSeed(), character);
    }

    /**
     * 不传种子时随机开一局，但允许用系统属性 / 环境变量固定下来：
     * <pre>
     * java -Drogue.seed=2026 -jar MyRogueGame.jar
     * set ROGUE_SEED=2026 &amp;&amp; java -jar MyRogueGame.jar
     * </pre>
     * 调试地图布局、复现某个 bug 的时候用得上。
     */
    private static long resolveSeed() {
        String raw = System.getProperty("rogue.seed");
        if (raw == null) {
            raw = System.getenv("ROGUE_SEED");
        }
        if (raw != null) {
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                // 写错了就退回随机，不要因为这个让游戏起不来
            }
        }
        return System.nanoTime();
    }

    /** 指定种子（调试 / 复现某张地图用） */
    public RunState(long seed) {
        this(seed, CharacterType.GUICHEN);
    }

    /** 指定种子 + 角色 */
    public RunState(long seed, CharacterType character) {
        this(seed, new Player(character));
    }

    /**
     * 指定种子 + 已构造好的 Player（0.2.8 降临者捏脸用：三维是分配出来的，不能用
     * {@code new Player(CharacterType.DESCENDER)} 的模板默认值）。
     */
    public RunState(long seed, Player player) {
        this.seed = seed;
        this.character = player.character;
        this.rng = new Random(seed);
        this.player = player;
        this.curses = player.curses;   // 共享引用，RunState 与 Player 的诅咒列表是同一份
        this.map = GameMap.generate(rng);
        this.floor = resolveStartFloor();
        this.pending = map.nodesOn(floor).get(0);   // 第 1 层固定一个战斗节点，直接开打
    }

    /** 调试用：{@code -Drogue.floor=8} 直接从首领层开局，省得每次打上去 */
    private int resolveStartFloor() {
        String raw = System.getProperty("rogue.floor");
        if (raw == null) {
            raw = System.getenv("ROGUE_FLOOR");
        }
        if (raw != null) {
            try {
                int value = Integer.parseInt(raw.trim());
                return Math.max(1, Math.min(map.floorCount(), value));
            } catch (NumberFormatException ignored) {
                // 同上，写错就当没写
            }
        }
        return 1;
    }

    // ==================== 地图查询 ====================

    /** 当前层可选的节点（1~3 个） */
    public List<MapNode> options() {
        return map.nodesOn(floor);
    }

    /** 选中本层第 index 个节点 */
    public MapNode choose(int index) {
        List<MapNode> list = options();
        if (index < 0 || index >= list.size()) {
            return null;
        }
        pending = list.get(index);
        return pending;
    }

    // ==================== 节点结算 ====================

    /**
     * 非战斗节点的入口分发。篝火 / 宝箱 / 事件各有各的结算，但都由这里按类型转发。
     * <p>
     * 0.2.2 起篝火和事件需要「玩家选择」，不再是纯文字结算；宝箱仍是随机奖励。
     * 所以这个方法只处理宝箱（随机奖励，直接给结果文字），篝火和事件交给
     * 各自的 {@link #resolveRest(int)} / {@link #rollEvent()} 由界面弹选择框。
     */
    public String resolveNonCombat() {
        if (pending == null) {
            return "";
        }
        switch (pending.type) {
            case TREASURE:
                return resolveTreasure();
            default:
                return "";
        }
    }

    /**
     * 宝箱：随机触发三种奖励之一（0.2.2 防脸黑）。
     * <ul>
     *   <li>随机 1 个未拥有天赋</li>
     *   <li>随机提升 1 个未满级技能 1 级</li>
     *   <li>最大生命 +5 并回满生命</li>
     * </ul>
     * 防脸黑：{@link #consecutiveTalentFromTreasure} 连续 2 次开出天赋，第 3 次强制不给天赋。
     *
     * @return 给界面显示的结算文字
     */
    public String resolveTreasure() {
        // 决定「这次能不能给天赋」：连续 2 次开天赋 -> 这次强制不给
        boolean talentForbidden = consecutiveTalentFromTreasure >= 2;

        // 三种奖励里先挑「允许」的类别
        int kind;
        if (talentForbidden) {
            // 只能给技能或生命，二选一
            kind = rng.nextInt(2);   // 0 = 技能，1 = 生命
        } else {
            kind = rng.nextInt(3);   // 0 = 天赋，1 = 技能，2 = 生命
        }

        String result;
        switch (kind) {
            case 0 -> {   // 天赋
                Talent t = rollUnownedTalent();
                if (t == null) {
                    // 没天赋可给了，退化成生命奖励
                    result = giveTreasureLife();
                    break;
                }
                player.grantTalent(t);
                consecutiveTalentFromTreasure++;
                result = "你撬开木箱，一道微光闪过。\n\n获得天赋【" + t.name + "】：" + t.desc;
            }
            case 1 -> {   // 技能：随机提升 1 个未满级技能
                List<Integer> upgradable = new ArrayList<>();
                for (int i = 0; i < Player.SLOT_COUNT; i++) {
                    if (player.skillLevels[i] < Skill.MAX_LEVEL) {
                        upgradable.add(i);
                    }
                }
                if (upgradable.isEmpty()) {
                    result = giveTreasureLife();   // 全满级，退化成生命
                    break;
                }
                int slot = upgradable.get(rng.nextInt(upgradable.size()));
                player.upgradeSkill(slot);
                consecutiveTalentFromTreasure = 0;   // 非天赋，重置防脸黑计数
                result = "箱子里躺着一卷写满心得的羊皮纸。\n\n【" + player.slots[slot].name
                        + "】等级 +1（当前 Lv." + player.skillLevels[slot] + "）";
            }
            default -> {   // 生命
                result = giveTreasureLife();
            }
        }
        return result;
    }

    /** 宝箱的「生命」奖励：最大生命 +5 并回满。同时重置防脸黑计数（非天赋） */
    private String giveTreasureLife() {
        consecutiveTalentFromTreasure = 0;
        int beforeMax = player.maxHp;
        player.maxHp += 5;
        player.heal(9999);   // 回满（heal 内部会 clamp 到 maxHp）
        return "一只落满灰的木箱，锁早就锈断了。\n\n"
                + "最大生命 " + beforeMax + " -> " + player.maxHp + "，并回满生命（" + player.hp + "）";
    }

    /**
     * 篝火二选一（0.2.2）。0 = 休息（回 30% 生命），1 = 冥想（+2 待分配属性）。
     *
     * @return 给界面显示的结算文字
     */
    public String resolveRest(int choice) {
        if (choice == 1) {
            pendingAttributePoints += 1;
            return "你在火堆旁盘腿而坐，梳理这一路的得失。\n\n获得 1 点自由属性（待分配 " + pendingAttributePoints + " 点）";
        }
        int amount = Math.max(1, (int) (player.maxHp * 0.30));
        int before = player.hp;
        player.heal(amount);
        return "篝火噼啪作响，你把湿透的外衣摊在石头上。\n\n"
                + "恢复 " + (player.hp - before) + " 点生命（" + before + " -> " + player.hp
                + " / " + player.maxHp + "）";
    }

    /**
     * 随机抽一个「还没遇过」的事件（0.2.8 起事件整局只遇一次）。
     * <p>
     * 从 {@link Event#ALL} 里剔除已触发过的事件（按标题记），在剩余池里随机抽一个，
     * 抽中即标记为「已遇过」。所有事件都遇过了则返回 null（调用方需兜底，比如降级成别的节点）。
     */
    public Event rollEvent() {
        List<Event> pool = new ArrayList<>();
        for (Event e : Event.ALL) {
            if (!seenEvents.contains(e.title)) {
                pool.add(e);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        Event picked = pool.get(rng.nextInt(pool.size()));
        seenEvents.add(picked.title);
        return picked;
    }

    /** 战斗胜利：清 CD、发金币和经验、标记节点、推进层数；首领打完就是通关 */
    public void onBattleWin() {
        // CD 跨节点清零（0.2 需求第 1 条）：下一场一开局四个技能必须都是可用状态。
        player.resetCooldowns();

        // 宝箱怪（0.2.8）：按精英战结算（精英奖励二选一）+ 额外 20 金币
        lastBattleWasElite = treasureMimic
                || (pending != null && pending.type == MapNode.Type.ELITE);

        // 金币 10~15，精英/首领更多（风险越高回报越大）。
        lastGoldGain = 10 + rng.nextInt(6);
        if (pending != null && pending.type == MapNode.Type.ELITE) {
            lastGoldGain += 5;
        } else if (pending != null && pending.type == MapNode.Type.BOSS) {
            lastGoldGain += 10;
        }
        if (treasureMimic) {
            lastGoldGain += 20;   // 宝箱怪额外 20 金币
        }
        player.gold += lastGoldGain;
        // 经验：按节点类型发，精英/首领给得多。gainExp 会顺便结算升级。
        int expGain = combatExpGain();
        lastExpGain = expGain;
        exp += expGain;   // 先累加，升级判定留给 ResultScreen 逐级处理（见 processLevelUps）

        boolean wasBoss = pending != null && pending.type == MapNode.Type.BOSS;
        markVisited();
        if (wasBoss) {
            cleared = true;
            return;
        }
        floor = Math.min(map.floorCount(), floor + 1);
        pending = null;
        // 0.2.8 帐篷主人：事件触发的战斗结束后清除标记，避免下一场普通战斗误出帐篷主人
        eventCombat = false;
    }

    /** 战斗节点给的经验（普通 8 / 精英 16 / 首领 24，都是 1 级升 2 级所需 8 的整数倍） */
    private int combatExpGain() {
        if (pending == null) {
            return 8;
        }
        return switch (pending.type) {
            case ELITE -> 16;
            case BOSS -> 24;
            default -> 8;
        };
    }

    /**
     * 升到下一级需要的经验（斐波那契：8 / 13 / 21 / 34 ……）。
     * <p>
     * 直接从 8 起跳而不是 1：前两层是教学区，升级太快会让玩家在第 1 层就开始做三选一，
     * 而他连四个技能的区别都还没摸清。
     */
    public int expToNextLevel() {
        return fib(level + 5);
    }

    /** 第 n 项斐波那契数（fib(1)=1, fib(2)=1, fib(6)=8 ……） */
    private static int fib(int n) {
        int a = 1;
        int b = 1;
        for (int i = 3; i <= n; i++) {
            int c = a + b;
            a = b;
            b = c;
        }
        return b;
    }

    /**
     * 加经验并结算所有升级（0.2 需求第 2 条）：多出来的经验**留在 exp 里**，
     * 不会升了一级就清零 —— 否则玩家会觉得「我打了半天，经验怎么没了」。
     * <p>
     * 注意：这只做「数值」结算（等级 +1、发属性点），**不弹三选一**。
     * 三选一是界面的事（ResultScreen 逐级弹），这里一次性把所有升级都结掉，
     * 主要给控制台自检用。
     *
     * @return 这次涨了几级
     */
    public int gainExp(int amount) {
        if (amount <= 0) {
            return 0;
        }
        exp += amount;
        int gained = 0;
        // 上限 20 是防御性的：万一将来把 expToNextLevel 改成返回 0，这里会变成死循环
        while (processLevelUp() && gained < 20) {
            gained++;
        }
        return gained;
    }

    public void onBattleLose() {
        failed = true;
    }

    // ==================== 0.2 成长：升级与三选一 ====================

    /**
     * 三选一的一个选项。UI 只读这几样来画，不碰生成逻辑。
     * <p>
     * {@code kind} 五种（0.2.3）：
     * <ul>
     *   <li>{@code SKILL}：进入「技能自选」子阶段（{@code slot} 固定 -1）</li>
     *   <li>{@code ATTRIBUTE}：+2 自由属性点</li>
     *   <li>{@code COMBAT_SKILL_CHOICE}：进入「战斗技巧自选」子阶段（抽 2 个自选）</li>
     *   <li>{@code GOLD}：+15 金币（技能全满级 / 技巧池空时顶替）</li>
     * </ul>
     */
    public static class Choice {
        public enum Kind { SKILL, ATTRIBUTE, COMBAT_SKILL_CHOICE, GOLD }

        public final Kind kind;
        /** 技能槽下标。恒 -1（自选，不指定） */
        public final int slot;
        /** 展示用文字 */
        public final String label;

        public Choice(Kind kind, int slot, String label) {
            this.kind = kind;
            this.slot = slot;
            this.label = label;
        }

        /** 技能升级：进入自选子阶段 */
        public static Choice skill() {
            return new Choice(Kind.SKILL, -1, "技能升级（自选）");
        }

        /** 属性点 +2 */
        public static Choice attribute() {
            return new Choice(Kind.ATTRIBUTE, -1, "属性点 +2");
        }

        /** 战斗技巧：进入自选子阶段（抽 2 个） */
        public static Choice combatSkillChoice() {
            return new Choice(Kind.COMBAT_SKILL_CHOICE, -1, "战斗技巧（二选一）");
        }

        public static Choice gold() {
            return new Choice(Kind.GOLD, -1, "金币 +15");
        }
    }

    /**
     * 生成一次三选一（0.2.3 规则）。
     * <ul>
     *   <li>槽 1：技能升级（进入自选）；技能全满级时换成 +15 金币</li>
     *   <li>槽 2：属性点 +2</li>
     *   <li>槽 3：战斗技巧（进入自选，抽 2 个）；技巧池空则换成 +15 金币</li>
     * </ul>
     */
    public List<Choice> rollChoices() {
        List<Choice> choices = new ArrayList<>();

        // 有没有还能升级的技能
        boolean hasUpgradable = false;
        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            if (player.skillLevels[i] < Skill.MAX_LEVEL) {
                hasUpgradable = true;
                break;
            }
        }

        // 槽 1：技能升级 / 金币
        choices.add(hasUpgradable ? Choice.skill() : Choice.gold());

        // 槽 2：属性点 +2
        choices.add(Choice.attribute());

        // 槽 3：战斗技巧自选 / 金币
        boolean hasUnownedSkill = rollUnownedCombatSkill() != null;
        choices.add(hasUnownedSkill ? Choice.combatSkillChoice() : Choice.gold());

        return choices;
    }

    /**
     * 随机抽 {@code count} 个玩家还没拥有的战斗技巧（不重复），用于「战斗技巧自选」子阶段。
     * 通用池 + 当前角色专属池一起抽；池子里不够 count 个就返回实际数量（可能为空）。
     */
    public List<CombatSkill> rollUnownedCombatSkills(int count) {
        List<CombatSkill> pool = new ArrayList<>();
        for (CombatSkill skill : CombatSkill.ALL) {
            if (skill.owner != null && skill.owner != character) {
                continue;   // 别人的专属技巧，本角色抽不到
            }
            if (!player.hasCombatSkill(skill.id)) {
                pool.add(skill);
            }
        }
        java.util.Collections.shuffle(pool, rng);
        List<CombatSkill> result = new ArrayList<>();
        for (int i = 0; i < count && i < pool.size(); i++) {
            result.add(pool.get(i));
        }
        return result;
    }

    /** 随机一个玩家还没拥有的技巧（通用 + 当前角色专属）；全拥有返回 null */
    public CombatSkill rollUnownedCombatSkill() {
        List<CombatSkill> pool = new ArrayList<>();
        for (CombatSkill skill : CombatSkill.ALL) {
            if (skill.owner != null && skill.owner != character) {
                continue;
            }
            if (!player.hasCombatSkill(skill.id)) {
                pool.add(skill);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    /** 随机一个玩家还没拥有的天赋（只抽当前角色专属池）；全拥有返回 null */
    public Talent rollUnownedTalent() {
        List<Talent> pool = new ArrayList<>();
        for (Talent talent : Talent.ALL) {
            if (talent.owner != character) {
                continue;   // 天赋按角色完全专属，别人的天赋抽不到
            }
            if (!player.hasTalent(talent.id)) {
                pool.add(talent);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    /**
     * 随机一个玩家还没拥有的「可替换技能」。
     * <p>
     * 0.2.8 起按角色过滤：只抽「当前角色专属 + 通用」的替换技能，
     * 判断标准：槽 2 / 槽 3 当前装的不是这个技能（即这个技能不在玩家手上）。
     * 全拥有返回 null。
     * <p>
     * 修复：之前写死归尘的蓄力/多重射击/血刺，导致降临者（及空梦）打精英战后
     * 会抽到归尘的技能，违反「技能按角色专属」的规则。
     */
    public Skill rollUnownedReplSkill() {
        List<Skill> pool = new ArrayList<>();
        Skill[] replPool = { Skill.CHARGE, Skill.MULTI_SHOT, Skill.BLOOD_SPIKE, Skill.SLASH };
        for (Skill s : replPool) {
            // 只抽当前角色的专属技能 + 通用技能（owner == null）
            if (s.owner != null && s.owner != character) {
                continue;
            }
            if (player.slots[2] != s && player.slots[3] != s) {
                pool.add(s);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    /**
     * 精英战胜利后的奖励：天赋 / 技能二选一（0.2.3）。
     * <p>
     * 返回一个二选一结果，{@code talent} 和 {@code skill} 各随机抽一个
     * （分别从未拥有天赋池、未拥有可替换技能池），玩家在 ResultScreen 里按 1/2 选。
     * 某一侧池子空了就返回 null，由调用方兜底（转属性点或跳过）。
     */
    public static class EliteReward {
        public final Talent talent;
        public final Skill skill;

        public EliteReward(Talent talent, Skill skill) {
            this.talent = talent;
            this.skill = skill;
        }
    }

    public EliteReward rollEliteReward() {
        Talent t = rollUnownedTalent();
        Skill s = rollUnownedReplSkill();
        if (t == null && s == null) {
            return null;
        }
        return new EliteReward(t, s);
    }

    /** 还有没有未拥有的天赋（只算当前角色专属池；精英二选一 / 宝箱 / 祭坛判断用） */
    public boolean hasAnyUnownedTalent() {
        for (Talent talent : Talent.ALL) {
            if (talent.owner != character) {
                continue;
            }
            if (!player.hasTalent(talent.id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 应用一个三选一结果。技能升级 / 属性点 / 技巧，各自落到对应的地方。
     */
    public void applyChoice(Choice choice) {
        switch (choice.kind) {
            case SKILL, COMBAT_SKILL_CHOICE -> {
                // 自选类型：真正的目标由 ResultScreen 的 SKILL_CHOICE / COMBAT_SKILL_CHOICE
                // 子阶段让玩家选。这里不做任何事，防止把「自选」误当成「自动执行」。
            }
            case ATTRIBUTE -> pendingAttributePoints += 1;
            case GOLD -> player.gold += 15;
        }
    }

    /**
     * 结算一次升级：消耗经验、等级 +1、给 1 点待分配属性。
     *
     * @return 是否真的升了一级（经验不足返回 false）
     */
    public boolean processLevelUp() {
        if (exp < expToNextLevel()) {
            return false;
        }
        exp -= expToNextLevel();
        level++;
        pendingAttributePoints += 1;
        return true;
    }

    /**
     * 一次性结算所有升级（0.2.1 起：三选一不再绑定升级，改为战斗胜利必触发）。
     * <p>
     * 玩家在三选一界面选完奖励后，才轮到经验结算升级 —— 升级只发「+2 待分配属性点」，
     * **不再**重复触发三选一。这里把「经验还够就继续升」的循环封起来，
     * 供 ResultScreen 和 BattleTest 共用，避免两处各写一份 while 循环。
     *
     * @return 这次一共升了几级
     */
    public int settleLevelUps() {
        int gained = 0;
        // 上限 20 是防御性的：万一 expToNextLevel 返回 0，这里不会死循环
        while (processLevelUp() && gained < 20) {
            gained++;
        }
        return gained;
    }

    /** 非战斗节点看完结果，推进到下一层 */
    public void advance() {
        markVisited();
        floor = Math.min(map.floorCount(), floor + 1);
        pending = null;
    }

    private void markVisited() {
        if (pending != null) {
            pending.visited = true;
        }
    }

    // ==================== 敌人配置 ====================

    /**
     * 当前节点该打谁（0.2.5 起委托 {@link EnemyPool}，按节点类型和层数抽取）。
     * <p>
     * 0.2.8 宝箱怪：若 {@link #treasureMimic} 为 true，固定返回宝箱怪（宝箱节点的隐藏战斗）。
     */
    public List<Enemy> createEnemies() {
        if (pending == null) {
            return new ArrayList<>();
        }
        if (treasureMimic) {
            List<Enemy> list = new ArrayList<>();
            list.add(new TreasureChest());
            return list;
        }
        if (eventCombat) {
            // 0.2.8 帐篷主人：事件触发的隐藏战斗
            List<Enemy> list = new ArrayList<>();
            list.add(new TentOwner());
            return list;
        }
        return EnemyPool.create(pending.type, pending.floor, rng);
    }

    /**
     * 宝箱节点是否触发宝箱怪（0.2.8）：10% 概率。
     * <p>
     * 由 {@code MapScreen} 在玩家选中 TREASURE 节点时调用一次，命中则置
     * {@link #treasureMimic} = true，本次宝箱节点变成宝箱怪战斗。
     */
    public boolean rollTreasureMimic() {
        treasureMimic = rng.nextDouble() < 0.10;
        return treasureMimic;
    }
}
