package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;

import java.util.List;

/**
 * 战斗胜利后的结算界面（0.2.1 体验修正版）。
 * <p>
 * 流程（0.2.1 需求：三选一不再绑定升级，改为战斗胜利必触发）：
 * <pre>
 * 胜利 -> 显示金币/经验/进度
 *      -> 按空格 -> 必弹「三选一」（技能升级 / 属性点 / 战斗技巧）
 *      -> 选完 -> 结算经验升级（只发 +2 属性点，不再重复三选一）
 *      -> 若有待分配属性点：弹「属性点分配」（临时池 + 可反悔 + 置灰确认）
 *      -> 全部完成，按空格回地图
 * </pre>
 * <p>
 * 本类只负责「展示 + 接按键/鼠标」，所有数值结算都在 {@link RunState} / {@link Player} 里。
 */
public class ResultScreen implements Screen {

    private enum Phase {
        RESULT,                 // 显示金币 + 经验 + 进度
        CHOICE,                 // 三选一
        SKILL_CHOICE,           // 技能自选（选中「技能升级」后）
        COMBAT_SKILL_CHOICE,    // 战斗技巧自选（选中「战斗技巧」后，抽 2 个）
        ELITE_REWARD,           // 精英战的天赋/技能二选一
        SKILL_REPLACE,          // 替换技能（选槽 3 / 槽 4）
        ATTRIBUTE,              // 属性点分配（确认制）
        DONE                    // 全部完成，等空格回地图
    }

    // ---- 配色 ----
    private static final Color BG = new Color(0.05f, 0.05f, 0.06f, 1f);
    private static final Color PANEL_BG = new Color(0.09f, 0.10f, 0.12f, 0.96f);
    private static final Color BAR_BG = new Color(0.22f, 0.22f, 0.26f, 1f);
    private static final Color DIM = new Color(0.45f, 0.45f, 0.50f, 1f);
    private static final Color GREEN = new Color(0.45f, 0.85f, 0.45f, 1f);
    private static final Color GOLD = new Color(0.95f, 0.82f, 0.30f, 1f);
    private static final Color GOLD_BRIGHT = new Color(1f, 0.94f, 0.50f, 1f);
    private static final Color BLUE = new Color(0.45f, 0.78f, 0.98f, 1f);
    private static final Color WARN = new Color(0.98f, 0.62f, 0.30f, 1f);
    private static final Color HP_COLOR = new Color(0.85f, 0.24f, 0.24f, 1f);

    private final Main game;
    private final RunState run;
    private final Ui ui;
    private final GlyphLayout layout = new GlyphLayout();

    private Phase phase = Phase.RESULT;

    /** 当前这一级的三选一选项（phase == CHOICE 时非空） */
    private List<RunState.Choice> choices;

    /** 战斗技巧自选的候选（phase == COMBAT_SKILL_CHOICE 时非空） */
    private List<CombatSkill> combatSkillChoices;

    /** 精英战的天赋/技能二选一（phase == ELITE_REWARD 时非空） */
    private RunState.EliteReward eliteReward;

    /** 待替换的新技能（phase == SKILL_REPLACE 时非空） */
    private Skill pendingReplaceSkill;

    /**
     * 属性点分配的临时池状态机（0.2.1 可反悔 + 置灰确认）。核心逻辑在纯 Java 的
     * {@link AttributeAllocator} 里，BattleTest 直接测它；这里只负责按键/鼠标转发。
     * phase == ATTRIBUTE 时非空。
     */
    private AttributeAllocator allocator;

    /** 属性分配界面四行「-」「+」按钮的命中区域（渲染时填充，鼠标点击时查表） */
    private final Rectangle[] minusRects = new Rectangle[4];
    private final Rectangle[] plusRects = new Rectangle[4];

    public ResultScreen(Main game, RunState run) {
        this(game, run, false);
    }

    /**
     * @param attributeOnly true 表示「从地图节点跳进来，只做属性点分配」，
     *                      跳过结算页和三选一，直接进属性分配阶段。
     */
    public ResultScreen(Main game, RunState run, boolean attributeOnly) {
        this.game = game;
        this.run = run;
        this.ui = new Ui(game.batch, game.font);
        if (attributeOnly) {
            enterAttributeOrDone();
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        handleInput();

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        ui.batch().begin();

        switch (phase) {
            case RESULT -> drawResult(w, h);
            case CHOICE -> drawChoice(w, h);
            case SKILL_CHOICE -> drawSkillChoice(w, h);
            case COMBAT_SKILL_CHOICE -> drawCombatSkillChoice(w, h);
            case ELITE_REWARD -> drawEliteReward(w, h);
            case SKILL_REPLACE -> drawSkillReplace(w, h);
            case ATTRIBUTE -> drawAttribute(w, h);
            case DONE -> drawDone(w, h);
        }

        ui.batch().end();
    }

    /** 阶段 1：金币 + 经验 + 进度条 */
    private void drawResult(float w, float h) {
        float cy = h / 2f;
        float boxW = 480f;
        float boxH = 300f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GOLD);

        ui.textCentered("战斗胜利", w / 2f, by + boxH - 24, GOLD, 1.8f);

        // 金币
        ui.textCentered("金币 +" + run.lastGoldGain + "（共 " + run.player.gold + "）",
                w / 2f, by + boxH - 66, GOLD_BRIGHT, 1.0f);

        // 经验
        int exp = run.exp;
        int need = run.expToNextLevel();
        ui.textCentered("经验 +" + run.lastExpGain, w / 2f, by + boxH - 104, BLUE, 1.0f);
        ui.textCentered(exp + " / " + need, w / 2f, by + boxH - 132, Color.WHITE, 1.0f);
        // 进度条放在经验数字下方：文字占 [y-24, y]，条从 y-28 起画才不压字
        ui.bar(bx + 40, by + boxH - 158, boxW - 80, 14,
                Math.min(1f, (float) exp / need), BLUE, BAR_BG);

        // 等级
        ui.textCentered("等级 " + run.level, w / 2f, by + boxH - 192, DIM, 1.0f);

        ui.textCentered("按 [空格] 继续", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 阶段 2：三选一 */
    private void drawChoice(float w, float h) {
        float cy = h / 2f;
        float boxW = 560f;
        float boxH = 320f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GOLD);

        ui.textCentered("升级！选择一个奖励", w / 2f, by + boxH - 28, GOLD_BRIGHT, 1.6f);

        if (choices == null || choices.isEmpty()) {
            ui.textCentered("（无可选项）", w / 2f, cy, DIM, 1.0f);
            return;
        }

        float y = by + boxH - 70;
        for (int i = 0; i < choices.size(); i++) {
            RunState.Choice c = choices.get(i);
            Color accent = switch (c.kind) {
                case SKILL -> GOLD_BRIGHT;
                case ATTRIBUTE -> GREEN;
                case GOLD -> GOLD_BRIGHT;
                case COMBAT_SKILL_CHOICE -> BLUE;
            };
            String text = "[" + (i + 1) + "] " + choiceLabel(c);
            ui.text(text, bx + 28, y, accent, 1.0f);
            y -= 40;
        }
    }

    /** 三选一选项的展示文字 */
    private String choiceLabel(RunState.Choice c) {
        return switch (c.kind) {
            case SKILL -> "技能升级（自选一个技能 +1 级）";
            case ATTRIBUTE -> "属性点 +1";
            case GOLD -> "金币 +15";
            case COMBAT_SKILL_CHOICE -> "战斗技巧（二选一）";
        };
    }

    /** 技能自选子阶段：平铺 4 个技能，显示名称与当前等级 */
    private void drawSkillChoice(float w, float h) {
        float cy = h / 2f;
        float boxW = 560f;
        float boxH = 340f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GOLD);

        ui.textCentered("选择要升级的技能", w / 2f, by + boxH - 28, GOLD_BRIGHT, 1.6f);

        Player p = run.player;
        float y = by + boxH - 72;
        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            Skill s = p.slots[i];
            boolean maxed = p.skillLevels[i] >= Skill.MAX_LEVEL;
            Color c = maxed ? DIM : Color.WHITE;
            String text = "[" + (i + 1) + "] " + s.name + "  Lv." + p.skillLevels[i]
                    + (maxed ? "  （已满级）" : "");
            ui.text(text, bx + 28, y, c, 1.0f);
            y -= 40;
        }

        ui.textCentered("按 [1]-[4] 选择要升级的技能", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 战斗技巧自选子阶段：从抽到的 2 个技巧里选 1 个 */
    private void drawCombatSkillChoice(float w, float h) {
        float cy = h / 2f;
        float boxW = 560f;
        float boxH = 300f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GOLD);

        ui.textCentered("获得一个战斗技巧", w / 2f, by + boxH - 28, GOLD_BRIGHT, 1.6f);

        if (combatSkillChoices == null || combatSkillChoices.isEmpty()) {
            ui.textCentered("（无技巧可选）", w / 2f, cy, DIM, 1.0f);
            return;
        }

        float y = by + boxH - 72;
        for (int i = 0; i < combatSkillChoices.size(); i++) {
            CombatSkill s = combatSkillChoices.get(i);
            String tag = s.owner != null ? "[专属] " : "";
            ui.text("[" + (i + 1) + "] " + tag + s.name + "：" + s.desc, bx + 28, y, BLUE, 1.0f);
            y -= 40;
        }

        ui.textCentered("按 [1]/[2] 选择技巧", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 精英战的天赋/技能二选一（0.2.3：不再二选一天赋，改为天赋 vs 技能） */
    private void drawEliteReward(float w, float h) {
        float cy = h / 2f;
        float boxW = 560f;
        float boxH = 300f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GOLD);

        ui.textCentered("精英战奖励：二选一", w / 2f, by + boxH - 28, GOLD_BRIGHT, 1.6f);

        if (eliteReward == null) {
            ui.textCentered("（无奖励）", w / 2f, cy, DIM, 1.0f);
            return;
        }

        float y = by + boxH - 72;
        if (eliteReward.talent != null) {
            ui.text("[1] 天赋：" + eliteReward.talent.name + "：" + eliteReward.talent.desc,
                    bx + 28, y, GOLD_BRIGHT, 1.0f);
            y -= 40;
        }
        if (eliteReward.skill != null) {
            ui.text("[2] 技能：" + eliteReward.skill.name + "：" + eliteReward.skill.desc,
                    bx + 28, y, BLUE, 1.0f);
        }

        ui.textCentered("按 [1]/[2] 选择", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 替换技能子阶段：把新技能替换进槽 3 或槽 4 */
    private void drawSkillReplace(float w, float h) {
        float cy = h / 2f;
        float boxW = 560f;
        float boxH = 300f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, BLUE);

        ui.textCentered("替换技能", w / 2f, by + boxH - 28, GOLD_BRIGHT, 1.6f);

        if (pendingReplaceSkill == null) {
            ui.textCentered("（无技能）", w / 2f, cy, DIM, 1.0f);
            return;
        }

        ui.text("新技能：" + pendingReplaceSkill.name + "：" + pendingReplaceSkill.desc,
                bx + 28, by + boxH - 66, BLUE, 1.0f);

        // 槽 3 / 槽 4 当前技能，按 1/2 选要替换哪个
        Player p = run.player;
        ui.text("[1] 槽 3 当前：" + p.slots[2].name + "  ->  替换为 " + pendingReplaceSkill.name,
                bx + 28, by + boxH - 104, Color.WHITE, 1.0f);
        ui.text("[2] 槽 4 当前：" + p.slots[3].name + "  ->  替换为 " + pendingReplaceSkill.name,
                bx + 28, by + boxH - 142, Color.WHITE, 1.0f);

        ui.textCentered("按 [1]/[2] 选择要替换的槽位", w / 2f, by + 26, GREEN, 1.0f);
    }

    /** 阶段 3：属性点分配（临时池 + 可反悔 + 置灰确认，0.2.1） */
    private void drawAttribute(float w, float h) {
        float cy = h / 2f;
        float boxW = 600f;
        float boxH = 360f;
        float bx = w / 2f - boxW / 2f;
        float by = cy - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, GREEN);

        int remaining = allocator.remaining();
        ui.textCentered("分配属性点（剩 " + remaining + " 点）",
                w / 2f, by + boxH - 28, GREEN, 1.6f);

        Player p = run.player;

        // 四行属性，每行：名称 + 当前值 + 本次临时加点 + [-] [+] 两个按钮
        String[] names = {"力量", "精准", "防御", "生命"};
        int[] cur = {p.strength, p.precision, p.defense, p.maxHp};

        float y = by + boxH - 72;
        float btnSize = 26f;
        float plusX = bx + boxW - 40;
        float minusX = plusX - btnSize - 10;

        for (int i = 0; i < 4; i++) {
            // 行文本：名称 + 当前值 + 临时加点预览（0.2.8 起纯鼠标操作，无 [N] 键盘前缀）
            StringBuilder sb = new StringBuilder();
            sb.append(names[i]).append("  ").append(cur[i]);
            int assigned = allocator.pendingAt(i);
            if (assigned > 0) {
                sb.append("  +").append(assigned * AttributeAllocator.perPoint(i));
            }
            ui.text(sb.toString(), bx + 28, y, Color.WHITE, 1.0f);

            // [-] 按钮
            boolean canMinus = assigned > 0;
            float btnY = y - btnSize;   // 按钮与文字顶部对齐略靠下
            minusRects[i] = new Rectangle(minusX, btnY, btnSize, btnSize);
            Color minusColor = canMinus ? WARN : DIM;
            ui.rect(minusX, btnY, btnSize, btnSize, PANEL_BG);
            ui.frame(minusX, btnY, btnSize, btnSize, minusColor);
            ui.textCentered("-", minusX + btnSize / 2f, btnY + btnSize - 4, minusColor, 1.0f);

            // [+] 按钮
            boolean canPlus = remaining > 0;
            plusRects[i] = new Rectangle(plusX, btnY, btnSize, btnSize);
            Color plusColor = canPlus ? GREEN : DIM;
            ui.rect(plusX, btnY, btnSize, btnSize, PANEL_BG);
            ui.frame(plusX, btnY, btnSize, btnSize, plusColor);
            ui.textCentered("+", plusX + btnSize / 2f, btnY + btnSize - 4, plusColor, 1.0f);

            y -= 40;
        }

        // 底部提示：剩余点数归 0 前「继续」置灰且不可用
        Color hintColor = allocator.canContinue() ? GREEN : DIM;
        String hint = allocator.canContinue()
                ? "点击 [-] [+] 分配，按 [空格] 确认"
                : "点击 [-] [+] 分配（还剩 " + remaining + " 点，暂不可确认）";
        ui.textCentered(hint, w / 2f, by + 26, hintColor, 1.0f);
    }

    /** 阶段 4：全部完成 */
    private void drawDone(float w, float h) {
        float cy = h / 2f;
        ui.textCentered("结算完成", w / 2f, cy + 40, GREEN, 1.8f);
        ui.textCentered("等级 " + run.level + "   金币 " + run.player.gold, w / 2f, cy, Color.WHITE, 1.0f);
        ui.textCentered("按 [空格] 返回地图", w / 2f, cy - 40, GREEN, 1.0f);
    }

    // ==================== 输入 ====================

    private void handleInput() {
        switch (phase) {
            case RESULT -> {
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                        || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                    advanceFromResult();
                }
            }
            case CHOICE -> {
                if (choices == null || choices.isEmpty()) {
                    afterChoice();
                    return;
                }
                int[] keys = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3};
                for (int i = 0; i < keys.length && i < choices.size(); i++) {
                    if (Gdx.input.isKeyJustPressed(keys[i])) {
                        RunState.Choice c = choices.get(i);
                        if (c.kind == RunState.Choice.Kind.SKILL) {
                            phase = Phase.SKILL_CHOICE;   // 进入技能自选
                        } else if (c.kind == RunState.Choice.Kind.COMBAT_SKILL_CHOICE) {
                            combatSkillChoices = run.rollUnownedCombatSkills(2);
                            phase = Phase.COMBAT_SKILL_CHOICE;   // 进入技巧自选
                        } else {
                            run.applyChoice(c);
                            afterChoice();
                        }
                        return;
                    }
                }
            }
            case SKILL_CHOICE -> {
                int[] keys = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3, Input.Keys.NUM_4};
                for (int i = 0; i < keys.length; i++) {
                    if (Gdx.input.isKeyJustPressed(keys[i])) {
                        // 只有未满级的技能才能升；满级按了不响应
                        if (run.player.skillLevels[i] < Skill.MAX_LEVEL) {
                            run.player.upgradeSkill(i);
                        }
                        afterChoice();
                        return;
                    }
                }
            }
            case COMBAT_SKILL_CHOICE -> {
                if (combatSkillChoices == null || combatSkillChoices.isEmpty()) {
                    afterChoice();
                    return;
                }
                int[] keys = {Input.Keys.NUM_1, Input.Keys.NUM_2};
                for (int i = 0; i < keys.length && i < combatSkillChoices.size(); i++) {
                    if (Gdx.input.isKeyJustPressed(keys[i])) {
                        CombatSkill s = combatSkillChoices.get(i);
                        if (!run.player.hasCombatSkill(s.id)) {
                            run.player.combatSkills.add(s);
                        }
                        afterChoice();
                        return;
                    }
                }
            }
            case ELITE_REWARD -> {
                if (eliteReward == null) {
                    afterEliteReward();
                    return;
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                    if (eliteReward.talent != null) {
                        run.player.grantTalent(eliteReward.talent);
                    }
                    afterEliteReward();
                    return;
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                    if (eliteReward.skill != null) {
                        enterSkillReplace(eliteReward.skill);
                    } else {
                        afterEliteReward();
                    }
                    return;
                }
            }
            case SKILL_REPLACE -> {
                if (pendingReplaceSkill == null) {
                    afterEliteReward();
                    return;
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                    // 替换槽 3（下标 2）
                    run.player.replaceSkill(2, pendingReplaceSkill);
                    pendingReplaceSkill = null;
                    afterEliteReward();
                    return;
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                    // 替换槽 4（下标 3）
                    run.player.replaceSkill(3, pendingReplaceSkill);
                    pendingReplaceSkill = null;
                    afterEliteReward();
                    return;
                }
            }
            case ATTRIBUTE -> {
                // 0.2.8 起属性分配只允许鼠标点击 +/- 按钮，去掉键盘加减（NUM/QWER）
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                    if (handleAttributeClick(Gdx.input.getX(), Gdx.input.getY())) {
                        return;
                    }
                }
                // 空格/回车：只有剩余点数归 0 才生效（置灰逻辑）
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                        || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                    if (allocator.canContinue()) {
                        commitAttributes();
                    }
                }
            }
            case DONE -> {
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                        || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                    backToMap();
                }
            }
        }
    }

    /** 结算页点继续：0.2.1 起战斗胜利后**必定**弹一次三选一（不再看经验够不够） */
    private void advanceFromResult() {
        choices = run.rollChoices();
        phase = Phase.CHOICE;
    }

    /** 三选一（含技能/技巧自选）选完：结算经验升级，精英战额外弹天赋/技能二选一，否则进属性分配 */
    private void afterChoice() {
        // 经验结算升级：升级只发「+2 待分配属性点」，不再重复触发三选一（0.2.1 需求第 2 条）
        run.settleLevelUps();
        maybeEliteRewardOrAttribute();
    }

    /** 精英战的天赋/技能二选一选完（或没奖励可选）后：进属性分配 / 完成 */
    private void afterEliteReward() {
        enterAttributeOrDone();
    }

    /** 精英战：弹天赋/技能二选一；普通战：直接进属性分配 */
    private void maybeEliteRewardOrAttribute() {
        if (run.lastBattleWasElite) {
            eliteReward = run.rollEliteReward();
            if (eliteReward != null) {
                phase = Phase.ELITE_REWARD;
                return;
            }
            // 天赋池和技能池都空了：转为给 1 点自由属性
            run.pendingAttributePoints += 1;
        }
        enterAttributeOrDone();
    }

    /** 玩家选了精英奖励的「技能」：进入替换界面，选槽 3 / 槽 4 之一替换 */
    private void enterSkillReplace(Skill newSkill) {
        // 记录待替换的新技能
        pendingReplaceSkill = newSkill;
        phase = Phase.SKILL_REPLACE;
    }

    /** 进属性分配阶段（有待分配点），否则完成。进阶段时新建分配器，从 run 抄总点数 */
    private void enterAttributeOrDone() {
        if (run.pendingAttributePoints > 0) {
            allocator = new AttributeAllocator(run.player, run.pendingAttributePoints);
            phase = Phase.ATTRIBUTE;
        } else {
            phase = Phase.DONE;
        }
    }

    /** 处理属性分配界面的鼠标点击。返回 true 表示点到了某个按钮（本次已消费） */
    private boolean handleAttributeClick(int screenX, int screenY) {
        float flippedY = Gdx.graphics.getHeight() - screenY;
        for (int i = 0; i < 4; i++) {
            if (minusRects[i] != null && minusRects[i].contains(screenX, flippedY)) {
                allocator.sub(i);
                return true;
            }
            if (plusRects[i] != null && plusRects[i].contains(screenX, flippedY)) {
                allocator.add(i);
                return true;
            }
        }
        return false;
    }

    /** 确认属性点：一次性写入玩家真实面板，并扣掉全局待分配点 */
    private void commitAttributes() {
        if (allocator != null && allocator.commit()) {
            run.pendingAttributePoints = 0;
            allocator = null;
            phase = Phase.DONE;
        }
    }

    private void backToMap() {
        game.setScreen(new MapScreen(game, run));
        dispose();
    }

    // ==================== Screen 生命周期 ====================

    @Override
    public void show() {
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        ui.dispose();
    }
}
