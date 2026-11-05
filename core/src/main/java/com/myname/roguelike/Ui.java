package com.myname.roguelike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;

/**
 * 画图小工具：面板底、边框、血条、文字、连线。
 * <p>
 * 为什么不直接用 ShapeRenderer：ShapeRenderer 有自己的 begin/end，
 * 和 SpriteBatch 混用时必须严格交替，界面一多就很容易漏掉 end 导致崩溃。
 * 这里用一张 1x1 白图拉伸来画所有矩形，全程只在 batch 里，不会冲突。
 * <p>
 * 每个 Screen 各自持有一个实例（各自一份 1x1 纹理），在 dispose 里释放。
 */
public class Ui {

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    /** 1x1 白图 */
    public final Texture white;

    public Ui(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        this.white = new Texture(pixmap);
        pixmap.dispose();
    }

    public void dispose() {
        white.dispose();
    }

    // ==================== 矩形 ====================

    public void rect(float x, float y, float w, float h, Color color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        batch.setColor(color);
        batch.draw(white, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /** 1px 边框 */
    public void frame(float x, float y, float w, float h, Color color) {
        rect(x, y, w, 1, color);
        rect(x, y + h - 1, w, 1, color);
        rect(x, y, 1, h, color);
        rect(x + w - 1, y, 1, h, color);
    }

    /** 进度条：先铺底槽再填前景 */
    public void bar(float x, float y, float w, float h, float ratio, Color fill, Color bg) {
        rect(x, y, w, h, bg);
        rect(x, y, w * Math.max(0f, Math.min(1f, ratio)), h, fill);
    }

    /**
     * 任意两点之间的线段。
     * <p>
     * 用 1x1 白图拉长 + 旋转实现：把绘制原点设在起点，
     * 长度作为宽度、厚度作为高度，再按两点夹角旋转。
     */
    public void line(float x1, float y1, float x2, float y2, float thickness, Color color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) {
            return;
        }
        float degrees = (float) Math.toDegrees(Math.atan2(dy, dx));
        batch.setColor(color);
        batch.draw(white,
                x1, y1 - thickness / 2f,   // 位置（让线段的中心落在连线上）
                0, thickness / 2f,         // 旋转原点
                len, thickness,            // 宽高
                1f, 1f,                    // 缩放
                degrees,                   // 旋转角度
                0, 0, 1, 1,                // 源区域（整张 1x1）
                false, false);
        batch.setColor(Color.WHITE);
    }

    // ==================== 文字 ====================

    /**
     * 画一行文字。
     * <p>
     * 注意 {@code y} 是**文字顶部**，文字占 [y - 行高, y]。
     * 血条要画在 y - 行高 之下，否则会压住字。
     */
    public void text(String s, float x, float y, Color color, float scale) {
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(batch, s, x, y);
    }

    public void textCentered(String s, float centerX, float y, Color color, float scale) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, s);
        font.draw(batch, s, centerX - layout.width / 2f, y);
    }

    /** 自动换行的段落，返回占用的高度 */
    public float textWrapped(String s, float x, float y, float width, Color color, float scale) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, s, color, width, Align.left, true);
        font.draw(batch, layout, x, y);
        return layout.height;
    }

    /** 量一段不换行文字的宽度（字体缩放会变，所以每次都要先设缩放） */
    public float measure(String s, float scale) {
        font.getData().setScale(scale);
        layout.setText(font, s);
        return layout.width;
    }

    public BitmapFont font() {
        return font;
    }

    public SpriteBatch batch() {
        return batch;
    }

    // ==================== 浮窗（0.3.2 悬停详情）====================

    /**
     * 在鼠标位置右下方画一个半透明黑色浮窗，白字显示完整描述。
     * <p>
     * 自动换行到 {@code maxWidth}，浮窗高度按内容自适应；超出屏幕右/下边就向左/上翻，
     * 避免浮窗跑到窗口外面看不见。画在最上层（调用方保证它在其它绘制之后调用）。
     *
     * @param text     描述文字（可多行，内部自动换行）
     * @param mouseX   鼠标屏幕坐标 x（Gdx.input.getX()，原点左上）
     * @param mouseY   鼠标屏幕坐标 y（Gdx.input.getY()，原点左上）
     * @param maxWidth 浮窗最大宽度
     */
    public void drawTooltip(String text, float mouseX, float mouseY, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return;
        }
        font.getData().setScale(1f);
        layout.setText(font, text, Color.WHITE, maxWidth - 24f, Align.left, true);

        float pad = 12f;
        float boxW = layout.width + pad * 2;
        float boxH = layout.height + pad * 2;

        // 屏幕坐标：原点在左上。鼠标右下方 = x 右移、y 下移（屏幕 y 向下增）。
        float sx = mouseX + 18f;
        float sy = mouseY + 24f;

        // 越界则翻转：浮窗底边超出屏幕底部 -> 放到鼠标上方；右边超出 -> 放到鼠标左侧
        if (sy - boxH < 0) {
            sy = mouseY - 12f;   // 浮窗底边贴鼠标上方
        }
        if (sx + boxW > Gdx.graphics.getWidth()) {
            sx = mouseX - boxW - 18f;
        }

        // 半透明黑底 + 细边框
        batch.setColor(0f, 0f, 0f, 0.92f);
        batch.draw(white, sx, sy - boxH, boxW, boxH);
        batch.setColor(0.55f, 0.55f, 0.60f, 0.9f);
        // 边框用四条细矩形拼
        batch.draw(white, sx, sy - boxH, boxW, 1f);
        batch.draw(white, sx, sy - 1f, boxW, 1f);
        batch.draw(white, sx, sy - boxH, 1f, boxH);
        batch.draw(white, sx + boxW - 1f, sy - boxH, 1f, boxH);
        batch.setColor(Color.WHITE);

        // 白字正文，左上留 pad
        font.setColor(Color.WHITE);
        font.draw(batch, layout, sx + pad, sy - pad);
        font.setColor(Color.WHITE);
    }
}
