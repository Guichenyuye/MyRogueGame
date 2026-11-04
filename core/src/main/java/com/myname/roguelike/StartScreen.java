package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * 主菜单 —— 就是你原来 {@code Main} 里那段打字机标题 + 两个选项，
 * 原样搬到这里，只为适配「Screen 之间能互相切换」。
 */
public class StartScreen implements Screen {

    private final Main game;
    private final GlyphLayout layout = new GlyphLayout();

    private final String titleText = "MY ROGUELIKE";
    private int titleVisibleChars = 0;
    private float typeTimer = 0;
    private boolean titleFinished = false;

    public StartScreen(Main game) {
        this.game = game;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // 打字机效果
        if (!titleFinished) {
            typeTimer += delta;
            if (typeTimer > 0.1f) {
                titleVisibleChars++;
                typeTimer = 0;
                if (titleVisibleChars >= titleText.length()) {
                    titleFinished = true;
                }
            }
        }

        SpriteBatch batch = game.batch;
        BitmapFont font = game.font;
        batch.begin();

        // 标题
        String currentTitle = titleText.substring(0, Math.min(titleVisibleChars, titleText.length()));
        drawCentered(batch, font, currentTitle, screenWidth / 2f, screenHeight * 0.75f, 2.0f, Color.WHITE);

        if (titleFinished) {
            // 中文副标题：顺便验证中文字形能不能画出来
            // 注意：不要用「·」这个字符，这套补字库里它是空框占位符
            drawCentered(batch, font, "—— 回合制肉鸽 ——",
                    screenWidth / 2f, screenHeight * 0.63f, 1.0f, new Color(0.55f, 0.55f, 0.6f, 1f));

            drawCentered(batch, font, "[1] Start Game", screenWidth / 2f, screenHeight / 2f, 1.2f, Color.YELLOW);
            drawCentered(batch, font, "[2] Exit", screenWidth / 2f, screenHeight / 2f - 60, 1.2f, Color.LIGHT_GRAY);
        }

        batch.end();

        // 按键
        if (titleFinished) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                // 0.2.4：先选角色，再开局。角色选择界面会创建 RunState 并进地图。
                game.setScreen(new CharacterSelectScreen(game));
                dispose();
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                Gdx.app.exit();
            }
        }
    }

    private void drawCentered(SpriteBatch batch, BitmapFont font, String text,
                              float centerX, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, centerX - layout.width / 2f, y);
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
        // 字体和 batch 是 Main 的共享资源，这里不释放
    }
}
