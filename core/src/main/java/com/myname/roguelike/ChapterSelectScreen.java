package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Rectangle;

/**
 * 章节选择界面（0.3.1）。
 * <p>
 * 选完角色后、进入地图前，让玩家选章节（当前默认测试第二章「南边领地」）。
 * 纯鼠标点选（和角色选择一致），也可以按 [1]/[2] 快捷选择。
 */
public class ChapterSelectScreen implements Screen {

    private static final Color BG = new Color(0.05f, 0.05f, 0.06f, 1f);
    private static final Color PANEL_BG = new Color(0.09f, 0.10f, 0.12f, 0.94f);
    private static final Color FRAME = new Color(0.30f, 0.34f, 0.42f, 1f);
    private static final Color TITLE_COLOR = new Color(0.95f, 0.82f, 0.30f, 1f);
    private static final Color GREEN = new Color(0.45f, 0.85f, 0.45f, 1f);
    private static final Color BLUE = new Color(0.45f, 0.78f, 0.98f, 1f);
    private static final Color DIM = new Color(0.72f, 0.72f, 0.76f, 1f);
    private static final Color HOVER = new Color(0.26f, 0.68f, 0.31f, 1f);

    private final Main game;
    private final Ui ui;
    /** 已选定的角色类型（普通角色用） */
    private final CharacterType type;
    /** 降临者用：已经捏脸构造好的 Player（非 null 时优先用它） */
    private final Player prebuiltPlayer;

    /** 两个章节选项的命中区域 */
    private final Rectangle[] chapterRects = new Rectangle[2];

    private final Chapter[] chapters = { Chapter.SWAMP, Chapter.SOUTHERN_LAND };
    private final String[] chapterDescs = {
            "史莱姆为主、骸骨为辅的潮湿森林",
            "骑士为主的南方平原（测试第二章）"
    };

    public ChapterSelectScreen(Main game, CharacterType type) {
        this(game, type, null);
    }

    /** prebuiltPlayer 非 null 时表示降临者（已捏脸），进入地图时直接用这个 Player */
    public ChapterSelectScreen(Main game, CharacterType type, Player prebuiltPlayer) {
        this.game = game;
        this.ui = new Ui(game.batch, game.font);
        this.type = type;
        this.prebuiltPlayer = prebuiltPlayer;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        int mx = Gdx.input.getX();
        int my = Gdx.graphics.getHeight() - Gdx.input.getY();

        ui.batch().begin();

        ui.textCentered("选择章节", w / 2f, h * 0.82f, TITLE_COLOR, 2.0f);

        float boxW = 480f;
        float boxH = 86f;
        float gap = 30f;
        float totalH = boxH * 2 + gap;
        float startY = h * 0.52f;

        for (int i = 0; i < chapters.length; i++) {
            float bx = w / 2f - boxW / 2f;
            float by = startY - i * (boxH + gap);
            chapterRects[i] = new Rectangle(bx, by, boxW, boxH);

            boolean hover = chapterRects[i].contains(mx, my);
            ui.rect(bx, by, boxW, boxH, hover ? new Color(0.16f, 0.18f, 0.20f, 1f) : PANEL_BG);
            ui.frame(bx, by, boxW, boxH, hover ? GREEN : FRAME);

            ui.textCentered("[" + (i + 1) + "] " + chapters[i].name,
                    w / 2f, by + boxH - 28, hover ? Color.WHITE : BLUE, 1.4f);
            ui.textCentered(chapterDescs[i], w / 2f, by + boxH - 58, DIM, 0.9f);
        }

        ui.textCentered("点击选择章节    [ESC] 返回角色选择",
                w / 2f, h * 0.12f, DIM, 1.0f);

        ui.batch().end();

        handleInput();
    }

    private void handleInput() {
        int mx = Gdx.input.getX();
        int my = Gdx.graphics.getHeight() - Gdx.input.getY();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            for (int i = 0; i < chapterRects.length; i++) {
                if (chapterRects[i] != null && chapterRects[i].contains(mx, my)) {
                    enter(chapters[i]);
                    return;
                }
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            enter(chapters[0]);
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            enter(chapters[1]);
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new CharacterSelectScreen(game));
            dispose();
        }
    }

    private void enter(Chapter chapter) {
        RunState run;
        if (prebuiltPlayer != null) {
            run = new RunState(System.nanoTime(), prebuiltPlayer);
            run.currentChapter = chapter;
        } else {
            run = new RunState(System.nanoTime(), type, chapter);
        }
        Gdx.app.log("ChapterSelect", "进入章节：" + chapter.name);
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
        ui.dispose();
    }
}
