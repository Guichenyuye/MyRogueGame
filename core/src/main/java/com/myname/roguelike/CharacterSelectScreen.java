package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * 角色选择界面（0.2.4 引入，0.2.7 视觉化重构）。
 * <p>
 * 仿英雄联盟选人：头像缩小并排放在左侧/中下方（以后角色多了放得下），
 * 点击某个头像后，右侧弹出该角色的详情面板（基础数据 + 天赋 + 技能），
 * 选中的反馈是「头像放大」而不是画框；底部有一个实心「确认进入」按钮。
 * <p>
 * 角色名字 / 介绍 / 头像 / 背景 / 面板数据**都不硬编码**，全部从
 * {@link CharacterData#ALL} 循环读取，加新角色只需补数据，本类不用改。
 */
public class CharacterSelectScreen implements Screen {

    /** 头像渲染框边长：8 的 8 倍 = 64，进一步缩小以贴近英雄联盟选人（一排放下更多） */
    private static final float BOX_SIZE = 64f;

    /** 两个头像之间的间距 */
    private static final float AVATAR_GAP = 28f;

    /** 一行最多放几个头像（超过则换到下一行） */
    private static final int PER_ROW = 6;

    /** 选中头像的放大倍数 */
    private static final float SELECT_SCALE = 1.2f;

    private static final Color TITLE_COLOR = new Color(0.95f, 0.82f, 0.30f, 1f);
    private static final Color NAME_COLOR = Color.CYAN;
    private static final Color HINT_COLOR = Color.LIGHT_GRAY;
    private static final Color PANEL_BG = new Color(0.08f, 0.09f, 0.11f, 0.92f);
    private static final Color PANEL_FRAME = new Color(0.30f, 0.34f, 0.42f, 1f);
    private static final Color GOLD_BRIGHT = new Color(1f, 0.90f, 0.45f, 1f);
    private static final Color BUTTON_BG = new Color(0.20f, 0.55f, 0.25f, 1f);
    private static final Color BUTTON_HOVER = new Color(0.26f, 0.68f, 0.31f, 1f);
    private static final Color DIM = new Color(0.72f, 0.72f, 0.76f, 1f);

    private final Main game;
    private final GlyphLayout layout = new GlyphLayout();

    /** 可选角色列表：正式角色 + 降临者（降临者走捏脸，但同样出现在选人排里可点击选中） */
    private final CharacterData[] chars;

    /** 当前选中的角色下标（-1 = 未选中） */
    private int selected = -1;

    public CharacterSelectScreen(Main game) {
        this.game = game;
        // 降临者（DESCENDER）不在 CharacterData.ALL 里，这里拼进可选列表末尾
        CharacterData[] all = CharacterData.ALL;
        this.chars = new CharacterData[all.length + 1];
        System.arraycopy(all, 0, this.chars, 0, all.length);
        this.chars[all.length] = CharacterData.of(CharacterType.DESCENDER);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        SpriteBatch batch = game.batch;
        BitmapFont font = game.font;

        CharacterData[] chars = this.chars;

        // 鼠标坐标（LibGDX Y 轴反转：左上角是 (0,0)，要翻成左下角原点）
        int mouseX = Gdx.input.getX();
        int mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        int hoverIndex = hoverIndex(chars, w, h, mouseX, mouseY);

        batch.begin();

        // 背景：选中角色（或正悬停它）时铺满并降到 60% 透明度；否则纯黑
        Texture bg = backgroundFor(chars, hoverIndex);
        if (bg != null) {
            drawBackgroundCover(batch, bg, w, h, 0.6f);
        }

        // 标题
        drawCentered(batch, font, "选择你的角色", w / 2f, h * 0.90f, 2.0f, TITLE_COLOR);

        // 头像：从左往右一排，每行最多 PER_ROW 个，超过换行；整体居中
        int rows = (int) Math.ceil(chars.length / (double) PER_ROW);
        float avatarBaseY = h * 0.52f;   // 第一行头像框底边（行从上往下，这里取最上一行底边）

        for (int i = 0; i < chars.length; i++) {
            CharacterData c = chars[i];
            int row = i / PER_ROW;
            int col = i % PER_ROW;
            int inThisRow = Math.min(PER_ROW, chars.length - row * PER_ROW);

            float rowTotalW = inThisRow * BOX_SIZE + (inThisRow - 1) * AVATAR_GAP;
            float rowStartX = w / 2f - rowTotalW / 2f;
            float centerX = rowStartX + col * (BOX_SIZE + AVATAR_GAP) + BOX_SIZE / 2f;
            // 每行下移一行头像 + 间距（行距 = BOX_SIZE + AVATAR_GAP）
            float rowBaseY = avatarBaseY - row * (BOX_SIZE + AVATAR_GAP + 40f);

            boolean isSelected = (selected == i);
            boolean isHover = (hoverIndex == i);

            // 选中放大（反馈用放大，不画框）；悬停也稍微放大，但不抢选中的放大级别
            float scale = isSelected ? SELECT_SCALE : (isHover ? 1.08f : 1.0f);
            float drawSize = BOX_SIZE * scale;
            float drawX = centerX - drawSize / 2f;
            float drawY = rowBaseY - drawSize;

            Texture avatar = game.avatar(c.type);
            if (avatar != null) {
                batch.draw(avatar, drawX, drawY, drawSize, drawSize);
            }

            // 角色名画在头像正下方
            drawCentered(batch, font, c.name, centerX, rowBaseY - BOX_SIZE - 14f, 1.2f,
                    isSelected ? GOLD_BRIGHT : NAME_COLOR);
        }

        // 右侧详情面板：只有选中角色后显示
        if (selected >= 0 && selected < chars.length) {
            drawDetailPanel(batch, font, chars[selected], w, h);
        }

        // 底部确认按钮（实心）+ 提示
        drawConfirmButton(batch, font, w, h, selected >= 0);

        if (selected < 0) {
            drawCentered(batch, font, "点击头像查看角色   /   [ESC] 返回主菜单",
                    w / 2f, h * 0.08f, 1.0f, HINT_COLOR);
        }

        batch.end();

        handleInput(chars, w, h);
    }

    /** 右侧角色详情面板：基础数据 + 天赋 + 技能 */
    private void drawDetailPanel(SpriteBatch batch, BitmapFont font, CharacterData c,
                                 float w, float h) {
        float panelW = Math.min(360f, w * 0.32f);
        float panelH = h * 0.62f;
        float px = w - panelW - 28f;
        float py = h * 0.16f;

        // 底板 + 边框
        rect(batch, px, py, panelW, panelH, PANEL_BG);
        frame(batch, px, py, panelW, panelH, PANEL_FRAME, 2f);

        float left = px + 20f;
        float top = py + panelH - 24f;

        // 角色名 + 描述
        drawText(batch, font, c.name, left, top, 2.0f, GOLD_BRIGHT);
        drawText(batch, font, c.desc, left, top - 44, 1.0f, DIM);

        float y = top - 84;

        // 基础数据
        drawText(batch, font, "—— 基础 ——", left, y, 1.1f, NAME_COLOR);
        y -= 32;
        drawText(batch, font, "生命  " + c.maxHp + "        精力  " + c.maxMp, left, y, 1.0f, Color.WHITE);
        y -= 30;
        drawText(batch, font, "力量  " + c.strength + "    精准  " + c.precision + "    防御  " + c.defense,
                left, y, 1.0f, Color.WHITE);
        y -= 44;

        // 初始天赋
        drawText(batch, font, "—— 天赋 ——", left, y, 1.1f, NAME_COLOR);
        y -= 32;
        if (c.initialTalents.length == 0) {
            drawText(batch, font, "（无）", left, y, 1.0f, HINT_COLOR);
            y -= 28;
        }
        for (Talent t : c.initialTalents) {
            drawText(batch, font, t.name + "：", left, y, 1.0f, new Color(0.88f, 0.84f, 0.68f, 1f));
            // 描述换行显示在名字下方
            y -= 28;
            y -= wrapText(batch, font, t.desc, left + 8, y, panelW - 56f, 0.9f, DIM);
            y -= 8;
        }
        y -= 10;

        // 初始技能
        drawText(batch, font, "—— 技能 ——", left, y, 1.1f, NAME_COLOR);
        y -= 32;
        for (Skill s : c.initialSkills) {
            drawText(batch, font, s.name + "（耗" + s.mpCost + " CD" + s.cd + "）",
                    left, y, 1.0f, Color.WHITE);
            y -= 28;
        }
    }

    /** 底部实心确认按钮 */
    private void drawConfirmButton(SpriteBatch batch, BitmapFont font, float w, float h,
                                   boolean enabled) {
        float bw = 300f;
        float bh = 56f;
        float bx = w / 2f - bw / 2f;
        float by = h * 0.10f;

        // 实心底：未选中时灰色，选中时绿色；鼠标悬停再亮一档
        int mx = Gdx.input.getX();
        int my = Gdx.graphics.getHeight() - Gdx.input.getY();
        boolean hovering = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

        Color bg = enabled ? (hovering ? BUTTON_HOVER : BUTTON_BG) : new Color(0.25f, 0.25f, 0.28f, 1f);
        rect(batch, bx, by, bw, bh, bg);
        frame(batch, bx, by, bw, bh, enabled ? new Color(0.35f, 0.75f, 0.40f, 1f) : PANEL_FRAME, 2f);

        String label = enabled ? "确 认 进 入" : "请先选择角色";
        drawCentered(batch, font, label, w / 2f, by + bh / 2f + 10f, 1.5f,
                enabled ? Color.WHITE : HINT_COLOR);
    }

    /** 当前应显示谁的背景：优先选中角色，其次悬停角色，都没有返回 null（纯黑） */
    private Texture backgroundFor(CharacterData[] chars, int hoverIndex) {
        if (selected >= 0 && selected < chars.length) {
            Texture t = game.background(chars[selected].type);
            if (t != null) {
                return t;
            }
        }
        if (hoverIndex >= 0 && hoverIndex < chars.length) {
            return game.background(chars[hoverIndex].type);
        }
        return null;
    }

    /** 判断鼠标落在哪个头像框内（返回下标，-1 = 都没中）。支持多行布局 */
    private int hoverIndex(CharacterData[] chars, float w, float h, int mouseX, int mouseY) {
        float avatarBaseY = h * 0.52f;
        for (int i = 0; i < chars.length; i++) {
            int row = i / PER_ROW;
            int col = i % PER_ROW;
            int inThisRow = Math.min(PER_ROW, chars.length - row * PER_ROW);

            float rowTotalW = inThisRow * BOX_SIZE + (inThisRow - 1) * AVATAR_GAP;
            float rowStartX = w / 2f - rowTotalW / 2f;
            float left = rowStartX + col * (BOX_SIZE + AVATAR_GAP);
            float rowBaseY = avatarBaseY - row * (BOX_SIZE + AVATAR_GAP + 40f);
            float avatarTop = rowBaseY;
            float avatarBottom = rowBaseY - BOX_SIZE;

            if (mouseY < avatarBottom || mouseY > avatarTop) {
                continue;
            }
            if (mouseX >= left && mouseX <= left + BOX_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 背景 Cover 模式：等比缩放直到完全覆盖屏幕，居中绘制，超出的部分被屏幕边缘裁切。
     */
    private void drawBackgroundCover(SpriteBatch batch, Texture bg, float w, float h, float alpha) {
        float scale = Math.max(w / bg.getWidth(), h / bg.getHeight());
        float drawW = bg.getWidth() * scale;
        float drawH = bg.getHeight() * scale;
        float x = (w - drawW) / 2f;
        float y = (h - drawH) / 2f;

        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(bg, x, y, drawW, drawH);
        batch.setColor(Color.WHITE);
    }

    private void handleInput(CharacterData[] chars, float w, float h) {
        int mx = Gdx.input.getX();
        int my = Gdx.graphics.getHeight() - Gdx.input.getY();

        // 鼠标点击头像：选中
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            int hover = hoverIndex(chars, w, h, mx, my);
            if (hover >= 0) {
                selected = hover;
                return;
            }
        }

        // 鼠标点击确认按钮：进入
        if (selected >= 0 && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float bw = 300f;
            float bh = 56f;
            float bx = w / 2f - bw / 2f;
            float by = h * 0.10f;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                select(chars[selected]);
                return;
            }
        }

        // Enter 确认（需已选中）
        if (selected >= 0 && Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            select(chars[selected]);
            return;
        }

        // 数字键快捷选择：按下标 1~N 直接选中（不再直接进地图，统一走 select）
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            if (chars.length >= 1) {
                select(chars[0]);
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            if (chars.length >= 2) {
                select(chars[1]);
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            if (chars.length >= 3) {
                select(chars[2]);
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new StartScreen(game));
            dispose();
        }
    }

    /** 锁定角色：降临者走捏脸流程，其余角色初始化一局进地图 */
    private void select(CharacterData c) {
        if (c.type == CharacterType.DESCENDER) {
            game.setScreen(new CustomCharacterScreen(game));
            dispose();
            return;
        }
        RunState run = new RunState(c.type);
        Gdx.app.log("CharacterSelect", "选择角色：" + c.type + "，seed = " + run.seed);
        game.setScreen(new MapScreen(game, run));
        dispose();
    }

    // ==================== 绘制工具（复用 Main 共享白图）====================

    private void rect(SpriteBatch batch, float x, float y, float w, float h, Color color) {
        batch.setColor(color);
        batch.draw(game.whiteTexture(), x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    private void frame(SpriteBatch batch, float x, float y, float w, float h, Color color, float t) {
        batch.setColor(color);
        batch.draw(game.whiteTexture(), x, y, w, t);
        batch.draw(game.whiteTexture(), x, y + h - t, w, t);
        batch.draw(game.whiteTexture(), x, y, t, h);
        batch.draw(game.whiteTexture(), x + w - t, y, t, h);
        batch.setColor(Color.WHITE);
    }

    private void drawText(SpriteBatch batch, BitmapFont font, String text,
                          float x, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(batch, text, x, y);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont font, String text,
                              float centerX, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, centerX - layout.width / 2f, y);
    }

    /** 换行绘制一段文字，返回占用的高度 */
    private float wrapText(SpriteBatch batch, BitmapFont font, String text,
                           float x, float y, float width, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text, color, width, com.badlogic.gdx.utils.Align.left, true);
        font.draw(batch, layout, x, y);
        return layout.height;
    }

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
        // 字体、batch、纹理都是 Main 的共享资源，这里不释放
    }
}
