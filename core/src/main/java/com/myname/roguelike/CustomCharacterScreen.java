package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * 降临者捏脸界面（0.2.8）。
 * <p>
 * 玩家有 10 点自由属性，分配到力量 / 精准 / 防御（初始均为 0），生命固定 50、精力固定 10。
 * 0.2.8 追加调整：
 * <ul>
 *   <li>属性分配<b>只允许鼠标</b>点击每一行的 [-] [+] 按钮，去掉键盘加减/切换；</li>
 *   <li>可自定义<b>名字</b>（点名字栏弹系统输入框，支持中文）；</li>
 *   <li>可自定义<b>头像</b>（点头像栏弹文件选择器，选一张本地图片）。</li>
 * </ul>
 * [R] 一键重置、[Enter] 确认、[ESC] 返回角色选择仍保留键盘。
 */
public class CustomCharacterScreen implements Screen {

    private static final int TOTAL_POINTS = 10;
    private static final int MAX_HP = 50;
    private static final int MAX_MP = 10;

    private static final Color TITLE_COLOR = new Color(0.95f, 0.82f, 0.30f, 1f);
    private static final Color PANEL_BG = new Color(0.08f, 0.09f, 0.11f, 0.94f);
    private static final Color FRAME = new Color(0.30f, 0.34f, 0.42f, 1f);
    private static final Color GOLD_BRIGHT = new Color(1f, 0.90f, 0.45f, 1f);
    private static final Color DIM = new Color(0.72f, 0.72f, 0.76f, 1f);
    private static final Color GREEN = new Color(0.45f, 0.85f, 0.45f, 1f);
    private static final Color CYAN = Color.CYAN;

    private static final String[] ATTR_NAMES = { "力量", "精准", "防御" };

    private final Main game;
    private final Ui ui;
    private final BitmapFont font;

    /** 三个属性的分配值（力量/精准/防御） */
    private final int[] allocated = new int[3];

    /** 自定义名字（默认「降临者」） */
    private String name = "降临者";

    /** 自定义头像纹理（null = 用默认问号头像） */
    private Texture customAvatar = null;

    // 鼠标点击区域（每帧 render 时更新）
    private final Rectangle[] minusRects = new Rectangle[3];
    private final Rectangle[] plusRects = new Rectangle[3];
    private Rectangle nameRect = new Rectangle();
    private Rectangle avatarRect = new Rectangle();

    public CustomCharacterScreen(Main game) {
        this.game = game;
        this.ui = new Ui(game.batch, game.font);
        this.font = game.font;
    }

    private int remaining() {
        int used = 0;
        for (int v : allocated) {
            used += v;
        }
        return TOTAL_POINTS - used;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        ui.batch().begin();

        // 标题
        ui.textCentered("降临者 —— 自定义角色", w / 2f, h * 0.88f, TITLE_COLOR, 2.0f);
        ui.textCentered("自由点数：" + remaining() + " / " + TOTAL_POINTS,
                w / 2f, h * 0.80f, GOLD_BRIGHT, 1.4f);

        // 顶部：头像（左）+ 名字（右），都可点击
        float topY = h * 0.68f;
        float avatarSize = 96f;
        float avatarX = w / 2f - 220f;
        float avatarY = topY - avatarSize;

        // 头像框
        avatarRect.set(avatarX, avatarY, avatarSize, avatarSize);
        ui.frame(avatarX, avatarY, avatarSize, avatarSize, GOLD_BRIGHT);
        Texture shownAvatar = customAvatar != null ? customAvatar : game.avatar(CharacterType.DESCENDER);
        if (shownAvatar != null) {
            ui.batch().draw(shownAvatar, avatarX + 4, avatarY + 4, avatarSize - 8, avatarSize - 8);
        }
        ui.textCentered("头像（点击上传）", avatarX + avatarSize / 2f, avatarY - 16, DIM, 0.9f);

        // 名字栏
        float nameW = 260f;
        float nameH = 40f;
        float nameX = w / 2f + 20f;
        float nameY = topY - nameH;
        nameRect.set(nameX, nameY, nameW, nameH);
        ui.rect(nameX, nameY, nameW, nameH, PANEL_BG);
        ui.frame(nameX, nameY, nameW, nameH, FRAME);
        ui.textCentered(name, nameX + nameW / 2f, nameY + nameH - 12, Color.WHITE, 1.2f);
        ui.textCentered("名字（点击修改）", nameX + nameW / 2f, nameY - 16, DIM, 0.9f);

        // 面板：三项属性
        float boxW = 480f;
        float boxH = 240f;
        float bx = w / 2f - boxW / 2f;
        float by = h * 0.22f;
        ui.rect(bx, by, boxW, boxH, PANEL_BG);
        ui.frame(bx, by, boxW, boxH, FRAME);

        float rowTop = by + boxH - 36f;
        float rowGap = 66f;
        float btnSize = 30f;
        for (int i = 0; i < 3; i++) {
            float y = rowTop - i * rowGap;

            ui.text(ATTR_NAMES[i], bx + 30, y, Color.WHITE, 1.2f);
            ui.text(String.valueOf(allocated[i]), bx + 190, y, GOLD_BRIGHT, 1.4f);

            // [-] [+] 按钮（纯鼠标点击，不再有「当前高亮行」）
            float btnY = y - 26;
            float minusX = bx + 250;
            float plusX = bx + 310;

            minusRects[i] = new Rectangle(minusX, btnY, btnSize, btnSize);
            boolean canMinus = allocated[i] > 0;
            Color minusColor = canMinus ? GOLD_BRIGHT : DIM;
            ui.rect(minusX, btnY, btnSize, btnSize, PANEL_BG);
            ui.frame(minusX, btnY, btnSize, btnSize, minusColor);
            ui.textCentered("-", minusX + btnSize / 2f, btnY + btnSize - 4, minusColor, 1.0f);

            plusRects[i] = new Rectangle(plusX, btnY, btnSize, btnSize);
            boolean canPlus = remaining() > 0;
            Color plusColor = canPlus ? GREEN : DIM;
            ui.rect(plusX, btnY, btnSize, btnSize, PANEL_BG);
            ui.frame(plusX, btnY, btnSize, btnSize, plusColor);
            ui.textCentered("+", plusX + btnSize / 2f, btnY + btnSize - 4, plusColor, 1.0f);
        }

        // 底部说明
        ui.textCentered("生命 " + MAX_HP + "   精力 " + MAX_MP, w / 2f, by - 12, DIM, 1.0f);
        ui.textCentered("点击 [-] [+] 分配属性   [R] 重置   [Enter] 确认   [ESC] 返回",
                w / 2f, h * 0.10f, remaining() == 0 ? GREEN : DIM, 1.0f);

        ui.batch().end();

        // 输入放在渲染末尾：此时本帧的点击矩形（nameRect/avatarRect/±按钮）已更新好，
        // 鼠标点击能用「当前帧」的坐标正确命中，避免第一帧点不到。
        handleInput();
    }

    private void handleInput() {
        // 鼠标点击（属性 +/- 按钮、名字栏、头像栏）
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            int sx = Gdx.input.getX();
            int sy = Gdx.graphics.getHeight() - Gdx.input.getY();

            for (int i = 0; i < 3; i++) {
                if (minusRects[i] != null && minusRects[i].contains(sx, sy)) {
                    if (allocated[i] > 0) {
                        allocated[i]--;
                    }
                    return;
                }
                if (plusRects[i] != null && plusRects[i].contains(sx, sy)) {
                    if (remaining() > 0) {
                        allocated[i]++;
                    }
                    return;
                }
            }

            if (nameRect.contains(sx, sy)) {
                promptName();
                return;
            }
            if (avatarRect.contains(sx, sy)) {
                promptAvatar();
                return;
            }
        }

        // 重置
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            for (int i = 0; i < 3; i++) {
                allocated[i] = 0;
            }
        }

        // 确认（点数必须分配完）
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (remaining() == 0) {
                confirm();
            }
        }

        // 返回
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new CharacterSelectScreen(game));
            dispose();
        }
    }

    /** 点名字栏：弹系统输入框，支持中文（0.2.8）。
     *  <p>
     *  不用 {@code Gdx.input.getTextInput}（lwjgl3 桌面端默认不可用/静默失败），
     *  改用 Swing JOptionPane，和头像的文件选择器行为一致、稳定弹出。 */
    private void promptName() {
        try {
            String input = javax.swing.JOptionPane.showInputDialog(null,
                    "输入角色名字（留空或取消则不改）", name);
            if (input != null && !input.trim().isEmpty()) {
                name = input.trim();
            }
        } catch (Throwable t) {
            Gdx.app.log("CustomCharacter", "名字输入失败：" + t.getMessage());
        }
    }

    /** 点头像栏：弹文件选择器，选一张本地图片当头像（0.2.8） */
    private void promptAvatar() {
        // Swing 文件选择器需在非 GL 线程外弹？桌面端 Gdx 直接调用 Swing 会阻塞渲染线程，
        // 但 JFileChooser 是模态的，弹窗期间游戏暂停渲染是允许的（用户在选择中）。
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择头像图片");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "图片 (png / jpg / bmp)", "png", "jpg", "jpeg", "bmp"));
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                FileHandle fh = new FileHandle(chooser.getSelectedFile());
                loadAvatar(fh);
            }
        } catch (Throwable t) {
            Gdx.app.log("CustomCharacter", "头像选择失败：" + t.getMessage());
        }
    }

    /** 从本地文件加载头像纹理并设为当前头像 */
    private void loadAvatar(FileHandle fh) {
        try {
            Texture tex = new Texture(fh);
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            // 释放旧的自定义头像（如果之前传过）
            if (customAvatar != null) {
                customAvatar.dispose();
            }
            customAvatar = tex;
        } catch (Throwable t) {
            Gdx.app.log("CustomCharacter", "头像加载失败：" + t.getMessage());
        }
    }

    private void confirm() {
        Player p = Player.createDescender(allocated[0], allocated[1], allocated[2]);
        p.name = name;   // 自定义名字
        // 自定义头像写入 Main，后续界面按 DESCENDER 取到；
        // 交出去后置 null，避免 dispose() 重复释放这张纹理
        if (customAvatar != null) {
            game.setAvatar(CharacterType.DESCENDER, customAvatar);
            customAvatar = null;
        }
        RunState run = new RunState(System.nanoTime(), p);
        Gdx.app.log("CustomCharacter", "降临者：" + name + "，力 " + allocated[0]
                + " / 准 " + allocated[1] + " / 防 " + allocated[2]);
        game.setScreen(new MapScreen(game, run));
        dispose();
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
        // customAvatar 若已交给 Main（confirm 时 setAvatar）则不该在这里释放；
        // 未确认就返回时，释放掉这个临时头像。
        if (customAvatar != null) {
            customAvatar.dispose();
        }
        ui.dispose();
    }
}
