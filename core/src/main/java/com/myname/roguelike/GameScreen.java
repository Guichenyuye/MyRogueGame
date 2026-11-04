package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/**
 * 战斗界面。
 * <p>
 * 分工：这个类只负责「把 {@link BattleSystem} 的状态画出来 + 把按键转成技能调用」，
 * 所有数值都由战斗系统算，界面不掺和逻辑。
 * <p>
 * 玩家对象由 {@link RunState} 持有并跨节点保留，这里不 new —— 否则打完一场血就丢了。
 * <p>
 * 按键：1 随心一箭 / 2 防御 / 3 精准射击 / 4 横扫 / ESC 放弃本局。
 * 结算页：胜利后空格回地图，失败后 ESC 回主菜单。
 */
public class GameScreen implements Screen {

    // ---- 布局常量 ----
    private static final float MARGIN = 18f;
    private static final float HOTBAR_H = 96f;
    private static final float PANEL_H = 214f;
    private static final float LINE_GAP = 4f;

    /** 天赋 / 技巧每行的高度（字不缩放，固定 20px 字体一行的高度即可） */
    private static final float ROW_H = 30f;

    /** 天赋触发后的闪烁总时长（秒）。结束后恢复默认金色 */
    private static final float FLASH_SECONDS = 0.5f;

    /** 闪烁时红/白交替的半周期（秒）。0.1 秒切一次，0.5 秒内大概闪 2~3 个来回 */
    private static final float FLASH_STEP = 0.1f;

    // ---- 配色 ----
    private static final Color PANEL_BG = new Color(0.09f, 0.10f, 0.12f, 0.94f);
    private static final Color BAR_BG = new Color(0.22f, 0.22f, 0.26f, 1f);
    private static final Color HP_COLOR = new Color(0.85f, 0.24f, 0.24f, 1f);
    private static final Color MP_COLOR = new Color(0.30f, 0.55f, 0.95f, 1f);
    private static final Color SHIELD_COLOR = new Color(0.95f, 0.82f, 0.25f, 1f);
    private static final Color DIM = new Color(0.45f, 0.45f, 0.50f, 1f);
    private static final Color GREEN = new Color(0.45f, 0.85f, 0.45f, 1f);
    private static final Color ENEMY_NAME = new Color(1f, 0.55f, 0.7f, 1f);
    private static final Color INTENT = new Color(1f, 0.75f, 0.35f, 1f);

    // 天赋（金）与战斗技巧（浅蓝）：两套颜色是为了让「质变 / 量变」一眼能分开
    private static final Color GOLD_TITLE = new Color(0.95f, 0.80f, 0.30f, 1f);
    private static final Color GOLD_BRIGHT = new Color(1f, 0.94f, 0.50f, 1f);
    private static final Color TALENT_TEXT = new Color(0.88f, 0.84f, 0.68f, 1f);
    private static final Color SKILL_TITLE = new Color(0.45f, 0.78f, 0.98f, 1f);
    private static final Color SKILL_TEXT = new Color(0.72f, 0.86f, 0.95f, 1f);
    // 诅咒（紫红，本局持续）与负面效果（橙，战斗临时）
    private static final Color CURSE_TITLE = new Color(0.75f, 0.45f, 0.80f, 1f);
    private static final Color CURSE_TEXT = new Color(0.85f, 0.65f, 0.90f, 1f);
    private static final Color STATUS_TITLE = new Color(0.95f, 0.62f, 0.30f, 1f);
    private static final Color STATUS_TEXT = new Color(0.95f, 0.75f, 0.45f, 1f);
    private static final Color GHOST = new Color(0.38f, 0.38f, 0.42f, 1f);
    private static final Color ENEMY_ACTION = new Color(0.95f, 0.45f, 0.45f, 1f);
    private static final Color WARN = new Color(0.98f, 0.62f, 0.30f, 1f);

    private final Main game;
    private final RunState run;
    private final Ui ui;
    private final BitmapFont font;

    private final BattleSystem battle;

    /** 日志换行用，独立于 Ui，因为要反复量高度 */
    private final GlyphLayout logLayout = new GlyphLayout();

    /** 按键反馈：刚按下的槽位 + 高亮剩余时间 */
    private int pressedSlot = -1;
    private float pressTimer = 0f;

    /**
     * 天赋闪烁：每个天赋上次看到的触发次数 + 剩余高亮时间，按 {@code Talent.Id} 的下标存。
     * 用数组而不是一个 {@code int lastSeen}：以后加第二个天赋/技巧不用改这段逻辑。
     */
    private final int[] talentSeen = new int[Talent.Id.values().length];
    private final float[] talentFlash = new float[Talent.Id.values().length];

    /** 战斗结果是否已经写回 RunState，防止每帧重复结算 */
    private boolean settled = false;

    /** 是否弹出了「放弃本局」确认框（0.2.5：ESC 不再直接回主菜单，先确认） */
    private boolean confirmAbort = false;

    /**
     * 开打时所在的层。结算页必须用这个而不是 {@code run.floor} ——
     * 胜利那一刻 {@link RunState#onBattleWin()} 已经把 floor +1 了，
     * 直接读会显示成「刚打完第 1 层，结算页却写着层 2」。
     */
    private final int battleFloor;

    public GameScreen(Main game, RunState run) {
        this.game = game;
        this.run = run;
        this.ui = new Ui(game.batch, game.font);
        this.font = game.font;
        this.battleFloor = run.floor;

        this.battle = new BattleSystem(run.player, run.rng);
        for (Enemy enemy : run.createEnemies()) {
            battle.addEnemy(enemy);
        }
        battle.start();
    }

    // ==================== 渲染 ====================

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        syncRunState();
        handleInput();
        updateTalentFlash(delta);

        if (pressTimer > 0) {
            pressTimer -= delta;
        }

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // 左右面板压到 286：日志区宽一点，一行伤害记录才放得下不换行
        float leftW = Math.min(286f, w * 0.22f);
        float rightW = Math.min(286f, w * 0.22f);
        float panelTop = h - MARGIN;
        float hotbarY = MARGIN;
        float logX = MARGIN + leftW + 14f;
        float logW = w - logX - rightW - MARGIN - 14f;
        float logTop = panelTop;
        float logBottom = hotbarY + HOTBAR_H + 14f;

        // 左面板高度按内容算（天赋/技巧越多越长），右面板固定：
        // 写死一个高度的话，加第三个天赋要么留一大片空白，要么顶出面板。
        float leftH = playerPanelHeight();

        ui.batch().begin();

        // 面板底板
        ui.rect(MARGIN, panelTop - leftH, leftW, leftH, PANEL_BG);
        ui.rect(w - MARGIN - rightW, panelTop - PANEL_H, rightW, PANEL_H, PANEL_BG);
        ui.rect(logX, logBottom, logW, logTop - logBottom, PANEL_BG);

        drawPlayerPanel(MARGIN, panelTop, leftW, leftH);
        drawEnemyPanel(w - MARGIN - rightW, panelTop, rightW);
        drawLog(logX, logTop, logW, logBottom);
        drawHotbar(hotbarY, w);

        if (battle.getState() != BattleSystem.State.PLAYER_TURN) {
            drawResultOverlay(w, h);
        } else if (confirmAbort) {
            drawAbortConfirm(w, h);
        }

        ui.batch().end();
    }

    /** 把战斗结果同步进 RunState（只做一次） */
    private void syncRunState() {
        if (settled) {
            return;
        }
        if (battle.getState() == BattleSystem.State.VICTORY) {
            run.onBattleWin();
            settled = true;
        } else if (battle.getState() == BattleSystem.State.DEFEAT) {
            run.onBattleLose();
            settled = true;
        }
    }

    /** 左上：玩家面板。高度按内容算，见 {@link #playerPanelHeight()} */
    private void drawPlayerPanel(float x, float top, float width, float panelH) {
        Player p = battle.getPlayer();
        float left = x + 14;

        // 头像（0.2.7）：8x8 像素图 Nearest 放大到 64x64，替换原来的名字文本
        float avatarSize = 64f;
        com.badlogic.gdx.graphics.Texture avatar = game.avatar(p.character);
        if (avatar != null) {
            ui.batch().draw(avatar, left, top - 6 - avatarSize, avatarSize, avatarSize);
        }

        // 名字放头像右侧，垂直居中偏上
        ui.text(p.name, left + avatarSize + 12, top - 28, Color.CYAN, 1.4f);
        // 属性（全称）放头像右侧名字下面；空间不够就收窄到只显示数值
        ui.text("防 " + p.defense + "  力 " + p.strength + "  准 " + p.precision,
                left + avatarSize + 12, top - 54, DIM, 1.0f);

        // 盾值并进 HP 行：原来单独占一行，会把下面的「天赋」标题一路往下顶
        String hpLine = "HP  " + p.hp + " / " + p.maxHp;
        ui.text(hpLine, left, top - 80, HP_COLOR, 1.0f);
        if (p.shield > 0) {
            ui.text("盾 " + p.shield, left + ui.measure(hpLine, 1.0f) + 18, top - 80, SHIELD_COLOR, 1.0f);
        }
        ui.bar(left, top - 118, width - 28, 12, (float) p.hp / p.maxHp, HP_COLOR, BAR_BG);
        if (p.shield > 0) {
            // 血条外圈描金边 = 当前有盾
            ui.frame(x + 12, top - 120, width - 24, 16, SHIELD_COLOR);
        }

        String mpLine = "MP  " + p.mp + " / " + p.maxMp;
        ui.text(mpLine, left, top - 126, MP_COLOR, 1.0f);
        ui.text("第 " + battle.getRound() + " 回合", left + ui.measure(mpLine, 1.0f) + 18, top - 126, DIM, 1.0f);
        ui.bar(left, top - 160, width - 28, 12, (float) p.mp / p.maxMp, MP_COLOR, BAR_BG);

        // ---- 天赋（质变，金色）----
        float y = top - 194;
        ui.text("—— 天赋 ——", left, y, GOLD_TITLE, 1.0f);
        y -= 26;
        if (p.talents.isEmpty()) {
            ui.text("（无）", left, y, GHOST, 1.0f);
            y -= ROW_H;
        }
        for (Talent t : p.talents) {
            float remaining = talentFlash[t.id.ordinal()];
            Color color = TALENT_TEXT;   // 默认金色
            if (remaining > 0) {
                // 触发中：字号位置绝对不变，只做红/白交替闪烁（0.1s 切一次）。
                // 用剩余时间的奇偶段判断当前该红还是该白，避免额外计时器。
                int step = (int) (remaining / FLASH_STEP);
                color = (step % 2 == 0)
                        ? new Color(p.talentFlashR, p.talentFlashG, p.talentFlashB, 1f)
                        : Color.WHITE;
            }
            ui.text(t.name, left, y, color, 1.0f);
            y -= ROW_H;
        }

        // ---- 战斗技巧（量变，浅蓝）----
        ui.text("—— 战斗技巧 ——", left, y, SKILL_TITLE, 1.0f);
        y -= 26;
        if (p.combatSkills.isEmpty()) {
            ui.text("（尚未获得）", left, y, GHOST, 1.0f);
            y -= ROW_H;
        } else {
            for (CombatSkill skill : p.combatSkills) {
                ui.text(skill.name, left, y, SKILL_TEXT, 1.0f);
                y -= ROW_H;
            }
        }

        // ---- 诅咒（本局持续，紫红，完整名字）----
        if (!p.curses.isEmpty()) {
            ui.text("—— 诅咒 ——", left, y, CURSE_TITLE, 1.0f);
            y -= 26;
            for (Curse c : p.curses) {
                ui.text(c.name, left, y, CURSE_TEXT, 1.0f);
                y -= ROW_H;
            }
        }

        // ---- 负面效果（战斗临时，橙，单字 + 层数）----
        if (!p.statusEffects.isEmpty()) {
            ui.text("—— 状态 ——", left, y, STATUS_TITLE, 1.0f);
            y -= 26;
            for (StatusEffect.Instance inst : p.statusEffects) {
                String label = inst.effect.shortName;
                if (inst.stacks > 1) {
                    label += "x" + inst.stacks;
                }
                ui.text(label, left, y, STATUS_TEXT, 1.0f);
                y -= ROW_H;
            }
        }

        // 底行提示。这一版还没做悬停浮窗，写成「悬停查看详情」会让玩家真去试然后发现没反应 ——
        // 和当初「按 1 没反应」是同一类问题，所以先写清楚详情现在在哪里看。
        ui.text("（触发详情见日志）", left, top - panelH + 32, GHOST, 1.0f);
    }

    /**
     * 天赋触发后闪一下：数字变大说明刚刚又触发了一次。
     * 放在 {@code handleInput()} 之后调用，这样同一次按键里触发的天赋当帧就能亮起来。
     */
    private void updateTalentFlash(float delta) {
        for (Talent.Id id : Talent.Id.values()) {
            int index = id.ordinal();
            int now = battle.talentTriggerCount(id);
            if (now > talentSeen[index]) {
                talentSeen[index] = now;
                talentFlash[index] = FLASH_SECONDS;
            }
            if (talentFlash[index] > 0) {
                talentFlash[index] = Math.max(0f, talentFlash[index] - delta);
            }
        }
    }

    /** 左面板需要的高度：基础信息 + 天赋 + 技巧 + 诅咒 + 负面效果 + 底部提示 */
    private float playerPanelHeight() {
        Player p = battle.getPlayer();
        float h = 194f;                                                 // 头像 / 名字 / 属性 / HP / MP
        h += 26f + Math.max(1, p.talents.size()) * ROW_H;               // 天赋标题 + 每个天赋一行
        h += 26f + Math.max(1, p.combatSkills.size()) * ROW_H;          // 技巧标题 + 每个技巧一行
        if (!p.curses.isEmpty()) {
            h += 26f + p.curses.size() * ROW_H;                         // 诅咒标题 + 每个诅咒一行
        }
        if (!p.statusEffects.isEmpty()) {
            h += 26f + p.statusEffects.size() * ROW_H;                  // 状态标题 + 每个状态一行
        }
        return h + 32f + 14f;                                           // 底部提示行
    }

    /**
     * 右上：敌人。
     * <p>
     * 单个敌人用完整布局（含防御、意图）；两只以上时压缩成紧凑布局，
     * 否则第 7 层的双史莱姆会把意图挤出面板。
     */
    private void drawEnemyPanel(float x, float top, float width) {
        List<Enemy> enemies = battle.getEnemies();
        if (enemies.isEmpty()) {
            return;
        }

        if (enemies.size() == 1) {
            drawEnemyBlock(enemies.get(0), x, top, width, true);
            return;
        }

        float slotH = PANEL_H / enemies.size();
        for (int i = 0; i < enemies.size(); i++) {
            drawEnemyBlock(enemies.get(i), x, top - i * slotH, width, false);
        }
    }

    private void drawEnemyBlock(Enemy enemy, float x, float top, float width, boolean roomy) {
        Color nameColor = enemy.isAlive() ? ENEMY_NAME : DIM;

        ui.text(enemy.name, x + 14, top - 6, nameColor, roomy ? 1.4f : 1.2f);

        if (roomy) {
            ui.text("防御 " + enemy.defense, x + 14, top - 42, DIM, 1.0f);
            ui.text("HP  " + enemy.hp + " / " + enemy.maxHp, x + 14, top - 68, HP_COLOR, 1.0f);
            ui.bar(x + 14, top - 106, width - 28, 14, (float) enemy.hp / enemy.maxHp, HP_COLOR, BAR_BG);

            ui.text("下回合意图", x + 14, top - 118, DIM, 1.0f);
            ui.text(enemy.isAlive() ? enemy.getIntent() : "已倒下", x + 14, top - 146,
                    enemy.isAlive() ? INTENT : DIM, 1.0f);
        } else {
            ui.text("HP  " + enemy.hp + " / " + enemy.maxHp + "   防御 " + enemy.defense,
                    x + 14, top - 34, HP_COLOR, 1.0f);
            ui.bar(x + 14, top - 56, width - 28, 12, (float) enemy.hp / enemy.maxHp, HP_COLOR, BAR_BG);
            ui.text(enemy.isAlive() ? enemy.getIntent() : "已倒下", x + 14, top - 76,
                    enemy.isAlive() ? INTENT : DIM, 1.0f);
        }
    }

    /**
     * 中间：战斗日志。从最新一条往前算，放得下几条就显示几条（超长的行自动换行）。
     * <p>
     * {@code "# "} 开头的是回合状态摘要，左右面板已经画了，这里跳过不重复占地方。
     */
    private void drawLog(float x, float top, float width, float bottom) {
        List<String> log = battle.getLog();
        float textWidth = width - 24f;
        float budget = top - bottom - 12f;

        font.getData().setScale(1f);

        // 从最新一条往前累加高度，决定这一屏从第几条开始画
        int start = log.size();
        float used = 0;
        for (int i = log.size() - 1; i >= 0; i--) {
            String line = log.get(i);
            if (line.startsWith("# ")) {
                continue;
            }
            logLayout.setText(font, line, Color.WHITE, textWidth, Align.left, true);
            float need = logLayout.height + LINE_GAP;
            if (used + need > budget) {
                break;
            }
            used += need;
            start = i;
        }

        float y = top - 6;
        for (int i = start; i < log.size(); i++) {
            String line = log.get(i);
            if (line.startsWith("# ")) {
                continue;
            }
            Color color = logColor(line);
            logLayout.setText(font, line, color, textWidth, Align.left, true);
            font.draw(ui.batch(), logLayout, x + 12, y);
            y -= logLayout.height + LINE_GAP;
        }
    }

    /**
     * 按前缀上色（0.2 需求第 4 条）。前缀约定：
     * <pre>
     * &gt;      玩家行动（青）
     * &lt;      敌人行动（红）
     * [天赋]   天赋触发（亮金）
     * [技巧]   技巧触发（浅蓝）
     * *      结算 / 胜利（绿）
     * **     强调事件：暴击（金）
     * !      非法操作（橙）—— 和敌人的红色分开，别把「你按错了」和「敌人打了你」混为一谈
     * !!     死亡（正红）
     * #      回合状态摘要，界面里已过滤（详见 drawLog）
     * </pre>
     */
    private Color logColor(String line) {
        // 标记都用 ASCII：这套字库里 ★ ▶ ✦ 之类的符号字形不可靠，会画成奇怪的形状
        if (line.startsWith("> ")) return Color.CYAN;
        if (line.startsWith("< ")) return ENEMY_ACTION;
        if (line.startsWith("[天赋]")) return GOLD_BRIGHT;
        if (line.startsWith("[技巧]")) return SKILL_TITLE;
        if (line.startsWith("!!")) return new Color(0.95f, 0.35f, 0.35f, 1f);
        if (line.startsWith("! ")) return WARN;
        if (line.startsWith("**")) return SHIELD_COLOR;
        if (line.startsWith("* ")) return GREEN;
        if (line.contains("护盾")) return SHIELD_COLOR;
        if (line.contains("蓄力")) return new Color(1f, 0.48f, 0.22f, 1f);
        if (line.contains("腐液")) return new Color(0.68f, 0.48f, 1f, 1f);
        if (line.contains("=====")) return new Color(0.75f, 0.75f, 0.8f, 1f);
        return Color.WHITE;
    }

    /** 底部：四个技能槽（1~4），不可用时整块变暗并写原因 */
    private void drawHotbar(float y, float w) {
        float gap = 10f;
        float boxW = (w - MARGIN * 2 - gap * 3) / 4f;
        Player p = battle.getPlayer();
        boolean myTurn = battle.getState() == BattleSystem.State.PLAYER_TURN;

        for (int i = 0; i < Player.SLOT_COUNT; i++) {
            Skill skill = p.slots[i];
            if (skill == null) {
                continue;
            }
            float x = MARGIN + i * (boxW + gap);
            boolean usable = myTurn && p.canUse(i);
            boolean flashing = pressedSlot == i && pressTimer > 0;

            ui.rect(x, y, boxW, HOTBAR_H, flashing ? new Color(0.20f, 0.24f, 0.16f, 0.98f) : PANEL_BG);
            ui.frame(x, y, boxW, HOTBAR_H,
                    flashing ? (usable ? Color.YELLOW : HP_COLOR) : (usable ? new Color(0.38f, 0.42f, 0.50f, 1f) : DIM));

            ui.text("[" + (i + 1) + "] " + skill.name, x + 12, y + HOTBAR_H - 6,
                    usable ? Color.WHITE : DIM, 1.0f);

            String cost = skill.mpCost == 0 ? "不耗精力" : (skill.mpCost + " 精力");
            if (skill.cd > 0) {
                cost += "  CD" + skill.cd;
            }
            ui.text(cost, x + 12, y + HOTBAR_H - 34, DIM, 1.0f);

            String status;
            Color statusColor;
            if (!myTurn) {
                status = "等待中";
                statusColor = DIM;
            } else if (p.cdDisplay[i] > 0) {
                status = "冷却 " + p.cdDisplay[i] + " 回合";
                statusColor = INTENT;
            } else if (p.mp < skill.mpCost) {
                status = "精力不足";
                statusColor = new Color(0.95f, 0.45f, 0.45f, 1f);
            } else {
                status = "可用";
                statusColor = GREEN;
            }
            ui.text(status, x + 12, y + 26, statusColor, 1.0f);
        }

        // 底栏右侧提示：ESC 放弃本局（0.2.5）
        String abortHint = "[ESC] 放弃本局";
        ui.text(abortHint, w - MARGIN - ui.measure(abortHint, 1.0f), y + HOTBAR_H + 6, DIM, 1.0f);
    }

    /**
     * 结算页：整屏不透明覆盖。
     * 半透明会让下面的日志透出来，和结算字糊成一片（之前踩过）。
     */
    private void drawResultOverlay(float w, float h) {
        boolean win = battle.getState() == BattleSystem.State.VICTORY;

        ui.rect(0, 0, w, h, new Color(0.06f, 0.06f, 0.07f, 1f));

        float cy = h / 2f;
        float boxW = 460f;
        float boxH = 240f;
        Color accent = win ? SHIELD_COLOR : HP_COLOR;

        ui.rect(w / 2f - boxW / 2f, cy - boxH / 2f, boxW, boxH, PANEL_BG);
        ui.frame(w / 2f - boxW / 2f, cy - boxH / 2f, boxW, boxH, accent);

        String title;
        String subtitle;
        String hint;
        if (win) {
            title = run.cleared ? "CLEARED" : "VICTORY";
            subtitle = run.cleared ? "腐化史莱姆王倒下了" : "敌人被击倒";
            hint = run.cleared ? "按 [ESC] 返回主菜单" : "按 [空格] 查看结算";
        } else {
            title = "DEFEAT";
            subtitle = run.player.name + "倒下了";
            hint = "按 [ESC] 返回主菜单";
        }

        ui.textCentered(title, w / 2f, cy + boxH / 2f - 24, accent, 2.0f);
        ui.textCentered(subtitle, w / 2f, cy + 20, Color.WHITE, 1.0f);
        ui.textCentered("HP " + run.player.hp + " / " + run.player.maxHp
                        + "    第 " + battle.getRound() + " 回合    第 " + battleFloor + " 层",
                w / 2f, cy - 8, DIM, 1.0f);

        // 金币是本轮新加的唯一成长资源，胜利页上必须看到它在涨，否则玩家不知道有这套东西
        if (win && run.lastGoldGain > 0) {
            ui.textCentered("金币 +" + run.lastGoldGain + "（共 " + run.player.gold + "）",
                    w / 2f, cy - 40, GOLD_BRIGHT, 1.0f);
        }
        ui.textCentered(hint, w / 2f, cy - 86, win && !run.cleared ? GREEN : DIM, 1.0f);
    }

    /** 放弃本局确认框（0.2.5） */
    private void drawAbortConfirm(float w, float h) {
        ui.rect(0, 0, w, h, new Color(0.05f, 0.05f, 0.06f, 1f));

        float boxW = 520f;
        float boxH = 180f;
        float bx = w / 2f - boxW / 2f;
        float by = h / 2f - boxH / 2f;

        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, WARN);

        ui.textCentered("确定要放弃本次游戏并重新开始吗？", w / 2f, by + boxH - 34, Color.WHITE, 1.3f);
        ui.textCentered("[Y] 确定    [N] 取消", w / 2f, by + boxH - 92, WARN, 1.1f);
    }

    // ==================== 输入 ====================

    private void handleInput() {
        // 战斗结束
        if (battle.getState() != BattleSystem.State.PLAYER_TURN) {
            boolean win = battle.getState() == BattleSystem.State.VICTORY;

            // 胜利（非首领）：进结算界面（金币/经验/三选一），而不是直接回地图
            if (win && !run.cleared && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                game.setScreen(new ResultScreen(game, run));
                dispose();
                return;
            }
            // 首领通关：没有下一层了，直接回主菜单
            if (win && run.cleared && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                gotoMenu();
                return;
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                gotoMenu();
            }
            return;
        }

        // 放弃本局确认框：Y 确定重开，N 取消
        if (confirmAbort) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
                gotoMenu();
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.N)
                    || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                confirmAbort = false;
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            confirmAbort = true;
            return;
        }

        int[] keyCodes = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3, Input.Keys.NUM_4};
        for (int i = 0; i < keyCodes.length; i++) {
            if (Gdx.input.isKeyJustPressed(keyCodes[i])) {
                boolean ok = battle.useSkill(i);
                // 无论成功失败都给一次视觉反馈：成功黄框、失败红框（原因已经写进日志）
                pressedSlot = i;
                pressTimer = ok ? 0.22f : 0.35f;
                return;
            }
        }
    }

    private void gotoMenu() {
        game.setScreen(new StartScreen(game));
        dispose();   // 只释放本屏的 Ui，字体和 batch 是共享的
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
